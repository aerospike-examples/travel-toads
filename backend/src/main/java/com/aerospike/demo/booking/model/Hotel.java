package com.aerospike.demo.booking.model;

import java.util.List;
import java.util.Map;

/**
 * One line of /data/hotels.ndjson, matching docs/design.md's {@code hotels} set schema exactly.
 * {@code rooms} is left as a raw parsed-JSON map (String key -> room map, each room's "rates" a
 * List<Map<String,Object>>) rather than a further nested POJO tree - the physical/rate structure
 * is already exactly what Aerospike needs to store, and the interesting mapping work (per
 * docs/design.md's "RecordMapper earns its keep" narrative) is the top-level fields plus the
 * GEO-typed location bin, not re-modelling a five-level-deep JSON tree in Java.
 */
public final class Hotel {
    public String hotelId;
    public String name;
    public String city;
    public String neighborhood;
    public String locality;
    public String propertyType;
    public GeoPoint location;
    public int stars;
    public int rating;
    public int reviewCount;
    public List<String> amenities;
    public Map<String, Object> rooms;

    public Hotel() {
    }
}
