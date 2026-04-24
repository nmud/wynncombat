# wynncombat

A Wynncraft combat HUD mod for Fabric.

## Features

- [x] **Ability Log** — chat-style log of recently cast spells and the mana each one consumed. With stacking on, consecutive casts of the same spell merge (`x2`, `x3`, ...) with summed cost. With stacking off, each cast is its own row showing base + recast-penalty (`Dash 15 (+5)`). Entries fade after ~3s. Supports label mode, font size (1–4), color, background, shadow, and an Edit Position/Size mode.
- [x] **DPS Counter** — rolling DPS overlay based on floating damage labels. Multiple configurable time windows (defaults 1s / 10s / 30s) with per-window user-editable label text, display modes (Show All vs. Cycle), color tiers by DPS threshold, label decoration styles (none / border / pill) with independent label / value / border / background colors, settings organized into tabs, per-row free drag (with Reset Positions), and an Edit Position/Size mode.
- [x] **DPS Recorder** — records a combat session and saves it as a local file under `config/wynncombat-recordings/<id>.json`. Captures every spell cast with millisecond-offset timings, per-second DPS samples, total damage, average & peak DPS (plus peak second). Infers the player's Wynncraft class from the spells used. Includes a main list screen with live Start/Stop status, a bottom-right blinking `● DPS Recording MM:SS` HUD indicator while recording (defaults to F9 keybind), and a video-editor-style recording viewer: the DPS-per-second graph is the background, each cast is a node on the strip above, hovering anywhere shows a cyan playhead + tooltip with the time and DPS at that moment, hovering a node also shows the spell name and mana/hp cost, mouse-wheel zooms anchored to the cursor, click-and-drag pans, and there are explicit `-/+/Fit` zoom buttons. Delete has a confirm step.
- [x] **Main Menu / Config** — top-level screen with entries for each feature; per-feature settings + live edit-position editors. Feature overlays stay visible and highlighted while their settings screen is open (not just Edit Position/Size).
- [x] **Keybinds** — toggle Ability Log, toggle DPS overlay, DPS cycle prev/next (defaults ← / →), and start / stop DPS Recording (default F9). All rebindable from vanilla Controls.

## TODO

### Later / nice-to-have

- [ ] Export DPS Recorder sessions (clipboard / file).
- [ ] Per-ability damage breakdown inside the DPS Recorder view.

## Setup

For setup instructions, please see the [Fabric Documentation page](https://docs.fabricmc.net/develop/getting-started/creating-a-project#setting-up) related to the IDE that you are using.

## License

This template is available under the CC0 license. Feel free to learn from it and incorporate it in your own projects.
