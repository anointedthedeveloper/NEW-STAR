package com.drift.ui;

import java.awt.image.BufferedImage;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Small JavaFX helpers shared by the windows. */
public final class Fx {
    private Fx() {}

    public static void ui(Runnable r) {
        if (Platform.isFxApplicationThread()) r.run(); else Platform.runLater(r);
    }

    public static Image toFx(BufferedImage img) {
        WritableImage out = new WritableImage(img.getWidth(), img.getHeight());
        PixelWriter pw = out.getPixelWriter();
        for (int y = 0; y < img.getHeight(); y++) for (int x = 0; x < img.getWidth(); x++) pw.setArgb(x, y, img.getRGB(x, y));
        return out;
    }

    public static Button button(String text, Runnable action, String... styleClasses) {
        Button b = new Button(text);
        b.getStyleClass().add("drift-button");
        b.getStyleClass().addAll(styleClasses);
        b.setOnAction(e -> action.run());
        return b;
    }

    public static Label label(String text, String... styleClasses) {
        Label l = new Label(text);
        l.getStyleClass().addAll(styleClasses);
        return l;
    }

    public static Region spacer() {
        Region r = new Region();
        javafx.scene.layout.HBox.setHgrow(r, javafx.scene.layout.Priority.ALWAYS);
        VBox.setVgrow(r, javafx.scene.layout.Priority.ALWAYS);
        return r;
    }
}
