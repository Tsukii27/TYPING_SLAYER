package typingboss.core;

import typingboss.audio.Sfx;
import typingboss.logic.TypingEngine;
import typingboss.model.Boss;
import typingboss.model.Difficulty;
import typingboss.model.FightResult;
import typingboss.model.WordBank;
import typingboss.ui.Fx;
import typingboss.ui.Hud;

import javax.swing.JPanel;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Game loop, state machine and rendering. Typing rules live in TypingEngine. */
public class GamePanel extends JPanel implements KeyListener {
    public static final int W = 960, H = 600;
    private static final double MISTAKE_DAMAGE = 4;      // player HP lost per wrong key (boss modes)
    private static final double DRAIN_PER_FIGHT = 85;    // player HP lost over a full timer (damage over time)
    private static final double HARDCORE_PENALTY = 1.0;  // seconds taken off the timer per error in Hardcore
    private static final int DAMAGE_PER_LETTER = 2;      // word length decides boss damage
    private static final int FREE_SECONDS = 60, FREE_COLS = 36, FREE_WORDS = 300;
    private static final Color GOLD = new Color(255, 215, 90), RED = new Color(235, 70, 70), GREEN = new Color(90, 230, 110);

    private enum State { MENU, MODE_SELECT, INTRO, FIGHT, DEFEATED, FREE_PLAY, RESULTS, LOST }

    private final List<Boss> roster = Boss.roster();
    private final Fx fx = new Fx();
    private final Random rng = new Random();
    private State state = State.MENU;
    private Boss boss;
    private TypingEngine engine;
    private boolean free, hardcore, freeStarted;
    private int bossIdx, fightWords, selected, freeIdx;
    private List<String> freeWords = new ArrayList<>();
    private int[] wordLine = new int[0], wordCol = new int[0];
    private FightResult result;
    private final Rectangle[] buttons = { new Rectangle(), new Rectangle(), new Rectangle() };
    private double bossHp, timeLeft, fightTime, playerHp, penaltyT, clock, stateT, wordAge, wordOffset;
    private long lastNanos = System.nanoTime();

