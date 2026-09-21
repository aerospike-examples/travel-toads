package com.aerospike.demo.datagen.model;

import java.util.List;

/**
 * A room within a hotel record. Physical attributes ({@code bed}, {@code sqm}, {@code view},
 * {@code maxOccupancy}, {@code nonSmoking}, room {@code amenities}) never change; only
 * {@code rates} is time-varying.
 */
public record Room(
        String bed,
        int sqm,
        String view,
        int maxOccupancy,
        boolean nonSmoking,
        List<String> amenities,
        List<RatePeriod> rates) {
}
