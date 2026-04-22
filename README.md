# wynncombat

A Wynncraft combat HUD mod for Fabric.

## Features

- [x] **Ability Log** — chat-style log of recently cast spells and the mana each one consumed. Consecutive casts of the same spell stack (`x2`, `x3`, ...) with summed cost. Entries fade after ~3s. Supports label mode, font size (1–4), color, background, shadow, and an Edit Position/Size mode.
- [x] **DPS Counter** — rolling DPS overlay based on floating damage labels. Multiple configurable time windows (defaults 1s / 10s / 30s), display modes (Show All vs. Cycle), color tiers by DPS threshold, multiple font families, and an Edit Position/Size mode.
- [x] **Main Menu / Config** — top-level screen with entries for each feature; per-feature settings + live edit-position editors.
- [x] **Keybinds** — toggle Ability Log, toggle DPS overlay, and DPS cycle prev/next (defaults ← / →). All rebindable from vanilla Controls.

## TODO

- [ ] **DPS Recorder** — record a section of combat and capture:
  - Every ability cast during the recording, with timings between casts.
  - Per-second DPS samples across the whole recording.
  - Overall average DPS and peak DPS for the session.
  - Start/stop keybind, saved sessions, and a viewer screen.
  - Timeline viewer that shows at what points what was casted and tracks DPS in like a graph over time
- [ ] **Show overlay highlight in settings view** — when a feature's settings screen is open (not just Edit Position/Size mode), still render the corresponding overlay box highlighted so the user can see which area is being configured.
- [ ] **DPS label customization** — per-window customization for the DPS value label:
  - User-inputtable label text per time window (replace the auto `"1s"`, `"30s"`, etc.).
  - Label display style: no decoration / border / background "pill" behind the text.
  - Option to keep label + value colors uniform, or color them independently.
  - Independent label color / background color / border color pickers.

### Later / nice-to-have

- [ ] Export DPS Recorder sessions (clipboard / file).
- [ ] Per-ability damage breakdown inside the DPS Recorder view.

## Setup

For setup instructions, please see the [Fabric Documentation page](https://docs.fabricmc.net/develop/getting-started/creating-a-project#setting-up) related to the IDE that you are using.

## License

This template is available under the CC0 license. Feel free to learn from it and incorporate it in your own projects.
