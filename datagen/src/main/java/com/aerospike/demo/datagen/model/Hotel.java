package com.aerospike.demo.datagen.model;

import java.util.List;
import java.util.Map;

/**
 * One record in the {@code hotels} set. {@code rooms} is a map keyed by room ID — matches the
 * exact shape in docs/design.md so this can be inserted with a RecordMapper and no reshaping.
 */
public record Hotel(
        String hotelId,
        String name,
        String city,
        String neighborhood,
        String locality,
        String propertyType,
        GeoPoint location,
        int stars,
        int rating,
        int reviewCount,
        List<String> amenities,
        Map<String, Room> rooms) {
}
