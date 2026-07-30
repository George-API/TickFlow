# TickFlow — Product Overview

## 1. Purpose

TickFlow is a RuneLite training plugin that makes Old School RuneScape's invisible 0.6-second game-tick structure visually understandable.

Its purpose is not to count ticks for the player forever. Its purpose is to expose the relationship between attacks, movement, prayers, consumables, and idle time clearly enough that the player internalizes the rhythm and eventually needs less guidance.

**Core promise:**

> Turn invisible tick timing into a simple visual rhythm that becomes instinct.

## 2. Problem

OSRS combat looks continuous, but the underlying game state advances in discrete ticks. Newer and intermediate PvM players often react to animations rather than understanding when the game actually processes actions.

Existing visual and audio metronomes reveal the global tick pulse, but they generally do not answer:

1. What did I do on this tick?
2. When can I attack again?
3. Did I intentionally use the space between attacks, or accidentally delay my next attack?

TickFlow connects the pulse to the player's own actions.

## 3. Product thesis

A player learns tick timing faster when each tick is presented as a usable action slot rather than an abstract beat.

The plugin should therefore visualize combat as a short sequence:

```text
Attack → Move → Pray → Empty → Attack
```

The player should gradually recognize patterns such as:

```text
4-tick weapon: Attack · · · Attack · · · Attack
```

and then begin placing movement, prayer changes, and inventory actions naturally between attacks.

## 4. Target user

Primary user:

- Understands basic OSRS combat but does not intuitively feel ticks.
- Wants to improve at PvM without studying dense timing theory.
- Benefits from immediate, low-noise visual feedback.
- Wants training wheels that can fade away rather than a permanent information dashboard.

TickFlow is not primarily designed for speedrunners, advanced encounter solvers, automation, or boss-mechanic prediction.

## 5. MVP experience

### 5.1 Live tick strip

A compact movable overlay shows five slots:

```text
PAST                 NOW                 NEXT
[Attack] [Move] [Pray] [Empty] [Attack ready]
   -2      -1      0       +1        +2
```

The exact visual arrangement may be adjusted for RuneLite overlay constraints, but the hierarchy must remain:

1. Current tick
2. Recent player actions
3. Earliest inferred next attack opportunity

### 5.2 Action categories

The MVP recognizes only these categories:

- **Attack** — the player attempts or begins an attack.
- **Move** — the player issues a walk/run destination and/or changes tile.
- **Prayer** — a prayer is activated or deactivated.
- **Consumable** — food, potion, or another inventory item is used.
- **Other** — a relevant interaction that does not fit the core categories.
- **Empty** — no recognized action was assigned to the tick.

A tick may contain several observed inputs. The UI should show one dominant action and optionally small secondary indicators. Never imply that only one click can occur per tick.

### 5.3 Attack cadence

When the plugin has enough evidence to infer the equipped weapon's attack speed, it shows the earliest expected next attack tick.

Example:

```text
Attack → Move → Pray → Ready
```

Attack readiness is an estimate derived from observable client state. It must be described as inferred, not guaranteed server truth.

### 5.4 Cycle feedback

After a new attack is recognized, the plugin may briefly summarize the preceding cycle:

```text
Last cycle
Attack → Move → Pray → Attack
On time
```

or:

```text
Last cycle
Attack → Move → Empty → Prayer → Attack
Attack occurred 1 tick after the inferred ready tick
```

Do not use aggressive grades, scores, DPS percentages, or shame-oriented language in the MVP.

## 6. Modes

### 6.1 Learn Mode — default

Learn Mode exposes the model clearly:

- Visible current-tick pulse
- Five-slot history/preview strip
- Action labels and icons
- Inferred attack-ready marker
- Brief cycle feedback
- Optional subtle tick sound

The UI should teach without requiring documentation.

### 6.2 Feel Mode — stretch goal after MVP stability

Feel Mode removes most explicit guidance:

- No tick numbers by default
- Compact pulse or four-step attack-cycle indicator
- Feedback appears mainly when timing differs from the inferred cycle
- Player remains focused on the fight

Do not build Feel Mode until Learn Mode is accurate, usable, and visually polished.

## 7. Interaction and visual design principles

### 7.1 Intuitive before technical

The user should understand the overlay in seconds. Prefer icons, position, rhythm, and subtle animation over verbose labels.

### 7.2 Calm during correct play

Correct play should produce minimal visual noise. The overlay becomes noticeable when it has useful information.

### 7.3 Show observations separately from inference

The interface must distinguish:

- **Observed:** a menu action, animation change, tile change, prayer state change, or inventory interaction.
- **Inferred:** attack readiness or a likely delayed attack.

Never present inference as exact server-side fact.

### 7.4 Empty is not automatically wasted

An empty tick is simply a tick with no recognized action. It becomes a potential missed attack opportunity only when the plugin has strong evidence that:

- An earlier attack occurred.
- A usable attack speed is known.
- The inferred cooldown ended.
- The player remained in combat with a valid interaction target.
- A later attack occurred.

Even then, use neutral wording such as **“Attack occurred 1 tick after ready”** rather than **“Wasted tick.”**

