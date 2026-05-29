## Version 1.1.0 — Animated GIF Icons

### New feature: animated `.gif` support for mod icons

Mod authors can now use animated GIFs as their mod's icon in the settings panel.

**How to use:**

1. Place a `.gif` file inside your mod folder (e.g. `graphics/icons/my_mod.gif`).
2. In your mod's `LunaSettingsConfig.json`, set the `iconPath` to that file:
   ```json
   { "iconPath": "graphics/icons/my_mod.gif" }
   ```
3. Done. LunaLib detects the `.gif` extension and plays it automatically in the mod list.

**Details:**

- Static PNG/JPG icons continue to work exactly as before — no changes required for existing mods.
- If a static icon path (e.g. `.png`) is specified but a `.gif` file with the same base name exists next to it, LunaLib automatically prefers the animated version.
- GIF frames are decoded once on first panel open and cached as GPU textures. Memory is freed when the settings panel closes.
- Icons are constrained to 40×40 pixels to match the standard card layout.
- No extra dependencies — the decoder is a single pure-Java file bundled inside `LunaLib.jar`.
- Playback loops forever regardless of the GIF's own loop-count header.

---

## Version 1.0.0 — Extended Search

## Attribution

This is a patch on top of [LunaLib](https://github.com/Lukas22041/LunaLib)
2.0.5 by **Lukas04**, licensed under
[CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). The original
work is unchanged outside the six files listed below; this patch is
distributed under the same license.

Patch author: **ecnamor.**

A fork of LunaLib 2.0.5 that extends the mod settings menu's search box from
"matches mod name/id only" to "matches every searchable field of every setting,"
plus a strict-search toggle, visual feedback (highlight + tab gray-out),
quality-of-life fixes for the search text field (Caps Lock / Delete / repeat
rate), readable main-menu button labels, and a small reliability fix for
corrupt per-mod config JSONs.

Built and tested against Starsector 0.98a-RC8 on Windows. ModID is unchanged
(`lunalib`), so this is a drop-in replacement — other mods that declare a
dependency on `lunalib` continue to work.

## Behavior

**Search filter (mod carousel)**
- The search input now matches against, for every mod:
  - The mod's `name` (and `id`, in default mode only — see "Exact only" below)
  - Plus `fieldName`, `fieldDescription`, `tab` and (default mode) `fieldID`
    of every setting the mod registered through `LunaSettings.csv`.
- Whitespace-separated words use AND semantics across the mod's fields:
  typing `nexerelin credits` keeps a mod where one setting mentions Nexerelin
  and another contains a "Credits" block, even though no single field has
  both words. Each word still has to land somewhere in the mod's data.
- Each word matches as a **prefix of a whitespace-bounded token**, not an
  arbitrary substring. So `id` matches `ID` / `Identifier` but skips
  `hidden`, `raid`, `mid-save`, `individual`, etc. This keeps short queries
  from blowing up the result set.
- The carousel scroll position resets to the top on every keystroke so the
  filtered list never appears empty just because the scroller was parked
  below the new bottom.

**"Exact only" toggle (under the search field)**
- Off by default — the matching rules above (case-insensitive prefix) apply.
- On — each search word must equal a whole whitespace-bounded token,
  **case-sensitively**. Typing `Nexerelin` hits the mod "Nexerelin" only;
  `nexerelin` lowercase no longer matches because the displayed name has a
  capital N. Useful for short identifiers that would otherwise pull in noise
  even with the prefix rule (e.g. searching for an exact-cased CSV id).
- In exact mode the haystack also drops internal IDs (`mod.id`, `fieldID`).
  These aren't visible to the user, and including them would let a
  lowercase mod-id like `"nexerelin"` sneak through case-sensitive search
  against the displayed `"Nexerelin"`. Default-mode keeps IDs in the
  haystack so power users can still grep by them.
- Resets to off every time the panel is reopened — there is no persisted
  state for it. Toggle's text colour (gray when off, search-green when on)
  is the only state indicator; the LunaUIButton "regularButton" auto-text
  was bypassed so the label stays "Exact only" instead of flipping to
  True/False.

**Tabs (right-hand panel)**
- Tabs that contain at least one matching setting render normally.
- Tabs whose settings contain none of the search words have their label
  drawn in `Misc.getGrayColor()` — the tab is still clickable, just visually
  dimmed. This avoids hiding/rearranging tabs (which would be disorienting)
  while still showing the user where the matches live.
- Tab labels that themselves contain a search prefix get the match
  highlighted in the search color.
- Word semantics differ on purpose between the two surfaces:
  - **Carousel filter** is `AND` across the search words — the mod must
    contain every word somewhere in its data, but the words may live in
    different tabs / settings / fields. This is what makes
    `nexerelin credits` correctly surface a mod that mentions Nexerelin in
    one setting and Credits in another.
  - **Tab gray-out** is `OR` across the search words — a tab stays active
    if it contains any one of the words. If the gray-out were also `AND`,
    a tab that hosts only part of a multi-word query (say only `disable`
    out of `force disable`) would render gray even though the user can
    plainly see green matches in it. `OR` keeps the gray-out aligned with
    "is there anything green to look at here."

**Highlight color**
- Search matches render in `Misc.getPositiveHighlightColor()` (the standard
  "positive number" green), distinct from `Misc.getHighlightColor()` (yellow).
  This is deliberate: a lot of mod authors use yellow `[brackets]` in their
  setting descriptions, and using the same color for search matches would be
  ambiguous. With two colors, yellow always means "author-highlighted" and
  green always means "this is what your search just matched."
- Highlighting is applied to: the mod's name in the carousel, the tab label,
  section headers inside a tab (Header-type entries), the setting's
  `fieldName`, and the setting's `fieldDescription` / `defaultValue`
  (for Text-type entries).

**Bracket/search overlap rule**
- `LabelAPI.setHighlight` matches whole whitespace-bounded tokens with a
  forward-only cursor, so the implementation has to be careful with two
  separate highlight sources sharing one paragraph.
- When a search match overlaps with a bracket-extracted highlight (one
  string is contained in the other, case-insensitive), the bracket is
  dropped and only the search highlight is emitted. The search color wins
  over the author's yellow at the point the user is searching for, which is
  the visually correct outcome.
- All highlights (kept brackets + search matches) are sorted by their first
  whole-token position in the text before being passed to `setHighlight` /
  `setHighlightColors`, because the cursor only moves forward. Without
  sorting, later-in-text highlights would silently fail to render.

**Tooltip**
- `searchFieldTooltip` rewritten to one short paragraph describing the new
  behavior, with `prefix` / `highlighted` / `grayed out` as in-line
  highlights.
- `searchFieldName` shortened from `"Search Mod"` to `"Search"`.

## Text field QoL (LunaUITextField)

The search box surfaced a few longstanding text-field annoyances that bit
during testing. Fixes:

- **Caps Lock / function keys no longer inject junk.** Hitting Caps Lock,
  Scroll Lock, Print Screen, Pause, the Menu key, F1-F12, Num Lock, Insert,
  Home, End, PgUp/PgDn, arrows, etc. used to append the key's control-code
  `eventChar` to the text. Now the loop drops any event whose `eventChar`
  sits in ASCII 0-31 or equals 127 — a universal printable-character gate
  instead of an ever-growing per-key skip list.
- **Delete acts as Backspace.** The field has no caret, so "remove a
  character" is the only deletion mental model. `Keyboard.KEY_DELETE` is now
  bound to the same handler as `Keyboard.KEY_BACK`.
- **Framerate-independent delete cooldown.** The old per-frame cooldown of
  8 frames was ~44 ms at 180 FPS — short enough that a single physical tap
  often deleted two characters. Replaced with a 150 ms wall-clock minimum
  gap tracked via `System.currentTimeMillis()`. One tap is always one
  character; a hold settles into ~7 chars/second after the OS keyboard
  repeat delay kicks in.

## Main-menu button polish (CombatHandler)

The "Mod Settings" / "Version Checker" buttons drawn over the title screen
were rendering with `Fonts.DEFAULT_SMALL` at 15 px, which looked blurry and
squished. Swapped to `Fonts.ORBITRON_20AABOLD` rendered at 14 px — same
family Starsector uses elsewhere for UI labels, reads as bold/crisp at
button size. The "(N Updates)" overlay is now rendered as a separate
`DrawableString` (also Orbitron, 14 px) and centered horizontally under the
main label; the old `appendIndented` approach left-aligned it within the
two-line block.

The existing in-panel buttons (Save All, Reset Mod To Default, About,
Search-field placeholder) used `position.height - computeTextHeight/2` for
the Y offset, which put the label near the bottom of the button instead of
the middle. Fixed to `height/2 - computeTextHeight/2` against the button's
own width/height — all five top-row buttons now visually align.

## Reliability fix (small, separate)

`LunaSettingsLoader.loadConfigSafe(modId)` wraps `JSONUtils.loadCommonJSON`
calls used during settings load and on-disk default sync. If a per-mod JSON
under `Starsector/saves/common/LunaSettings/<modID>.json` is unreadable
(e.g. a partial write after a crash, or a stale incompatible file left
behind by a removed mod), the file is renamed to
`<modID>.json.corrupt-<timestamp>` and a fresh default is written instead
of bubbling the exception up to `LunaLibPlugin.onApplicationLoad`. Two
call sites in `saveDefaultsToFile` and `loadSettings` were updated to use
the wrapper.

## Changed files

Files relevant to upstream — the rest of the mod folder is untouched.

| Path | Change |
| --- | --- |
| `data/strings/strings.json` | `searchFieldName`, `searchFieldTooltip` rewritten |
| `src/lunalib/backend/ui/settings/LunaSettingsUIModsPanel.kt` | search filter (AND across words, prefix-match, Exact-only toggle with case-sensitive whole-token equality), companion helpers `extractMatches` / `findFirstTokenPosition` / `anyTokenMatches` / `anyTokenHasPrefix` / `getSearchHighlightColor`, `rawSearch` + `currentSearch` + `exactMatchOnly` companion mirrors reset in `init`, mod-name highlight, scroll reset on keystroke, button-text centering fix; GIF icon support (auto-detect `.gif`, skip `loadTexture`, use `LunaUIAnimatedSprite`, call `GifAnimationManager.clearAll()` on panel close) |
| `src/lunalib/backend/ui/settings/LunaSettingsUISettingsPanel.kt` | tab gray-out (OR across words) + tab-name highlight, fieldName highlight, Header-type highlight, fieldDescription / Text-type highlight + bracket overlap handling + position-sorted setHighlight, `advance()` re-renders on search-text change |
| `src/lunalib/backend/ui/settings/LunaSettingsLoader.kt` | `loadConfigSafe` wrapper around `JSONUtils.loadCommonJSON`, used by `saveDefaultsToFile` and `loadSettings` |
| `src/lunalib/backend/ui/components/base/LunaUITextField.kt` | universal non-printable-key filter, Delete bound to backspace handler, framerate-independent delete cooldown |
| `src/lunalib/backend/scripts/CombatHandler.kt` | main-menu button labels switched to Orbitron, separate `versionUpdateText` DrawableString centered under "Version Checker" |
| `src/lunalib/backend/util/GifDecoder.java` | new — pure-Java GIF frame decoder (Kevin Weiner, Public Domain) |
| `src/lunalib/backend/util/GifAnimationManager.kt` | new — GL texture cache with frame timing |
| `src/lunalib/backend/ui/components/base/LunaUIAnimatedSprite.kt` | new — drop-in animated-sprite UI element |

## Notes & non-goals

- Match semantics are whole-token prefix because Starsector's
  `LabelAPI.setHighlight` only highlights whole whitespace-bounded tokens.
  Mid-word substring highlighting (e.g. "Hotk" inside "Hotkey") was tried
  and silently fails at the API level; the implementation expands matches
  to the surrounding token instead.
- The credits/about screens, debug UI, and the per-setting renderers
  (color picker, keybind, radio, etc.) were not touched.
- No new dependencies. `fuzzywuzzy` is still on the classpath; it is no
  longer used by the search path but the import is preserved.
- ModID stays `lunalib`. The forked folder ships with mod_info.json
  renaming to "LunaLib (Search Fork)" purely so the launcher list shows
  which one is enabled — that's a local cosmetic change, not a part of
  the patch intended for upstream.
