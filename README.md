# LunaLib — Extended Search + Animated Icons

A fork of [LunaLib 2.0.5](https://github.com/Lukas22041/LunaLib) (Lukas04, CC BY 4.0) with two sets of additions:

- **Extended Search** — the settings panel search box now matches every searchable field of every setting, not just the mod name.
- **Animated GIF Icons** — mod authors can use animated `.gif` files as their mod's icon in the settings panel.

Same modID (`lunalib`), same folder name — drop-in replacement.

---

## What's new in 1.1.0

**Animated GIF icons** for the settings panel mod list:

- Place a `.gif` in your mod folder and set `"iconPath"` in `LunaSettingsConfig.json`.
- If a static icon path is set but a `.gif` with the same base name exists, LunaLib automatically prefers the animated version.
- Frames decoded once on panel open, cached as GPU textures, freed on panel close.
- Icons constrained to 40×40 px to match the standard card layout.
- No extra dependencies — pure Java 8, no native code.

---

## Extended Search (1.0.0)

### Search behavior

- Matches against the mod's **name**, **id** (default mode only), and every setting's **fieldName**, **fieldDescription**, **tab**, and **fieldID** (default mode only).
- Multiple words use **AND semantics** — each word must match somewhere in the mod's data, but words can be in different settings or tabs.
- Matching is **prefix of a whitespace-bounded token** (case-insensitive by default). `id` matches `ID` and `Identifier` but not `hidden` or `raid`.
- Scroll position resets to the top on every keystroke.

### Exact-only toggle

- Located below the search field. Off by default.
- When on: each word must equal a whole token, **case-sensitively**. IDs are excluded from the haystack to prevent case-mismatch leakage.
- Resets to off every time the panel reopens.

### Tab highlights

- Tabs with at least one matching setting render normally.
- Tabs with no matches have their label drawn in gray (still clickable).
- Tab labels that contain a search prefix are highlighted in green.

### Text field QoL

- Caps Lock, function keys, navigation keys no longer inject junk characters.
- Delete key acts as Backspace (field has no caret).
- Delete repeat cooldown is 150 ms wall-clock instead of 8 frames — one tap = one character regardless of framerate.

### Main-menu button polish

- "Mod Settings" / "Version Checker" labels switched from DEFAULT_SMALL to ORBITRON_20AABOLD at 14 px.
- "(N Updates)" text rendered as a separate centered DrawableString.
- All in-panel button labels vertically centered.

### Reliability fix

- Corrupt per-mod config JSONs under `saves/common/LunaSettings/` are renamed to `<modID>.json.corrupt-<timestamp>` and replaced with defaults instead of crashing on load.

---

## Installation

1. **Remove** the original `LunaLib` folder from your mods directory (same modID — they conflict).
2. **Drop** the `LunaLib-Search-2.0.5` folder into `Starsector/mods/`.
3. Enable in the launcher.

---

## For modders — animated icons

In your mod's `LunaSettingsConfig.json`:

```json
{
  "iconPath": "graphics/icons/my_mod.gif"
}
```

Or keep the existing static path and place a `.gif` with the same base name alongside the `.png` — LunaLib will prefer the `.gif` automatically.

Requirements: the `.gif` must be readable at runtime from the mod's own folder. Standard GIF87a and GIF89a are supported.

---

## Building from source

Requirements: **Gradle 8+**, **JDK 8+** (or JDK 17/21/26 — Kotlin targets 1.8 bytecode), a local **Starsector** install, and **LazyLib** in your mods folder.

```
# 1. Clone
git clone https://github.com/ecnamor-rewolc/LunaLib-Extended-Search.git
cd LunaLib-Extended-Search/LunaLib-Search-2.0.5

# 2. Set your Starsector path
cp gradle.properties.example gradle.properties
# edit gradle.properties — set starsectorDir to your install path

# 3. Build
gradle jar
# output: jars/LunaLib.jar
```

The compiled `LunaLib.jar` is not committed — it is a build artifact. Download a pre-built release from the [Releases](https://github.com/ecnamor-rewolc/LunaLib-Extended-Search/releases) page if you just want to play.

---

## License

CC BY 4.0 — same as the original LunaLib. See [creativecommons.org/licenses/by/4.0/](https://creativecommons.org/licenses/by/4.0/).

Original work: Lukas04.  
Extended Search patch: ecnamor.  
GIF animated icons: ecnamor.  
GIF decoder (`GifDecoder.java`): Kevin Weiner / FM Software, Public Domain.
