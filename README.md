# Type Slayer (Java Swing)

Typing boss-fight game. Requires JDK 17+.

Run: `./run.sh` (Linux/Mac) or `run.bat` (Windows)
Modes: Free Mode (60s typing test), Boss Fight > Normal or Hardcore (each error = -1s), Exit.
Controls: type the words, ARROWS + ENTER (or mouse) in menus, ESC = back to menu.

```
src/typingboss/
  Main.java            window entry point
  core/                GamePanel (loop, states, rendering), Assets (sprite loader)
  logic/TypingEngine   word + per-key validation + accuracy
  model/               Boss roster, Difficulty, WordBank
  ui/                  Fx (flash/shake/slash/damage pops), Hud (bars, text, red vignette)
  audio/Sfx            sound effects
res/sprites, res/sfx   generated PNG + WAV assets
tools/generate_assets.py   regenerates all assets (needs Pillow)
```

Tweak bosses in `model/Boss.java` (HP, timer, difficulty) and words in `model/WordBank.java`.
