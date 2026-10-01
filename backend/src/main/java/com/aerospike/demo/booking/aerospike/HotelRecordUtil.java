package com.aerospike.demo.booking.aerospike;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.aerospike.client.sdk.Record;
import com.aerospike.demo.booking.model.GeoPoint;
import com.aerospike.demo.booking.util.Dates;
import com.aerospike.demo.booking.util.Json;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Shared helpers for turning a raw hotel {@link Record} (bin-level: whatever the SDK's CDT
 * getters return - AerospikeMap/AerospikeList/Long/String/etc, all plain {@link Map}/{@link List}
 * implementations) into the JSON shapes the API contract specifies.
 */
public final class HotelRecordUtil {
    private HotelRecordUtil() {
    }

    /** {@code {hotelId,name,city,neighborhood,stars,rating,reviewCount,minPrice,propertyType,location}}. */
    public static Map<String, Object> toSearchResultItem(Record rec) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("hotelId", rec.getString("hotelId"));
        out.put("name", rec.getString("name"));
        out.put("city", rec.getString("city"));
        out.put("neighborhood", rec.getString("neighborhood"));
        out.put("stars", rec.getInt("stars"));
        out.put("rating", rec.getInt("rating"));
        out.put("reviewCount", rec.getInt("reviewCount"));
        out.put("minPrice", minPrice(rec.getMap("rooms")));
        out.put("propertyType", rec.getString("propertyType"));
        out.put("location", geoPoint(rec).toApi());
        return out;
    }

    /**
     * Full property-detail shape, with each room's "rates" filtered to [checkIn, checkOut].
     * {@code filteredRatesByRoom} is the server-side path-expression projection's result
     * (roomId -&gt; {@code {rates: [...]}}, already filtered to the overlapping segments) - see
     * {@link com.aerospike.demo.booking.service.HotelDetailService#projectRatesByPath} for how
     * it's built. Room-level fields (bed, sqm, etc.) still come from the plain {@code rec} read,
     * since a CDT path selection can't keep sibling fields alongside a transformed nested one.
     */
    public static Map<String, Object> toHotelDetail(
            Record rec, Map<?, ?> filteredRatesByRoom, long checkIn, long checkOut) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("hotelId", rec.getString("hotelId"));
        out.put("name", rec.getString("name"));
        out.put("city", rec.getString("city"));
        out.put("neighborhood", rec.getString("neighborhood"));
        out.put("locality", rec.getString("locality"));
        out.put("propertyType", rec.getString("propertyType"));
        out.put("location", geoPoint(rec).toApi());
        out.put("stars", rec.getInt("stars"));
        out.put("rating", rec.getInt("rating"));
        out.put("reviewCount", rec.getInt("reviewCount"));
        out.put("amenities", rec.getList("amenities"));
        out.put("rooms", projectRooms(rec.getMap("rooms"), filteredRatesByRoom, checkIn, checkOut));
        return out;
    }

    public static GeoPoint geoPoint(Record rec) {
        String geoJson = rec.getGeoJSON("location");
        try {
            JsonNode node = Json.MAPPER.readTree(geoJson);
            JsonNode coords = node.get("coordinates");
            return new GeoPoint(coords.get(0).asDouble(), coords.get(1).asDouble());
        } catch (Exception e) {
            throw new IllegalStateException("Malformed location GeoJSON on record: " + geoJson, e);
        }
    }

    /**
     * Rank-0 (minimum) nightly price across every rate of every room - the same value
     * hotel-minprice-idx indexes, computed here in Java for display since /search doesn't project
     * a server-side expression read for it (see backend/README.md).
     */
    public static long minPrice(Map<?, ?> rooms) {
        long min = Long.MAX_VALUE;
        if (rooms != null) {
            for (Object roomObj : rooms.values()) {
                Map<?, ?> room = (Map<?, ?>) roomObj;
                List<?> rates = (List<?>) room.get("rates");
                if (rates == null) {
                    continue;
                }
                for (Object rateObj : rates) {
                    Map<?, ?> rate = (Map<?, ?>) rateObj;
                    long price = ((Number) rate.get("price")).longValue();
                    if (price < min) {
                        min = price;
                    }
                }
            }
        }
        return min == Long.MAX_VALUE ? 0 : min;
    }

    /**
     * At least one (room, rate) has an "available" day in [checkIn, checkOut] - the same
     * predicate as design.md's {@code $.rooms..rates..available.[?(@ >= X and @ <= Y)].count() > 0}
     * AEL clause, applied here in Java instead of as a classic Exp-tree filter. See
     * {@link ClassicFilters}'s class Javadoc for why: {@code CdtExp.selectByPath} was confirmed
     * (against a real server, not assumed) to flatten nested *scalar* leaves correctly but not
     * nested *list*-typed leaves like "available", so every value/range-match attempt against it
     * came back zero. This is checked against every candidate record returned by the classic-Exp
     * scan (SearchService), not instead of it - the index-selecting and scalar predicates still
     * run server-side; only this one nested-list check runs here.
     */
    public static boolean hasDateCoverage(Map<?, ?> rooms, long checkIn, long checkOut) {
        if (rooms == null) {
            return false;
        }
        for (Object roomObj : rooms.values()) {
            Map<?, ?> room = (Map<?, ?>) roomObj;
            List<?> rates = (List<?>) room.get("rates");
            if (rates == null) {
                continue;
            }
            for (Object rateObj : rates) {
                Map<?, ?> rate = (Map<?, ?>) rateObj;
                List<?> available = (List<?>) rate.get("available");
                if (available == null) {
                    continue;
                }
                for (Object dayObj : available) {
                    long day = ((Number) dayObj).longValue();
                    if (day >= checkIn && day <= checkOut) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * The property-detail path-expression projection (docs/design.md step 4 / "Property detail"
     * row): for every room, only the rate segments whose [from,to] period overlaps
     * [checkIn, checkOut] cross into the response, each still carrying its full available/booked
     * lists for that period. The overlap decision itself is made server-side now - see
     * {@link com.aerospike.demo.booking.service.HotelDetailService#projectRatesByPath}, a real
     * {@code CdtOperation.selectByPath} path expression confirmed against a live server (not
     * assumed) - this method just merges that result with each room's other fields, which a CTX
     * path selection can't carry alongside a transformed nested one in a single call.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> projectRooms(
            Map<?, ?> rooms, Map<?, ?> filteredRatesByRoom, long checkIn, long checkOut) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (rooms == null) {
            return out;
        }
        List<Long> nights = Dates.nights(checkIn, checkOut);
        for (Map.Entry<?, ?> entry : rooms.entrySet()) {
            String roomId = (String) entry.getKey();
            Map<String, Object> room = (Map<String, Object>) entry.getValue();
            Map<String, Object> roomOut = new LinkedHashMap<>();
            roomOut.put("bed", room.get("bed"));
            roomOut.put("sqm", room.get("sqm"));
            roomOut.put("view", room.get("view"));
            roomOut.put("maxOccupancy", room.get("maxOccupancy"));
            roomOut.put("nonSmoking", room.get("nonSmoking"));
            roomOut.put("amenities", room.get("amenities"));

            List<Map<String, Object>> matchingRates = List.of();
            Object filteredRoom = filteredRatesByRoom == null ? null : filteredRatesByRoom.get(roomId);
            if (filteredRoom instanceof Map) {
                Object ratesObj = ((Map<?, ?>) filteredRoom).get("rates");
                if (ratesObj instanceof List) {
                    matchingRates = (List<Map<String, Object>>) ratesObj;
                }
            }
            roomOut.put("rates", matchingRates);

            // Whether the *whole* stay is bookable, and its true total price - not "does at least
            // one rate row cover this", and not price-per-rate * nights. A rate period is a
            // contiguous weekday-only or weekend-only band (see datagen's RateGenerator), so a
            // multi-night stay routinely straddles two of them (a Sat-Mon stay needs a weekend
            // night from one segment and a weekday night from the next) - no single row's
            // "available" list covers every requested night, even though the room genuinely has
            // all of them free. Confirmed empirically: with today's data, a plain 3-night default
            // search window matched zero single-rate-covers-everything rooms out of 39 sampled,
            // even though every one of those nights was actually free somewhere in the room's rate
            // list. Checking per-night across every overlapping segment (matched here) instead of
            // requiring one segment to cover the whole range is what BookingService also does when
            // it actually removes the nights, so this flag and totalPrice stay consistent with what
            // a booking attempt will really do.
            long totalPrice = 0;
            boolean bookable = !nights.isEmpty();
            for (Long night : nights) {
                Long priceForNight = priceOfSegmentCoveringNight(matchingRates, night);
                if (priceForNight == null) {
                    bookable = false;
                } else {
                    totalPrice += priceForNight;
                }
            }
            roomOut.put("available", bookable);
            roomOut.put("totalPrice", bookable ? totalPrice : null);

            out.put(roomId, roomOut);
        }
        return out;
    }

    private static Long priceOfSegmentCoveringNight(List<Map<String, Object>> rateSegments, long night) {
        for (Map<String, Object> rate : rateSegments) {
            List<?> available = (List<?>) rate.get("available");
            if (available == null) {
                continue;
            }
            for (Object dayObj : available) {
                if (((Number) dayObj).longValue() == night) {
                    return ((Number) rate.get("price")).longValue();
                }
            }
        }
        return null;
    }
}
