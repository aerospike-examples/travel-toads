package com.aerospike.demo.booking.service;

import java.util.LinkedHashMap;
import java.util.Map;

import com.aerospike.client.sdk.Key;
import com.aerospike.client.sdk.Record;
import com.aerospike.client.sdk.RecordResult;
import com.aerospike.client.sdk.RecordStream;
import com.aerospike.client.sdk.ResultCode;
import com.aerospike.demo.booking.aerospike.AerospikeService;
import com.aerospike.demo.booking.aerospike.HotelRecordUtil;
import com.aerospike.demo.booking.data.ScenarioRegistry;
import com.aerospike.demo.booking.web.ApiException;

/**
 * GET /hotels/{id}. Per docs/design.md step 4 ("Property detail" row of the screen-to-query
 * table), this is the same path-expression traversal used to filter search results, just
 * projecting one record's rooms/rates down to the segments overlapping [checkIn, checkOut]
 * instead of filtering a set of records - so it reuses scenarios/path-expressions.json for its
 * demo block. Unlike /search, this endpoint has no documented 503 case, so the projection itself
 * is done in Java after a plain key read (no AEL/index dependency) - see backend/README.md
 * "Deviations" for why a true server-side AEL projection isn't used here today.
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

        Map<String, Object> detail = HotelRecordUtil.toHotelDetail(rec, checkIn, checkOut);
        Map<String, Object> demoValues = new LinkedHashMap<>();
        demoValues.put("checkIn", checkIn);
        demoValues.put("checkOut", checkOut);
        detail.put("demo", demo ? scenarios.buildDemo("path-expressions", demoValues) : null);
        return detail;
    }
}
