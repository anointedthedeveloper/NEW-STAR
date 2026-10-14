package com.drift.widgets;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record WidgetLayout(String name, List<WidgetConfig> widgets) {
    public static final int MAX_WIDGETS = 30;

    public WidgetLayout { widgets = widgets == null ? List.of() : List.copyOf(widgets); }

    public List<String> validate() {
        List<String> e = new ArrayList<>();
        if (name == null || !name.matches("[\\p{L}\\p{N} _.-]{1,40}")) e.add("Layout name must be 1-40 letters, digits, spaces or _.-");
        if (widgets.size() > MAX_WIDGETS) e.add("A layout may hold at most " + MAX_WIDGETS + " widgets");
        Set<String> ids = new HashSet<>();
        for (WidgetConfig w : widgets) {
            e.addAll(w.validate());
            if (!ids.add(w.id())) e.add("Duplicate widget id " + w.id());
        }
        return e;
    }

    public WidgetLayout with(WidgetConfig c) {
        List<WidgetConfig> l = new ArrayList<>(widgets.stream().filter(w -> !w.id().equals(c.id())).toList());
        l.add(c);
        return new WidgetLayout(name, l);
    }

    public WidgetLayout without(String id) { return new WidgetLayout(name, widgets.stream().filter(w -> !w.id().equals(id)).toList()); }
}
