package com.aerospike.demo.booking.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aerospike.client.sdk.AerospikeException;
import com.aerospike.client.sdk.Key;
import com.aerospike.client.sdk.Record;
import com.aerospike.client.sdk.RecordResult;
import com.aerospike.client.sdk.RecordStream;
import com.aerospike.client.sdk.ResultCode;
import com.aerospike.client.sdk.Session;
import com.aerospike.demo.booking.aerospike.AerospikeService;
import com.aerospike.demo.booking.data.ScenarioRegistry;
import com.aerospike.demo.booking.util.Dates;
import com.aerospike.demo.booking.web.ApiException;
import com.aerospike.demo.booking.web.dto.BookingRequest;

/**
 * POST /bookings. Per docs/design.md step 6 and scenarios/booking.json: atomically remove the
 * requested nights from a rate's "available" list, and only if every requested night was actually
 * there, append the reservation to "booked". The remove is a plain CDT remove-by-value-list, whose
 * returned count is asserted against the number of nights requested - this is the trap design.md
 * calls out: remove-by-value on an already-gone day is a silent no-op, so without this check a
 * second booker would "succeed" without the room.
 *
 * <p><b>Booking spans whichever rate segments the stay actually touches, not one.</b> A rate
 * period is a contiguous weekday-only or weekend-only band (see datagen's RateGenerator), so a
 * multi-night stay routinely needs nights from more than one - e.g. a Sat-Mon stay pulls a weekend
 * night from one segment and a weekday night from the next. Requiring a single rate to cover the
 * whole stay (the original shape of this method, keyed on one {@code rateId}) meant almost no
 * realistic multi-night booking ever succeeded: confirmed empirically against live data, zero of
 * 39 sampled hotels had any single rate row covering the frontend's own default 3-night search
 * window, even though every one of those nights was genuinely free somewhere on the room. This
 * version instead groups the requested nights by whichever segment's "available" list currently
 * has each one (see {@link HotelRecordUtil}'s {@code projectRooms}, which computes the same
 * per-night-across-every-segment coverage for the read side so the two stay consistent) and touches
 * every segment the stay needs.</p>
 *
 * <p><b>Deviation from a single combined operate() call:</b> each segment's remove and append are
 * two separate atomic single-record operations (not one operate() containing both), specifically
 * so the append can be skipped entirely when that segment's count check fails - a single operate()
 * runs every operation in the call regardless of an earlier operation's own result, so combining
 * them would mean either always appending (defeating the check) or appending-then-compensating
 * after the fact. This does still leave a narrow window, between a segment's remove and its
 * append, where its nights are gone from "available" but not yet recorded in "booked" - acceptable
 * for a demo; see backend/README.md.</p>
 */
public final class BookingService {
    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final AerospikeService aerospike;
    private final ScenarioRegistry scenarios;

