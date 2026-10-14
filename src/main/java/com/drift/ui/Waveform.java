package com.drift.ui;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

/** Live input-level visualisation. Bars come only from measured microphone levels, never from a canned animation. */
public class Waveform extends Canvas {
    private static final int BARS = 48;
    private final double[] levels = new double[BARS];
    private int head;
    private Color color = Color.web("#38BDF8");
    private boolean active;

    public Waveform(double width, double height) {
        super(width, height);
        redraw();
    }

    public void setColor(Color c) { color = c; redraw(); }

    public void push(double level) {
        levels[head] = Math.max(0, Math.min(1, level));
        head = (head + 1) % BARS;
        redraw();
    }

    public void setActive(boolean on) {
        active = on;
        if (!on) java.util.Arrays.fill(levels, 0);
        redraw();
    }

    public double[] snapshot() {
        double[] out = new double[BARS];
        for (int i = 0; i < BARS; i++) out[i] = levels[(head + i) % BARS];
        return out;
    }

    private void redraw() {
        GraphicsContext g = getGraphicsContext2D();
        double w = getWidth(), h = getHeight();
        g.clearRect(0, 0, w, h);
        double gap = 2, bar = (w - gap * (BARS - 1)) / BARS, mid = h / 2;
        double[] data = snapshot();
        for (int i = 0; i < BARS; i++) {
            double v = Math.sqrt(data[i]);                       // perceptual lift for quiet speech
            double bh = Math.max(2, v * (h - 4));
            double alpha = 0.35 + 0.65 * (i / (double) BARS);    // newer samples brighter
            g.setFill(active ? Color.color(color.getRed(), color.getGreen(), color.getBlue(), alpha) : Color.color(0.55, 0.59, 0.66, 0.35));
            g.fillRoundRect(i * (bar + gap), mid - bh / 2, bar, bh, bar, bar);
        }
    }
}
