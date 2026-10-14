package com.drift;

import static org.assertj.core.api.Assertions.assertThat;

import com.drift.config.DriftSettings;
import com.drift.config.SettingsService;
import com.drift.core.DriftEventBus;
import com.drift.persistence.Database;
import com.drift.widgets.CalendarModel;
import com.drift.widgets.WeatherService;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WidgetLogicTest {
    @TempDir Path dir;

    @Test void clockFormatsGivenInstant() {
        ZonedDateTime t = ZonedDateTime.of(2026, 3, 9, 15, 4, 5, 0, ZoneId.of("UTC"));
        assertThat(CalendarModel.time(t, true, true)).isEqualTo("15:04:05");
        assertThat(CalendarModel.time(t, true, false)).isEqualTo("15:04");
        assertThat(CalendarModel.time(t, false, false)).startsWith("3:04");
    }

    @Test void calendarGridIsCorrect() {
        var weeks = CalendarModel.weeks(YearMonth.of(2026, 2), DayOfWeek.MONDAY); // 1 Feb 2026 is a Sunday
        assertThat(weeks.get(0)).containsExactly(0, 0, 0, 0, 0, 0, 1);
        assertThat(weeks).hasSize(5);
        assertThat(weeks.get(4)).containsExactly(23, 24, 25, 26, 27, 28, 0);
        assertThat(CalendarModel.weeks(YearMonth.of(2026, 2), DayOfWeek.SUNDAY).get(0)).containsExactly(1, 2, 3, 4, 5, 6, 7);
    }

    @Test void countdownRemaining() {
        ZonedDateTime now = ZonedDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneId.of("UTC"));
        assertThat(CalendarModel.remaining(now, now.plusDays(3).plusHours(4).plusMinutes(12))).isEqualTo("3d 4h 12m");
        assertThat(CalendarModel.remaining(now, now.plusMinutes(5).plusSeconds(9))).isEqualTo("5m 9s");
        assertThat(CalendarModel.remaining(now, now.minusSeconds(1))).isEmpty();
    }

    private SettingsService settings(boolean enabled, double lat, double lon) {
        SettingsService s = new SettingsService(new Database(dir.resolve("w" + System.nanoTime() + ".db")), new DriftEventBus());
        s.update(x -> new DriftSettings(x.appearance(), x.floating(), x.voice(), x.startup(), x.developer(), x.privacy(), x.monitoring(), x.shortcuts(),
                new DriftSettings.Weather(enabled, lat, lon, "metric")));
        return s;
    }

    @Test void weatherIsUnavailableWhenNotConfiguredAndNeverContactsNetwork() {
        int[] calls = {0};
        WeatherService off = new WeatherService(settings(false, 10, 10), u -> { calls[0]++; return "{}"; });
        assertThat(off.fetch()).isInstanceOf(WeatherService.State.Unavailable.class);
        WeatherService noLocation = new WeatherService(settings(true, 0, 0), u -> { calls[0]++; return "{}"; });
        assertThat(((WeatherService.State.Unavailable) noLocation.fetch()).reason()).contains("No location");
        assertThat(calls[0]).isZero();
    }

    @Test void weatherParsesProviderResponseAndReportsOfflineHonestly() {
        WeatherService ok = new WeatherService(settings(true, 51.5, -0.12),
                u -> { assertThat(u.getQuery()).contains("latitude=51.5000").contains("celsius"); return "{\"current\":{\"temperature_2m\":12.5,\"weather_code\":3,\"wind_speed_10m\":9.0}}"; });
        var d = (WeatherService.State.Data) ok.fetch();
        assertThat(d.temperature()).isEqualTo(12.5);
        assertThat(d.description()).isEqualTo("Overcast");
        WeatherService offline = new WeatherService(settings(true, 51.5, -0.12), u -> { throw new java.io.IOException("no route"); });
        assertThat(offline.fetch()).isInstanceOf(WeatherService.State.Unavailable.class);
        WeatherService garbage = new WeatherService(settings(true, 51.5, -0.12), u -> "<html>");
        assertThat(garbage.fetch()).isInstanceOf(WeatherService.State.Unavailable.class);
    }
}
