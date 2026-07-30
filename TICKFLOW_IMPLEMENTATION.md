# TickFlow — One-Shot Implementation Brief

## 0. Agent instruction

You are the senior Java/RuneLite engineer responsible for delivering a polished, runnable TickFlow plugin in this repository.

Read `TICKFLOW_OVERVIEW.md` first and treat it as the product contract. Then inspect the current repository, official RuneLite example plugin, current RuneLite API/source, and Plugin Hub requirements before changing code.

Your task is to implement the complete MVP, test it, document it, and leave the repository ready for local gameplay validation and eventual Plugin Hub submission.

Do not stop after scaffolding, pseudocode, or a partial overlay. Make reasonable decisions independently. Prefer a smaller reliable implementation over speculative complexity.

Before coding, briefly record your implementation plan in the working log or final response. Then execute it without waiting for confirmation.

## 1. Source-of-truth links

Verify all implementation details against current official sources:

- Developer guide: https://github.com/runelite/runelite/wiki/Developer-Guide
- Example plugin template: https://github.com/runelite/example-plugin
- Plugin Hub setup/submission requirements: https://github.com/runelite/plugin-hub
- RuneLite source: https://github.com/runelite/runelite
- RuneLite API Javadocs: https://static.runelite.net/runelite-api/apidocs/
- RuneLite client Javadocs: https://static.runelite.net/runelite-client/apidocs/
- Jagex-account development login: https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts
- Rejected/rolled-back features: https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features

When documentation and checked-out source disagree, use the version resolved by the project build and note the discrepancy.

## 2. Non-negotiable constraints

- Java only.
- Passive observation and rendering only.
- Never click, move, pray, attack, swap menus, modify input, or automate gameplay.
- No boss-specific helpers or predictions.
- No tile, prayer, gear, or attack recommendations.
- No external network calls or telemetry.
- No new third-party dependencies unless unavoidable.
- No reflection, JNI, subprocesses, runtime downloads, or bundled executable code.
- Keep the project compatible with standard Plugin Hub build expectations.
- Use `build=standard` unless the repository genuinely requires otherwise.
- Keep all state bounded and resettable.
- Do not block the client thread or perform heavy work in render methods.

## 3. Deliverables

Produce a complete repository containing at minimum:

```text
src/main/java/<package>/
  TickFlowPlugin.java
  TickFlowConfig.java
  TickFlowOverlay.java
  TickFlowState.java
  TickRecord.java
  TickAction.java
  ActionType.java
  AttackCycleTracker.java
  ActionClassifier.java

src/test/java/<package>/
  AttackCycleTrackerTest.java
  TickFlowStateTest.java
  ActionClassifierTest.java   # only where classification can be tested without brittle mocks

src/main/resources/          # only if needed
README.md
LICENSE
runelite-plugin.properties
build.gradle
settings.gradle
```

Class names may be consolidated when doing so genuinely improves clarity, but avoid a monolithic plugin class.

Also update or add:

- Correct `pluginMainClass` in `build.gradle`
- Correct package names
- Plugin metadata
- Useful README screenshots placeholder section
- Manual validation checklist
- Known limitations

## 4. Recommended architecture

### 4.1 `TickFlowPlugin`

Responsibilities:

- RuneLite lifecycle
- Dependency injection
- Overlay registration/removal
- Event subscriptions
- Forward normalized observations into state/tracker classes
- Reset state on invalidating game transitions

Keep business logic out of event handlers.

Likely injected dependencies:

- `Client`
- `OverlayManager`
- `TickFlowOverlay`
- `TickFlowConfig`
- `ItemManager` if needed for equipment/item metadata
- `ConfigManager`

Use the current official APIs and imports resolved by the project.

### 4.2 `TickFlowState`

Owns the current bounded session model:

- Monotonic local tick index
- Rolling deque of `TickRecord`
- Current tick's observations
- Current and previous interaction target identity where observable
- Last known local-player tile
- Last known active prayers or prayer snapshot
- Last known equipment fingerprint
- Current inferred attack-cycle state
- Most recent completed cycle feedback
- Confidence state

