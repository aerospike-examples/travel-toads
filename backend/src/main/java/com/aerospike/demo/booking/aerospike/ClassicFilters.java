package com.aerospike.demo.booking.aerospike;

import com.aerospike.client.sdk.Value;
import com.aerospike.client.sdk.cdt.CTX;
import com.aerospike.client.sdk.cdt.ListReturnType;
import com.aerospike.client.sdk.cdt.SelectFlags;
import com.aerospike.client.sdk.exp.CdtExp;
import com.aerospike.client.sdk.exp.Exp;
import com.aerospike.client.sdk.exp.ListExp;
import com.aerospike.client.sdk.exp.MapExp;

/**
 * Classic {@link Exp}-tree equivalents of the AEL where-clauses in SearchService/SuggestService,
 * used when {@link AerospikeService#supportsAel()} is false (server &lt; 8.1.3). These don't need
 * AEL at all — {@code Exp}/CDT expression filters are a long-standing Aerospike feature, not part
 * of the new AEL text parser gated to 8.1.3 — confirmed by this SDK branch's own geo test
 * ({@code QueryGeoTest.java}), which runs the identical predicate this way instead of via AEL.
 *
 * <p>The one real cost, stated directly in {@code QueryBuilder#where(Exp)}'s own javadoc ("If
 * this method is used, no secondary index can be used"): every query built from these runs as a
 * full scan with a filter, not an index probe. Fine for this demo's 900-record dataset.
 *
 * <p>Every predicate here was verified against a real 8.1.2.4 server with known-good expected
 * counts (cross-checked against {@code data/stats.json}), not assumed — see backend/README.md
 * "Getting real results without 8.1.3" for each one's number. One predicate from the original AEL
 * where-clause is deliberately <b>not</b> here: date-range availability
 * (design.md's {@code $.rooms..rates..available.[?(@ >= X and @ <= Y)].count() > 0}). That needs
 * {@code CdtExp.selectByPath} to reach into a LIST-typed leaf (each rate's "available" list)
 * through two levels of wildcard CTX, and — confirmed empirically, not assumed — this SDK's
 * {@code selectByPath} flattens a nested *scalar* leaf correctly (proven twice: this class's own
 * {@link #hasBedType} and {@link AerospikeService#minPriceExpression()}, both real, non-degenerate
 * counts) but does not flatten a nested *list*-typed leaf the same way — every value/range match
 * against the result came back zero, even with a deliberately absurd all-encompassing range, while
 * a plain non-emptiness check on the same selected structure passed for every record. That's
 * exactly the kind of query AEL's path expressions exist to make simple; replicating it by hand in
 * raw CDT expressions turned out not to work cleanly in this SDK preview. See
 * {@code SearchService#hasDateCoverage} for the honest fallback: an application-layer check after
 * the scan, not Aerospike doing the filtering.
 */
public final class ClassicFilters {
    private ClassicFilters() {
    }

    public static Exp geoWithinCircle(String circleJson) {
        return Exp.geoCompare(Exp.geoBin("location"), Exp.geo(circleJson));
    }

    public static Exp localityEquals(String locality) {
        return Exp.eq(Exp.stringBin("locality"), Exp.val(locality));
    }

    /** Plain top-level "city" bin — a whole-city destination search (e.g. "Austin"), no nesting. */
    public static Exp cityEquals(String city) {
        return Exp.eq(Exp.stringBin("city"), Exp.val(city));
    }

    public static Exp ratingAtLeast(int minRating) {
        return Exp.ge(Exp.intBin("rating"), Exp.val((long) minRating));
    }

    public static Exp propertyTypeEquals(String propertyType) {
        return Exp.eq(Exp.stringBin("propertyType"), Exp.val(propertyType));
    }

    /** Top-level hotel "amenities" list contains {@code amenity} — a plain list-bin check, no nesting. */
    public static Exp hasAmenity(String amenity) {
        return Exp.gt(
                ListExp.getByValue(ListReturnType.COUNT, Exp.val(amenity), Exp.listBin("amenities")),
                Exp.val(0L));
    }

