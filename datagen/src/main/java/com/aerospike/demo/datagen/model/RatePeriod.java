package com.aerospike.demo.datagen.model;

import java.util.List;

/**
 * A date-bounded rate period. {@code from}/{@code to} are immutable policy bounds (YYYYMMDD
 * integers); {@code available} shrinks and {@code booked} grows as bookings land — the
 * Availability/Rates/Inventory (ARI) split. See docs/design.md's design-decisions table.
 */
public record RatePeriod(
        String rateId,
        int from,
        int to,
        int price,
        List<Integer> available,
        List<BookedNight> booked) {
}
