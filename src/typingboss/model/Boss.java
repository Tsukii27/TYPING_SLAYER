package typingboss.model;

import java.util.List;

/** Boss definition: sprite name, difficulty, total HP and time limit (seconds). */
public record Boss(String name, String sprite, Difficulty difficulty, int hp, int seconds) {
    public static List<Boss> roster() {
        return List.of(
            new Boss("Slime King", "slime", Difficulty.EASY, 60, 45),
            new Boss("Bone Lord", "skeleton", Difficulty.NORMAL, 150, 50),
            new Boss("Ember Drake", "dragon", Difficulty.HARD, 240, 55),
            new Boss("Void Demon", "demon", Difficulty.NIGHTMARE, 360, 60));
    }
}
