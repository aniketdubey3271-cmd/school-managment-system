package com.sms.report;

import java.time.LocalDate;

/** An inclusive date range; defaults to the last 30 days, and a reversed range is swapped. */
public record DateRange(LocalDate from, LocalDate to) {

    public static final int DEFAULT_DAYS = 30;

    public static DateRange of(LocalDate from, LocalDate to) {
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.minusDays(DEFAULT_DAYS - 1) : from;
        return start.isAfter(end) ? new DateRange(end, start) : new DateRange(start, end);
    }
}