    public GamePanel() {
        setPreferredSize(new Dimension(W, H));
        setFocusable(true);
        addKeyListener(this);
        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                int i = buttonAt(e.getPoint());
                if (i >= 0) selected = i;
                setCursor(Cursor.getPredefinedCursor(i >= 0 ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            }
            @Override public void mousePressed(MouseEvent e) {
                int i = buttonAt(e.getPoint());
                if (i >= 0) activate(i);
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        new javax.swing.Timer(16, e -> {
            long now = System.nanoTime();
            double dt = Math.min(0.05, (now - lastNanos) / 1e9);
            lastNanos = now;
            update(dt);
            repaint();
        }).start();
    }

    // ---------- game logic ----------

    private void setState(State s) { state = s; stateT = 0; }

    private void toMenu() { fx.clear(); selected = 0; setState(State.MENU); }

    private void startFight(int idx) {
        free = false; fightTime = 0; penaltyT = 0;
        bossIdx = idx; boss = roster.get(idx);
        bossHp = boss.hp(); timeLeft = boss.seconds(); playerHp = 100; fightWords = 0;
        engine = new TypingEngine(boss.difficulty());
        fx.clear(); spawnWord(); setState(State.INTRO);
    }

    /** Free Mode: a fixed list of words laid out in lines, timed, no bosses. */
    private void startFree() {
        free = true; freeStarted = false; freeIdx = 0; fightWords = 0; fightTime = 0; timeLeft = FREE_SECONDS;
        List<String> pool = WordBank.filter(3, 9);
        freeWords = new ArrayList<>();
        wordLine = new int[FREE_WORDS]; wordCol = new int[FREE_WORDS];
        String prev = "";
        int line = 0, col = 0;
        for (int i = 0; i < FREE_WORDS; i++) {
            String w;
            do { w = pool.get(rng.nextInt(pool.size())); } while (w.equals(prev));
            prev = w; freeWords.add(w);
            if (col + w.length() > FREE_COLS) { line++; col = 0; }
            wordLine[i] = line; wordCol[i] = col; col += w.length() + 1;
        }
        engine = new TypingEngine(Difficulty.NORMAL);
        engine.load(freeWords.get(0));
        fx.clear(); setState(State.FREE_PLAY);
    }

    private void spawnWord() { wordAge = 0; wordOffset = (rng.nextDouble() - .5) * 280; }

    private void update(double dt) {
        clock += dt; stateT += dt; wordAge += dt; fx.update(dt);
        penaltyT = Math.max(0, penaltyT - dt);
        switch (state) {
            case INTRO -> { if (stateT > 1.8) setState(State.FIGHT); }
            case FIGHT -> {
                timeLeft -= dt; fightTime += dt;
                playerHp -= dt * DRAIN_PER_FIGHT / boss.seconds();
                if (timeLeft <= 0 || playerHp <= 0) { playerHp = 0; timeLeft = Math.max(0, timeLeft); Sfx.play("lose"); finishFight(State.LOST); }
            }
            case FREE_PLAY -> {
                if (freeStarted) {   // timer starts on the first keystroke
                    timeLeft -= dt; fightTime += dt;
                    if (timeLeft <= 0) { timeLeft = 0; Sfx.play("win"); finishFight(State.RESULTS); }
                }
            }
            case DEFEATED -> {
                if (stateT > 2.0) {
                    if (bossIdx + 1 == roster.size()) Sfx.play("win");
                    setState(State.RESULTS);
                }
            }
            default -> { }
        }
    }

    private void handleTyping(char c) {
        switch (engine.type(c)) {
            case CORRECT -> Sfx.play("key");
            case WRONG -> {
                playerHp -= MISTAKE_DAMAGE;
                if (hardcore) { timeLeft -= HARDCORE_PENALTY; penaltyT = 0.6; }
                Sfx.play("error");
            }
            case COMPLETE -> {
                fightWords++;
                int dmg = engine.word().length() * DAMAGE_PER_LETTER;
                bossHp = Math.max(0, bossHp - dmg);
                fx.hit(dmg, W / 2.0, 200);
                Sfx.play("hit");
                if (bossHp <= 0) { Sfx.play("kill"); finishFight(State.DEFEATED); }
                else { engine.next(); spawnWord(); }
            }
        }
    }

    private void handleFreeTyping(char c) {
        freeStarted = true;
        switch (engine.type(c)) {
            case CORRECT -> Sfx.play("key");
            case WRONG -> Sfx.play("error");
            case COMPLETE -> {
                fightWords++; Sfx.play("key");
                freeIdx = Math.min(freeIdx + 1, freeWords.size() - 1);
                engine.load(freeWords.get(freeIdx));
            }
        }
    }

    /** Freezes this run's stats for the results panel. */
    private void finishFight(State next) {
        selected = 0;
        result = FightResult.of(engine.correct(), engine.accuracy(), fightWords, fightTime);
        setState(next);
    }

    /** Results / game-over panel: 0 = primary (retry / next boss / play again), 1 = main menu. */
    private void choose(int i) {
        if (i == 1) toMenu();
        else if (free) startFree();
        else if (state == State.LOST) startFight(bossIdx);
        else startFight(bossIdx + 1 < roster.size() ? bossIdx + 1 : 0);
    }

    /** Handles a click / ENTER on whichever button list is on screen. */
    private void activate(int i) {
        switch (state) {
            case MENU -> {
                if (i == 0) startFree();
                else if (i == 1) { selected = 0; setState(State.MODE_SELECT); }
                else System.exit(0);
            }
            case MODE_SELECT -> {
                if (i == 2) { setState(State.MENU); selected = 1; }
                else { hardcore = i == 1; startFight(0); }
            }
            default -> choose(i);
        }
    }

    private int optionCount() {
        return state == State.MENU || state == State.MODE_SELECT ? 3 : state == State.RESULTS || state == State.LOST ? 2 : 0;
    }

    private int buttonAt(Point p) {
        for (int i = 0; i < optionCount(); i++) if (buttons[i].contains(p)) return i;
        return -1;
    }

    private double danger() {
        double d = timeLeft <= 10 ? 0.45 + 0.55 * (1 - timeLeft / 10) : 0;
        if (playerHp < 30) d = Math.max(d, 0.3 + 0.7 * (1 - playerHp / 30));
        return Math.min(1, d);
    }

    // ---------- rendering ----------

    @Override
    protected void paintComponent(Graphics gr) {
        super.paintComponent(gr);
        Graphics2D g = (Graphics2D) gr.create();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g.drawImage(Assets.img("bg"), 0, 0, W, H, null);
        if (state == State.MENU || state == State.MODE_SELECT) drawMenu(g);
        else if (free) { drawFree(g); if (state == State.RESULTS) drawResults(g); }
        else drawFight(g);
        g.dispose();
    }

    private void drawButton(Graphics2D g, Rectangle b, String label, boolean sel, Color accent, int size) {
        g.setColor(sel ? accent : new Color(40, 28, 64)); g.fillRect(b.x, b.y, b.width, b.height);
        g.setColor(sel ? Color.WHITE : accent); g.setStroke(new BasicStroke(3)); g.drawRect(b.x, b.y, b.width, b.height);
        Hud.text(g, label, b.x + b.width / 2, b.y + b.height / 2 + size / 3, size, sel ? new Color(25, 12, 35) : Color.WHITE, 0);
    }

    private void drawMenu(Graphics2D g) {
        boolean sub = state == State.MODE_SELECT;
        Hud.dim(g, W, H, 110);
        Hud.text(g, "TYPE SLAYER", W / 2, 120, 84, GOLD, 0);
        Hud.text(g, sub ? "BOSS FIGHT - CHOOSE A MODE" : "Type fast. Slay the bosses. Don't die.", W / 2, 165, 22, Color.WHITE, 0);
        for (int i = 0; i < roster.size(); i++) {
            int x = i < 2 ? 90 : 742, y = 195 + (i % 2) * 145 + (int) (Math.sin(clock * 2 + i) * 6);
            g.drawImage(Assets.img(roster.get(i).sprite()), x, y, 128, 128, null);
        }
        String[] labels = sub ? new String[]{"NORMAL MODE", "HARDCORE MODE", "BACK"} : new String[]{"FREE MODE", "BOSS FIGHT", "EXIT"};
        String[] desc = sub
            ? new String[]{"Beat each boss before the timer runs out.", "Like Normal, but every error costs 1 second.", "Back to the main menu."}
            : new String[]{"Type for " + FREE_SECONDS + " seconds. No bosses, just the words.", "Defeat " + roster.size() + " bosses before time runs out.", "Close the game."};
        for (int i = 0; i < 3; i++) {
            buttons[i].setBounds(W / 2 - 170, 215 + i * 80, 340, 60);
            drawButton(g, buttons[i], labels[i], i == selected, sub && i == 1 ? RED : GOLD, 26);
        }
        Hud.text(g, desc[selected], W / 2, 500, 18, Color.LIGHT_GRAY, 0);
        Hud.text(g, "ARROWS to select   ENTER to confirm", W / 2, 560, 14, Color.GRAY, 0);
    }

    private void drawFree(Graphics2D g) {
        Hud.text(g, "FREE MODE", 40, 50, 26, GOLD, -1);
        Hud.text(g, "ESC - menu", W - 40, 50, 16, Color.GRAY, 1);
        int t = (int) Math.ceil(Math.max(0, timeLeft));
        boolean urgent = freeStarted && timeLeft <= 10;
        Hud.text(g, String.format("%02d:%02d", t / 60, t % 60), W / 2, 125, 64, urgent && Math.sin(clock * 12) > 0 ? RED : Color.WHITE, 0);
        if (!freeStarted && (int) (clock * 2) % 2 == 0) Hud.text(g, "Start typing to begin", W / 2, 162, 18, Color.LIGHT_GRAY, 0);

        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 34));
        int cw = g.getFontMetrics().charWidth('m'), pw = FREE_COLS * cw + 60, px = (W - pw) / 2, py = 185, ph = 260;
        g.setColor(new Color(10, 5, 20, 210)); g.fillRect(px, py, pw, ph);
        g.setColor(GOLD); g.setStroke(new BasicStroke(4)); g.drawRect(px, py, pw, ph);

        int firstLine = Math.max(0, wordLine[freeIdx] - 1);   // 4 visible lines, current word on the 2nd
        for (int i = 0; i < freeWords.size(); i++) {
            int row = wordLine[i] - firstLine;
            if (row < 0) continue;
            if (row > 3) break;
            String w = freeWords.get(i);
            int x = px + 30 + wordCol[i] * cw, y = py + 65 + row * 56;
            if (i != freeIdx) { Hud.text(g, w, x, y, 34, i < freeIdx ? GREEN : Color.WHITE, -1); continue; }
            for (int j = 0; j < w.length(); j++) {
                boolean cur = j == engine.index(), err = cur && engine.hasError();
                Color c = j < engine.index() ? GREEN : err ? RED : cur ? new Color(255, 225, 80) : Color.WHITE;
                int cx = x + j * cw + (err ? (int) (Math.sin(clock * 90) * 3) : 0);
                Hud.text(g, String.valueOf(w.charAt(j)), cx, y, 34, c, -1);
                if (cur) { g.setColor(c); g.fillRect(cx, y + 10, cw - 3, 4); }
            }
        }
        double wpm = fightTime < 1 ? 0 : engine.correct() / 5.0 / (fightTime / 60.0);
        Hud.text(g, String.format("WPM %.0f     ACC %.0f%%     WORDS %d", wpm, engine.accuracy(), fightWords), W / 2, 510, 26, Color.WHITE, 0);
    }

