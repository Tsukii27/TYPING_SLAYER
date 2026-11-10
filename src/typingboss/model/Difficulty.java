package typingboss.model;

import java.awt.Color;

/** Each difficulty controls the word length range used for a boss. */
public enum Difficulty {
    EASY("EASY", new Color(0x5BD65B), 3, 5),
    NORMAL("NORMAL", new Color(0xF2C94C), 4, 7),
    HARD("HARD", new Color(0xF2994A), 5, 9),
    NIGHTMARE("NIGHTMARE", new Color(0xEB5757), 7, 12);

    public final String label;
    public final Color color;
    public final int minLen, maxLen;

    Difficulty(String label, Color color, int minLen, int maxLen) {
        this.label = label; this.color = color; this.minLen = minLen; this.maxLen = maxLen;
    }
}