### 7.5 Progressive disclosure

The default UI should be small. Advanced diagnostics belong behind a debug configuration option and must not appear in normal play.

### 7.6 RuneLite-native appearance

Use RuneLite overlay conventions, standard fonts, spacing, panels, config controls, and rendering utilities wherever practical. Avoid a web-app aesthetic pasted over the game.

## 8. Required MVP scope

The first releasable prototype must include:

1. A standard RuneLite Plugin Hub-compatible Java project.
2. A plugin descriptor and configuration panel.
3. A movable overlay with a five-tick rolling timeline.
4. Current tick highlighting.
5. Detection/classification for attack, movement, prayer, and inventory-item interactions.
6. A bounded rolling history model.
7. Best-effort weapon attack-speed inference.
8. Earliest inferred attack-ready tick.
9. Neutral one-tick-or-more late attack feedback when confidence is sufficient.
10. Clean handling of login, logout, world hop, death, weapon change, target change, plugin disable, and missing data.
11. Unit tests for the pure timing/state logic.
12. A README with local run, testing, limitations, and Plugin Hub submission steps.

## 9. Explicit exclusions

Do not implement any of the following in the initial plugin:

- Automated clicks, menu actions, prayer changes, movement, or combat actions
- Input modification or menu-entry swapping
- Boss-specific mechanic prediction
- Upcoming attack-type prediction
- Tile recommendations
- Prayer recommendations
- Gear-switch recommendations
- Encounter solvers
- Network calls, telemetry, accounts, cloud storage, or analytics
- Long-term performance scoring
- DPS calculations
- External dependencies unless absolutely necessary
- Reflection, JNI, subprocess execution, runtime code downloads, or non-Java JVM languages

The plugin must remain a passive visualization and training tool.

## 10. Accuracy and honesty requirements

RuneLite observes client-side events and state, not every server decision. Therefore:

- Inputs and processed outcomes may not align perfectly under lag.
- Animations are not universal proof of a successful attack.
- Attack speed may be unknown or altered by special mechanics.
- Some item interactions may be ambiguous.
- Multiple actions may be registered around one game tick.

The plugin must fail gracefully:

- Show **Unknown** rather than inventing timing.
- Suppress delay feedback when confidence is low.
- Reset stale combat state aggressively.
- Provide an optional debug overlay/log for validation.

## 11. Configuration — lean default set

Required settings:

- Enable overlay
- Overlay mode: Learn / Compact
- Show action labels
- Show inferred attack readiness
- Show cycle feedback
- Timeline length: 5–8 ticks
- Overlay scale
- Debug diagnostics

Optional only if simple:

- Tick pulse sound
- Color customization
- Hide overlay outside combat

Avoid a large settings surface.

## 12. Success criteria

The MVP is successful when a player can:

1. Equip a common weapon and attack a low-risk NPC or training target.
2. See attacks, movement, prayers, and inventory actions placed on the correct rolling tick history with understandable consistency.
3. See a believable next-attack-ready marker for standard weapon attacks.
4. Intentionally attack one tick late and receive neutral, correct feedback.
5. Understand the interface without reading a manual.
6. Play for ten minutes without the overlay feeling distracting.
7. Disable the plugin and retain a better intuitive sense of the attack rhythm.

## 13. Validation scenarios

Use these scenarios before expanding scope:

### Scenario A — stationary four-tick attacks

- Use a common four-tick weapon.
- Attack a low-risk target continuously.
- Confirm a stable four-tick cadence appears.

### Scenario B — attack and move

- Attack, move during cooldown, then attack at the first inferred opportunity.
- Confirm movement is visible without falsely delaying the attack cycle.

### Scenario C — attack and prayer

- Toggle a prayer between attacks.
- Confirm the prayer action appears in the appropriate tick slot.

### Scenario D — intentional delay

- Wait one extra tick after readiness before attacking.
- Confirm feedback reports one late tick only when confidence is sufficient.

### Scenario E — weapon change

- Change to a weapon with another attack speed.
- Confirm timing resets and reacquires rather than continuing stale cadence.

### Scenario F — interrupted combat

- Walk away, lose target, eat, log out, hop worlds, or die.
- Confirm stale cycle feedback is not generated.

## 14. Product boundary for launch

A polished narrow plugin is preferable to a broad inaccurate plugin.

The launch version should do one thing exceptionally well:

> Make the player's recent actions and basic attack rhythm visible on the game-tick timeline.

Everything beyond that must earn its complexity through real validation.

## 15. Official references

Use these as source-of-truth starting points and verify APIs against the checked-out RuneLite version during implementation:

- RuneLite Developer Guide: https://github.com/runelite/runelite/wiki/Developer-Guide
- Official example plugin template: https://github.com/runelite/example-plugin
- Plugin Hub repository and submission guide: https://github.com/runelite/plugin-hub
- RuneLite source repository: https://github.com/runelite/runelite
- RuneLite API Javadocs: https://static.runelite.net/runelite-api/apidocs/
- RuneLite client Javadocs: https://static.runelite.net/runelite-client/apidocs/
- Jagex-account development login guide: https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts
- Rejected or rolled-back features: https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features