    private void drawFight(Graphics2D g) {
        drawBoss(g);
        drawHud(g);
        if (state == State.FIGHT) { drawWord(g); Hud.vignette(g, W, H, danger(), clock); }
        if (state == State.LOST) Hud.vignette(g, W, H, 1, clock);
        for (Fx.Pop p : fx.pops())
            Hud.text(g, p.text, (int) p.x, (int) p.y, 44, new Color(255, 230, 80, (int) (255 * Math.min(1, p.life * 1.5))), 0);

        switch (state) {
            case INTRO -> {
                Hud.dim(g, W, H, 120);
                Hud.text(g, "BOSS " + (bossIdx + 1) + " / " + roster.size(), W / 2, 250, 26, Color.WHITE, 0);
                Hud.text(g, boss.name().toUpperCase(), W / 2, 315, 60, GOLD, 0);
                Hud.text(g, boss.difficulty().label + "  -  " + boss.seconds() + " SECONDS", W / 2, 360, 24, boss.difficulty().color, 0);
                if (hardcore) Hud.text(g, "HARDCORE: EACH ERROR = -" + (int) HARDCORE_PENALTY + " SECOND", W / 2, 400, 20, RED, 0);
            }
            case DEFEATED -> Hud.text(g, "BOSS DEFEATED!", W / 2, 470, 56, GOLD, 0);
            case RESULTS, LOST -> drawResults(g);
            default -> { }
        }
    }

