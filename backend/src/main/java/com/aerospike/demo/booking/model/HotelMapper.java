package com.aerospike.demo.booking.model;

import java.util.Map;

import com.aerospike.client.sdk.Key;
import com.aerospike.client.sdk.RecordMapper;
import com.aerospike.client.sdk.util.MapUtil;

/**
 * RecordMapper<Hotel> per docs/design.md's demo narrative ("no policy-object juggling ... the
 * RecordMapper earns its keep here").
 *
 * <p><b>Deliberately excludes {@code location} from {@link #toMap}.</b> This is the one gotcha
 * design.md calls out by name: if a GEO2DSPHERE-typed bin is written by putting a nested
 * {@code {"type":"Point","coordinates":[...]}} map through the generic RecordMapper -> toMap()
 * path, Aerospike silently stores it as a MAP bin, {@code hotel-loc-idx} silently never covers it,
 * and every geo query silently returns zero rows - no exception, no server error. Confirmed
 * against this SDK branch's own test (QueryGeoTest): the only way to write a GEO-typed bin is the
 * explicit {@code .bin(name).setToGeoJson(json)} operation. {@link OperationObjectBuilder}/
 * {@code ObjectBuilder} (the {@code .object(hotel).using(mapper)} builder returned by
 * {@code session.insert(dataSet)}) has no hook to splice in an extra explicit bin operation
 * alongside the mapped object, so the loader (see HotelLoader) does the object insert via this
 * mapper THEN immediately issues a second explicit
 * {@code session.upsert(key).bin("location").setToGeoJson(...).execute()} on the same key - see
 * backend/README.md "GEO bin" for the integration check that proves the index actually covers it.
 */
public final class HotelMapper implements RecordMapper<Hotel> {

    @Override
    public Hotel fromMap(Map<String, Object> map, Key recordKey, int generation) {
        Hotel h = new Hotel();
        h.hotelId = MapUtil.asString(map, "hotelId");
        h.name = MapUtil.asString(map, "name");
        h.city = MapUtil.asString(map, "city");
        h.neighborhood = MapUtil.asString(map, "neighborhood");
        h.locality = MapUtil.asString(map, "locality");
        h.propertyType = MapUtil.asString(map, "propertyType");
        h.stars = MapUtil.asInt(map, "stars");
        h.rating = MapUtil.asInt(map, "rating");
        h.reviewCount = MapUtil.asInt(map, "reviewCount");
        h.amenities = MapUtil.asList(map, "amenities");
        h.rooms = MapUtil.asMap(map, "rooms");
        // "location" is intentionally not read here - callers needing it read the GEO bin
        // directly via Record.getGeoJSON("location"), since it never round-trips through this
        // generic map (see class Javadoc).
        return h;
    }

    @Override
    public Map<String, Object> toMap(Hotel hotel) {
        return MapUtil.buildMap()
                .add("hotelId", hotel.hotelId)
                .add("name", hotel.name)
                .add("city", hotel.city)
                .add("neighborhood", hotel.neighborhood)
                .add("locality", hotel.locality)
                .add("propertyType", hotel.propertyType)
                .add("stars", hotel.stars)
                .add("rating", hotel.rating)
                .add("reviewCount", hotel.reviewCount)
                .add("amenities", hotel.amenities)
                .add("rooms", hotel.rooms)
                .done();
    }

    @Override
    public Object id(Hotel hotel) {
        return hotel.hotelId;
    }
}
