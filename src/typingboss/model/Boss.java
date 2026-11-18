package typingboss.model;

import java.util.List;

public record Boss(String name, String sprite, Difficulty difficulty, int hp, int seconds, Trait trait) {
    public enum Trait { NONE, HORROR, CLOWN }

    public Boss(String name, String sprite, Difficulty difficulty, int hp, int seconds) {
        this(name, sprite, difficulty, hp, seconds, Trait.NONE);
    }

    public boolean horror() { return trait == Trait.HORROR; }
    public boolean clown() { return trait == Trait.CLOWN; }

    public static List<Boss> roster() {
        return List.of(
            new Boss("Slime King", "slime", Difficulty.EASY, 60, 45),
            new Boss("Bone Lord", "skeleton", Difficulty.NORMAL, 150, 50),
            new Boss("Ember Drake", "dragon", Difficulty.HARD, 240, 55),
            new Boss("Chuckles", "clown", Difficulty.HARD, 300, 60, Trait.CLOWN),
            new Boss("Void Demon", "demon", Difficulty.NIGHTMARE, 360, 60),
            new Boss("Hollow Wraith", "wraith", Difficulty.NIGHTMARE, 400, 65, Trait.HORROR));
    }
}