    public BookingService(AerospikeService aerospike, ScenarioRegistry scenarios) {
        this.aerospike = aerospike;
        this.scenarios = scenarios;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> book(BookingRequest req) {
        Session session = aerospike.session();
        Key key = aerospike.hotelsDataSet().id(req.hotelId);

        Record rec = readHotel(session, key, req.hotelId);

        Map<String, Object> rooms = (Map<String, Object>) rec.getMap("rooms");
        Object roomObj = rooms == null ? null : rooms.get(req.roomId);
        if (roomObj == null) {
            throw ApiException.notFound("No room " + req.roomId + " on hotel " + req.hotelId);
        }
        Map<String, Object> room = (Map<String, Object>) roomObj;
        List<Object> rates = (List<Object>) room.get("rates");
        if (rates == null) {
            rates = List.of();
        }

        List<Long> nights = Dates.nights(req.checkIn, req.checkOut);
        if (nights.isEmpty()) {
            throw ApiException.badRequest("checkOut must be after checkIn");
        }

        // Which rate segment currently has each requested night in its "available" list - a night
        // with no match here (already booked, or never existed) just won't appear in any group, so
        // it naturally falls out of totalRemoved below and trips the same-count check.
        Map<Integer, List<Long>> nightsByRateIndex = new LinkedHashMap<>();
        for (Long night : nights) {
            int idx = findRateIndexWithAvailableNight(rates, night);
            if (idx >= 0) {
                nightsByRateIndex.computeIfAbsent(idx, k -> new java.util.ArrayList<>()).add(night);
            }
        }

        String reservationId = "RES-" + Math.abs(UUID.randomUUID().getMostSignificantBits() % 100_000);

        Map<Integer, List<?>> actuallyRemovedByRateIndex = new LinkedHashMap<>();
        int totalRemoved = 0;
        for (Map.Entry<Integer, List<Long>> e : nightsByRateIndex.entrySet()) {
            List<?> removed = removeNightsFromAvailable(session, key, req.roomId, e.getKey(), e.getValue());
            actuallyRemovedByRateIndex.put(e.getKey(), removed);
            totalRemoved += removed.size();
        }

        if (totalRemoved != nights.size()) {
            // Compensate: put back whatever we did manage to remove from every touched segment,
            // then fail the booking.
            for (Map.Entry<Integer, List<?>> e : actuallyRemovedByRateIndex.entrySet()) {
                if (!e.getValue().isEmpty()) {
                    session.update(key)
                            .bin("rooms").onMapKey(req.roomId).onMapKey("rates").onListIndex(e.getKey())
                            .onMapKey("available").listAppendItems(e.getValue())
                            .execute();
                }
            }
            log.info("Booking rejected for {}/{} {}..{}: requested {} nights, only {} were available",
                    req.hotelId, req.roomId, req.checkIn, req.checkOut, nights.size(), totalRemoved);
            throw ApiException.conflict("ROOM_NOT_AVAILABLE",
                    "Only " + totalRemoved + " of the " + nights.size()
                            + " requested nights are still available - someone else booked first.");
        }

        long totalPrice = 0;
        // Which rate segment(s) actually ended up holding this reservation - almost always one,
        // but a stay spanning a rate-period boundary touches two (see this class's own doc
        // comment). "rates" is a CDT *list*, not a map, so Voyager displays it by numeric list
        // position, not by rateId - the rateId alone doesn't tell you which entry to expand, you'd
        // still have to open each one and check. Returning both together (the same rateIndex this
        // method already used to address the CDT operations above) is what actually lets the
        // caller (currently the Presenter Panel) point at rooms.{roomId}.rates[index] directly.
        List<Map<String, Object>> rateSegments = new java.util.ArrayList<>();
        for (Map.Entry<Integer, List<Long>> e : nightsByRateIndex.entrySet()) {
            int rateIndex = e.getKey();
            Map<String, Object> rate = (Map<String, Object>) rates.get(rateIndex);
            long price = ((Number) rate.get("price")).longValue();
            totalPrice += price * e.getValue().size();
            Map<String, Object> segment = new LinkedHashMap<>();
            segment.put("index", rateIndex);
            segment.put("rateId", rate.get("rateId"));
            rateSegments.add(segment);
            appendBookedEntries(session, key, req, rateIndex, e.getValue(), reservationId);
        }

        Map<String, Object> demoValues = new LinkedHashMap<>();
        demoValues.put("roomId", req.roomId);
        demoValues.put("checkIn", req.checkIn);
        demoValues.put("checkOut", req.checkOut);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("reservationId", reservationId);
        response.put("status", "confirmed");
        response.put("totalPrice", totalPrice);
        response.put("rateSegments", rateSegments);
        response.put("demo", req.demo ? scenarios.buildDemo("booking", demoValues) : null);
        return response;
    }

    private static int findRateIndexWithAvailableNight(List<?> rates, long night) {
        for (int i = 0; i < rates.size(); i++) {
            Map<?, ?> rate = (Map<?, ?>) rates.get(i);
            List<?> available = (List<?>) rate.get("available");
            if (available == null) {
                continue;
            }
            for (Object d : available) {
                if (((Number) d).longValue() == night) {
                    return i;
                }
            }
        }
        return -1;
    }

    private Record readHotel(Session session, Key key, String hotelId) {
        try (RecordStream rs = session.query(key).execute()) {
            if (!rs.hasNext()) {
                throw ApiException.notFound("No hotel with id " + hotelId);
            }
            RecordResult rr = rs.next();
            return rr.recordOrThrow();
        } catch (AerospikeException e) {
            if (e.getResultCode() == ResultCode.KEY_NOT_FOUND_ERROR) {
                throw ApiException.notFound("No hotel with id " + hotelId);
            }
            throw e;
        }
    }

    private static List<?> removeNightsFromAvailable(
            Session session, Key key, String roomId, int rateIndex, List<Long> nights) {
        RecordStream rs = session.update(key)
                .bin("rooms").onMapKey(roomId).onMapKey("rates").onListIndex(rateIndex)
                .onMapKey("available").onListValueList(nights).removeAnd().getValues()
                .execute();
        Record rec = rs.next().recordOrThrow();
        return rec.operationResult(0).getList();
    }

    private static void appendBookedEntries(
            Session session, Key key, BookingRequest req, int rateIndex, List<Long> nights, String reservationId) {
        List<Map<String, Object>> bookedEntries = new java.util.ArrayList<>();
        for (Long night : nights) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("day", night);
            entry.put("reservationId", reservationId);
            entry.put("breakfast", req.breakfast);
            entry.put("flex", req.flex);
            // Two fields beyond docs/design.md's original sample booked entry
            // ({day, reservationId, breakfast, flex}): guestName was collected by the booking
            // form and used to personalize the confirmation screen, but never actually persisted -
            // confirmed live (booked a room as "Jane Verifier", read the record back, no trace of
            // the name anywhere). paid is new outright: nothing in this demo ever collects payment
            // (the confirmation page's own "Payment" button is deliberately inert), so every real
            // booking made through this flow starts unpaid. Neither field exists on the
            // background-booked entries datagen seeds directly into "booked" (see
            // datagen's RateGenerator/BookedNight) - those predate the guest ever visiting the
            // site, so there's no guest name or payment status to record for them.
            entry.put("guestName", req.guestName);
            entry.put("paid", false);
            bookedEntries.add(entry);
        }
        session.update(key)
                .bin("rooms").onMapKey(req.roomId).onMapKey("rates").onListIndex(rateIndex)
                .onMapKey("booked").listAppendItems(bookedEntries)
                .execute();
    }
}
