package com.aerospike.demo.booking.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** YYYYMMDD integer date helpers, per docs/design.md's "Dates are YYYYMMDD integers" decision. */
public final class Dates {
    private static final DateTimeFormatter FMT = DateTimeFormatter.BASIC_ISO_DATE;

    private Dates() {
    }

    public static LocalDate toLocalDate(long yyyymmdd) {
        return LocalDate.parse(Long.toString(yyyymmdd), FMT);
    }

    public static long toYyyymmdd(LocalDate date) {
        return Long.parseLong(date.format(FMT));
    }

    /** Every calendar day from checkIn (inclusive) to checkOut (exclusive) - the nights stayed. */
    public static List<Long> nights(long checkIn, long checkOut) {
        List<Long> out = new ArrayList<>();
        LocalDate d = toLocalDate(checkIn);
        LocalDate end = toLocalDate(checkOut);
        while (d.isBefore(end)) {
            out.add(toYyyymmdd(d));
            d = d.plusDays(1);
        }
        return out;
    }
}
