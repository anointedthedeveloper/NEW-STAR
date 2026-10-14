package com.drift.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.geom.GeneralPath;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Drift's mark: a charcoal tile with two drifting waves. Drawn in code so no binary asset is needed at runtime. */
public final class DriftIcon {
    private DriftIcon() {}

    public static BufferedImage render(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        var g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        double pad = size * 0.04, arc = size * 0.26;
        g.setColor(new Color(0x171B22));
        g.fill(new RoundRectangle2D.Double(pad, pad, size - 2 * pad, size - 2 * pad, arc, arc));
        g.setColor(new Color(0x2B3340));
        g.setStroke(new BasicStroke((float) Math.max(1, size * 0.02)));
        g.draw(new RoundRectangle2D.Double(pad, pad, size - 2 * pad, size - 2 * pad, arc, arc));
        g.setColor(new Color(0x38BDF8));
        g.setStroke(new BasicStroke((float) (size * 0.075), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (double yc : new double[]{0.40, 0.62}) g.draw(wave(size, yc));
        g.dispose();
        return img;
    }

    private static GeneralPath wave(int s, double yCenter) {
        GeneralPath p = new GeneralPath();
        double x0 = s * 0.22, x1 = s * 0.78, amp = s * 0.07, y = s * yCenter;
        p.moveTo(x0, y);
        double w = (x1 - x0) / 2;
        p.curveTo(x0 + w * 0.35, y - amp * 1.9, x0 + w * 0.65, y - amp * 1.9, x0 + w, y);
        p.curveTo(x0 + w * 1.35, y + amp * 1.9, x0 + w * 1.65, y + amp * 1.9, x1, y);
        return p;
    }

    /** Writes a multi-size .ico (PNG-compressed entries) for jpackage and the installer. */
    public static void writeIco(Path file, List<Integer> sizes) throws IOException {
        List<byte[]> pngs = new java.util.ArrayList<>();
        for (int s : sizes) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            javax.imageio.ImageIO.write(render(s), "png", b);
            pngs.add(b.toByteArray());
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream d = new DataOutputStream(out);
        d.write(new byte[]{0, 0, 1, 0});
        d.write(le16(sizes.size()));
        int offset = 6 + 16 * sizes.size();
        for (int i = 0; i < sizes.size(); i++) {
            int s = sizes.get(i);
            d.writeByte(s >= 256 ? 0 : s);
            d.writeByte(s >= 256 ? 0 : s);
            d.writeByte(0);
            d.writeByte(0);
            d.write(le16(1));
            d.write(le16(32));
            d.write(le32(pngs.get(i).length));
            d.write(le32(offset));
            offset += pngs.get(i).length;
        }
        for (byte[] p : pngs) d.write(p);
        Files.createDirectories(file.toAbsolutePath().getParent());
        Files.write(file, out.toByteArray());
    }

    private static byte[] le16(int v) { return new byte[]{(byte) v, (byte) (v >> 8)}; }
    private static byte[] le32(int v) { return new byte[]{(byte) v, (byte) (v >> 8), (byte) (v >> 16), (byte) (v >> 24)}; }

    public static void main(String[] args) throws IOException {
        Path dir = Path.of(args.length > 0 ? args[0] : "packaging");
        writeIco(dir.resolve("drift.ico"), List.of(16, 32, 48, 64, 128, 256));
        javax.imageio.ImageIO.write(render(512), "png", dir.resolve("drift.png").toFile());
    }
}
