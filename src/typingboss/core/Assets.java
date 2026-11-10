package typingboss.core;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/** Loads and caches PNG sprites from /sprites on the classpath. */
public final class Assets {
    private static final Map<String, BufferedImage> IMG = new HashMap<>(), SIL = new HashMap<>();

    private Assets() {}

    public static BufferedImage img(String name) {
        BufferedImage cached = IMG.get(name);
        if (cached != null) return cached;
        try (InputStream in = Assets.class.getResourceAsStream("/sprites/" + name + ".png")) {
            BufferedImage img = ImageIO.read(in);
            IMG.put(name, img);
            return img;
        } catch (Exception e) {
            throw new RuntimeException("Missing sprite: " + name, e);
        }
    }

    /** Solid white copy of a sprite, used for the hit-flash effect. */
    public static BufferedImage silhouette(String name) {
        BufferedImage cached = SIL.get(name);
        if (cached != null) return cached;
        BufferedImage s = img(name), o = new BufferedImage(s.getWidth(), s.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < s.getHeight(); y++)
            for (int x = 0; x < s.getWidth(); x++) {
                int p = s.getRGB(x, y);
                if ((p >>> 24) != 0) o.setRGB(x, y, (p & 0xFF000000) | 0xFFFFFF);
            }
        SIL.put(name, o);
        return o;
    }
}
