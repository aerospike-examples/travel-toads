package com.aerospike.demo.booking.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A GeoJSON {@code {"type":"Point","coordinates":[lon,lat]}} object, as used throughout
 * hotels.ndjson and landmarks.json. Coordinates are [longitude, latitude] - the reverse of how
 * everyone says it aloud (see docs/design.md's "GeoJSON gotchas").
 */
public final class GeoPoint {
    public final double lon;
    public final double lat;

    @JsonCreator
    public GeoPoint(@JsonProperty("type") String type, @JsonProperty("coordinates") double[] coordinates) {
        this.lon = coordinates[0];
        this.lat = coordinates[1];
    }

    public GeoPoint(double lon, double lat) {
        this.lon = lon;
        this.lat = lat;
    }

    /** GeoJSON Point text, for {@code setToGeoJson}/{@code geoJson()} and for parsing back. */
    public String toGeoJson() {
        return "{\"type\":\"Point\",\"coordinates\":[" + lon + "," + lat + "]}";
    }

    /** {lon, lat} shape used in every API response per the contract. */
    public java.util.Map<String, Object> toApi() {
        return java.util.Map.of("lon", lon, "lat", lat);
    }
}
