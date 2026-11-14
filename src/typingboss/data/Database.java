package typingboss.data;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Tiny text-file "database": data/players.csv, data/scores.csv and data/kills.csv.
 * Everything is read into memory once; every change is also appended to the file.
 * If the file can't be written the game keeps working from memory.
 */
public final class Database {
    public static final int MAX_NAME = 12;
    private static final String PLAYERS_HEADER = "name,created";
    private static final String SCORES_HEADER = "name,mode,wpm,accuracy,words,seconds,bosses,date";
    private static final String KILLS_HEADER = "name,mode,boss,date";

    /** One leaderboard entry. mode is FREE-5 / FREE-15 / FREE-30 / FREE-60 / NORMAL / HARDCORE; bosses is 0 in Free Mode. */
    public record Score(String name, String mode, double wpm, double accuracy, int words, double seconds, int bosses, String date) {}

    /** Boss kills of one player: all together and per mode. */
    public record Slayer(String name, int total, int normal, int hardcore) {}

    private static Path dir;
    private static List<String[]> kills;   // one entry per boss defeated: {name, mode, boss}
    private static List<String> players;
    private static List<Score> scores;

    private Database() {}

    // ---------- names ----------

    /** Characters allowed in a player name (also keeps the CSV safe: no commas). */
    public static boolean allowed(char c) {
        return (c < 128 && Character.isLetterOrDigit(c)) || c == ' ' || c == '_' || c == '-';
    }

    /** Removes bad characters, trims, collapses double spaces and cuts to MAX_NAME. */
    public static String cleanName(String raw) {
        StringBuilder sb = new StringBuilder();
        for (char c : raw.toCharArray()) if (allowed(c)) sb.append(c);
        String s = sb.toString().trim().replaceAll(" +", " ");
        return s.length() > MAX_NAME ? s.substring(0, MAX_NAME).trim() : s;
    }

    // ---------- players ----------

    public static List<String> players() { load(); return new ArrayList<>(players); }

    public static boolean exists(String name) {
        load();
        for (String p : players) if (p.equalsIgnoreCase(name)) return true;
        return false;
    }

    /** Returns false if the name is empty or already taken (case-insensitive). */
    public static boolean addPlayer(String raw) {
        load();
        String name = cleanName(raw);
        if (name.isEmpty() || exists(name)) return false;
        players.add(name);
        append("players.csv", PLAYERS_HEADER, name + "," + LocalDate.now());
        return true;
    }

    /** Highest WPM this player ever got, or -1 if they have no scores yet. */
    public static double bestWpm(String name) {
        load();
        double best = -1;
        for (Score s : scores) if (s.name().equalsIgnoreCase(name)) best = Math.max(best, s.wpm());
        return best;
    }

    // ---------- scores ----------

    public static void addScore(Score s) {
        load();
        scores.add(s);
        append("scores.csv", SCORES_HEADER, String.format(Locale.ROOT, "%s,%s,%.1f,%.1f,%d,%.1f,%d,%s",
            s.name(), s.mode(), s.wpm(), s.accuracy(), s.words(), s.seconds(), s.bosses(), s.date()));
    }

    /**
     * Best entry of each player for one mode, ranked.
     * Free Mode: WPM, then accuracy. Boss modes: bosses beaten, then WPM, then accuracy.
     */
    public static List<Score> top(String mode, int limit) {
        load();
        Comparator<Score> better = mode.startsWith("FREE")
            ? Comparator.comparingDouble(Score::wpm).thenComparingDouble(Score::accuracy)
            : Comparator.comparingInt(Score::bosses).thenComparingDouble(Score::wpm).thenComparingDouble(Score::accuracy);
        Map<String, Score> best = new LinkedHashMap<>();
        for (Score s : scores) {
            if (!s.mode().equals(mode)) continue;
            best.merge(s.name().toLowerCase(), s, (a, b) -> better.compare(b, a) > 0 ? b : a);
        }
        List<Score> out = new ArrayList<>(best.values());
        out.sort(better.reversed());
        return out.size() > limit ? new ArrayList<>(out.subList(0, limit)) : out;
    }

    // ---------- boss kills ----------

    /** Call once every time a boss is defeated. */
    public static void addKill(String name, String mode, String boss) {
        load();
        kills.add(new String[]{name, mode, boss});
        append("kills.csv", KILLS_HEADER, name + "," + mode + "," + boss + "," + LocalDate.now());
    }

    /** Players ranked by total bosses slain (Hardcore kills break ties). */
    public static List<Slayer> slayers(int limit) {
        load();
        Map<String, int[]> count = new LinkedHashMap<>();   // lower-case name -> {normal, hardcore}
        Map<String, String> shown = new HashMap<>();
        for (String[] k : kills) {
            String key = k[0].toLowerCase();
            shown.putIfAbsent(key, k[0]);
            count.computeIfAbsent(key, x -> new int[2])["HARDCORE".equals(k[1]) ? 1 : 0]++;
        }
        List<Slayer> out = new ArrayList<>();
        for (Map.Entry<String, int[]> e : count.entrySet()) {
            int[] c = e.getValue();
            out.add(new Slayer(shown.get(e.getKey()), c[0] + c[1], c[0], c[1]));
        }
        out.sort(Comparator.comparingInt(Slayer::total).thenComparingInt(Slayer::hardcore).reversed());
        return out.size() > limit ? new ArrayList<>(out.subList(0, limit)) : out;
    }

    // ---------- file handling ----------

    /** data/ next to where the game is run; falls back to ~/.typeslayer if that folder can't be written. */
    private static Path dir() {
        if (dir != null) return dir;
        Path[] options = { Paths.get("data"), Paths.get(System.getProperty("user.home"), ".typeslayer") };
        for (Path p : options) {
            try {
                Files.createDirectories(p);
                if (Files.isWritable(p)) return dir = p.toAbsolutePath();
            } catch (IOException | RuntimeException ignored) { }
        }
        return dir = options[0];
    }

    private static void load() {
        if (players != null) return;
        players = new ArrayList<>();
        scores = new ArrayList<>();
        kills = new ArrayList<>();
        for (String line : readLines("players.csv")) {
            String name = line.split(",", -1)[0];
            if (!name.isBlank()) players.add(name);
        }
        for (String line : readLines("scores.csv")) {
            String[] f = line.split(",", -1);
            try {
                scores.add(new Score(f[0], f[1], Double.parseDouble(f[2]), Double.parseDouble(f[3]),
                    Integer.parseInt(f[4]), Double.parseDouble(f[5]), Integer.parseInt(f[6]), f[7]));
            } catch (RuntimeException ignored) { }   // bad line: skip it
        }
        for (String line : readLines("kills.csv")) {
            String[] f = line.split(",", -1);
            if (f.length >= 3) kills.add(new String[]{f[0], f[1], f[2]});
        }
    }

    /** Data lines only: no header, no blank lines. */
    private static List<String> readLines(String file) {
        List<String> out = new ArrayList<>();
        try {
            Path p = dir().resolve(file);
            if (!Files.exists(p)) return out;
            for (String l : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                l = l.replace("\uFEFF", "").trim();
                if (!l.isEmpty() && !l.startsWith("name,")) out.add(l);
            }
        } catch (IOException ignored) { }
        return out;
    }

    private static void append(String file, String header, String line) {
        try {
            Path p = dir().resolve(file);
            String text = (Files.exists(p) ? "" : header + System.lineSeparator()) + line + System.lineSeparator();
            Files.writeString(p, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) { }
    }
}