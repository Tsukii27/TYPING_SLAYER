package typingboss.model;

public record FightResult(int words, double accuracy, double wpm, double seconds) {

    public static FightResult of(int correctKeys, double accuracy, int words, double seconds) {
        double secs = Math.max(1, seconds);
        return new FightResult(words, accuracy, correctKeys / 5.0 / (secs / 60.0), secs);
    }
}
