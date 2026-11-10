package typingboss.audio;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.SourceDataLine;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.Random;

/**
 * Looping background music streamed from /sfx (the files are too big to load as a Clip).
 * Only one track plays at a time. Fails silently if no audio device or file exists.
 */
public final class Music {
    private static final String MENU_TRACK = "main_bgm";
    private static final String[] FIGHT_TRACKS = { "bgm_1", "bgm_2", "bgm_3", "bgm_4", "bgm_5" };
    private static final float GAIN_DB = -6f;   // keeps the music under the sound effects
    private static final Random RNG = new Random();

    private static int generation;              // bumped on every change; an old thread sees it and exits
    private static String current = "", lastFight = "";

    private Music() {}

    /** Main menu theme. Does nothing if it is already playing, so moving between menus won't restart it. */
    public static synchronized void playMenu() {
        if (!MENU_TRACK.equals(current)) start(MENU_TRACK);
    }

    /** Random fight / free mode track, never the same one twice in a row. */
    public static synchronized void playRandom() {
        String t;
        do { t = FIGHT_TRACKS[RNG.nextInt(FIGHT_TRACKS.length)]; } while (t.equals(lastFight));
        lastFight = t;
        start(t);
    }

    public static synchronized void stop() { generation++; current = ""; }

    private static void start(String name) {
        int gen = ++generation;
        current = name;
        Thread t = new Thread(() -> stream(name, gen), "music");
        t.setDaemon(true);
        t.start();
    }

    private static synchronized boolean active(int gen) { return gen == generation; }

    private static void stream(String name, int gen) {
        SourceDataLine line = null;
        byte[] buf = new byte[8192];
        try {
            while (active(gen)) {   // re-open the file each pass so the track loops
                try (InputStream in = Music.class.getResourceAsStream("/sfx/" + name + ".wav");
                     AudioInputStream ais = AudioSystem.getAudioInputStream(new BufferedInputStream(in))) {
                    if (line == null) {
                        // ~0.25s buffer, so a track change cuts off almost immediately
                        int size = ais.getFormat().getFrameSize() * (int) ais.getFormat().getFrameRate() / 4;
                        line = AudioSystem.getSourceDataLine(ais.getFormat());
                        line.open(ais.getFormat(), size);
                        setGain(line);
                        line.start();
                    }
                    int n;
                    while (active(gen) && (n = ais.read(buf)) > 0) line.write(buf, 0, n);
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (line != null) { line.stop(); line.flush(); line.close(); }
        }
    }

    private static void setGain(SourceDataLine line) {
        try {
            FloatControl c = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
            c.setValue(Math.max(c.getMinimum(), Math.min(c.getMaximum(), GAIN_DB)));
        } catch (Exception ignored) { }
    }
}