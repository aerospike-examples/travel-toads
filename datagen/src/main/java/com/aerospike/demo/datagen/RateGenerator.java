package com.aerospike.demo.datagen;

import com.aerospike.demo.datagen.model.BookedNight;
import com.aerospike.demo.datagen.model.RatePeriod;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Builds the {@code rates} list for one room: alternating weekday/weekend periods across the
 * date window, per docs/design.md's "Record size sanity check" (90 days → ~26 periods per room).
 *
 * <p>Weekend is Fri/Sat/Sun; weekday is Mon-Thu — matches how OTA rate plans actually split, and
 * gives roughly the 90/3.5 ≈ 26 periods the sizing note assumes.
 */
final class RateGenerator {

    private static final double BACKGROUND_BOOK_PROBABILITY = 0.10;
    private static final double BREAKFAST_PROBABILITY = 0.40;
    private static final double FLEX_PROBABILITY = 0.50;

    private RateGenerator() {
    }

    static List<RatePeriod> generate(
            LocalDate start,
            int days,
            int weekdayPrice,
            int weekendPrice,
            LocalDate eventStart,
            LocalDate eventEnd,
            boolean eventSoldOut,
            ReservationIdCounter reservationIds,
            Random rng) {

        List<RatePeriod> periods = new ArrayList<>();
        int weekdayCounter = 1;
        int weekendCounter = 1;

        int i = 0;
        while (i < days) {
            LocalDate periodStart = start.plusDays(i);
            boolean weekend = isWeekend(periodStart);

            int j = i;
            while (j < days && isWeekend(start.plusDays(j)) == weekend) {
                j++;
            }
            LocalDate periodEnd = start.plusDays(j - 1);

            String rateId = weekend ? ("WKND-" + weekendCounter++) : ("WKD-" + weekdayCounter++);
            int price = weekend ? weekendPrice : weekdayPrice;

            List<Integer> available = new ArrayList<>();
            List<BookedNight> booked = new ArrayList<>();
            for (int d = i; d < j; d++) {
                LocalDate day = start.plusDays(d);
                boolean forcedSoldOut = eventSoldOut && !day.isBefore(eventStart) && !day.isAfter(eventEnd);
                boolean isBooked = forcedSoldOut || rng.nextDouble() < BACKGROUND_BOOK_PROBABILITY;

                int dayInt = DateUtil.toInt(day);
                if (isBooked) {
                    booked.add(new BookedNight(
                            dayInt,
                            reservationIds.nextId(),
                            rng.nextDouble() < BREAKFAST_PROBABILITY,
                            rng.nextDouble() < FLEX_PROBABILITY));
                } else {
                    available.add(dayInt);
                }
            }

            periods.add(new RatePeriod(rateId, DateUtil.toInt(periodStart), DateUtil.toInt(periodEnd),
                    price, available, booked));
            i = j;
        }

        return periods;
    }

    private static boolean isWeekend(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        return dow == DayOfWeek.FRIDAY || dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
    }
}
