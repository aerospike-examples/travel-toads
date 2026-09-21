package com.aerospike.demo.booking.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aerospike.client.sdk.Record;
import com.aerospike.client.sdk.RecordResult;
import com.aerospike.client.sdk.RecordStream;
import com.aerospike.client.sdk.exp.Exp;
import com.aerospike.client.sdk.query.QueryBuilder;
import com.aerospike.demo.booking.aerospike.AerospikeService;
import com.aerospike.demo.booking.aerospike.ClassicFilters;
import com.aerospike.demo.booking.aerospike.HotelRecordUtil;
import com.aerospike.demo.booking.data.LandmarkStore;
import com.aerospike.demo.booking.data.ScenarioRegistry;
import com.aerospike.demo.booking.model.GeoPoint;
import com.aerospike.demo.booking.model.Landmark;
import com.aerospike.demo.booking.web.ApiException;
import com.aerospike.demo.booking.web.dto.SearchRequest;

/**
 * POST /search. Builds one AEL where-clause per docs/design.md's "one index predicate per query":
 * geo (landmark + radius) or locality is the index-selecting predicate, with rating/propertyType/
 * amenities/bed/date-coverage layered on as filter-expression-shaped clauses in the same string.
 * See scenarios/geo.json, locality.json, path-expressions.json for the demo-panel copy this
 * mirrors. A third destination shape, whole-city (e.g. "Austin"), isn't in design.md's original
 * index list at all - "city" has no dedicated index (locality already covers city+neighborhood),
 * so it's just another unindexed filter-expression predicate, mutually exclusive with geo/locality
 * the same way they're mutually exclusive with each other.
 *
 * <p>Every clause below is built twice — once as an AEL string fragment, once as an equivalent
 * {@link Exp} tree (see {@link ClassicFilters}) — so this runs with real results on any server:
 * the AEL form when {@link AerospikeService#supportsAel()} is true (server 8.1.3+, index-accelerated),
 * the classic-Exp form otherwise (any server, full scan). One exception: date-range availability
 * has no working classic-Exp form (see {@link ClassicFilters}'s Javadoc for why) and is applied as
 * an application-layer post-filter in the classic branch instead — every other predicate still
 * runs server-side. See backend/README.md "Getting real results without 8.1.3" for how each was
 * verified.
 */
public final class SearchService {
    private static final Logger log = LoggerFactory.getLogger(SearchService.class);
    private static final double METERS_PER_MILE = 1609.344;

    private final AerospikeService aerospike;
    private final LandmarkStore landmarks;
    private final ScenarioRegistry scenarios;

    public SearchService(AerospikeService aerospike, LandmarkStore landmarks, ScenarioRegistry scenarios) {
        this.aerospike = aerospike;
        this.landmarks = landmarks;
        this.scenarios = scenarios;
    }