    private void drawBoss(Graphics2D g) {
        BufferedImage spr = Assets.img(boss.sprite());
        int size = 256, x = W / 2 - size / 2 + (int) (Math.sin(clock * 90) * fx.shake() * 10);
        int y = (int) (120 + Math.sin(clock * 2) * 6 + (state == State.DEFEATED ? stateT * 25 : 0));
        float alpha = state == State.DEFEATED ? (float) Math.max(0, 1 - stateT / 1.6) : 1f;
        Composite old = g.getComposite();
        g.setComposite(AlphaComposite.SrcOver.derive(alpha));
        g.drawImage(spr, x, y, size, size, null);
        if (fx.flash() > 0) {   // white flash on hit
            g.setComposite(AlphaComposite.SrcOver.derive((float) (alpha * fx.flash())));
            g.drawImage(Assets.silhouette(boss.sprite()), x, y, size, size, null);
        }
        g.setComposite(old);
        if (fx.slashFrame() >= 0) g.drawImage(Assets.img("slash_" + fx.slashFrame()), x, y, size, size, null);
    }

    private void drawHud(Graphics2D g) {
        int bx = 130, bw = 700;
        if (hardcore) Hud.text(g, "HARDCORE", 20, 30, 16, RED, -1);
        Hud.text(g, boss.name().toUpperCase(), bx, 30, 22, Color.WHITE, -1);
        Hud.text(g, "[" + boss.difficulty().label + "]", bx + bw, 30, 22, boss.difficulty().color, 1);
        Hud.bar(g, bx, 40, bw, 22, bossHp / boss.hp(), new Color(220, 60, 70));
        Hud.text(g, (int) Math.ceil(bossHp) + " / " + boss.hp(), W / 2, 57, 16, Color.WHITE, 0);

        int t = (int) Math.ceil(Math.max(0, timeLeft));
        boolean urgent = timeLeft <= 10 && state == State.FIGHT;
        Color tc = (urgent && Math.sin(clock * 12) > 0) || penaltyT > 0 ? RED : Color.WHITE;
        Hud.text(g, String.format("%02d:%02d", t / 60, t % 60), W / 2, 104, 40, tc, 0);
        if (penaltyT > 0) Hud.text(g, "-" + (int) HARDCORE_PENALTY + "s", W / 2 + 130, 104, 32, RED, -1);

        g.drawImage(Assets.img("heart"), 30, 534, null);
        Hud.bar(g, 75, 540, 300, 18, playerHp / 100.0, playerHp < 30 ? RED : new Color(90, 220, 110));
        Hud.text(g, String.format("ACC %.0f%%   WORDS %d", engine.accuracy(), fightWords), W - 30, 558, 20, Color.WHITE, 1);
    }

