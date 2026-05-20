# LunaLib Extended Search — Session Handoff

Self-contained briefing for any agent continuing this work. Replaces the full
conversation transcript. Read top-to-bottom once, then jump back via the section
list.

- [1. Project at a glance](#1-project-at-a-glance)
- [2. Environment & paths](#2-environment--paths)
- [3. Build & rebuild loop](#3-build--rebuild-loop)
- [4. Files changed (with rationale)](#4-files-changed-with-rationale)
- [5. Search semantics — exact rules](#5-search-semantics--exact-rules)
- [6. Starsector / LunaLib API findings (the load-bearing tribal knowledge)](#6-starsector--lunalib-api-findings-the-load-bearing-tribal-knowledge)
- [7. State held by the companion object](#7-state-held-by-the-companion-object)
- [8. Helper functions on `LunaSettingsUIModsPanel.Companion`](#8-helper-functions-on-lunasettingsuimodspanelcompanion)
- [9. Known limitations & open issues](#9-known-limitations--open-issues)
- [10. Publication: GitHub repo + releases](#10-publication-github-repo--releases)
- [11. Archives produced](#11-archives-produced)
- [12. User preferences / collaboration notes](#12-user-preferences--collaboration-notes)
- [13. Failed experiments worth not repeating](#13-failed-experiments-worth-not-repeating)

---

## 1. Project at a glance

A drop-in fork of LunaLib 2.0.5 (by Lukas04, CC BY 4.0) for the game
**Starsector**. The fork extends the in-game mod-settings menu's search box so
it matches setting names, descriptions, IDs, tab names, section headers — not
just the mod name/id. Plus an "Exact only" toggle (case-sensitive whole-token
equality), per-token green highlighting, gray-out of non-matching tabs,
resilience to corrupt per-mod config JSONs, and unrelated text-field /
main-menu polish picked up along the way. ModID stays `lunalib` so it's a
literal drop-in for any mod that depends on LunaLib.

Forked folder: `C:\my\Starsector\mods\LunaLib-Search-2.0.5\` (separate from
the original `LunaLib-2.0.5\` at the same root).

Patch lives publicly at https://github.com/ecnamor-rewolc/LunaLib-Extended-Search
with v1.0.0 release containing two `.7z` assets.

## 2. Environment & paths

Windows 11. PowerShell preferred (use `PowerShell` tool, not `Bash` — bash
fork frequently fails on this machine with `dofork: Resource temporarily
unavailable`).

| Thing | Where |
| --- | --- |
| Starsector install | `C:\my\Starsector\` |
| Bundled JRE (game) | `C:\my\Starsector\jre\` |
| Mods | `C:\my\Starsector\mods\` |
| Forked mod (workspace) | `C:\my\Starsector\mods\LunaLib-Search-2.0.5\` |
| Original LunaLib (reference) | `C:\my\Starsector\mods\LunaLib-2.0.5\` |
| Starsector API jars | `C:\my\Starsector\starsector-core\` — `starfarer.api.jar`, `starfarer_obf.jar`, `fs.common_obf.jar`, `json.jar`, `log4j-1.2.9.jar`, `lwjgl.jar`, `lwjgl_util.jar`, `xstream-1.4.10.jar` |
| LazyLib jars | `C:\my\Starsector\mods\LazyLib-3.0.0\jars\LazyLib.jar`, `LazyLib-Kotlin.jar` |
| Bundled fuzzywuzzy | `<mod>\jars\libs\fuzzywuzzy-1.3.0.jar` (still on classpath, unused) |
| Gradle | `C:\my\gradle-9.5.1\bin\gradle.bat` (Gradle 8.x doesn't work with Java 26) |
| JDK | `C:\Program Files\Java\jdk-26.0.1\` (Java 26 — Kotlin 1.9 cannot target it, need Kotlin 2.x) |
| 7-Zip | `C:\Program Files\7-Zip\7z.exe` |
| GitHub CLI | `C:\Program Files\GitHub CLI\gh.exe` (installed via `winget install GitHub.cli`) |
| Git identity | `ecnamor-rewolc <286395491+ecnamor-rewolc@users.noreply.github.com>` |
| `gh` git protocol | switched from SSH to HTTPS (Windows bundled OpenSSH too old for GitHub's KEX — `sntrup761x25519-sha512@openssh.com` unsupported). Use `gh config set -h github.com git_protocol https`. |
| Published archives | `C:\my\LunaLib-search-patch.7z`, `C:\my\LunaLib-search-fullmod.7z`, `C:\my\LunaLib-search-patch-stable.7z` |
| Handoff doc (this file) | `C:\my\LunaLib-Search-Session-Handoff.md` |
| Public patch summary | `C:\my\PATCH_SUMMARY.md` (also pushed to repo as `README.md`, but with extra Credits / Disclaimer sections added in the repo copy) |

## 3. Build & rebuild loop

`build.gradle.kts` in mod root pins Kotlin 2.1.20 (1.9.x explodes against
Java 26 with an internal compiler error). Java target stays 1.8 because
Starsector ships an old JVM at runtime. JVM toolchain is unset — Gradle uses
whatever JDK is on `JAVA_HOME` / PATH, which is JDK 26 here.

Dependencies are all `compileOnly` — Starsector itself provides them at
runtime via its mod classloader.

Rebuild jar after editing any `.kt`:

```powershell
Set-Location "C:\my\Starsector\mods\LunaLib-Search-2.0.5"
& "C:\my\gradle-9.5.1\bin\gradle.bat" jar --no-daemon
```

Output goes to `<mod>\jars\LunaLib.jar` (overwrites the original). No daemon
because parallel sessions sometimes leave a stale daemon and break the build.

Gradle prints lots of deprecation warnings — they're not actionable, ignore.

## 4. Files changed (with rationale)

Six files. Patch summary auto-generates from this list.

### `data/strings/strings.json`
- `searchFieldName` shortened `"Search Mod"` → `"Search"`.
- `searchFieldTooltip` rewritten to describe the new behavior. The
  call-site for this tooltip uses an **inline** `TooltipMakerAPI.TooltipCreator`
  (not the shared `TooltipHelper`) so it can assign per-highlight colors:
  `prefix` yellow, `highlighted` green (search color), `grayed out` gray.

### `src/lunalib/backend/ui/settings/LunaSettingsLoader.kt`
- Adds private `loadConfigSafe(modId)` wrapping `JSONUtils.loadCommonJSON`.
  Catches anything thrown by load, renames the bad file to
  `<modID>.json.corrupt-<timestamp>`, recreates a fresh default, returns it.
  If recovery also fails, returns null and caller continues.
- Two call sites updated: `saveDefaultsToFile` and `loadSettings`. Also
  fixed a latent NPE pattern in `loadSettings` where `data.length() == 0 || data == null`
  was reordered to null-check first.
- **Why**: one corrupted per-mod JSON used to bubble up through
  `LunaLibPlugin.onApplicationLoad` and crash the whole settings load. Now
  it gracefully resets that one mod's settings.

### `src/lunalib/backend/ui/settings/LunaSettingsUIModsPanel.kt`
The big one. All search filter logic, the carousel, the "Exact only" toggle,
the companion-object helpers. Key changes:

- Companion state: `currentSearch` (lowercased mirror, used as change-detection
  key by SettingsPanel.advance()), `rawSearch` (original-case mirror, fed to
  case-sensitive helpers in exact mode), `exactMatchOnly: Boolean`. All three
  reset in `init()` (the panel is recreated when the player reopens the menu,
  but the companion outlives that — so without manual reset, highlights from
  a previous session leak).
- Companion helpers (see section 8) — `getSearchHighlightColor`,
  `extractMatches`, `findFirstTokenPosition`, `anyTokenMatches`,
  `anyTokenHasPrefix` (legacy, kept for now).
- Carousel filter rewritten: AND across whitespace-separated search words,
  OR across the mod's fields. In default mode haystack includes `mod.id`,
  `mod.name`, plus `fieldName` / `fieldID` / `fieldDescription` / `tab` of
  every setting. In exact mode the haystack drops `mod.id` and `fieldID`
  (invisible internal identifiers — keeping them lets lowercase mod ids
  like `"nexerelin"` sneak past case-sensitive search against the displayed
  `"Nexerelin"`).
- Mod-name paragraph in the carousel: per-token highlights via `extractMatches`.
  If matches found → green; if no in-name match but mod still passed the
  filter (matched via settings) → whole name highlighted yellow (legacy).
- Search field onUpdate: when text changes, set `lastScroller = 0f` *before*
  `createModsList()` so the scroller doesn't park below the new end of the
  filtered list (the "carousel looks empty" bug).
- "Exact only" toggle button:
  - `LunaUIButton(value, regularButton=false, ...)`. **regularButton=false
    is critical**: `regularButton=true` makes LunaUIButton auto-rewrite the
    text to True/False every advance frame.
  - Manually flip `value` in onClick (regularButton=false doesn't auto-toggle).
  - onUpdate sets text color: green when on (search-color), base-player-color
    when off. Background alpha varies by hover+state.
  - Centered manually (LunaUIButton with regularButton=false defaults the
    text to top-left at (5,5)).
- **Other-button centering**: the existing Save All / Reset / About buttons
  used `position.height - computeTextHeight/2` for Y, which pinned text near
  the bottom of the button. Replaced with `this.height/2 - computeTextHeight/2`.
  Same for the search field's placeholder text.
- Tooltip on search field: replaced shared `TooltipHelper` call with inline
  `TooltipCreator` so we can call `setHighlightColors` for per-highlight
  colors (`prefix` yellow, `highlighted` green, `grayed out` gray).

### `src/lunalib/backend/ui/settings/LunaSettingsUISettingsPanel.kt`
The right-hand panel — tabs and the per-setting cards.

- Tabs: gray label (`Misc.getGrayColor()`) when the tab contains no settings
  that match any search word. Match rule is OR across words (different from
  the carousel which is AND) — see section 5 for why.
- Tab labels themselves get search-color highlights for any prefix match
  in the tab name.
- `advance()` watches `LunaSettingsUIModsPanel.currentSearch` against
  `lastObservedSearch`; when it changes, recreates tabs + panel.
- `fieldName` paragraph: per-token highlights from `extractMatches` against
  `rawSearch`. Same for Header-type entries (drawn via `addSectionHeading`
  which also returns a `LabelAPI`).
- Description paragraph: this is the gnarly one. Two highlight sources
  share one paragraph — author `[brackets]` (yellow) and search matches
  (green). Algorithm:
  1. Parse brackets out of the description string as before, but into a
     separate `bracketHighlights: MutableList<String>` (no leading `""` —
     that was load-bearing junk that broke color indexing).
  2. Compute `searchHighlights = extractMatches(strippedText, rawSearch)`.
  3. **Dedup**: drop any bracket whose lowercase string overlaps with any
     search match (in either direction — `br.contains(sh) || sh.contains(br)`).
     Reason: `LabelAPI.setHighlight` matches each entry against the first
     unconsumed occurrence; two entries pointing at the same substring
     collide, the first wins, the second silently fails. We want search
     green to always win where the user is looking.
  4. **Position-sort**: build `(position, text, color)` triples and sort by
     position via `findFirstTokenPosition`. Reason: `LabelAPI.setHighlight`'s
     match cursor only moves forward. Out-of-order entries are silently
     dropped.
  5. Pass sorted texts to `setHighlight(*)` and sorted colors to
     `setHighlightColors(*)`.
- The Text-type entry path (used for credits blocks etc.) has the same
  bracket parsing → same dedup+sort treatment.

### `src/lunalib/backend/ui/components/base/LunaUITextField.kt`
Text field used everywhere in LunaLib (including our search field). Three
unrelated QoL fixes triggered by testing the search:

- Universal non-printable filter: `if (event.eventChar.code < 32 || event.eventChar.code == 127) continue`
  immediately before appending. Catches Caps Lock, Scroll Lock, Print Screen,
  Pause, Menu, F1-F12, Num Lock, Insert, Home, End, PgUp/PgDn, arrows, etc.
  in one shot — these previously injected NUL or DEL into the text. (Earlier
  attempt to filter by individual `Keyboard.KEY_*` constants was a losing
  battle — too many keys.)
- `Keyboard.KEY_DELETE` bound to the same handler as `Keyboard.KEY_BACK`.
  The field has no caret, so "remove a character" is the only deletion
  model and both keys should do it.
- Deletion cooldown moved off frame-counter onto `System.currentTimeMillis()`
  with a 150 ms minimum gap. The old `cooldown = 8f` was ~44 ms at 180 FPS —
  short enough that a single physical tap deleted multiple characters on
  high-refresh setups. Added field `lastBackspaceMs: Long = 0L`.

### `src/lunalib/backend/scripts/CombatHandler.kt`
Main-menu overlay (the "Mod Settings" / "Version Checker" buttons drawn over
the title screen).

- Font swap: button labels now use `Fonts.ORBITRON_20AABOLD` rendered at
  14 px instead of `Fonts.DEFAULT_SMALL` at 15 px. Crisper, bolder, matches
  Starsector's own button styling. `Fonts.INSIGNIA15LTAA` does **not exist**
  — the only available Insignia constants are `INSIGNIA_LARGE` and
  `INSIGNIA_VERY_LARGE`. Available `Fonts` constants are: `ORBITRON_24AABOLD`,
  `ORBITRON_24AA`, `ORBITRON_20AABOLD`, `ORBITRON_20AA`, `ORBITRON_16`,
  `ORBITRON_12`, `VICTOR_10`, `INSIGNIA_LARGE`, `INSIGNIA_VERY_LARGE`,
  `DAMAGE_FLOATIES_FONT`, `GROUP_NUM_FONT`, `DEFAULT_SMALL`.
- "(N Updates)" line refactored into a separate `versionUpdateText:
  LazyFont.DrawableString` so it can be centered independently. The
  previous `text + "\n" + appendIndented` approach left-aligned the second
  line within the wider-of-two-lines block, which read as off-center.
  Same Orbitron font, same 14 px size as the main label. Two-line layout
  branches at draw time depending on whether the count text is empty.
- The tip popup body (`tip`) stays on `DEFAULT_SMALL` — it's a paragraph,
  small font is appropriate.

## 5. Search semantics — exact rules

The pair of AND/OR choices below is intentional and load-bearing for UX. If
you change one, think about the other.

| Surface | Match rule | Word semantics | Mode-dependent? |
| --- | --- | --- | --- |
| **Carousel filter** (which mods show up) | every search word must appear somewhere in the mod's haystack | **AND** across words | default = case-insensitive prefix; exact = case-sensitive whole-token equality |
| **Tab gray-out** | at least one search word must appear in the tab's settings | **OR** across words | same mode-dependent rule |
| **Highlight extraction** (`extractMatches`) | per-token: any token starting with any search word (default) / equal to any search word (exact) | per-token OR | same mode-dependent rule |

**Why AND for the carousel but OR for tabs**: typing `nexerelin credits`
should surface a mod that mentions Nexerelin in one setting and Credits in
another (justifies AND across the whole mod). But once you're looking at
that mod, a tab that contains only `disable` and not `force` (out of search
`force disable`) still has green matches visible — graying it out would
contradict what the player sees on screen. So tab gray-out is OR.

**Why prefix instead of substring**: short queries like `id` would otherwise
flood the result set with `hidden` / `raid` / `mid-save` / `individual` /
etc. Prefix matching against whitespace-bounded tokens keeps short queries
tight. The tradeoff: searching `clicks` won't find `Right click` (which is
fine — most people type partial words, not suffixes).

**Why expand to the whole token before highlighting**: `LabelAPI.setHighlight`
only highlights whole whitespace-bounded tokens. A search of `hotk` against
text containing `hotkey` would silently fail if we passed `Hotk` to
setHighlight. We expand the match outward to whitespace boundaries (so the
highlight string becomes `hotkey`), accepting that we can't paint partial
words. This is the "occasional misses" the disclaimer mentions — sometimes
the user-perceived correct behavior would be a partial highlight that just
isn't possible via this API.

**Why Exact-only drops `mod.id` and `fieldID` from the haystack**: those are
invisible internal identifiers. With case-sensitive matching, a lowercase
`mod.id = "nexerelin"` would let lowercase `nexerelin` slip past a strict
search against the displayed `"Nexerelin"` (capital N). Exact mode is about
"what you see is what you can search," so internal IDs are excluded. Default
mode keeps them, because power users may grep by id.

## 6. Starsector / LunaLib API findings (the load-bearing tribal knowledge)

These are the non-obvious things that caused real bugs during the session.
Anyone touching the UI code should know them before editing.

### `LabelAPI.setHighlight(vararg highlights: String)`
- **Whole-token matching only.** A highlight string must align to
  whitespace-bounded token boundaries in the target text. `"Hotk"` inside
  `"Hotkey"` silently does nothing. `"Credits"` inside `"Credits:"` also
  fails (the colon doesn't end a token — `"Credits:"` is one token). When
  in doubt, expand outward to whitespace.
- **Forward-only match cursor.** Highlights are matched in array order
  against successively later positions in the text. If you pass `["B", "A"]`
  for a text `"A B"`, `"B"` will match at position 2 and the cursor will
  advance past it; `"A"` then has nothing left to match. Always sort
  highlights by their first whole-token position in the text.
- **Duplicates work.** Passing `["the", "the"]` highlights the first two
  occurrences of `"the"` in the text. This is how we paint every occurrence
  of a token even when the same word repeats.
- **Empty string `""` in the highlight array breaks color indexing.** The
  original LunaLib code started its highlight list with `mutableListOf("")`
  as a placeholder. With `setHighlightColors`, the colors[0]=color for ""
  ends up applied to highlights[1] (the next real entry), shifting
  everything by one. The fix is to not seed the list with an empty string
  at all — use `mutableListOf<String>()`.

### `LabelAPI.setHighlightColors(vararg colors: Color)`
- Per-highlight colors aligned by index. Length must match the number of
  highlights actually passed to `setHighlight`. If shorter, later highlights
  get the paragraph's default highlight color (set in `addPara`).

### `addPara(text, padding, baseColor, highlightColor, vararg highlights)`
- Last varargs are the initial highlights. To change them later call
  `setHighlight(...)` on the returned `LabelAPI`. To change colors per-highlight
  later, call `setHighlightColors(...)`.

### `addSectionHeading(text, alignment, padding)` returns `LabelAPI`
- Same `setHighlight` rules apply. We use this for `Header`-type LunaLib
  settings, and they DO need the highlight pass for search matches.

### `LunaUIButton(value: Boolean, regularButton: Boolean, ...)`
- `regularButton = true`: this means "toggle that draws True/False". The
  advance() loop overwrites `buttonText.text` to the True/False string from
  LunaStrings on every frame. Use this only when you want a literal
  True/False display.
- `regularButton = false`: regular click button. Text stays as you set it.
  Default position is `(5, 5)` top-left — you must center it manually.
  Clicking does NOT auto-toggle `value` — you must do `button.value = !button.value`
  in your onClick.

### `LabelAPI.position.width / .height`
- This is the bounding box of the **text itself**, not the container. The
  original LunaLib code used `position.width / 2` as a centering anchor,
  which centered the text around itself — basically a no-op. Use the
  container's `.width` / `.height` for centering math.

### `LunaSettingsLoader.SettingsData`
- Public, `@JvmStatic`, `MutableList<LunaSettingsData>`. Populated at
  startup by reading every mod's `data/config/LunaSettings.csv`.
- Each entry: `modID`, `fieldID`, `fieldName`, `fieldType` (`Int` / `Double`
  / `Boolean` / `String` / `Color` / `Keycode` / `Radio` / `Multichoice` /
  `Text` / `Header`), `fieldDescription` (sourced from either `fieldTooltip`
  or `fieldDescription` CSV column — both names supported), `defaultValue`,
  `secondaryValue`, `minValue`, `maxValue`, `tab`.
- For `Header` type rows, the displayed text comes from `defaultValue`, not
  `fieldName`. Both might differ.
- For `Text` type rows, the displayed body text also comes from `defaultValue`.

### Per-mod JSON persistence
- Path: `<Starsector>\saves\common\LunaSettings\<modID>.json` (resolved via
  `JSONUtils.loadCommonJSON`).
- Stays around forever after a mod is uninstalled. Nothing reads orphaned
  files, but a corrupt one used to crash load. Now caught by
  `loadConfigSafe`.

### `Fonts.*` constants
- See the list under section 4 / `CombatHandler.kt`. **`Fonts.INSIGNIA15LTAA`
  does not exist** in this version of `starfarer.api.jar` — that was a
  guess based on common font names elsewhere. Use `javap -cp starfarer.api.jar
  com.fs.starfarer.api.ui.Fonts` to enumerate.

### `InputEventAPI.eventChar`
- The `Char` injected by a keyboard event. For non-printable keys it's
  either ` ` (NUL) or another ASCII control code. Filtering
  `char.code < 32 || char.code == 127` reliably excludes all keyboard
  garbage in one check.

### Build environment quirks
- Kotlin 1.9.x emits an internal compiler error when running under Java 26.
  Bump to 2.1.x for any new edits.
- Gradle 8.x can't parse a Java 26 launcher version string ("26.0.1" fails
  its regex). Use Gradle 9.x.
- Windows bundled OpenSSH (Win11 default) doesn't support GitHub's KEX
  algorithm. Use `gh config set -h github.com git_protocol https` to switch
  git operations to HTTPS, which uses gh's stored token.

## 7. State held by the companion object

`LunaSettingsUIModsPanel.Companion` carries process-lifetime state because
the panel is recreated every time the player opens the menu and we need
shared state across that recreate.

```kotlin
companion object {
    var lastSelectedMod: String   // existing — last clicked mod card
    var selectedMod: ModSpecAPI?  // existing
    var lastScroller: Float        // existing — preserve scroll position; reset to 0 on each search keystroke
    var currentSearch: String      // new — lowercase, used as change-detection key by SettingsPanel.advance
    var rawSearch: String          // new — original case, fed to case-sensitive helpers
    var exactMatchOnly: Boolean    // new — Exact-only toggle state
    // helpers below in section 8
}
```

All three of the new vars are reset to empty/false in `init()` because
they outlive the panel instance and would otherwise leak state between
menu opens.

## 8. Helper functions on `LunaSettingsUIModsPanel.Companion`

These are referenced from both this file and `LunaSettingsUISettingsPanel`.

```kotlin
fun getSearchHighlightColor(): java.awt.Color = Misc.getPositiveHighlightColor()

// Walks `text` token-by-token (whitespace-separated). Returns every token
// whose lowercased form startsWith any search word (default mode) or equals
// any search word (exact mode), in textual order. In exact mode the
// comparison is case-sensitive against the user-typed casing.
fun extractMatches(text: String, search: String): List<String>

// First whole-token (whitespace-bounded) position of `token` in `text`, or
// -1. Used to sort the highlight array before setHighlight, because the
// label's match cursor only moves forward.
fun findFirstTokenPosition(text: String, token: String): Int

// Predicate used by the carousel filter and tab gray-out. Mode-aware:
// case-insensitive prefix when exactMatchOnly is false, case-sensitive
// whole-token equality when true.
fun anyTokenMatches(text: String, word: String): Boolean

// Legacy prefix-only helper, still present. Not strictly needed anymore;
// could be removed if a future refactor wants to.
fun anyTokenHasPrefix(text: String, word: String): Boolean
```

## 9. Known limitations & open issues

- **"Search has occasional misses" (the disclaimer entry).** Sometimes the
  filter or highlight drops a result the user expected to see. Repro is
  unclear. Most likely candidates: edge cases in `findFirstTokenPosition`
  with bracket-stripped text, or unusual whitespace (tabs, non-breaking
  spaces) in CSV fields. Not worth chasing unless a reliable repro shows up.
- **Cannot paint partial-word highlights.** Constrained by
  `LabelAPI.setHighlight`'s whole-token rule. Searching `hotk` highlights
  the whole word `hotkey`. The user accepted this; they earlier asked for
  partial highlighting "if cheap," and confirmed the whole-word fallback
  is fine.
- **No persistence of `exactMatchOnly` across menu opens.** Deliberately —
  the user explicitly didn't want it persisted ("modders & coders make
  changes in code anyway").
- **fuzzywuzzy is still on the classpath but unused.** Was used by the
  original LunaLib search. Could be removed from `mod_info.json` `jars`
  list + the import in `LunaSettingsUIModsPanel`, but kept to minimize the
  diff for any upstream consideration.

## 10. Publication: GitHub repo + releases

- Repo: https://github.com/ecnamor-rewolc/LunaLib-Extended-Search
  - Public, Issues + Wiki disabled (hands-off — no inbox to manage).
  - Topics: `starsector`, `starsector-mod`, `lunalib`.
  - Files: `README.md` (= patch summary with extra Credits + Disclaimer at
    top), `LICENSE` (short CC BY 4.0 reference pointing at LunaLib).
  - Credits section links Lukas04's GitHub (`Lukas22041`) and the LunaLib
    forum thread (`fractalsoftworks.com/forum/index.php?topic=25658.0`).
  - Status & disclaimer block uses GitHub's `> [!WARNING]` alert with
    CAPS-ed bullets — chosen because plain `<details>` had no color
    differentiation and the user couldn't see it.
- Release v1.0.0: tag `v1.0.0`, two assets attached
  (`LunaLib-search-fullmod.7z`, `LunaLib-search-patch.7z`). Release notes
  cover Downloads + Install + Credits. Install instructions explicitly say
  to DELETE the original `LunaLib` folder from `mods/`, not just disable —
  because TriOS / Starsector mod loader can confuse two folders with the
  same modID.

To update either the release notes or the README from a future session:

```powershell
$gh = "C:\Program Files\GitHub CLI\gh.exe"
& $gh release edit v1.0.0 --repo ecnamor-rewolc/LunaLib-Extended-Search --notes "..."
# or
Set-Location "$env:TEMP\lunalib-search-publish"  # if still present
git pull
# edit README.md
git add README.md && git commit -m "..." && git push
```

## 11. Archives produced

All under `C:\my\`:

| File | Size | Contents |
| --- | --- | --- |
| `LunaLib-search-patch.7z` | ~25 KB | The six source files + `PATCH_SUMMARY.md`, mirrors the source tree (`data/strings/...`, `src/lunalib/...`). Use as a code-review / upstream-PR attachment. |
| `LunaLib-search-fullmod.7z` | ~433 KB | Full mod ready to drop into `Starsector/mods/`. Contains `mod_info.json`, `LunaLib.version`, `changelog.txt`, `jars/LunaLib.jar`, `jars/libs/fuzzywuzzy-1.3.0.jar`, `data/`, `graphics/`, `PATCH_SUMMARY.md`. Excludes build artifacts (`src/`, `build/`, `.gradle/`, `.kotlin/`, gradle config, `BUILD.md`). |
| `LunaLib-search-patch-stable.7z` | ~18 KB | Snapshot taken right before the Exact-only toggle work began. Kept as a rollback point. Contents identical to an older state of `LunaLib-search-patch.7z`. |

Reassemble the fullmod 7z from current sources:

```powershell
$gh = "C:\Program Files\7-Zip\7z.exe"
$mod = "C:\my\Starsector\mods\LunaLib-Search-2.0.5"
$out = "C:\my\LunaLib-search-fullmod.7z"
$staging = "$env:TEMP\lunalib-fullmod-staging"
Remove-Item -Recurse -Force $staging -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path "$staging\LunaLib-Search-2.0.5" | Out-Null
$dst = "$staging\LunaLib-Search-2.0.5"
Copy-Item "$mod\data", "$mod\graphics", "$mod\jars" "$dst" -Recurse
Copy-Item "$mod\mod_info.json", "$mod\LunaLib.version", "$mod\changelog.txt" "$dst"
Copy-Item "C:\my\PATCH_SUMMARY.md" "$dst\"
& $gh a -t7z -mx=9 $out "$staging\LunaLib-Search-2.0.5"
```

## 12. User preferences / collaboration notes

(Also captured in `auto-memory` under `feedback_starsector_english.md`.)

- **English-only inside Starsector files.** Code, comments, log messages,
  in-game UI strings, JSON values, tooltip text — all English, even if the
  user writes in Russian. Chat replies stay Russian. This is a hard rule
  for any work touching `C:\my\Starsector\mods\`.
- **User prefers terse output.** Don't summarize what just happened in
  detail — they can read the diff. End-of-turn summaries should be 1-2
  sentences.
- **User wants to be in the loop on visible UX changes but not micromanaged
  on internals.** Show screenshots' results, ask before publishing or
  archiving, don't ask before fixing a bug.
- **No comments in code unless WHY is non-obvious.** Comments should explain
  hidden constraints, subtle invariants, workarounds — never the WHAT.
  Never write multi-paragraph docstrings. Most edits get no comment at all.
- **User typed name**: `ecnamor` (signs `ecnamor.` in markdown). GitHub
  handle: `ecnamor-rewolc`.

## 13. Failed experiments worth not repeating

- **Kotlin 1.9.24 + Java 26.** Compiler crash. Bump to 2.x.
- **Gradle 8.10.2 + Java 26.** Version-string parser dies on "26.0.1".
  Use Gradle 9.x.
- **`Fonts.INSIGNIA15LTAA`** — does not exist. Compile error. Use
  `ORBITRON_*` family or `INSIGNIA_LARGE` / `INSIGNIA_VERY_LARGE`.
- **Per-key `Keyboard.KEY_CAPITAL` / `KEY_F1` / etc. filtering in
  LunaUITextField.** Too many keys to enumerate. The
  `eventChar.code < 32 || == 127` gate covers them all in one shot.
- **Frame-counter cooldown for backspace (`cooldown = 8f`).** Framerate-
  dependent; broke at 180 FPS. Use wall-clock `System.currentTimeMillis()`
  with a 150 ms gap.
- **LunaUIButton(regularButton=true) for the Exact toggle.** Auto-overrides
  text to "True"/"False" every frame. Use `regularButton=false` and
  manage toggle state manually.
- **Single highlight color via `setHighlightColor` when a paragraph mixes
  author brackets and search matches.** Doesn't work — author yellow and
  search green can't coexist that way. Use `setHighlightColors(vararg)`
  with per-index colors, sort by token position.
- **Leading empty string in the bracket-highlight list (`mutableListOf("")`).**
  Shifts color indexing by one. Drop it.
- **Identical highlight string sharing the first occurrence with a bracket.**
  Second entry silently drops. Dedup the bracket if it overlaps with a
  search match.
- **Searching by substring (default mode).** Floods on short queries like
  `id`. Use whitespace-bounded prefix.
- **Highlighting `Hotk` inside `Hotkey`.** LabelAPI ignores it. Expand to
  full whitespace-bounded token.
- **SSH for `gh` git protocol on Windows.** Bundled OpenSSH doesn't
  support GitHub's KEX. Switch to HTTPS.

---

End of handoff. If something in the current state doesn't match what's
described here, trust the code (it's been built and visually validated by
the user) over this doc.