    public Map<String, Object> search(SearchRequest req) {
        List<String> aelClauses = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        List<Exp> expClauses = new ArrayList<>();
        String primaryScenario = null;
        String hintIndex = null;
        Map<String, Object> demoValues = new LinkedHashMap<>();

        boolean useGeo = req.landmarkId != null && !req.landmarkId.isBlank();
        boolean useLocality = !useGeo && req.locality != null && !req.locality.isBlank();
        boolean useCity = !useGeo && !useLocality && req.city != null && !req.city.isBlank();

        if (req.landmarkId != null && req.locality != null) {
            log.warn("/search received both landmarkId ({}) and locality ({}); per docs/design.md "
                    + "'one index predicate per query', using landmarkId/geo and ignoring locality.",
                    req.landmarkId, req.locality);
        }

        if (useGeo) {
            Landmark landmark = landmarks.byId(req.landmarkId);
            if (landmark == null) {
                throw ApiException.badRequest("Unknown landmarkId: " + req.landmarkId);
            }
            double radiusMiles = req.radiusMiles != null ? req.radiusMiles : 5.0;
            long radiusMeters = Math.round(radiusMiles * METERS_PER_MILE);
            String circle = geoCircleJson(landmark.location, radiusMeters);

            aelClauses.add("geoCompare($.location, geoJson(?" + params.size() + "))");
            params.add(circle);
            expClauses.add(ClassicFilters.geoWithinCircle(circle));
            primaryScenario = "geo";
            hintIndex = AerospikeService.LOC_IDX;
            demoValues.put("landmarkName", landmark.name);
            demoValues.put("circleJson", circle);
        } else if (useLocality) {
            aelClauses.add("$.locality == ?" + params.size());
            params.add(req.locality);
            expClauses.add(ClassicFilters.localityEquals(req.locality));
            primaryScenario = "locality";
            hintIndex = AerospikeService.LOCALITY_IDX;
            demoValues.put("locality", req.locality);
        } else if (useCity) {
            // A whole-city destination (e.g. typing "Austin" rather than picking a specific
            // landmark or neighborhood). "city" has no dedicated index of its own - "locality" is
            // the composite city+neighborhood workaround design.md describes for that ("Aerospike
            // has no composite indexes") - so this runs the same as propertyType/bed/amenities:
            // an unindexed filter-expression predicate, not an index-selecting one. No scenario
            // file describes this shape specifically, so the demo panel stays null for it, same
            // as the no-destination-at-all case below.
            aelClauses.add("$.city == ?" + params.size());
            params.add(req.city);
            expClauses.add(ClassicFilters.cityEquals(req.city));
        } else {
            log.info("/search received no destination (landmarkId, locality, or city) - running an "
                    + "unindexed scan with only whatever filter-expression predicates were given.");
        }

        int minRating = req.minRating != null ? req.minRating : 0;
        if (req.minRating != null) {
            aelClauses.add("$.rating >= ?" + params.size());
            params.add(minRating);
            expClauses.add(ClassicFilters.ratingAtLeast(minRating));
        }
        demoValues.put("minRating", minRating);

        if (req.propertyType != null && !req.propertyType.isBlank()) {
            aelClauses.add("$.propertyType == ?" + params.size());
            params.add(req.propertyType);
            expClauses.add(ClassicFilters.propertyTypeEquals(req.propertyType));
        }

        if (req.amenities != null) {
            for (String amenity : req.amenities) {
                aelClauses.add("?" + params.size() + " in $.amenities");
                params.add(amenity);
                expClauses.add(ClassicFilters.hasAmenity(amenity));
            }
        }

        // Price bracket - not part of design.md's original 7-query narrative (no scenario
        // describes it, so it never touches primaryScenario/demoValues). Originally classic-Exp
        // only ("no AEL grammar for a rank/min aggregate was ever designed for it") - but min(price)
        // across every rate has an exact AEL form that doesn't need real rank-select at all:
        //   min(price) >= N  <=>  no rate has price < N
        //   min(price) <  N  <=>  some rate has price < N
        // Both directions verified against the real 8.1.3.0 server as existence-counts over the
        // rate object itself ($.rooms:MAP.*.rates.*[?(@.price ...)].count() ...) - the same shape
        // as the proven bed filter below, not the flat rank-select ("[#0]") tried first, which
        // failed ("Parameter error") in every form attempted; rank-select apparently doesn't chain
        // after a wildcarded path on this server build. This makes ClassicFilters.minPriceAtLeast/
        // minPriceBelow's own Exp.ge/Exp.lt-on-a-rank-0-selection logic the reference semantics an
        // AEL string had to match, not just inspiration.
        if (req.minPrice != null) {
            aelClauses.add("$.rooms:MAP.*.rates.*[?(@.price < ?" + params.size() + ")].count() == 0");
            params.add(req.minPrice);
            expClauses.add(ClassicFilters.minPriceAtLeast(req.minPrice));
        }
        if (req.maxPrice != null) {
            aelClauses.add("$.rooms:MAP.*.rates.*[?(@.price < ?" + params.size() + ")].count() > 0");
            params.add(req.maxPrice);
            expClauses.add(ClassicFilters.minPriceBelow(req.maxPrice));
        }

        // Guests and rooms - same "originally classic-Exp only" history as price, same fix.
        // Always applied (there's no "any" state for this picker, just a default of 2 adults / 0
        // children / 1 room), but at that default every clause below is true for virtually every
        // hotel in the dataset (min room count is 10, per datagen/README.md), so it rarely changes
        // results unless the guest count is pushed up.
        //  - roomCountAtLeast: total room count >= roomCount. ClassicFilters uses a map-size Exp;
        //    the AEL equivalent, $.rooms:MAP.size() >= N, gave "Parameter error" against the real
        //    server (size() apparently isn't wired up for a bare :MAP root yet) - counting the
        //    wildcard itself with no filter predicate, $.rooms:MAP.*.count() >= N, is what's
        //    actually verified working, so that's what's here instead.
        //  - hasRoomForOccupancy: ClassicFilters checks the *largest* room's maxOccupancy (rank -1)
        //    against the requirement - but max(x) >= N <=> at least one x >= N, so this is the same
        //    existence-count trick as price, and the same proven shape as the bed filter below.
        int adults = req.adults != null ? req.adults : 2;
        int children = req.children != null && req.children > 0 ? req.children : 0;
        int roomsRequested = req.rooms != null && req.rooms > 0 ? req.rooms : 1;
        int guestsPerRoom = (int) Math.ceil((adults + children) / (double) roomsRequested);
        aelClauses.add("$.rooms:MAP.*.count() >= ?" + params.size());
        params.add(roomsRequested);
        aelClauses.add("$.rooms:MAP.*[?(@.maxOccupancy >= ?" + params.size() + ")].count() > 0");
        params.add(guestsPerRoom);
        expClauses.add(ClassicFilters.roomCountAtLeast(roomsRequested));
        expClauses.add(ClassicFilters.hasRoomForOccupancy(guestsPerRoom));

        // Date coverage - checkIn/checkOut are required on every /search call. The classic-Exp
        // path can't express this one server-side (see ClassicFilters' class Javadoc) - it's
        // applied as an application-layer post-filter in the classic branch below instead.
        // Two fixes vs. design.md's original "$.rooms..rates..available.[?(...)].count() > 0",
        // both confirmed against a real 8.1.3.0 server (not guessed):
        //  1. No ".." recursive-descent operator exists in AEL - the server rejects it ("'..' is
        //     reserved -- use ':' for ranges"), and the SDK's own docs/ael/*.md never use ".." for
        //     anything but the "[1:3]" range form. Every multi-level path-expressions.md example
        //     (e.g. "$.store.stationery.*.quantity.*[?(...)]") chains an explicit ".*" per level.
        //  2. "$.rooms" needs an explicit ":MAP" type annotation - the server can't otherwise
        //     resolve whether a bare CDT root bin is a list or a map ("unresolved bin type, use
        //     $.bin:LIST or $.bin:MAP"). Only the root needs it; nested "rates"/"available" steps
        //     resolve fine once the root is typed (design.md's docs/ael/type-inference.md
        //     describes this general mechanism but doesn't show this exact colon shorthand -
        //     confirmed directly against the server's own error message instead of guessed).
        //  3. A filter predicate "[?(...)]" must directly follow a "*" wildcard, per every
        //     doc example - "available.[?(...)]" (bare field, then filter) is a syntax error;
        //     it needs its own ".*" first: "available.*[?(...)]".
        aelClauses.add("$.rooms:MAP.*.rates.*.available.*[?(@ >= ?" + params.size() + " and @ <= ?" + (params.size() + 1) + ")].count() > 0");
        params.add(req.checkIn);
        params.add(req.checkOut);
        demoValues.put("checkIn", req.checkIn);
        demoValues.put("checkOut", req.checkOut);

        boolean bedAndDates = req.bed != null && !req.bed.isBlank();
        if (bedAndDates) {
            // ":MAP" needed here too - same root-bin-type resolution as the date-coverage clause
            // above, confirmed the same way (this exact string, unannotated, gave "unresolved bin
            // type ... @0" against the real server).
            aelClauses.add("$.rooms:MAP.*[?(@.bed == ?" + params.size() + ")].count() > 0");
            params.add(req.bed);
            expClauses.add(ClassicFilters.hasBedType(req.bed));
            demoValues.put("bed", req.bed);
            if (primaryScenario == null) {
                primaryScenario = "path-expressions";
            }
        }

        // Computed once, unconditionally: the real, fully-assembled AEL string for *this* request
        // - every currently-active predicate above, not a static per-scenario template. Used to
        // actually execute the query (with real ?N placeholders, bound safely - see
        // AerospikeService#buildAelQuery's own comment on why never to string-concatenate values
        // in). renderForDisplay() below produces a literal-valued copy of the same string, purely
        // for the demo panel: a presenter showing this to a room wants to see
        // "$.rating >= 80", not "$.rating >= ?0" - the placeholder form reads as a prepared
        // statement template, not "the query", even though it's the identical predicate.
        String ael = String.join(" and ", aelClauses);
        List<Map<String, Object>> allResults = new ArrayList<>();

        if (aerospike.supportsAel()) {
            QueryBuilder query = aerospike.buildAelQuery(ael, params.toArray());
            if (hintIndex != null) {
                String finalHintIndex = hintIndex;
                query = query.withHint(h -> h.forIndex(finalHintIndex).hardHint());
            }
            try (RecordStream rs = aerospike.executeAelQuery(query)) {
                collect(rs, allResults);
            }
        } else if (expClauses.isEmpty()) {
            // No index-selecting or scalar predicate at all (date coverage, the only mandatory
            // one, is applied below instead - see ClassicFilters' Javadoc). Exp.and() with zero
            // clauses was confirmed, against a real server, to degenerate into a filter matching
            // nothing at all rather than "no filter" - so this is a real case to special-case,
            // not a hypothetical. Run the equivalent of no filter directly instead.
            try (RecordStream rs = aerospike.session().query(aerospike.hotelsDataSet()).execute()) {
                collectWithDateCoverage(rs, req.checkIn, req.checkOut, allResults);
            }
        } else {
            Exp filter = ClassicFilters.and(expClauses.toArray(new Exp[0]));
            QueryBuilder query = aerospike.buildClassicQuery(filter);
            try (RecordStream rs = query.execute()) {
                collectWithDateCoverage(rs, req.checkIn, req.checkOut, allResults);
            }
        }

        int page = Math.max(req.page, 0);
        int pageSize = req.pageSize <= 0 ? 20 : req.pageSize;
        int from = Math.min(page * pageSize, allResults.size());
        int to = Math.min(from + pageSize, allResults.size());
        List<Map<String, Object>> pageResults = allResults.subList(from, to);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("results", pageResults);
        response.put("total", allResults.size());
        response.put("demo", req.demo
                ? buildLiveDemo(primaryScenario, demoValues, ael, params, hintIndex)
                : null);
        return response;
    }

