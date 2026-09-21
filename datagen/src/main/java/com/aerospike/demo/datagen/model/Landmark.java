package com.aerospike.demo.datagen.model;

/**
 * One record in the {@code landmarks} set. Read by key and cached at app startup — landmarks are
 * the origin of proximity searches, never the target, so they carry no geo index.
 */
public record Landmark(
        String landmarkId,
        String city,
        String name,
        String type,
        GeoPoint location) {
}
