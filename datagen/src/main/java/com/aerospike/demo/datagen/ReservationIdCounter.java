package com.aerospike.demo.datagen;

/** Deterministic, monotonically increasing reservation IDs — no randomness needed for uniqueness. */
final class ReservationIdCounter {

    private int next = 1;

    String nextId() {
        return "RES-" + String.format("%06d", next++);
    }
}