Required operations:

- `beginNextTick(...)`
- `recordAction(...)`
- `recordSecondaryAction(...)`
- `finalizeCurrentTick(...)`
- `reset(reason)`
- immutable/read-only snapshot for rendering

Do not expose mutable collections to the overlay.

### 4.3 `TickRecord`

Immutable or effectively immutable representation of one game tick:

```text
localTickIndex
primaryAction
secondaryActions
observedPlayerLocation
observedInteractionTarget
attackReadyState
cycleFeedback
```

Only store information needed for the UI/debugging. Keep history length bounded by configuration with a small safe maximum.

### 4.4 `TickAction`

Represents an observation assigned to a tick:

```text
type
source
label
confidence
sequence/order within tick
optional metadata
```

Suggested sources:

- Menu interaction
- Game state change
- Player animation
- Position change
- Prayer state change
- Equipment change

### 4.5 `ActionClassifier`

Converts raw RuneLite observations into semantic actions.

It must be deterministic, conservative, and easy to debug. Prefer allowlisted patterns and enum/opcode checks over broad string matching. When string matching is necessary, normalize tags/case and isolate it in one place.

### 4.6 `AttackCycleTracker`

Pure state machine responsible for:

- Recognizing credible attack observations
- Estimating attack speed
- Setting the inferred next-ready tick
- Updating confidence
- Producing neutral cycle feedback when a subsequent attack is observed
- Resetting on invalidating conditions

This should be highly unit-testable and independent of Swing/overlay rendering.

### 4.7 `TickFlowOverlay`

RuneLite-native movable overlay.

Responsibilities:

- Render the rolling five-tick timeline
- Highlight current tick
- Show dominant action icons/short labels
- Show inferred ready marker only when confidence is sufficient
- Show recent cycle feedback briefly
- Respect configuration and scaling
- Remain readable at common fixed/resizable layouts

Rendering must be lightweight and side-effect free.

## 5. RuneLite events and observations to investigate

Do not blindly assume these exact events remain sufficient. Inspect current API/source and choose the smallest robust set.

Primary candidates:

- `GameTick` — authoritative boundary for rolling/finalizing timeline slots.
- `MenuOptionClicked` — observe user menu actions before/around processing.
- `AnimationChanged` — corroborate player attack or consumption animations where useful.
- `GameStateChanged` — reset on login transitions, hopping, connection loss, etc.
- `ActorDeath` — reset on local-player death when available.
- `ItemContainerChanged` — observe equipment/inventory changes where useful.
- `VarbitChanged` or current prayer APIs — detect active-prayer changes conservatively.
- Position/local-player state sampled on `GameTick` — recognize movement outcome.

Potential client APIs to inspect:

- Local player interaction target
- Local player animation ID
- Local/world location
- Active prayers
- Equipment container
- Selected item/spell state
- Menu action/opcode and target text

Document the final event mapping in the README or code comments.

## 6. Tick assignment model

This is the most important implementation detail.

RuneLite menu events, client state changes, and `GameTick` boundaries may not map perfectly to server processing. The plugin must explicitly model observations rather than pretending to know exact server truth.

Recommended approach:

1. Maintain a monotonically increasing `localTickIndex` advanced on each `GameTick`.
2. Buffer menu/input observations occurring between tick boundaries.
3. On `GameTick`:
   - Finalize the prior displayed slot.
   - Sample player position, target, animation, prayers, and equipment.
   - Reconcile buffered inputs with observed state changes.
   - Assign observations to the new/current tick using one documented convention.
4. If ambiguity exists, preserve the action but lower confidence rather than shifting it unpredictably.
5. In debug mode, expose both event time/order and assigned local tick.

Choose and document whether a menu click after boundary N is displayed in slot N or N+1. Consistency matters more than pretending to reconstruct inaccessible server timestamps.

## 7. Action classification rules

### 7.1 Attack

A credible attack observation should preferably combine more than one signal:

- User selected an attack/cast interaction against an actor, and/or
- Local player's interaction target becomes an actor, and/or
- Local player's animation changes to a credible attack animation, and/or
- Combat-related state corroborates the action.

