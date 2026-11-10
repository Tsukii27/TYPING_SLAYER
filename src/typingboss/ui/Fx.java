package typingboss.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Hit effects: white flash, screen shake, slash animation and floating damage numbers. */
public class Fx {
    public static final class Pop {
        public final String text;
        public double x, y, life = 1;
        Pop(String text, double x, double y) { this.text = text; this.x = x; this.y = y; }
    }

    private double flash, shake, slashT = 4;
    private final List<Pop> pops = new ArrayList<>();
    private final Random rng = new Random();

    public void hit(int damage, double cx, double cy) {
        flash = 1; shake = 1; slashT = 0;
        pops.add(new Pop("-" + damage, cx + (rng.nextDouble() - .5) * 120, cy));
    }

    public void update(double dt) {
        flash = Math.max(0, flash - dt * 6);
        shake = Math.max(0, shake - dt * 3.5);
        if (slashT < 4) slashT += dt * 18;
        pops.removeIf(p -> (p.life -= dt * 0.9) <= 0);
        for (Pop p : pops) p.y -= 60 * dt;
    }

    public void clear() { flash = shake = 0; slashT = 4; pops.clear(); }

    public double flash() { return flash; }
    public double shake() { return shake; }
    public int slashFrame() { return slashT < 4 ? (int) slashT : -1; }
    public List<Pop> pops() { return pops; }
}
