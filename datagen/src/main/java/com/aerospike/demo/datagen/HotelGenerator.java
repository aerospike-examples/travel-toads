package com.aerospike.demo.datagen;

import com.aerospike.demo.datagen.model.GeoPoint;
import com.aerospike.demo.datagen.model.Hotel;
import com.aerospike.demo.datagen.model.Room;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Builds one {@link Hotel} record, following every choice documented in docs/design.md's data
 * model and demo-dataset sections: whole-dollar prices, 0-100 integer rating, amenity codes (not
 * display strings), no {@code wifi}/{@code parking} chips, and the {@code locality} slug computed
 * here exactly as the RecordMapper is supposed to compute it in the real app.
 */
final class HotelGenerator {

    // Hotel-level amenities. wifi/parking deliberately excluded — see docs/design.md: "a chip
    // matching every record is a dead chip on camera."
    private static final List<String> HOTEL_AMENITIES = List.of(
            "breakfast_included", "free_cancellation", "pool", "fitness", "restaurant",
            "room_service", "airport_shuttle", "spa", "business_center", "pet_friendly",
            "laundry", "bar", "has_view");

    private static final List<String> ROOM_AMENITIES = List.of(
            "air_conditioning", "private_bathroom", "balcony", "tv", "coffee_maker",
            "refrigerator", "bath", "kitchenette", "safe", "desk", "minibar", "iron", "hairdryer");

    private static final List<String> NON_DORM_BEDS = List.of("king", "queen", "twin", "double", "sofa_bed");
    private static final List<String> VIEWS = List.of("pool", "street", "garden", "city", "courtyard", "lake", "hill_country");

    /** [minStars, maxStars, minWeekdayPrice, maxWeekdayPrice] per property type. */
    private static final Map<String, int[]> PRICE_BANDS = Map.of(
            "hostel", new int[] { 1, 2, 22, 50 },
            "motel", new int[] { 1, 3, 55, 95 },
            "apartment", new int[] { 2, 4, 85, 175 },
            "hotel", new int[] { 2, 5, 90, 260 },
            "bed_and_breakfast", new int[] { 2, 4, 80, 155 },
            "resort", new int[] { 4, 5, 180, 420 });

    // Event week offset/length within the date window — an arbitrary but fixed mid-window span
    // (the design doc calls this "the SXSW sold-out week"; the generator is date-relative, so
    // this is a stand-in event slot rather than a literal SXSW-dated week).
    private static final int EVENT_WEEK_OFFSET_DAYS = 21;
    private static final int EVENT_WEEK_LENGTH_DAYS = 7;
    private static final double AUSTIN_EVENT_SOLDOUT_PROBABILITY = 0.55;

    private HotelGenerator() {
    }

    static Hotel generate(
            int hotelIndex,
            CityConfig city,
            LocalDate startDate,
            int days,
            ReservationIdCounter reservationIds,
            Random rng) {

        String propertyType = weightedPick(city.propertyTypeWeights(), rng);
        String neighborhood = city.neighborhoods().get(rng.nextInt(city.neighborhoods().size()));
        String locality = slug(city.name()) + "-" + slug(neighborhood);

        int[] band = PRICE_BANDS.get(propertyType);
        int stars = band[0] + rng.nextInt(band[1] - band[0] + 1);
        int weekdayBasePrice = band[2] + rng.nextInt(band[3] - band[2] + 1);
        double weekendMultiplier = 1.10 + rng.nextDouble() * 0.25;

        int rating = clamp(stars * 16 + (rng.nextInt(17) - 8), 30, 99);
        int reviewCount = 15 + rng.nextInt(40 * stars + 1);

        GeoPoint location = jitteredLocation(city, rng);

        Set<String> hotelAmenities = pickAmenities(HOTEL_AMENITIES, clamp(stars + 2, 3, HOTEL_AMENITIES.size()), rng);

        LocalDate eventStart = startDate.plusDays(EVENT_WEEK_OFFSET_DAYS);
        LocalDate eventEnd = eventStart.plusDays(EVENT_WEEK_LENGTH_DAYS - 1);
        boolean eventSoldOut = city.name().equals("Austin") && rng.nextDouble() < AUSTIN_EVENT_SOLDOUT_PROBABILITY;

        int roomCount = 10 + rng.nextInt(31); // 10..40 inclusive
        Map<String, Room> rooms = new LinkedHashMap<>();
        for (int r = 0; r < roomCount; r++) {
            String roomId = "room-" + (101 + r);
            rooms.put(roomId, generateRoom(propertyType, weekdayBasePrice, weekendMultiplier,
                    startDate, days, eventStart, eventEnd, eventSoldOut, reservationIds, rng));
        }

        return new Hotel(
                "HTL-" + String.format("%04d", hotelIndex),
                NameGenerator.forProperty(propertyType, rng),
                city.name(),
                neighborhood,
                locality,
                propertyType,
                location,
                stars,
                rating,
                reviewCount,
                List.copyOf(hotelAmenities),
                rooms);
    }

