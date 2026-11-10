package typingboss.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.geom.Point2D;

/** Small drawing helpers: pixel-ish text with shadow, bars, dim layer and danger vignette. */
public final class Hud {
    private Hud() {}

    /** align: -1 = left, 0 = center, 1 = right. */
    public static void text(Graphics2D g, String s, int x, int y, int size, Color c, int align) {
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, size));
        int w = g.getFontMetrics().stringWidth(s), px = align == 0 ? x - w / 2 : align > 0 ? x - w : x;
        int sh = Math.max(2, size / 14);
        g.setColor(new Color(0, 0, 0, 200)); g.drawString(s, px + sh, y + sh);
        g.setColor(c); g.drawString(s, px, y);
    }

    public static void bar(Graphics2D g, int x, int y, int w, int h, double frac, Color fill) {
        int fw = (int) (w * Math.max(0, Math.min(1, frac)));
        g.setColor(Color.BLACK); g.fillRect(x - 3, y - 3, w + 6, h + 6);
        g.setColor(new Color(60, 30, 45)); g.fillRect(x, y, w, h);
        g.setColor(fill); g.fillRect(x, y, fw, h);
        g.setColor(new Color(255, 255, 255, 70)); g.fillRect(x, y, fw, h / 3);
    }

    public static void dim(Graphics2D g, int w, int h, int alpha) {
        g.setColor(new Color(0, 0, 0, alpha)); g.fillRect(0, 0, w, h);
    }

    /** Pulsating red screen edge. intensity 0..1 (higher = stronger and faster pulse). */
    public static void vignette(Graphics2D g, int w, int h, double intensity, double clock) {
        if (intensity <= 0) return;
        double pulse = 0.5 + 0.5 * Math.sin(clock * (4 + 8 * intensity));
        int a = (int) (255 * Math.min(1, intensity * (0.35 + 0.55 * pulse)));
        g.setPaint(new RadialGradientPaint(new Point2D.Float(w / 2f, h / 2f), (float) Math.hypot(w, h) / 2f,
            new float[]{0.45f, 1f}, new Color[]{new Color(200, 0, 0, 0), new Color(200, 0, 0, a)}));
        g.fillRect(0, 0, w, h);
    }
}