    /**
     * At least one room has bed type {@code bed}. Extracts the flat list of every room's "bed"
     * value (a scalar per room) via {@code selectByPath}, then checks list-contains. Verified
     * against real data: {@code bed=king} correctly returned 864/900 hotels.
     */
    public static Exp hasBedType(String bed) {
        Exp beds = CdtExp.selectByPath(Exp.Type.LIST, SelectFlags.VALUE,
                Exp.mapBin("rooms"),
                CTX.allChildren(),
                CTX.mapKey(Value.get("bed")));
        return Exp.gt(ListExp.getByValue(ListReturnType.COUNT, Exp.val(bed), beds), Exp.val(0L));
    }

    /**
     * Minimum nightly price across every rate of every room in the record — the same rank-0
     * scalar-leaf extraction {@link AerospikeService#minPriceExpression()} indexes (that method
     * now just wraps this), and the same value {@code HotelRecordUtil#minPrice} computes in Java
     * for display, so a price-bracket filter built from this can never disagree with what's shown
     * on the card. Not private: the price filters below, and the index-creation code, both need it.
     */
    static Exp minPrice() {
        Exp prices = CdtExp.selectByPath(Exp.Type.LIST, SelectFlags.VALUE,
                Exp.mapBin("rooms"),
                CTX.allChildren(),
                CTX.mapKey(Value.get("rates")),
                CTX.allChildren(),
                CTX.mapKey(Value.get("price")));
        return ListExp.getByRank(ListReturnType.VALUE, Exp.Type.INT, Exp.val(0L), prices);
    }

    public static Exp minPriceAtLeast(long min) {
        return Exp.ge(minPrice(), Exp.val(min));
    }

    public static Exp minPriceBelow(long max) {
        return Exp.lt(minPrice(), Exp.val(max));
    }

    /**
     * The hotel has at least {@code roomCount} rooms at all — a plain map-size check, no
     * per-room inspection. Half of the "guests and rooms" filter; see {@link #hasRoomForOccupancy}
     * for the other half.
     */
    public static Exp roomCountAtLeast(int roomCount) {
        return Exp.ge(MapExp.size(Exp.mapBin("rooms")), Exp.val((long) roomCount));
    }

    /**
     * At least one room can sleep {@code guestsPerRoom} — extracts every room's "maxOccupancy"
     * (a scalar per room, same flattening pattern as {@link #hasBedType} and {@link #minPrice}) and
     * compares the *largest* one (rank -1, the opposite end from minPrice's rank 0) against the
     * requirement. Deliberately simplified: this doesn't attempt to bin-pack a party across
     * multiple rooms (a real hotel-booking problem even production OTA sites handle by asking the
     * guest to assign people per room, not by inferring it) — it just checks the biggest single
     * room is big enough for an even split of the party across the requested room count. Combined
     * with {@link #roomCountAtLeast}, that's "enough rooms, and rooms roomy enough," not a
     * guarantee any specific split works.
     */
    public static Exp hasRoomForOccupancy(int guestsPerRoom) {
        Exp occupancies = CdtExp.selectByPath(Exp.Type.LIST, SelectFlags.VALUE,
                Exp.mapBin("rooms"),
                CTX.allChildren(),
                CTX.mapKey(Value.get("maxOccupancy")));
        Exp largestRoom = ListExp.getByRank(ListReturnType.VALUE, Exp.Type.INT, Exp.val(-1L), occupancies);
        return Exp.ge(largestRoom, Exp.val((long) guestsPerRoom));
    }

    /**
     * Callers must not pass a zero-length array — confirmed against a real server,
     * {@code Exp.and()} with no clauses at all degenerates into a filter matching nothing rather
     * than "no filter" (a real bug SearchService hit and special-cases around, not a hypothetical).
     */
    public static Exp and(Exp... clauses) {
        return clauses.length == 1 ? clauses[0] : Exp.and(clauses);
    }
}