    private static Room generateRoom(
            String propertyType,
            int weekdayBasePrice,
            double weekendMultiplier,
            LocalDate startDate,
            int days,
            LocalDate eventStart,
            LocalDate eventEnd,
            boolean eventSoldOut,
            ReservationIdCounter reservationIds,
            Random rng) {

        boolean dorm = propertyType.equals("hostel") && rng.nextDouble() < 0.6;
        String bed = dorm ? "bunk" : NON_DORM_BEDS.get(rng.nextInt(NON_DORM_BEDS.size()));
        String view = VIEWS.get(rng.nextInt(VIEWS.size()));

        int sqm = dorm ? 8 + rng.nextInt(9) : 18 + rng.nextInt(38);
        int maxOccupancy = dorm ? 4 + rng.nextInt(3) : (bed.equals("twin") ? 2 : 2 + rng.nextInt(2));
        boolean nonSmoking = rng.nextDouble() < 0.92;

        Set<String> amenities = new LinkedHashSet<>();
        if (rng.nextDouble() < 0.9) amenities.add("air_conditioning");
        if (rng.nextDouble() < 0.9) amenities.add("private_bathroom");
        amenities.addAll(pickAmenities(ROOM_AMENITIES, 2 + rng.nextInt(4), rng));

        // Per-room price varies a little around the hotel's base band so not every room is
        // identically priced.
        double roomJitter = 0.85 + rng.nextDouble() * 0.5;
        int weekdayPrice = Math.max(15, (int) Math.round(weekdayBasePrice * roomJitter));
        int weekendPrice = Math.max(weekdayPrice + 5, (int) Math.round(weekdayPrice * weekendMultiplier));

        var rates = RateGenerator.generate(startDate, days, weekdayPrice, weekendPrice,
                eventStart, eventEnd, eventSoldOut, reservationIds, rng);

        return new Room(bed, sqm, view, maxOccupancy, nonSmoking, List.copyOf(amenities), rates);
    }

    private static GeoPoint jitteredLocation(CityConfig city, Random rng) {
        // Scatter within roughly a several-km radius of the city center. The design doc gives
        // only city-level centers, not per-neighborhood coordinates, so this is an approximation
        // of city extent rather than a true per-neighborhood placement.
        double latJitter = (rng.nextDouble() - 0.5) * 0.09; // ~±5 km
        double lonSpread = 0.09 / Math.cos(Math.toRadians(city.centerLat()));
        double lonJitter = (rng.nextDouble() - 0.5) * lonSpread;
        return GeoPoint.of(city.centerLon() + lonJitter, city.centerLat() + latJitter);
    }

    private static Set<String> pickAmenities(List<String> pool, int count, Random rng) {
        List<String> shuffled = new ArrayList<>(pool);
        java.util.Collections.shuffle(shuffled, rng);
        return new LinkedHashSet<>(shuffled.subList(0, Math.min(count, shuffled.size())));
    }

    private static String weightedPick(Map<String, Double> weights, Random rng) {
        double total = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        double target = rng.nextDouble() * total;
        double cumulative = 0;
        for (var entry : weights.entrySet()) {
            cumulative += entry.getValue();
            if (target <= cumulative) {
                return entry.getKey();
            }
        }
        return weights.keySet().iterator().next();
    }

    private static String slug(String s) {
        return s.toLowerCase()
                .replace(" ", "-")
                .replaceAll("[^a-z0-9-]", "");
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