    private void drawWord(Graphics2D g) {
        String w = engine.word();
        int idx = engine.index();
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 48));
        int cw = g.getFontMetrics().charWidth('m'), bw = w.length() * cw + 48;
        double s = Math.min(1, 0.5 + wordAge * 5);   // pop-in when a word appears
        AffineTransform old = g.getTransform();
        g.translate(W / 2 + wordOffset, 470);
        g.scale(s, s);
        g.setColor(new Color(10, 5, 20, 200)); g.fillRect(-bw / 2, -42, bw, 84);
        g.setColor(boss.difficulty().color); g.setStroke(new BasicStroke(4)); g.drawRect(-bw / 2, -42, bw, 84);
        int x0 = -w.length() * cw / 2;
        for (int i = 0; i < w.length(); i++) {
            boolean cur = i == idx, err = cur && engine.hasError();
            Color c = i < idx ? GREEN : err ? RED : cur ? new Color(255, 225, 80) : Color.WHITE;
            int x = x0 + i * cw + (err ? (int) (Math.sin(clock * 90) * 4) : 0);
            Hud.text(g, String.valueOf(w.charAt(i)), x, 16, 48, c, -1);
            if (cur) { g.setColor(c); g.fillRect(x, 26, cw - 4, 5); }
        }
        g.setTransform(old);
    }

    /** Shared by boss fights (win / lose) and Free Mode. */
    private void drawResults(Graphics2D g) {
        boolean lost = state == State.LOST, last = !free && bossIdx + 1 == roster.size();
        Color accent = lost ? RED : GOLD;
        int px = 220, py = 70, pw = 520, ph = 460;
        Hud.dim(g, W, H, 180);
        g.setColor(new Color(15, 8, 28, 240)); g.fillRect(px, py, pw, ph);
        g.setColor(accent); g.setStroke(new BasicStroke(4)); g.drawRect(px, py, pw, ph);
        String title = free ? "TIME'S UP!" : lost ? "YOU DIED" : last ? "ALL BOSSES SLAIN!" : "BOSS DEFEATED!";
        Hud.text(g, title, W / 2, py + 68, title.length() > 14 || free || lost ? 40 : 46, accent, 0);
        String sub = free ? "FREE MODE" : boss.name().toUpperCase() + "  [" + boss.difficulty().label + "]" + (hardcore ? "  HARDCORE" : "");
        Hud.text(g, sub, W / 2, py + 104, 20, free ? Color.WHITE : boss.difficulty().color, 0);

        String[][] rows = {
            {"WPM", String.format("%.0f", result.wpm())},
            {"ACCURACY", String.format("%.0f%%", result.accuracy())},
            {"WORDS", String.valueOf(result.words())},
            {"TIME", String.format("%.1fs", result.seconds())}};
        for (int i = 0; i < rows.length; i++) {
            int y = py + 175 + i * 48;
            Hud.text(g, rows[i][0], px + 70, y, 26, Color.LIGHT_GRAY, -1);
            Hud.text(g, rows[i][1], px + pw - 70, y, 32, i == 0 ? GOLD : Color.WHITE, 1);
            g.setColor(new Color(255, 255, 255, 40)); g.fillRect(px + 60, y + 14, pw - 120, 2);
        }

        String[] labels = {lost ? "RETRY" : free || last ? "PLAY AGAIN" : "NEXT BOSS", "MAIN MENU"};
        for (int i = 0; i < 2; i++) {
            buttons[i].setBounds(px + 30 + i * 250, py + ph - 100, 210, 54);
            drawButton(g, buttons[i], labels[i], i == selected, accent, 22);
        }
        Hud.text(g, "ARROWS to select   ENTER to confirm", W / 2, py + ph - 20, 14, Color.GRAY, 0);
    }

    // ---------- input ----------

    @Override public void keyTyped(KeyEvent e) {
        char c = e.getKeyChar();
        if (!Character.isLetter(c)) return;
        if (state == State.FIGHT) handleTyping(c);
        else if (state == State.FREE_PLAY) handleFreeTyping(c);
    }

    @Override public void keyPressed(KeyEvent e) {
        int k = e.getKeyCode(), n = optionCount();
        if (k == KeyEvent.VK_ESCAPE) {
            if (state == State.MODE_SELECT) { setState(State.MENU); selected = 1; }
            else if (state != State.MENU) toMenu();
        }
        else if (k == KeyEvent.VK_ENTER && n > 0) activate(selected);
        else if (n > 0 && (k == KeyEvent.VK_LEFT || k == KeyEvent.VK_UP)) selected = (selected + n - 1) % n;
        else if (n > 0 && (k == KeyEvent.VK_RIGHT || k == KeyEvent.VK_DOWN)) selected = (selected + 1) % n;
    }

    @Override public void keyReleased(KeyEvent e) { }
}