Avoid relying solely on animation IDs because they vary and may be absent or reused.

Avoid relying solely on target text because localization/tags/menu variants can differ.

For MVP, treat the menu action as an **attack attempt** and use corroborating state to determine whether it is strong enough to anchor an attack cycle.

### 7.2 Movement

Record movement when either:

- A walk/run menu action is issued, or
- The sampled local-player location differs from the previous tick.

Prefer the observed location change as confirmation. Multiple tiles moved while running still represent movement on that tick, not multiple timeline actions.

### 7.3 Prayer

At each `GameTick`, compare a compact snapshot of active prayers with the previous snapshot.

If changed, record one Prayer action with metadata indicating activation/deactivation count. Do not recommend which prayer should be used.

If there is a reliable menu/widget interaction event, use it only as supplementary context.

### 7.4 Consumable / inventory item

Record a consumable/item action when a player uses an inventory item or the inventory change strongly indicates consumption.

For MVP, it is acceptable to classify uncertain inventory interactions as **Item** rather than claiming **Food** or **Potion**.

Do not build a large item-ID database. Use `ItemManager` metadata or standard APIs if available, and keep the fallback generic.

### 7.5 Empty

`Empty` means no recognized primary action. It is not an error state.

### 7.6 Dominant action priority

Recommended default display priority when several observations share a tick:

```text
Attack > Consumable/Item > Prayer > Move > Other > Empty
```

Preserve secondary actions as small indicators or debug data. Do not discard them internally.

## 8. Attack-speed inference

Implement a layered strategy rather than a brittle universal claim.

### Layer 1 — equipped weapon metadata

Inspect official RuneLite APIs/source for an existing weapon-speed mapping or utility used by core plugins. Reuse a maintained RuneLite source when available rather than creating an independent giant table.

### Layer 2 — observed cadence

When no reliable mapping exists, infer a candidate speed from repeated credible attacks against a stable target:

- Collect a small number of recent attack intervals.
- Accept a cadence only when intervals agree within conservative rules.
- Ignore interrupted or obviously invalid cycles.
- Limit plausible normal speeds to a safe range.
- Lower/reset confidence on weapon, target, combat, or state changes.

### Layer 3 — unknown

When no reliable speed is available:

- Continue showing action history.
- Hide next-ready and delay feedback.
- Display no invented value.

### Special attacks and irregular mechanics

Do not attempt comprehensive special-attack handling in MVP. If the cadence becomes inconsistent, lower confidence or reacquire.

## 9. Attack-cycle state machine

Suggested states:

```text
IDLE
ACQUIRING
TRACKING_LOW_CONFIDENCE
TRACKING_HIGH_CONFIDENCE
INVALIDATED
```

State inputs:

- Credible attack observation
- Candidate weapon speed
- Observed interval
- Weapon/equipment fingerprint change
- Target change/loss
- Player movement out of interaction
- Game-state reset
- Long inactivity timeout
- Death

Core values:

```text
lastCredibleAttackTick
candidateAttackSpeedTicks
nextReadyTick
confidence
lastCycleDeltaTicks
```

Feedback rule:

```text
actual next credible attack tick - inferred nextReadyTick
```

- `0`: on inferred ready tick
- positive: occurred N ticks after inferred readiness
- negative: treat as evidence the cadence estimate was wrong; reacquire rather than praising an impossible early attack

Only surface feedback when confidence meets a documented threshold.

## 10. Reset and invalidation rules

Reset or downgrade attack timing on:

- Login/logout/world hop/loading transitions
- Local-player absence
- Local-player death
- Equipment fingerprint change
- Large unexplained cadence mismatch
- Long inactivity timeout
- Target loss/change where the existing cycle is no longer credible
- Plugin disable
- Configuration changes that alter timeline semantics

Do not retain stale readiness across encounters.

## 11. Overlay specification

### 11.1 Placement

Use a RuneLite `OverlayPanel` or appropriate overlay base with movable positioning and standard layer/priority conventions.

