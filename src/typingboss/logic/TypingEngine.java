package typingboss.logic;

import typingboss.model.Difficulty;
import typingboss.model.WordBank;

import java.util.List;
import java.util.Random;

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

    public void next() {
        String w;
        do { w = pool.get(rng.nextInt(pool.size())); } while (w.equals(last) && pool.size() > 1);
        word = last = w; index = 0; error = false;
    }

    public void load(String w) { word = last = w; index = 0; error = false; }

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
