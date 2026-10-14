package com.drift;

import static org.assertj.core.api.Assertions.assertThat;

import com.drift.ui.WindowPlacement;
import com.drift.ui.WindowPlacement.Rect;
import java.util.List;
import org.junit.jupiter.api.Test;

class WindowPlacementTest {
    final Rect main = new Rect(0, 0, 1920, 1040);
    final Rect right = new Rect(1920, 0, 1280, 984);
    final Rect left = new Rect(-1280, 0, 1280, 1024);
    final List<Rect> screens = List.of(main, right, left);

    @Test void anchorsToEachSide() {
        Rect r = WindowPlacement.anchored(main, "RIGHT", 360, 64);
        assertThat(r.x()).isEqualTo(1920 - 360 - 16);
        assertThat(WindowPlacement.anchored(main, "LEFT", 360, 64).x()).isEqualTo(16);
        assertThat(WindowPlacement.anchored(main, "TOP", 360, 64).y()).isEqualTo(16);
        assertThat(WindowPlacement.anchored(main, "BOTTOM", 360, 64).y()).isEqualTo(1040 - 64 - 16);
    }

    @Test void clampsInsideScreenAndKeepsSecondMonitorPosition() {
        Rect off = WindowPlacement.clamp(screens, main, new Rect(1900, 1030, 360, 64));
        assertThat(off.right()).isLessThanOrEqualTo(1920 + 1280);
        assertThat(off.bottom()).isLessThanOrEqualTo(1040);
        Rect onSecond = WindowPlacement.clamp(screens, main, new Rect(2500, 100, 360, 64));
        assertThat(onSecond).isEqualTo(new Rect(2500, 100, 360, 64));
        Rect onLeft = WindowPlacement.clamp(screens, main, new Rect(-800, 100, 360, 64));
        assertThat(onLeft.x()).isEqualTo(-800);
    }

    @Test void unplugMonitorFallsBackToNearestScreen() {
        Rect saved = new Rect(2500, 100, 360, 64); // was on the right monitor, now gone
        Rect r = WindowPlacement.clamp(List.of(main), main, saved);
        assertThat(r.right()).isLessThanOrEqualTo(1920);
        assertThat(r.x()).isGreaterThanOrEqualTo(0);
    }

    @Test void snapsToEdgesWithinThresholdOnly() {
        Rect near = WindowPlacement.snap(screens, main, new Rect(1920 - 360 - 20, 500, 360, 64));
        assertThat(near.x()).isEqualTo(1920 - 360 - 16);
        Rect far = WindowPlacement.snap(screens, main, new Rect(900, 500, 360, 64));
        assertThat(far).isEqualTo(new Rect(900, 500, 360, 64));
    }

    @Test void oversizedWindowShrinksToScreen() {
        Rect r = WindowPlacement.clamp(screens, main, new Rect(0, 0, 5000, 5000));
        assertThat(r.w()).isLessThanOrEqualTo(1920);
        assertThat(r.h()).isLessThanOrEqualTo(1040);
    }

    @Test void expandingNearRightEdgeGrowsLeft() {
        Rect compact = new Rect(1920 - 16 - 360, 300, 360, 64);
        Rect expanded = WindowPlacement.resized(screens, main, compact, 420, 360, true);
        assertThat(expanded.right()).isEqualTo(compact.right());
        assertThat(expanded.w()).isEqualTo(420);
    }
}