Default near the bottom-center or top-left of the viewport without covering inventory/chat. Let the user move it.

### 11.2 Layout

Default five slots:

```text
-2       -1        0        +1       +2
Attack   Move      Pray     Empty    Ready
PAST               NOW               NEXT
```

However, future slots should not pretend to predict future player actions. Only the inferred ready slot may appear ahead. All actual action icons belong to current/past slots.

A more accurate rendered model is:

```text
History:  [Attack] [Move] [Pray] [Current]
Readiness:                         1 tick
```

Choose whichever layout is most intuitive after implementation, while preserving the product intent.

### 11.3 Visual style

- RuneLite-native dark panel
- Neutral whites/grays for normal observations
- One restrained accent for current tick/readiness
- Muted warning accent for late-cycle feedback
- No constant flashing
- Small pulse or progress sweep for current tick only if rendering cadence allows it reliably
- Clear icons with text fallback
- Accessible without relying solely on color

Use standard RuneLite UI assets/utilities where possible. If custom icons are included, place them in `src/main/resources` and load through `getResourceAsStream`-compatible methods.

### 11.4 Feedback duration

Cycle feedback should expire automatically after a short bounded number of ticks. It must not accumulate into a notification feed.

### 11.5 Debug mode

Optional debug section may show:

```text
Tick index
Buffered menu events
Observed animation
Target
Position delta
Equipment fingerprint
Candidate speed
Confidence
Last ready tick
```

Debug mode is off by default.

## 12. Configuration interface

Implement a concise `Config` interface using RuneLite `@ConfigItem` conventions.

Recommended keys:

```text
enabledOverlay
mode               # LEARN / COMPACT
showLabels
showReadiness
showCycleFeedback
timelineLength      # bounded, e.g. 5–8
overlayScale        # bounded
autoHideOutsideCombat
debugMode
```

Optional tick audio may be omitted from the first implementation if it introduces resource or timing complexity.

## 13. Threading and performance

- Treat subscribed client events as the state-update path.
- Do not use background loops.
- Do not sleep.
- Do not poll continuously outside RuneLite events.
- Keep render work allocation-light.
- Keep history bounded.
- Cache static dimensions/icons where appropriate.
- Do not mutate shared state from the overlay.
- Use immutable snapshot transfer or client-thread-confined state.

## 14. Testing requirements

### 14.1 Unit tests

`AttackCycleTrackerTest` must cover:

1. Acquires a four-tick cadence.
2. Calculates correct next-ready tick.
3. Reports zero delta for an on-time attack.
4. Reports +1 for an attack one tick late.
5. Rejects/improves after an impossible early attack.
6. Resets on equipment change.
7. Resets or downgrades on target change/inactivity.
8. Produces no feedback at low confidence.

`TickFlowStateTest` must cover:

1. Rolling history stays bounded.
2. Primary action priority is deterministic.
3. Secondary actions are preserved.
4. Empty ticks are represented neutrally.
5. Reset clears stale combat timing.

Avoid tests that simply mirror implementation details.

### 14.2 Build checks

Run the repository's official tasks, at minimum:

```bash
./gradlew clean test
./gradlew build
```

On Windows, use:

```powershell
.\gradlew.bat clean test
.\gradlew.bat build
```

Also run the template's development task, normally:

```bash
./gradlew run
```

Confirm the plugin appears in RuneLite configuration and can be enabled/disabled without errors.

### 14.3 Manual gameplay test checklist

Document and perform as much as possible:

- Login and enable plugin
- Overlay appears and is movable
- Timeline advances exactly once per `GameTick`
- Walking appears
- Prayer toggle appears
- Item interaction appears
- Repeated attacks appear
- Common four-tick weapon cadence is inferred or clearly remains unknown
- One intentional late attack produces correct neutral feedback
- Weapon switch invalidates old cadence
- Target switch does not create false feedback
- Logout/hop/death clears state
- Plugin disable removes overlays and clears state
- No meaningful FPS degradation
- No exceptions in logs

If interactive login is unavailable, complete all non-login tests and state exactly what remains for the human tester.