    /**
     * Builds the demo block for the presenter panel / inline demo panel, always - not just when a
     * scenarios/*.json file happens to match this exact combination of filters. When one does
     * match, its narrative copy (userAction/aerospikeAction/relevantCode/notes) is used verbatim;
     * when none does (e.g. just a rating + amenities filter with no destination), a generic but
     * honest fallback is used instead so there's still something to show. Either way,
     * {@code aelTemplate} is always overwritten with the real, live-assembled query — literal
     * values substituted in for display (see {@link #renderForDisplay}) — rather than the
     * scenario's own static template, so what's displayed always reflects every currently-active
     * filter, not just the ones a scenario file happened to define a placeholder for.
     */
    private Map<String, Object> buildLiveDemo(String primaryScenario, Map<String, Object> demoValues,
            String ael, List<Object> params, String hintIndex) {
        Map<String, Object> demo = scenarios.buildDemo(primaryScenario, demoValues);
        if (demo == null) {
            demo = new LinkedHashMap<>();
            demo.put("userAction", null);
            demo.put("aerospikeAction", null);
            demo.put("queryMechanism", hintIndex != null ? "AEL + Secondary Index" : "AEL filter (full scan)");
            demo.put("index", hintIndex);
            demo.put("relevantCode", "session.query(hotels)\n    .where(ael, params)\n    .execute();");
            demo.put("queryHint", hintIndex != null ? "forIndex(\"" + hintIndex + "\")" : null);
            demo.put("notes", null);
        }
        demo.put("aelTemplate", renderForDisplay(ael, params));
        return aerospike.annotateIfCompat(demo);
    }

