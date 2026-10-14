package com.drift.widgets;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Pure calendar and clock formatting helpers for the clock/calendar widgets. */
public final class CalendarModel {
    private CalendarModel() {}

    /** Rows of 7 day numbers (0 = blank), starting each week on {@code first}. */
    public static List<int[]> weeks(YearMonth month, DayOfWeek first) {
        List<int[]> rows = new ArrayList<>();
        int offset = (month.atDay(1).getDayOfWeek().getValue() - first.getValue() + 7) % 7;
        int[] row = new int[7];
        int col = offset;
        for (int d = 1; d <= month.lengthOfMonth(); d++) {
            row[col++] = d;
            if (col == 7) { rows.add(row); row = new int[7]; col = 0; }
        }
        if (col > 0) rows.add(row);
        return rows;
    }

    public static String time(ZonedDateTime t, boolean hour24, boolean seconds) {
        String p = (hour24 ? "HH" : "h") + ":mm" + (seconds ? ":ss" : "") + (hour24 ? "" : " a");
        return t.format(DateTimeFormatter.ofPattern(p, Locale.getDefault()));
    }

    public static String date(LocalDate d) { return d.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())); }

    /** Remaining time to a target as "3d 4h 12m", or empty text for a past target. */
    public static String remaining(ZonedDateTime now, ZonedDateTime target) {
        long s = java.time.Duration.between(now, target).getSeconds();
        if (s <= 0) return "";
        long d = s / 86400, h = s % 86400 / 3600, m = s % 3600 / 60;
        if (d > 0) return d + "d " + h + "h " + m + "m";
        if (h > 0) return h + "h " + m + "m " + (s % 60) + "s";
        return m + "m " + (s % 60) + "s";
    }
}
