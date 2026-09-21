package com.aerospike.demo.booking.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One entry of /data/landmarks.json. Per docs/design.md, landmarks are "small reference data...
 * read by key and cacheable at app startup" and never queried/indexed - this backend reads the
 * file once at startup and serves everything from memory (see LandmarkStore), matching the API
 * contract's "GET /landmarks -> ... (from the cached file)".
 */
public final class Landmark {
    public String landmarkId;
    public String city;
    public String name;
    public String type;
    public GeoPoint location;

    public Landmark() {
    }

    public Map<String, Object> toApi() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("landmarkId", landmarkId);
        m.put("city", city);
        m.put("name", name);
        m.put("type", type);
        m.put("location", location.toApi());
        return m;
    }
}
