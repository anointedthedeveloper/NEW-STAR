package com.drift.ui;

import java.util.List;

/** Display-aware placement maths for the floating window. Pure geometry so it is testable without a display. */
public final class WindowPlacement {
    public record Rect(double x, double y, double w, double h) {
        public double right() { return x + w; }
        public double bottom() { return y + h; }
        public double centerX() { return x + w / 2; }
        public double centerY() { return y + h / 2; }
        double overlapArea(Rect o) {
            double ow = Math.min(right(), o.right()) - Math.max(x, o.x);
            double oh = Math.min(bottom(), o.bottom()) - Math.max(y, o.y);
            return ow > 0 && oh > 0 ? ow * oh : 0;
        }
    }

    public static final double MARGIN = 16, SNAP = 14;

    private WindowPlacement() {}

    /** Default position for an anchor (LEFT/RIGHT/TOP/BOTTOM) on the primary screen's usable area. */
    public static Rect anchored(Rect screen, String anchor, double w, double h) {
        double x, y;
        switch (anchor) {
            case "LEFT" -> { x = screen.x() + MARGIN; y = screen.y() + screen.h() / 3; }
            case "TOP" -> { x = screen.x() + (screen.w() - w) / 2; y = screen.y() + MARGIN; }
            case "BOTTOM" -> { x = screen.x() + (screen.w() - w) / 2; y = screen.bottom() - h - MARGIN; }
            default -> { x = screen.right() - w - MARGIN; y = screen.y() + screen.h() / 3; }
        }
        return clampTo(screen, new Rect(x, y, w, h));
    }

    /** Keeps a window fully inside the screen it mostly occupies; falls back to the primary screen if it is on none. */
    public static Rect clamp(List<Rect> screens, Rect primary, Rect desired) {
        Rect best = null;
        double bestArea = 0;
        for (Rect s : screens) {
            double a = desired.overlapArea(s);
            if (a > bestArea) { bestArea = a; best = s; }
        }
        if (best == null || bestArea < 0.3 * desired.w() * desired.h()) best = nearest(screens, primary, desired);
        return clampTo(best, desired);
    }

    private static Rect nearest(List<Rect> screens, Rect primary, Rect r) {
        Rect best = primary;
        double bestDist = Double.MAX_VALUE;
        for (Rect s : screens) {
            double dx = r.centerX() - s.centerX(), dy = r.centerY() - s.centerY();
            double d = dx * dx + dy * dy;
            if (d < bestDist) { bestDist = d; best = s; }
        }
        return best;
    }

    static Rect clampTo(Rect screen, Rect r) {
        double w = Math.min(r.w(), screen.w()), h = Math.min(r.h(), screen.h());
        double x = Math.max(screen.x(), Math.min(r.x(), screen.right() - w));
        double y = Math.max(screen.y(), Math.min(r.y(), screen.bottom() - h));
        return new Rect(x, y, w, h);
    }

    /** Snaps to the screen edges (with margin) when within the snap distance. */
    public static Rect snap(List<Rect> screens, Rect primary, Rect r) {
        Rect s = clamp(screens, primary, r);
        double x = s.x(), y = s.y();
        Rect screen = nearest(screens, primary, s);
        if (Math.abs(x - (screen.x() + MARGIN)) < SNAP) x = screen.x() + MARGIN;
        if (Math.abs(x + s.w() - (screen.right() - MARGIN)) < SNAP) x = screen.right() - MARGIN - s.w();
        if (Math.abs(y - (screen.y() + MARGIN)) < SNAP) y = screen.y() + MARGIN;
        if (Math.abs(y + s.h() - (screen.bottom() - MARGIN)) < SNAP) y = screen.bottom() - MARGIN - s.h();
        return new Rect(x, y, s.w(), s.h());
    }

    /** Resizing keeps the window on screen: if growing would push it off an edge it moves back in. */
    public static Rect resized(List<Rect> screens, Rect primary, Rect current, double newW, double newH, boolean growLeftWhenRight) {
        double x = current.x();
        if (growLeftWhenRight && Math.abs(current.right() - nearest(screens, primary, current).right()) < MARGIN + 2) x = current.right() - newW;
        return clamp(screens, primary, new Rect(x, current.y(), newW, newH));
    }
}
