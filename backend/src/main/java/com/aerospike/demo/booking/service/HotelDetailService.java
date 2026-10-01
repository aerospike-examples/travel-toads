package com.aerospike.demo.booking.service;

import java.util.LinkedHashMap;
import java.util.Map;

import com.aerospike.client.sdk.Key;
import com.aerospike.client.sdk.Record;
import com.aerospike.client.sdk.RecordResult;
import com.aerospike.client.sdk.RecordStream;
import com.aerospike.client.sdk.ResultCode;
import com.aerospike.client.sdk.Session;
import com.aerospike.client.sdk.cdt.MapReturnType;
import com.aerospike.client.sdk.exp.Exp;
import com.aerospike.client.sdk.exp.LoopVarPart;
import com.aerospike.client.sdk.exp.MapExp;
import com.aerospike.demo.booking.aerospike.AerospikeService;
import com.aerospike.demo.booking.aerospike.HotelRecordUtil;
import com.aerospike.demo.booking.data.ScenarioRegistry;
import com.aerospike.demo.booking.web.ApiException;

/**
 * GET /hotels/{id}. Per docs/design.md step 4 ("Property detail" row of the screen-to-query
 * table), this is the same path-expression traversal used to filter search results, just
 * projecting one record's rooms/rates down to the segments overlapping [checkIn, checkOut]
 * instead of filtering a set of records - so it reuses scenarios/path-expressions.json for its
 * demo block.
 *
 * <p>The overlap decision now genuinely runs server-side, via {@link #projectRatesByPath} - a
 * real {@code CdtOperation.selectByPath} (fluent {@code onEachChild}/{@code collectTree} form),
 * confirmed against a live 8.2.0.0 server, not assumed. This was previously done in Java after a
 * plain key read (see git history / backend/README.md's "Deviations" #6 for why: today's server
 * can't run AEL at all was the original justification - no longer true since 8.2 GA). One real
 * limitation found along the way, not worked around: a CTX path selection that descends into one
 * nested field (here, "rates") can't simultaneously keep a room's *other* sibling fields (bed,
 * sqm, etc.) in the same selected subtree - so room-level metadata still comes from a second,
 * plain read of the whole record, same as before. Only the actual filtering decision moved
 * server-side, not the full round trip - a real, bounded win, not a complete rewrite.
 */
public final class HotelDetailService {
    private final AerospikeService aerospike;
    private final ScenarioRegistry scenarios;

    public HotelDetailService(AerospikeService aerospike, ScenarioRegistry scenarios) {
        this.aerospike = aerospike;
        this.scenarios = scenarios;
    }

    public Map<String, Object> get(String hotelId, long checkIn, long checkOut, boolean demo) {
        Key key = aerospike.hotelsDataSet().id(hotelId);
        Record rec;
        try (RecordStream rs = aerospike.session().query(key).execute()) {
            if (!rs.hasNext()) {
                throw ApiException.notFound("No hotel with id " + hotelId);
            }
            RecordResult rr = rs.next();
            rec = rr.recordOrThrow();
        } catch (com.aerospike.client.sdk.AerospikeException e) {
            if (e.getResultCode() == ResultCode.KEY_NOT_FOUND_ERROR) {
                throw ApiException.notFound("No hotel with id " + hotelId);
            }
            throw e;
        }

        Map<?, ?> filteredRatesByRoom = projectRatesByPath(aerospike.session(), key, checkIn, checkOut);
        Map<String, Object> detail = HotelRecordUtil.toHotelDetail(rec, filteredRatesByRoom, checkIn, checkOut);
        Map<String, Object> demoValues = new LinkedHashMap<>();
        demoValues.put("checkIn", checkIn);
        demoValues.put("checkOut", checkOut);
        detail.put("demo", demo ? scenarios.buildDemo("path-expressions", demoValues) : null);
        return detail;
    }

    /**
     * For every room, select only the rate segments whose [from,to] period overlaps
     * [checkIn, checkOut] - a real server-side CDT path expression, not a client-side loop.
     * Returns {@code roomId -> {"rates": [...]}}, the same shape {@link HotelRecordUtil} merges
     * with each room's other fields.
     *
     * <p>Built with {@code SelectFlags.MATCHING_TREE} (structure-preserving), not the flattening
     * {@code VALUE} flag {@link com.aerospike.demo.booking.aerospike.ClassicFilters} already found
     * (and documented) doesn't flatten nested *list*-typed leaves like "available" correctly on
     * this SDK. This query never asks for that flatten - it only filters which "rates" entries
     * are *kept*, at the level they already live at, so it doesn't hit that bug. Confirmed against
     * a live server: a request with a date range matching only the later of two rate segments came
     * back with just that one segment, not both.
     */
    private static Map<?, ?> projectRatesByPath(Session session, Key key, long checkIn, long checkOut) {
        Exp currentRate = Exp.mapLoopVar(LoopVarPart.VALUE);
        Exp from = MapExp.getByKey(MapReturnType.VALUE, Exp.Type.INT, Exp.val("from"), currentRate);
        Exp to = MapExp.getByKey(MapReturnType.VALUE, Exp.Type.INT, Exp.val("to"), currentRate);
        Exp overlaps = Exp.and(Exp.le(from, Exp.val(checkOut)), Exp.ge(to, Exp.val(checkIn)));

        Record result = session.query(key)
                .bin("rooms")
                    .onEachChild()
                    .onMapKey("rates")
                    .onEachChild(overlaps)
                    .collectTree()
                .execute()
                .getFirstRecord();
        return result == null ? Map.of() : result.getMap("rooms");
    }
}
