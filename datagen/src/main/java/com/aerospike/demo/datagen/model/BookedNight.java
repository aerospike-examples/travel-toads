package com.aerospike.demo.datagen.model;

/** One entry in a rate period's {@code booked} list. */
public record BookedNight(int day, String reservationId, boolean breakfast, boolean flex) {
}
