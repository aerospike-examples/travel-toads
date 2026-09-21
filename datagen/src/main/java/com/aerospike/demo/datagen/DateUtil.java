package com.aerospike.demo.datagen;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** Converts between {@link LocalDate} and the YYYYMMDD integer format used on every date bin. */
final class DateUtil {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");

    private DateUtil() {
    }

    static int toInt(LocalDate date) {
        return Integer.parseInt(date.format(YYYYMMDD));
    }

    static LocalDate parseIsoDate(String s) {
        return LocalDate.parse(s);
    }
}
