package com.aerospike.demo.datagen.model;

/**
 * GeoJSON Point. Coordinates are [longitude, latitude] — the reverse of how everyone says it
 * aloud. This is the shape a RecordMapper must emit as a GEO-typed bin, not a nested map; see
 * docs/design.md's "GeoJSON gotchas that fail quietly" note.
 */
public record GeoPoint(String type, double[] coordinates) {

    public static GeoPoint of(double lon, double lat) {
        return new GeoPoint("Point", new double[] { lon, lat });
    }
}
