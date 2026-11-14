package typingboss.core;

import typingboss.audio.Music;
import typingboss.audio.Sfx;
import typingboss.data.Database;
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
import java.time.LocalDate;
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
    private static final int[] FREE_OPTIONS = { 5, 15, 30, 60 };   // Free Mode durations (seconds)
    private static final int FREE_COLS = 36, FREE_WORDS = 300;
    private static final String[] LB_MODES = { "FREE-5", "FREE-15", "FREE-30", "FREE-60", "NORMAL", "HARDCORE", "SLAYERS" };
    private static final String[] LB_LABELS = { "FREE 5s", "FREE 15s", "FREE 30s", "FREE 60s", "NORMAL", "HARDCORE", "SLAYERS" };
    private static final int LOAD_ROWS = 5;   // players visible at once on the Load Game screen
    private static final Color GOLD = new Color(255, 215, 90), RED = new Color(235, 70, 70), GREEN = new Color(90, 230, 110);

    private enum State { TITLE, NAME_ENTRY, LOAD_SELECT, LEADERBOARD, MENU, MODE_SELECT, FREE_SELECT, INTRO, FIGHT, DEFEATED, FREE_PLAY, RESULTS, LOST }

    private final List<Boss> roster = Boss.roster();
    private final Fx fx = new Fx();
    private final Random rng = new Random();
    private State state = State.TITLE;
    private Boss boss;
    private TypingEngine engine;
    private boolean free, hardcore, freeStarted;
    private int bossIdx, fightWords, selected, freeIdx, freeSeconds = 60;
    private List<String> freeWords = new ArrayList<>();
    private int[] wordLine = new int[0], wordCol = new int[0];
    private FightResult result;
    private final Rectangle[] buttons = java.util.stream.Stream.generate(Rectangle::new).limit(8).toArray(Rectangle[]::new);
    // player profile, name entry, load list and leaderboard
    private String player = "", nameInput = "", nameMsg = "";
    private List<String> players = new ArrayList<>();
    private List<Database.Score> lbRows = new ArrayList<>();
    private List<Database.Slayer> slayRows = new ArrayList<>();
    private State lbReturn = State.TITLE;
    private int scroll, lbTab, lbCacheTab = -1;
    // totals of the current boss run (kept across Next Boss / Retry, reset by beginRun)
    private int runBosses, runWords, runCorrect, runWrong;
    private double runSeconds;
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
        Music.playMenu();
    }

    // ---------- game logic ----------

    private void setState(State s) { state = s; stateT = 0; }

    private void toMenu() { fx.clear(); selected = 0; setState(State.MENU); Music.playMenu(); }

    private void startFight(int idx) {
        free = false; fightTime = 0; penaltyT = 0;
        bossIdx = idx; boss = roster.get(idx);
        bossHp = boss.hp(); timeLeft = boss.seconds(); playerHp = 100; fightWords = 0;
        engine = new TypingEngine(boss.difficulty());
        fx.clear(); spawnWord(); setState(State.INTRO);
        Music.playRandom();
    }

    /** Free Mode: a fixed list of words laid out in lines, timed, no bosses. */
    private void startFree() {
        free = true; freeStarted = false; freeIdx = 0; fightWords = 0; fightTime = 0; timeLeft = freeSeconds;
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
        Music.playRandom();
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
        record(next);
        setState(next);
    }

    private void beginRun() { runBosses = runWords = runCorrect = runWrong = 0; runSeconds = 0; }

    /**
     * Saves the finished fight to the leaderboard. Free Mode: one entry per timed run.
     * Boss modes: one entry for the whole run so far (bosses beaten, overall WPM / accuracy);
     * the leaderboard only shows each player's best entry, so repeated saves are fine.
     */
    private void record(State next) {
        if (player.isEmpty()) return;
        String today = LocalDate.now().toString();
        if (free) {
            if (result.wpm() > 0)
                Database.addScore(new Database.Score(player, "FREE-" + freeSeconds, result.wpm(), result.accuracy(), result.words(), result.seconds(), 0, today));
            return;
        }
        runCorrect += engine.correct(); runWrong += engine.wrong(); runWords += fightWords; runSeconds += result.seconds();
        if (next == State.DEFEATED) {
            runBosses = Math.max(runBosses, bossIdx + 1);
            Database.addKill(player, hardcore ? "HARDCORE" : "NORMAL", boss.name());   // counts every boss slain, replays too
        }
        if (runBosses == 0) return;
        int keys = runCorrect + runWrong;
        double wpm = runCorrect / 5.0 / (runSeconds / 60.0), acc = keys == 0 ? 100 : 100.0 * runCorrect / keys;
        Database.addScore(new Database.Score(player, hardcore ? "HARDCORE" : "NORMAL", wpm, acc, runWords, runSeconds, runBosses, today));
    }

    /** Results / game-over panel: 0 = primary (retry / next boss / play again), 1 = main menu. */
    private void choose(int i) {
        if (i == 1) toMenu();
        else if (free) startFree();
        else if (state == State.LOST) startFight(bossIdx);
        else {
            int nxt = bossIdx + 1 < roster.size() ? bossIdx + 1 : 0;
            if (nxt == 0) beginRun();
            startFight(nxt);
        }
    }

    /** Handles a click / ENTER on whichever button list is on screen. */
    private void activate(int i) {
        switch (state) {
            case TITLE -> {
                switch (i) {
                    case 0 -> { nameInput = ""; nameMsg = ""; selected = 0; setState(State.NAME_ENTRY); }
                    case 1 -> { players = Database.players(); scroll = 0; selected = 0; setState(State.LOAD_SELECT); }
                    case 2 -> openLeaderboard(State.TITLE);
                    default -> System.exit(0);
                }
            }
            case NAME_ENTRY -> { if (i == 0) confirmName(); else back(); }
            case LOAD_SELECT -> {
                if (i < players.size()) { player = players.get(i); selected = 0; setState(State.MENU); }
                else back();
            }
            case LEADERBOARD -> { if (i == LB_MODES.length) back(); }
            case MENU -> {
                switch (i) {
                    case 0 -> {
                        selected = 0;
                        for (int k = 0; k < FREE_OPTIONS.length; k++) if (FREE_OPTIONS[k] == freeSeconds) selected = k;
                        setState(State.FREE_SELECT);
                    }
                    case 1 -> { selected = 0; setState(State.MODE_SELECT); }
                    case 2 -> openLeaderboard(State.MENU);
                    default -> back();
                }
            }
            case MODE_SELECT -> {
                if (i == 2) back();
                else { hardcore = i == 1; beginRun(); startFight(0); }
            }
            case FREE_SELECT -> {
                if (i == FREE_OPTIONS.length) back();
                else { freeSeconds = FREE_OPTIONS[i]; startFree(); }
            }
            default -> choose(i);
        }
    }

    /** ESC / BACK: one step back in the menus, or out of a fight. */
    private void back() {
        switch (state) {
            case TITLE -> { }
            case NAME_ENTRY -> { selected = 0; setState(State.TITLE); }
            case LOAD_SELECT -> { selected = 1; setState(State.TITLE); }
            case LEADERBOARD -> closeLeaderboard();
            case MENU -> { selected = 1; setState(State.TITLE); }   // switch player
            case MODE_SELECT -> { selected = 1; setState(State.MENU); }
            case FREE_SELECT -> { selected = 0; setState(State.MENU); }
            default -> toMenu();
        }
    }

    private void confirmName() {
        String name = Database.cleanName(nameInput);
        if (name.isEmpty()) nameMsg = "Type a name first.";
        else if (Database.exists(name)) nameMsg = "That name is taken. Use LOAD GAME to play as " + name + ".";
        else {
            Database.addPlayer(name);
            player = name; selected = 0;
            setState(State.MENU);
        }
    }

    private void openLeaderboard(State from) { lbReturn = from; lbCacheTab = -1; selected = lbTab; setState(State.LEADERBOARD); }

    private void closeLeaderboard() { selected = 2; setState(lbReturn); }

    private int optionCount() {
        return switch (state) {
            case TITLE, MENU -> 4;
            case MODE_SELECT -> 3;
            case FREE_SELECT -> FREE_OPTIONS.length + 1;
            case NAME_ENTRY -> 2;
            case LOAD_SELECT -> players.size() + 1;           // every player + BACK
            case LEADERBOARD -> LB_MODES.length + 1;          // every tab + BACK
            case RESULTS, LOST -> 2;
            default -> 0;
        };
    }

    private int buttonAt(Point p) {
        if (state == State.LOAD_SELECT) {   // rows are scrolled, so map the visible row to the real player index
            for (int r = 0; r < LOAD_ROWS; r++) if (scroll + r < players.size() && buttons[r].contains(p)) return scroll + r;
            return buttons[LOAD_ROWS].contains(p) ? players.size() : -1;
        }
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
        if (state == State.TITLE || state == State.MENU || state == State.MODE_SELECT || state == State.FREE_SELECT) drawMenu(g);
        else if (state == State.NAME_ENTRY) drawNameEntry(g);
        else if (state == State.LOAD_SELECT) drawLoad(g);
        else if (state == State.LEADERBOARD) drawLeaderboard(g);
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
        Hud.dim(g, W, H, 110);
        Hud.text(g, "TYPE SLAYER", W / 2, 120, 84, GOLD, 0);
        String subtitle;
        String[] labels, desc;
        switch (state) {
            case TITLE -> {
                subtitle = "Type fast. Slay the bosses. Don't die.";
                labels = new String[]{"NEW GAME", "LOAD GAME", "LEADERBOARD", "EXIT"};
                desc = new String[]{"Create a new player.", "Continue as a saved player.", "See the best typists.", "Close the game."};
            }
            case MENU -> {
                subtitle = "WELCOME, " + player.toUpperCase();
                labels = new String[]{"FREE MODE", "BOSS FIGHT", "LEADERBOARD", "SWITCH PLAYER"};
                desc = new String[]{"Pick 5, 15, 30 or 60 seconds. No bosses, just the words.", "Defeat " + roster.size() + " bosses before time runs out.",
                    "Top scores for every mode.", "Back to the title screen."};
            }
            case MODE_SELECT -> {
                subtitle = "BOSS FIGHT - CHOOSE A MODE";
                labels = new String[]{"NORMAL MODE", "HARDCORE MODE", "BACK"};
                desc = new String[]{"Beat each boss before the timer runs out.", "Like Normal, but every error costs 1 second.", "Back to the main menu."};
            }
            default -> {   // FREE_SELECT
                subtitle = "FREE MODE - CHOOSE A DURATION";
                int n = FREE_OPTIONS.length;
                labels = new String[n + 1]; desc = new String[n + 1];
                for (int i = 0; i < n; i++) {
                    labels[i] = FREE_OPTIONS[i] + " SECONDS";
                    desc[i] = "Type for " + FREE_OPTIONS[i] + " seconds. No bosses, just the words.";
                }
                labels[n] = "BACK"; desc[n] = "Back to the main menu.";
            }
        }
        Hud.text(g, subtitle, W / 2, 165, 22, Color.WHITE, 0);
        if (state != State.TITLE) Hud.text(g, "PLAYER: " + player, 20, 30, 16, Color.LIGHT_GRAY, -1);
        for (int i = 0; i < roster.size(); i++) {
            int x = i < 2 ? 90 : 742, y = 195 + (i % 2) * 145 + (int) (Math.sin(clock * 2 + i) * 6);
            g.drawImage(Assets.img(roster.get(i).sprite()), x, y, 128, 128, null);
        }
        boolean compact = labels.length > 3;   // 4 or 5 buttons need tighter spacing
        int top = compact ? 190 : 215, gap = compact ? 64 : 80, bh = compact ? 52 : 60;
        for (int i = 0; i < labels.length; i++) {
            buttons[i].setBounds(W / 2 - 170, top + i * gap, 340, bh);
            drawButton(g, buttons[i], labels[i], i == selected, state == State.MODE_SELECT && i == 1 ? RED : GOLD, compact ? 24 : 26);
        }
        Hud.text(g, desc[selected], W / 2, compact ? 535 : 500, 18, Color.LIGHT_GRAY, 0);
        Hud.text(g, "ARROWS to select   ENTER to confirm", W / 2, compact ? 575 : 560, 14, Color.GRAY, 0);
    }

    private void drawNameEntry(Graphics2D g) {
        Hud.dim(g, W, H, 150);
        Hud.text(g, "NEW GAME", W / 2, 130, 64, GOLD, 0);
        Hud.text(g, "ENTER YOUR PLAYER NAME", W / 2, 190, 22, Color.WHITE, 0);
        int bw = 460, bh = 70, bx = (W - bw) / 2, by = 225;
        g.setColor(new Color(10, 5, 20, 230)); g.fillRect(bx, by, bw, bh);
        g.setColor(GOLD); g.setStroke(new BasicStroke(4)); g.drawRect(bx, by, bw, bh);
        Hud.text(g, nameInput + ((int) (clock * 2) % 2 == 0 ? "_" : " "), bx + 20, by + bh / 2 + 12, 36, Color.WHITE, -1);
        Hud.text(g, nameInput.length() + " / " + Database.MAX_NAME, bx + bw, by + bh + 24, 14, Color.GRAY, 1);
        if (!nameMsg.isEmpty()) Hud.text(g, nameMsg, W / 2, by + bh + 64, 18, RED, 0);
        buttons[0].setBounds(W / 2 - 230, 400, 220, 54);
        buttons[1].setBounds(W / 2 + 10, 400, 220, 54);
        drawButton(g, buttons[0], "START", selected == 0, GOLD, 24);
        drawButton(g, buttons[1], "BACK", selected == 1, GOLD, 24);
        Hud.text(g, "Letters, numbers, space, _ and -   ENTER to confirm   ESC to go back", W / 2, 500, 14, Color.GRAY, 0);
    }

    private void drawLoad(Graphics2D g) {
        Hud.dim(g, W, H, 150);
        Hud.text(g, "LOAD GAME", W / 2, 105, 56, GOLD, 0);
        Hud.text(g, players.isEmpty() ? "No saved players yet. Start a NEW GAME first." : "Choose your player", W / 2, 145, 20, Color.WHITE, 0);
        if (selected < players.size()) {   // keep the highlighted player inside the visible rows
            if (selected < scroll) scroll = selected;
            if (selected >= scroll + LOAD_ROWS) scroll = selected - LOAD_ROWS + 1;
        }
        for (int r = 0; r < LOAD_ROWS && scroll + r < players.size(); r++) {
            int idx = scroll + r;
            Rectangle b = buttons[r];
            b.setBounds(W / 2 - 260, 170 + r * 62, 520, 52);
            boolean sel = idx == selected;
            g.setColor(sel ? GOLD : new Color(40, 28, 64)); g.fillRect(b.x, b.y, b.width, b.height);
            g.setColor(sel ? Color.WHITE : GOLD); g.setStroke(new BasicStroke(3)); g.drawRect(b.x, b.y, b.width, b.height);
            Color fg = sel ? new Color(25, 12, 35) : Color.WHITE;
            String name = players.get(idx);
            double best = Database.bestWpm(name);
            Hud.text(g, name, b.x + 20, b.y + 35, 24, fg, -1);
            Hud.text(g, best < 0 ? "NO SCORES" : String.format("BEST %.0f WPM", best), b.x + b.width - 20, b.y + 34, 18, sel ? fg : Color.LIGHT_GRAY, 1);
        }
        buttons[LOAD_ROWS].setBounds(W / 2 - 100, 490, 200, 48);
        drawButton(g, buttons[LOAD_ROWS], "BACK", selected == players.size(), GOLD, 22);
        if (players.size() > LOAD_ROWS)
            Hud.text(g, (Math.min(selected, players.size() - 1) + 1) + " / " + players.size(), W - 40, 575, 14, Color.GRAY, 1);
        Hud.text(g, "ARROWS to select   ENTER to confirm   ESC to go back", W / 2, 575, 14, Color.GRAY, 0);
    }

    private void drawLeaderboard(Graphics2D g) {
        int n = LB_MODES.length;
        if (selected < n) lbTab = selected;   // the tab under the cursor is the one shown
        boolean slayTab = lbTab == n - 1, bossTab = lbTab >= 4 && !slayTab;
        if (lbCacheTab != lbTab) {
            if (slayTab) slayRows = Database.slayers(10); else lbRows = Database.top(LB_MODES[lbTab], 10);
            lbCacheTab = lbTab;
        }
        Color accent = lbTab == 5 ? RED : GOLD;
        Hud.dim(g, W, H, 150);
        Hud.text(g, "LEADERBOARD", W / 2, 62, 48, GOLD, 0);
        int tw = 122, tg = 6, x0 = (W - (n * tw + (n - 1) * tg)) / 2;
        for (int i = 0; i < n; i++) {
            buttons[i].setBounds(x0 + i * (tw + tg), 84, tw, 40);
            drawButton(g, buttons[i], LB_LABELS[i], i == lbTab, i == 5 ? RED : GOLD, 16);
        }
        int px = 100, py = 140, pw = 760, ph = 370;
        int[] colX = {440, 590, 730};   // right edge of each stat column
        g.setColor(new Color(15, 8, 28, 235)); g.fillRect(px, py, pw, ph);
        g.setColor(accent); g.setStroke(new BasicStroke(4)); g.drawRect(px, py, pw, ph);
        String[] head = slayTab ? new String[]{"SLAIN", "NORMAL", "HARDCORE"}
                      : bossTab ? new String[]{"BOSSES", "WPM", "ACC"} : new String[]{"WPM", "ACC", "WORDS"};
        Hud.text(g, "#", px + 30, py + 36, 18, Color.GRAY, -1);
        Hud.text(g, "PLAYER", px + 90, py + 36, 18, Color.GRAY, -1);
        for (int c = 0; c < 3; c++) Hud.text(g, head[c], px + colX[c], py + 36, 18, Color.GRAY, 1);
        g.setColor(new Color(255, 255, 255, 40)); g.fillRect(px + 20, py + 46, pw - 40, 2);
        int rows = slayTab ? slayRows.size() : lbRows.size();
        if (rows == 0) Hud.text(g, slayTab ? "NO BOSSES SLAIN YET!" : "NO SCORES YET - BE THE FIRST!", W / 2, py + ph / 2 + 20, 22, Color.LIGHT_GRAY, 0);
        for (int i = 0; i < rows; i++) {
            String name;
            String[] vals;
            if (slayTab) {
                Database.Slayer s = slayRows.get(i);
                name = s.name();
                vals = new String[]{String.valueOf(s.total()), String.valueOf(s.normal()), String.valueOf(s.hardcore())};
            } else {
                Database.Score s = lbRows.get(i);
                name = s.name();
                vals = bossTab
                    ? new String[]{s.bosses() + "/" + roster.size(), String.format("%.0f", s.wpm()), String.format("%.0f%%", s.accuracy())}
                    : new String[]{String.format("%.0f", s.wpm()), String.format("%.0f%%", s.accuracy()), String.valueOf(s.words())};
            }
            int y = py + 78 + i * 28;
            Color c = name.equalsIgnoreCase(player) ? GOLD : Color.WHITE;
            Hud.text(g, String.valueOf(i + 1), px + 30, y, 20, c, -1);
            Hud.text(g, name, px + 90, y, 20, c, -1);
            for (int k = 0; k < 3; k++) Hud.text(g, vals[k], px + colX[k], y, 20, c, 1);
        }
        buttons[n].setBounds(W / 2 - 100, 522, 200, 46);
        drawButton(g, buttons[n], "BACK", selected == n, GOLD, 22);
        Hud.text(g, "ARROWS to switch tab   ESC to go back", W / 2, 590, 14, Color.GRAY, 0);
    }

    private void drawFree(Graphics2D g) {
        Hud.text(g, "FREE MODE - " + freeSeconds + "s", 40, 50, 26, GOLD, -1);
        Hud.text(g, "ESC - menu", W - 40, 50, 16, Color.GRAY, 1);
        int t = (int) Math.ceil(Math.max(0, timeLeft));
        boolean urgent = freeStarted && timeLeft <= Math.min(10, freeSeconds / 3.0);
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
        String sub = free ? "FREE MODE - " + freeSeconds + " SECONDS" : boss.name().toUpperCase() + "  [" + boss.difficulty().label + "]" + (hardcore ? "  HARDCORE" : "");
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
        if (state == State.NAME_ENTRY) {
            if (c == '\b') { if (!nameInput.isEmpty()) nameInput = nameInput.substring(0, nameInput.length() - 1); nameMsg = ""; }
            else if (Database.allowed(c) && nameInput.length() < Database.MAX_NAME
                     && !(c == ' ' && (nameInput.isEmpty() || nameInput.endsWith(" ")))) { nameInput += c; nameMsg = ""; }
            return;
        }
        if (!Character.isLetter(c)) return;
        if (state == State.FIGHT) handleTyping(c);
        else if (state == State.FREE_PLAY) handleFreeTyping(c);
    }

    @Override public void keyPressed(KeyEvent e) {
        int k = e.getKeyCode(), n = optionCount();
        if (k == KeyEvent.VK_ESCAPE) back();
        else if (k == KeyEvent.VK_ENTER && n > 0) activate(selected);
        else if (n > 0 && (k == KeyEvent.VK_LEFT || k == KeyEvent.VK_UP)) selected = (selected + n - 1) % n;
        else if (n > 0 && (k == KeyEvent.VK_RIGHT || k == KeyEvent.VK_DOWN)) selected = (selected + 1) % n;
    }

    @Override public void keyReleased(KeyEvent e) { }
}