## 15. Development login and local run instructions

Use the official example-plugin workflow.

1. Ensure the project is based on or compatible with `https://github.com/runelite/example-plugin`.
2. Use the Java version currently required by the template/plugin-hub repository. Do not rely on stale prose; verify the build files and CI.
3. Run the Gradle `run` task.
4. For a Jagex Account, follow:
   `https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts`
5. Treat `.runelite/credentials.properties` as sensitive. Never print, commit, copy, or expose it.
6. Delete/invalidate development credentials after testing if desired, following the official guide.

Add these steps to README with Windows-friendly commands.

## 16. Plugin metadata

Set polished metadata, adapting author/package placeholders to the repository owner:

```properties
displayName=TickFlow
author=<repository owner>
description=Visualizes recent player actions and inferred attack rhythm on the OSRS game-tick timeline.
tags=tick,timing,pvm,combat,training,rhythm
plugins=<package>.TickFlowPlugin
version=
build=standard
```

Use a concise `@PluginDescriptor` with matching name, description, and tags.

Add an optional `icon.png` at repository root only if a suitable final icon exists, respecting current Plugin Hub dimensions.

## 17. README requirements

Write a user-facing README containing:

1. One-sentence value proposition
2. Current feature list
3. Screenshot/GIF placeholder
4. Accuracy disclaimer
5. Installation for local development
6. Build/test commands
7. Jagex Account login link and credential warning
8. Configuration summary
9. Manual validation checklist
10. Known limitations
11. Plugin Hub submission/update steps
12. License

Keep it concise and credible. Do not market unimplemented capabilities.

## 18. Plugin Hub readiness

Follow the current official guide at `https://github.com/runelite/plugin-hub`.

Before calling the repository launch-ready:

- Repository is public-ready and contains a BSD 2-Clause license.
- No forbidden language/features.
- No unnecessary dependencies.
- `runelite-plugin.properties` is complete.
- Build uses the current RuneLite release configuration expected by the template.
- Tests pass.
- README is accurate.
- Plugin does not duplicate an existing plugin without a clear distinct purpose.
- Review the rejected/rolled-back feature list.
- Create a Plugin Hub manifest file containing the repository HTTPS URL and exact 40-character commit hash when submitting.
- Confirm Plugin Hub CI and requested changes.

Do not automatically submit or push unless repository credentials and explicit permission are already available. Prepare exact commands/instructions instead.

## 19. Quality bar

The result should feel like a finished narrow plugin, not an AI-generated prototype.

Required qualities:

- Compiles cleanly
- Tests pass
- No obvious dead code
- No giant speculative mappings
- Clear naming
- Small methods
- Documented ambiguity
- Graceful unknown states
- Overlay polished at default scale
- Settings concise
- Lifecycle cleanup correct
- No misleading claims

Run formatting/checkstyle tasks provided by the repository and fix all failures.

## 20. Execution sequence

Use this order:

### Phase 1 — inspect and align

- Inspect repository and template version.
- Verify current API/event names.
- Identify reusable official RuneLite utilities for weapon speed and overlays.
- Record concise architecture decision.

### Phase 2 — pure model

- Implement action types, tick records, bounded state, and attack-cycle tracker.
- Write unit tests first or alongside implementation.

### Phase 3 — event integration

- Wire `GameTick` and minimum observation events.
- Add conservative classification and reset rules.
- Add debug diagnostics.

### Phase 4 — polished overlay

- Implement Learn Mode overlay.
- Add compact mode only if it does not compromise MVP completion.
- Tune dimensions, hierarchy, and lifecycle.

### Phase 5 — verify and document

- Run all tests/build/checks.
- Resolve warnings/errors.
- Write README and known limitations.
- Provide exact local test steps.

## 21. Completion response

At completion, report:

1. What was implemented
2. Files created/changed
3. Exact build/test commands run and results
4. What was validated automatically
5. What requires live OSRS testing
6. Known limitations
7. Exact next prompt or human steps to run the development client and validate
8. Plugin Hub readiness gaps, if any

Do not claim live gameplay validation unless it actually occurred.