    private static void collect(RecordStream rs, List<Map<String, Object>> out) {
        while (rs.hasNext()) {
            RecordResult rr = rs.next();
            if (!rr.isOk()) {
                continue;
            }
            Record rec = rr.recordOrThrow();
            out.add(HotelRecordUtil.toSearchResultItem(rec));
        }
    }

    /**
     * Same as {@link #collect}, plus the date-coverage check the classic-Exp filter couldn't
     * express (see {@link ClassicFilters}'s class Javadoc and
     * {@link HotelRecordUtil#hasDateCoverage}) applied to every candidate the server's filter
     * already narrowed down - every other predicate still ran server-side.
     */
    private static void collectWithDateCoverage(RecordStream rs, long checkIn, long checkOut,
            List<Map<String, Object>> out) {
        while (rs.hasNext()) {
            RecordResult rr = rs.next();
            if (!rr.isOk()) {
                continue;
            }
            Record rec = rr.recordOrThrow();
            if (HotelRecordUtil.hasDateCoverage(rec.getMap("rooms"), checkIn, checkOut)) {
                out.add(HotelRecordUtil.toSearchResultItem(rec));
            }
        }
    }

    /**
     * Substitutes each {@code ?N} placeholder in {@code ael} with its literal value from
     * {@code params} — for display only. The real query still executes via
     * {@link AerospikeService#buildAelQuery} with the placeholders bound safely (see that
     * method's own comment on why: raw string concatenation into AEL text is exactly the risk
     * placeholder binding avoids). This just makes the *shown* copy read like a query instead of a
     * prepared-statement template. Numbers/booleans render bare; everything else (strings, the
     * circle's GeoJSON) is single-quoted, matching AEL literal syntax.
     */
    private static String renderForDisplay(String ael, List<Object> params) {
        String rendered = ael;
        // Highest index first: "?1" is a substring of "?10", "?11", ... - replacing low-to-high
        // would corrupt those once there are 10+ placeholders (this demo can have that many with
        // enough amenities selected), so higher indices must be substituted out of the way first.
        for (int i = params.size() - 1; i >= 0; i--) {
            Object value = params.get(i);
            String literal = (value instanceof Number || value instanceof Boolean)
                    ? String.valueOf(value)
                    : "'" + value + "'";
            rendered = rendered.replace("?" + i, literal);
        }
        return rendered;
    }

    private static String geoCircleJson(GeoPoint center, long radiusMeters) {
        return "{\"type\":\"AeroCircle\",\"coordinates\":[[" + center.lon + "," + center.lat + "]," + radiusMeters + "]}";
    }
}
