package typingboss.audio;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.SourceDataLine;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.Random;

public final class Music {
    private static final String MENU_TRACK = "main_bgm";
    private static final String[] FIGHT_TRACKS = { "bgm_1", "bgm_2", "bgm_3", "bgm_4", "bgm_5" };
    private static final float GAIN_DB = -6f;  
    private static final Random RNG = new Random();

    private static volatile boolean paused;
    private static int generation;            
    private static String current = "", lastFight = "";

    private Music() {}

    public static synchronized void playMenu() {
        if (!MENU_TRACK.equals(current)) start(MENU_TRACK);
    }

    public static synchronized void playRandom() {
        String t;
        do { t = FIGHT_TRACKS[RNG.nextInt(FIGHT_TRACKS.length)]; } while (t.equals(lastFight));
        lastFight = t;
        start(t);
    }

    public static synchronized void stop() { generation++; current = ""; paused = false; }

    public static void pause() { paused = true; }

    public static void resume() { paused = false; }

    private static void start(String name) {
        int gen = ++generation;
        current = name;
        paused = false;
        Thread t = new Thread(() -> stream(name, gen), "music");
        t.setDaemon(true);
        t.start();
    }

    private static synchronized boolean active(int gen) { return gen == generation; }

    private static void stream(String name, int gen) {
        SourceDataLine line = null;
        byte[] buf = new byte[8192];
        try {
            while (active(gen)) {   
                try (InputStream in = Music.class.getResourceAsStream("/sfx/" + name + ".wav");
                     AudioInputStream ais = AudioSystem.getAudioInputStream(new BufferedInputStream(in))) {
                    if (line == null) {
                      
                        int size = ais.getFormat().getFrameSize() * (int) ais.getFormat().getFrameRate() / 4;
                        line = AudioSystem.getSourceDataLine(ais.getFormat());
                        line.open(ais.getFormat(), size);
                        setGain(line);
                        line.start();
                    }
                    int n;
                    while (active(gen) && (n = ais.read(buf)) > 0) {
                        if (paused) {
                            line.stop();   
                            while (paused && active(gen)) Thread.sleep(50);
                            if (!active(gen)) break;
                            line.start();
                        }
                        line.write(buf, 0, n);
                    }
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