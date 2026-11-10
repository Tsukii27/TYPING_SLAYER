package typingboss.audio;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;
import java.io.BufferedInputStream;
import java.io.InputStream;

/** Fire-and-forget sound effects from /sfx. Fails silently if no audio device exists. */
public final class Sfx {
    private Sfx() {}

    public static void play(String name) {
        try (InputStream in = Sfx.class.getResourceAsStream("/sfx/" + name + ".wav")) {
            Clip clip = AudioSystem.getClip();
            clip.open(AudioSystem.getAudioInputStream(new BufferedInputStream(in)));
            clip.addLineListener(e -> { if (e.getType() == LineEvent.Type.STOP) clip.close(); });
            clip.start();
        } catch (Exception ignored) { }
    }
}
