package typingboss.logic;

import typingboss.model.Difficulty;
import typingboss.model.WordBank;

import java.util.List;
import java.util.Random;

/** Handles the current word, per-key validation and accuracy. No rendering here. */
public class TypingEngine {
    public enum Result { CORRECT, WRONG, COMPLETE }

    private final List<String> pool;
    private final Random rng = new Random();
    private String word = "", last = "";
    private int index, correct, wrong;
    private boolean error;

    public TypingEngine(Difficulty d) {
        pool = WordBank.filter(d.minLen, d.maxLen);
        next();
    }

    /** Picks a new random word (never the same one twice in a row). */
    public void next() {
        String w;
        do { w = pool.get(rng.nextInt(pool.size())); } while (w.equals(last) && pool.size() > 1);
        word = last = w; index = 0; error = false;
    }

    /** Loads a specific word (used by Free Mode, where the word list is fixed). Counters are kept. */
    public void load(String w) { word = last = w; index = 0; error = false; }

    /** A wrong key is marked as an error immediately and lowers accuracy. */
    public Result type(char c) {
        if (Character.toLowerCase(c) == word.charAt(index)) {
            correct++; error = false; index++;
            return index == word.length() ? Result.COMPLETE : Result.CORRECT;
        }
        wrong++; error = true;
        return Result.WRONG;
    }

    public double accuracy() { int t = correct + wrong; return t == 0 ? 100 : 100.0 * correct / t; }
    public String word() { return word; }
    public int index() { return index; }
    public boolean hasError() { return error; }
    public int correct() { return correct; }
    public int wrong() { return wrong; }
}
