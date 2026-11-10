package typingboss.model;

import java.util.Arrays;
import java.util.List;

/** All words the game can spawn. Filtered by length per difficulty. */
public final class WordBank {
    private static final String WORDS =
        "cat sun bat map sky fire ice wind rock sand moon star gold sword shield flame storm night blade arrow " +
        "magic spell abyss rune cave bone claw fang gem king ghost crown tower torch armor quest goblin skull " +
        "monster phantom kingdom eclipse inferno obsidian mountain sorcerer shadow dragon castle knight wizard " +
        "thunder crystal warrior fortress guardian champion darkness overlord eternity wildfire berserker " +
        "frostbite bloodline lightning vengeance hurricane adventure labyrinth tombstone destroyer nightmare " +
        "revelation catastrophe battlefield";

    private WordBank() {}

    public static List<String> filter(int minLen, int maxLen) {
        return Arrays.stream(WORDS.split(" ")).filter(w -> w.length() >= minLen && w.length() <= maxLen).toList();
    }
}
