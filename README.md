# TickFlow

![TickFlow](icon.png)

Rolling **game-tick timeline** of your recent actions and inferred attack rhythm — so combat timing becomes instinct instead of guesswork.

**Plugin Hub short description:** Rolling game-tick timeline of your recent actions and inferred attack rhythm for combat timing practice.

## Features

- Circular (default) or square timeline styles, with Learn / Compact modes
- Rolling past / now / next tick slots (5–8), icon-first action display
- Conservative classification: **Attack**, **Move**, **Pray**, **Item**, **Other**, **Empty**
- Prayer-specific sprites when the prayer can be identified
- Yellow→green NOW progress (circle swipe / square mini-pulse) so tick boundaries are easy to read
- Best-effort weapon attack-speed inference (`ItemManager` `aspeed`) with observed-cadence fallback
- WoW-style attack-cycle cooldown HUD when confidence allows
- Optional soft metronome with mute + 3-level volume control on the overlay
- Clean resets on login/logout, hopping, death, equipment change, and plugin disable

## Accuracy disclaimer

TickFlow observes **client-side** events and state. It is not server truth.

- Menu clicks, animations, and processed outcomes can disagree under lag
- Attack readiness is **inferred**
- Unknown timing is preferred over invented accuracy
- Cycle feedback appears only when confidence is high

## Requirements

- Java 11+
- Gradle wrapper included
- RuneLite `latest.release` (resolved by the build)

## Local development

```powershell
.\gradlew.bat run
```

```bash
./gradlew run
```

1. Enable **TickFlow** in the RuneLite sidebar plugin list.
2. Drag the overlay to a comfortable position.
3. Attack a low-risk NPC with a common weapon and watch the timeline.

### Jagex Account login

Development clients need special credentials: https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts

Treat `.runelite/credentials.properties` as secret. Never commit or share it.

## Build and test

```powershell
.\gradlew.bat clean test
.\gradlew.bat build
```

## Configuration

| Setting | Default | Purpose |
| --- | --- | --- |
| Enable overlay | on | Show/hide overlay |
| Overlay mode | Learn | Learn (zone headers) or Compact |
| Timeline style | Circular | Circular cells or square cells |
| Show inferred attack readiness | on | Cycle HUD / ready marker |
| Show cycle feedback | on | Brief OK / late badge |
| Show tick pulse | on | Square: mini progress in NOW cell |
| Tick sound | off | Soft metronome each game tick |
| Timeline length | 5 | 5–8 tick slots |
| Overlay scale % | 100 | Size scaling |
| Hide outside combat | off | Auto-hide after combat inactivity |
| Debug diagnostics | off | Extra observation detail |

## Plugin Hub submission

Hub metadata lives in `runelite-plugin.properties`. The hub icon is `icon.png` (48×48, from `icon-source.png`).

1. Create a **public** GitHub repo (suggested name: `tickflow`) and push this project with the BSD 2-Clause `LICENSE`.
2. Confirm CI locally: `.\gradlew.bat clean test`.
3. Note the full **40-character commit SHA** of the tip you want published.
4. Fork https://github.com/runelite/plugin-hub
5. Add file `plugins/tickflow`:

```text
repository=https://github.com/<your-user>/tickflow.git
commit=<40-char-sha>
```

6. Open a PR against `runelite/plugin-hub` and address Plugin Hub CI / reviewer notes.

Official guide: https://github.com/runelite/plugin-hub

## License

BSD 2-Clause. See [LICENSE](LICENSE).
