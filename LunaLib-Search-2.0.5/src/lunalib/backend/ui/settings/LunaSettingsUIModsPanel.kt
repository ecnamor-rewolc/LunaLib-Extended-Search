package lunalib.backend.ui.settings

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ModSpecAPI
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.PositionAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.Misc
import lunalib.backend.ui.components.LunaUIColorPicker
import lunalib.backend.ui.components.LunaUIKeybindButton
import lunalib.backend.ui.components.LunaUIRadioButton
import lunalib.backend.ui.components.LunaUITextFieldWithSlider
import lunalib.backend.ui.components.base.LunaUIButton
import lunalib.backend.ui.components.base.LunaUIPlaceholder
import lunalib.backend.ui.components.base.LunaUIAnimatedSprite
import lunalib.backend.ui.components.base.LunaUISprite
import lunalib.backend.util.GifAnimationManager
import lunalib.backend.ui.components.base.LunaUITextField
import lunalib.backend.ui.components.util.TooltipHelper
import lunalib.backend.ui.debug.LunaDebugUISnippetsPanel
import lunalib.backend.util.getLunaString
import lunalib.lunaSettings.LunaSettings
import lunalib.lunaSettings.LunaSettingsListener
import me.xdrop.fuzzywuzzy.FuzzySearch
import org.lazywizard.lazylib.JSONUtils
import org.lwjgl.input.Keyboard
import java.awt.Color

class LunaSettingsUIModsPanel(var newGame: Boolean) : CustomUIPanelPlugin
{

    var parentPanel: CustomPanelAPI? = null

    var panel: CustomPanelAPI? = null
    var panelElement: TooltipMakerAPI? = null
    var searchField: LunaUITextField<String>? = null

    var subpanel: CustomPanelAPI? = null
    var subpanelElement: TooltipMakerAPI? = null

    var saveButton: LunaUIButton? = null
    var resetButton: LunaUIButton? = null

    var width = 0f
    var height = 0f

    var currentSearchText = ""

    var saveDelay = 0
    var saving = false

    companion object
    {
        var lastSelectedMod = "LunaAboutSection"
        var selectedMod: ModSpecAPI? = null
        var lastScroller = 0f

        // Search phrase currently entered in the mods-list search box, lowercased.
        // Mirrored from the instance so the settings/tabs panel can read it. Also used as a
        // change-detection key (re-rendering on text change).
        var currentSearch: String = ""

        // Same value, original case preserved. Helpers use this when [exactMatchOnly] is on,
        // because exact mode is also case-sensitive (a strict match is strict on letter case too).
        var rawSearch: String = ""

        // When true, search words must equal a whole whitespace-bounded token case-sensitively.
        // When false (default), they match as a prefix of a token, case-insensitively. Toggled by
        // the "Exact only" button under the search field. Reset every time the panel is re-opened.
        var exactMatchOnly: Boolean = false

        /** Color used to highlight search matches everywhere in the menu. Deliberately distinct
         *  from `Misc.getHighlightColor()` (yellow) so search highlights cannot be confused with
         *  the yellow brackets that mod authors add to their setting descriptions. */
        fun getSearchHighlightColor(): java.awt.Color = Misc.getPositiveHighlightColor()

        /** Case-insensitive: returns the substring of [text] that matches [search] as a whole phrase,
         *  preserving original casing. Used when we want a single contiguous highlight. */
        fun extractMatch(text: String, search: String): String? {
            if (search.isEmpty()) return null
            val idx = text.lowercase().indexOf(search.lowercase())
            if (idx < 0) return null
            return text.substring(idx, idx + search.length)
        }

        /** Walks [text] token-by-token (whitespace-separated) and returns every token whose
         *  lowercased form contains any of the whitespace-split words of [search]. Results are
         *  returned in textual order, preserving original casing.
         *
         *  Why all occurrences, not just the first: a description may mention the same root word
         *  multiple times (e.g. "Nexerelins" in one sentence and "Nexerelin" in the next). The
         *  user expects every visible instance to light up. LabelAPI.setHighlight uses a
         *  forward-only cursor, so we can pass the same string twice and it will match two
         *  consecutive occurrences.
         *
         *  Whole-token expansion is also required: LabelAPI silently drops mid-token highlights
         *  like "Hotk" inside "Hotkey", so we hand it the full surrounding token ("hotkey"). */
        fun extractMatches(text: String, search: String): List<String> {
            if (search.isEmpty()) return emptyList()
            val exact = exactMatchOnly
            // In exact mode keep the user-typed casing for case-sensitive comparison; otherwise
            // lowercase so the prefix match is case-insensitive.
            val searchTokens = (if (exact) search else search.lowercase())
                .split(Regex("\\s+"))
                .filter { it.isNotEmpty() }
            if (searchTokens.isEmpty()) return emptyList()
            val out = mutableListOf<String>()
            var pos = 0
            while (pos < text.length) {
                while (pos < text.length && text[pos].isWhitespace()) pos++
                if (pos >= text.length) break
                val start = pos
                while (pos < text.length && !text[pos].isWhitespace()) pos++
                val token = text.substring(start, pos)
                val hit = if (exact) {
                    searchTokens.any { token == it }
                } else {
                    val tokenLow = token.lowercase()
                    searchTokens.any { tokenLow.startsWith(it) }
                }
                if (hit) out.add(token)
            }
            return out
        }

        /** True if any whitespace-bounded token in [text] matches [word]. [word] should be passed
         *  in the user-typed case (i.e. NOT pre-lowercased). The matching rule depends on
         *  [exactMatchOnly]:
         *    - off (default): case-insensitive *prefix* — "id" hits "ID"/"Identifier", skips "raid"
         *    - on: case-sensitive *whole-token equality* — "ID" hits "ID" only, "id" hits nothing
         *  Used by the carousel filter and the tab gray-out check; [extractMatches] uses the same
         *  rule when picking which tokens to highlight. */
        fun anyTokenMatches(text: String, word: String): Boolean {
            if (word.isEmpty()) return false
            if (exactMatchOnly) {
                var pos = 0
                while (pos < text.length) {
                    while (pos < text.length && text[pos].isWhitespace()) pos++
                    if (pos >= text.length) break
                    val tokenStart = pos
                    while (pos < text.length && !text[pos].isWhitespace()) pos++
                    val tokenLen = pos - tokenStart
                    if (tokenLen == word.length && text.regionMatches(tokenStart, word, 0, word.length)) return true
                }
                return false
            }
            val low = text.lowercase()
            val wordLow = word.lowercase()
            var pos = 0
            while (pos < low.length) {
                while (pos < low.length && low[pos].isWhitespace()) pos++
                if (pos >= low.length) break
                val tokenStart = pos
                while (pos < low.length && !low[pos].isWhitespace()) pos++
                val tokenLen = pos - tokenStart
                if (tokenLen >= wordLow.length && low.regionMatches(tokenStart, wordLow, 0, wordLow.length)) return true
                // pos already advanced past this token by the inner loop above
            }
            return false
        }

        /** Locate the first whole-token occurrence of [token] in [text], or -1 if not present as a
         *  whitespace-bounded token. Used to interleave bracket and search highlights in textual
         *  order — `LabelAPI.setHighlight` advances its match cursor forward only, so we have to
         *  feed it highlights sorted by their actual position in the text. */
        fun findFirstTokenPosition(text: String, token: String): Int {
            if (token.isEmpty()) return -1
            val lower = text.lowercase()
            val lowerToken = token.lowercase()
            var start = 0
            while (start <= lower.length - lowerToken.length) {
                val idx = lower.indexOf(lowerToken, start)
                if (idx < 0) return -1
                val beforeOk = idx == 0 || text[idx - 1].isWhitespace()
                val afterIdx = idx + lowerToken.length
                val afterOk = afterIdx >= text.length || text[afterIdx].isWhitespace()
                if (beforeOk && afterOk) return idx
                start = idx + 1
            }
            return -1
        }
    }



    fun init(parentPanel: CustomPanelAPI, panel: CustomPanelAPI)
    {
        // The companion `currentSearch` / `rawSearch` persist across menu opens (they are static).
        // The actual search field text box is reset to empty whenever the panel is rebuilt, so we
        // must clear the mirrors here, otherwise highlights and tab gray-out from the previous
        // session leak into this one. Same reasoning applies to `exactMatchOnly`.
        currentSearch = ""
        rawSearch = ""
        exactMatchOnly = false

        this.parentPanel = parentPanel
        this.panel = panel

        width = panel.position.width
        height = panel.position.height

        panelElement = panel.createUIElement(width, height, false)
        panelElement!!.position.inTL(0f, 0f)

        panel.addUIElement(panelElement)

        panelElement!!.addSpacer(3f)

        saveButton = LunaUIButton(false, false,width - 15, 30f,"Test", "SettingGroup", panel!!, panelElement!!).apply {
            this.buttonText!!.text = "saveButtonName".getLunaString()
            this.buttonText!!.setHighlight("Save All")
            // Center against the button's actual width/height (not the paragraph's own size,
            // which is just the bounding box of the text itself — using that put the label near
            // the bottom-left of the button instead of the middle).
            this.buttonText!!.position.inTL(this.width / 2 - this.buttonText!!.computeTextWidth(this.buttonText!!.text) / 2, this.height / 2 - this.buttonText!!.computeTextHeight(this.buttonText!!.text) / 2)
            this.buttonText!!.setHighlightColor(Misc.getHighlightColor())


            this.uiElement.addTooltipToPrevious(TooltipHelper("saveButtonTooltip".getLunaString(), 300f, "all mods", "changed data", "lost"), TooltipMakerAPI.TooltipLocation.RIGHT)

            onHover {
                backgroundAlpha = 1f
            }
            onNotHover {
                backgroundAlpha = 0.5f
            }

            onHoverEnter {
                Global.getSoundPlayer().playUISound("ui_number_scrolling", 1f, 0.8f)
            }
            onUpdate {
                var button = this as LunaUIButton
                if (LunaSettingsUISettingsPanel.unsaved)
                {
                    button.buttonText!!.setHighlightColor(Misc.getHighlightColor())
                    this.buttonText!!.setHighlight("Save All")
                }
                else
                {
                    button.buttonText!!.setHighlightColor(Misc.getBasePlayerColor())
                    this.buttonText!!.setHighlight("Save All")
                }

               /* var counter = 0
                for ((key, value) in LunaSettingsUISettingsPanel.unsavedCounter)
                {
                    if (value == 1)
                    {
                        counter++
                    }
                }

                if (counter != 0)
                {
                    button.buttonText!!.text = "saveButtonName".getLunaString() + " ($counter Unsaved)"
                    button.buttonText!!.setHighlight("($counter Unsaved)")
                    button.buttonText!!.setHighlightColors(Misc.getNegativeHighlightColor())
                    // Center against the button's actual width/height (not the paragraph's own size,
            // which is just the bounding box of the text itself — using that put the label near
            // the bottom-left of the button instead of the middle).
            this.buttonText!!.position.inTL(this.width / 2 - this.buttonText!!.computeTextWidth(this.buttonText!!.text) / 2, this.height / 2 - this.buttonText!!.computeTextHeight(this.buttonText!!.text) / 2)
                }
                else
                {
                    button.buttonText!!.text = "saveButtonName".getLunaString()
                    // Center against the button's actual width/height (not the paragraph's own size,
            // which is just the bounding box of the text itself — using that put the label near
            // the bottom-left of the button instead of the middle).
            this.buttonText!!.position.inTL(this.width / 2 - this.buttonText!!.computeTextWidth(this.buttonText!!.text) / 2, this.height / 2 - this.buttonText!!.computeTextHeight(this.buttonText!!.text) / 2)
                }*/
            }
            onClick {
                if (selectedMod == null) return@onClick

                saveDelay = 5
                saving = true

            }
        }
        saveButton!!.borderAlpha = 0.5f


        panelElement!!.addSpacer(3f)

        resetButton = LunaUIButton(false, false,width - 15, 30f,"Test", "SettingGroup", panel!!, panelElement!!).apply {
            this.buttonText!!.text = "resetButtonName".getLunaString()
            // Center against the button's actual width/height (not the paragraph's own size,
            // which is just the bounding box of the text itself — using that put the label near
            // the bottom-left of the button instead of the middle).
            this.buttonText!!.position.inTL(this.width / 2 - this.buttonText!!.computeTextWidth(this.buttonText!!.text) / 2, this.height / 2 - this.buttonText!!.computeTextHeight(this.buttonText!!.text) / 2)
            this.buttonText!!.setHighlightColor(Misc.getHighlightColor())
            this.uiElement.addTooltipToPrevious(TooltipHelper("resetButtonTooltip".getLunaString(), 300f, "selected mod"), TooltipMakerAPI.TooltipLocation.RIGHT)
        }
        resetButton!!.onHoverEnter {
            Global.getSoundPlayer().playUISound("ui_number_scrolling", 1f, 0.8f)
        }
        resetButton!!.onHover {
            backgroundAlpha = 1f
        }
        resetButton!!.onNotHover {
            backgroundAlpha = 0.5f
        }
        resetButton!!.borderAlpha = 0.5f
        resetButton!!.onClick {
            if (selectedMod == null) return@onClick

            LunaSettingsUISettingsPanel.unsaved = true
            var changed = LunaSettingsUISettingsPanel.changedSettings

            for (data in LunaSettingsLoader.SettingsData)
            {
                if (selectedMod!!.id == data.modID)
                {
                    var c = changed.filter { it.modID == data.modID }.find { it.fieldID == data.fieldID }
                    if (c != null) changed.remove(c)

                    if (data.fieldType == "Color")
                    {
                        var text = data.defaultValue.toString()
                        if (!text.contains("#"))
                        {
                            text = "#$text"
                        }
                        var color = Color.decode(text.trim().uppercase())
                        changed.add(ChangedSetting(data.modID, data.fieldID, color))
                    }
                    else
                    {
                        changed.add(ChangedSetting(data.modID, data.fieldID, data.defaultValue))
                    }
                }
            }

            for (element in LunaSettingsUISettingsPanel.addedElements)
            {
                if (element is LunaUITextField<*>)
                {
                    element.updateValue((element.key as LunaSettingsData).defaultValue)
                }
                if (element is LunaUITextFieldWithSlider<*>)
                {
                    element.updateValue((element.key as LunaSettingsData).defaultValue)
                }
                if (element is LunaUIColorPicker)
                {
                    try {
                        var text = (element.key as LunaSettingsData).defaultValue as String
                        if (!text.contains("#"))
                        {
                            text = "#$text"
                        }
                        var color = Color.decode(text.trim().uppercase())
                        element.updateValue(color, text)
                    } catch (e: Throwable) {}
                }
                if (element is LunaUIButton)
                {
                    element.value = (element.key as LunaSettingsData).defaultValue as Boolean
                }
                if (element is LunaUIKeybindButton)
                {
                    var value = (element.key as LunaSettingsData).defaultValue as Int
                    element.keycode = value

                    if (value == 0)
                    {
                        element.button!!.buttonText!!.text = "Key: None"
                        element.button!!.buttonText!!.setHighlight("None")
                    }
                    else
                    {
                        element.button!!.buttonText!!.text = "Key: ${Keyboard.getKeyName(value!!)}"
                        element.button!!.buttonText!!.setHighlight("${Keyboard.getKeyName(value!!)}")
                    }
                    element.button!!.buttonText!!.position.inTL(element.width / 2 - element.button!!.buttonText!!.computeTextWidth(element.button!!.buttonText!!.text) / 2, element.height / 2 - element.button!!.buttonText!!.computeTextHeight(element.button!!.buttonText!!.text) / 2)

                }
                if (element is LunaUIRadioButton)
                {
                    var value = (element.key as LunaSettingsData).defaultValue as String
                    element.value = value
                    for (button in element.buttons)
                    {
                        if (button.buttonText!!.text == value)
                        {
                            button.setSelected()
                        }
                    }
                }
            }
            setUnsavedData()
        }

        panelElement!!.addSpacer(3f)

        var aboutButton = LunaUIButton(false, false,width - 15, 30f,"Test", "ModsButton", panel!!, panelElement!!).apply {
            this.buttonText!!.text = "aboutButtonName".getLunaString()
            // Center against the button's actual width/height (not the paragraph's own size,
            // which is just the bounding box of the text itself — using that put the label near
            // the bottom-left of the button instead of the middle).
            this.buttonText!!.position.inTL(this.width / 2 - this.buttonText!!.computeTextWidth(this.buttonText!!.text) / 2, this.height / 2 - this.buttonText!!.computeTextHeight(this.buttonText!!.text) / 2)
            this.buttonText!!.setHighlightColor(Misc.getHighlightColor())

           /* var tooltip1 = "aboutButtonTooltip1".getLunaString()
            var tooltip2 = "aboutButtonTooltip2".getLunaString()
            var tooltip3 = "aboutButtonTooltip3".getLunaString()
            var tooltip4 = "aboutButtonTooltip4".getLunaString()
            var tooltip5 = "aboutButtonTooltip5".getLunaString()

            var tooltip = TooltipHelper(tooltip1 + tooltip2 + tooltip3 + tooltip4 + tooltip5,
                500f, "Lunalib", "not", "hotkey", "new game creation", "persist", "Starsector\\saves\\common\\LunaSettings", "not", "modify", "delete")

            this.uiElement.addTooltipToPrevious(tooltip, TooltipMakerAPI.TooltipLocation.RIGHT)*/
        }
        aboutButton!!.onHoverEnter {
            Global.getSoundPlayer().playUISound("ui_number_scrolling", 1f, 0.8f)
        }
      /*  aboutButton!!.onHover {
            backgroundAlpha = 1f
        }
        aboutButton!!.onNotHover {
            backgroundAlpha = 0.5f
        }*/
        aboutButton!!.borderAlpha = 0.5f

        if (lastSelectedMod == "LunaAboutSection")
        {
            selectedMod = null
            aboutButton.setSelected()
        }

        aboutButton.onClick {
            lastSelectedMod = "LunaAboutSection"
            selectedMod = null
            aboutButton.setSelected()
        }

        aboutButton.onUpdate {
            if (isHovering)
            {
                backgroundAlpha = 1f
            }
            else if (isSelected())
            {
                backgroundAlpha = 0.9f
            }
            else
            {
                backgroundAlpha = 0.5f
            }
        }

        panelElement!!.addSpacer(3f)

        var searchField = LunaUITextField("",0f, 0f, width - 15, 30f,"Empty", "Search", panel, panelElement!!).apply {
            onUpdate {
                var field = this as LunaUITextField<String>
                if (field.paragraph != null && isSelected() && currentSearchText != field.paragraph!!.text)
                {
                    currentSearchText = field!!.paragraph!!.text
                    rawSearch = currentSearchText
                    currentSearch = currentSearchText.lowercase()
                    // Filtering can shrink the visible mod list, so jump back to the top of the
                    // carousel. Without this the scroller can stay parked below the new end of
                    // the list and the carousel looks empty until the user manually scrolls up.
                    lastScroller = 0f
                    createModsList()
                }
            }
        }

        var pan = searchField.lunaElement!!.createUIElement(searchField.position!!.width, searchField.position!!.height, false)
        // searchField.uiElement.addComponent(pan)
        searchField.lunaElement!!.addUIElement(pan)
        pan.position.inTL(0f, 0f)
        var para = pan.addPara("searchFieldName".getLunaString(), 0f, Misc.getBasePlayerColor(), Misc.getBasePlayerColor())
        // Center the placeholder text against the search field's actual size, same fix as the
        // buttons above. The previous expression used the paragraph's own bounding-box height,
        // which pinned the label near the bottom of the field.
        para.position.inTL(searchField.width / 2 - para.computeTextWidth(para.text) / 2, searchField.height / 2 - para.computeTextHeight(para.text) / 2)
        searchField.borderAlpha = 0.5f
        searchField.run {
            // Inline TooltipCreator so we can colour each highlighted word independently. The
            // shared TooltipHelper paints every highlight in `Misc.getHighlightColor()` (yellow);
            // here we want "highlighted" drawn in the actual search-match green and "grayed out"
            // drawn in the actual gray-out colour, so the tooltip visually demos the colours it
            // describes. "prefix" stays yellow.
            this.uiElement.addTooltipToPrevious(object : TooltipMakerAPI.TooltipCreator {
                override fun isTooltipExpandable(tooltipParam: Any?): Boolean = false
                override fun getTooltipWidth(tooltipParam: Any?): Float = 300f
                override fun createTooltip(tooltip: TooltipMakerAPI?, expanded: Boolean, tooltipParam: Any?) {
                    val label = tooltip!!.addPara(
                        "searchFieldTooltip".getLunaString(), 0f,
                        Misc.getBasePlayerColor(), Misc.getHighlightColor(),
                        "prefix", "highlighted", "grayed out")
                    label.setHighlightColors(
                        Misc.getHighlightColor(),
                        getSearchHighlightColor(),
                        Misc.getGrayColor())
                }
            }, TooltipMakerAPI.TooltipLocation.RIGHT)
        }
        searchField.onHoverEnter {
            Global.getSoundPlayer().playUISound("ui_number_scrolling", 1f, 0.8f)
        }
        searchField.onUpdate {
            var button = this as LunaUITextField<String>
            button.resetParagraphIfEmpty = false
            if (button.paragraph!!.text == "" && !button.isSelected())
            {
                para.text = "searchFieldName".getLunaString()
            }
            else
            {
                para.text = ""
            }
            if (isHovering)
            {
                backgroundAlpha = 0.75f
            }
            else if (isSelected())
            {
                backgroundAlpha = 1f
            }
            else
            {
                backgroundAlpha = 0.5f
            }
        }

        panelElement!!.addSpacer(3f)

        // "Exact only" toggle. Off by default (case-insensitive prefix matching). When on, search
        // words must equal a whole whitespace-bounded token case-sensitively. Built with
        // regularButton=false so it keeps the "Exact only" label instead of auto-flipping to
        // True/False — we manage the toggle state and text color ourselves. Resets every time
        // the panel is reopened.
        val exactToggle = LunaUIButton(exactMatchOnly, false, width - 15, 30f, "ExactToggle", "SearchToggle", panel!!, panelElement!!)
        exactToggle.buttonText!!.text = "Exact only"
        exactToggle.buttonText!!.position.inTL(
            exactToggle.width / 2 - exactToggle.buttonText!!.computeTextWidth(exactToggle.buttonText!!.text) / 2,
            exactToggle.height / 2 - exactToggle.buttonText!!.computeTextHeight(exactToggle.buttonText!!.text) / 2)
        exactToggle.borderAlpha = 0.5f
        exactToggle.uiElement.addTooltipToPrevious(TooltipHelper(
            "When on, each search word must match a whole token, case-sensitively. Off (default): words act as prefixes, case-insensitive.",
            300f, "whole token", "case-sensitively", "prefixes", "case-insensitive"), TooltipMakerAPI.TooltipLocation.RIGHT)
        exactToggle.onHoverEnter { Global.getSoundPlayer().playUISound("ui_number_scrolling", 1f, 0.8f) }
        exactToggle.onClick {
            // regularButton=false means LunaUIButton doesn't auto-flip its value, so we do it.
            exactToggle.value = !exactToggle.value
            exactMatchOnly = exactToggle.value
            lastScroller = 0f
            createModsList()
        }
        exactToggle.onUpdate {
            // Text color: gray base when off, search-color (green) when on.
            exactToggle.buttonText!!.setColor(
                if (exactToggle.value) getSearchHighlightColor() else Misc.getBasePlayerColor())
            exactToggle.backgroundAlpha = when {
                exactToggle.value && exactToggle.isHovering -> 0.9f
                exactToggle.value -> 0.75f
                exactToggle.isHovering -> 0.65f
                else -> 0.5f
            }
        }

        createModsList()


        //panel.removeComponent(subpanel)
    }

    fun setUnsavedData()
    {
        var changed = LunaSettingsUISettingsPanel.changedSettings
        var data = LunaSettingsUISettingsPanel.addedElements

        for (element in data)
        {
            var settingsData = element.key as LunaSettingsData

            var c = changed.filter { it.modID == settingsData.modID }.find { it.fieldID == settingsData.fieldID }
            if (c != null) changed.remove(c)

            if (element is LunaUITextField<*>)
            {
                changed.add(ChangedSetting(settingsData.modID, settingsData.fieldID, element.value))
            }
            if (element is LunaUITextFieldWithSlider<*>)
            {
                changed.add(ChangedSetting(settingsData.modID, settingsData.fieldID, element.value))
            }
            if (element is LunaUIColorPicker)
            {
                changed.add(ChangedSetting(settingsData.modID, settingsData.fieldID, element.value))

            }
            if (element is LunaUIButton)
            {
                changed.add(ChangedSetting(settingsData.modID, settingsData.fieldID, element.value))

            }
            if (element is LunaUIKeybindButton)
            {
                changed.add(ChangedSetting(settingsData.modID, settingsData.fieldID, element.keycode))
            }
            if (element is LunaUIRadioButton)
            {
                changed.add(ChangedSetting(settingsData.modID, settingsData.fieldID, element.value))
            }

            //changed.add(ChangedSetting(settingsData.modID, settingsData.fieldID, ))
        }
    }

    fun createModsList()
    {
        if (subpanel != null)
        {
            panel!!.removeComponent(subpanel)
        }

        // 32 * amount of buttons + their spacers before + an extra gap between mods and the config buttons
        // Five buttons now: Save All, Reset, About, Search field, Exact-match toggle.
        var space = (32f * 5f) + 10f

        subpanel = panel!!.createCustomPanel(width - 9, height - space, null)
        subpanel!!.position.inTL(0f, space)
        panel!!.addComponent(subpanel)
        subpanelElement = subpanel!!.createUIElement(width - 9, height - space, true)

        subpanelElement!!.position.inTL(0f, 0f)
        //subpanelElement!!.addSpacer(5f)

        val modsWithData = LunaSettingsLoader.SettingsData.map { it.modID }.distinct()
        val mods: List<ModSpecAPI> = Global.getSettings().modManager.enabledModsCopy.filter { modsWithData.contains( it.id) }

        var spacing = 0f
        for (mod in mods)
        {
            // AND across whitespace-separated search words, OR across the mod's fields. Each word
            // must match some token in the mod's id/name or in any of its settings'
            // name/id/description/tab — by prefix (default) or by exact case-sensitive equality
            // (when the toggle is on). [anyTokenMatches] handles the mode switch internally, so
            // we feed it the search words in the user-typed case.
            val search = currentSearchText
            if (search.isNotEmpty()) {
                val searchWords = search.split(Regex("\\s+")).filter { it.isNotEmpty() }
                val modSettings = LunaSettingsLoader.SettingsData.filter { it.modID == mod.id }
                // Visible-text haystack: what the user sees in the UI. In Exact mode this is the
                // ONLY thing we match against — internal identifiers (`mod.id`, `fieldID`) are
                // dropped, because matching them would otherwise let a lowercase mod id like
                // "nexerelin" sneak through case-sensitive search against the displayed
                // "Nexerelin". In default prefix mode we keep IDs in the haystack so power users
                // can still grep for them.
                val haystack = mutableListOf<String>()
                haystack.add(mod.name)
                if (!exactMatchOnly) haystack.add(mod.id)
                for (s in modSettings) {
                    haystack.add(s.fieldName)
                    if (!exactMatchOnly) haystack.add(s.fieldID)
                    haystack.add(s.fieldDescription)
                    haystack.add(s.tab)
                }
                if (searchWords.any { word -> haystack.none { anyTokenMatches(it, word) } }) continue
            }


            var cardPanel = LunaUIPlaceholder(true, width - 15 , 60f, mod, "ModsButton", subpanel!!, subpanelElement!!).apply {

                var text = "${mod.name}\n" +
                        "Version: ${mod.version}\n" +
                        "Author: ${mod.author}\n" +
                        "\n" +
                        "${mod.desc}"

                this.uiElement.addTooltipToPrevious(TooltipHelper(text, 400f, "${mod.name}"), TooltipMakerAPI.TooltipLocation.RIGHT)

                this.backgroundAlpha = 0.9f

                if (selectedMod == mod) {
                    this.darkColor = Misc.getDarkPlayerColor().brighter().brighter()
                    this.setSelected()
                }
                else {
                    this.darkColor = Misc.getDarkPlayerColor()
                }
                this.borderAlpha = 0.75f

                onClick {
                    this.setSelected()
                    LunaSettingsUISettingsPanel.lastSelectedTab = ""
                    LunaSettingsUISettingsPanel.lastScroller = 0f
                    Global.getSoundPlayer().playUISound("ui_button_pressed", 1f, 1f)
                }

                onUpdate {
                    if (this.isSelected())
                    {
                        this.darkColor = Misc.getDarkPlayerColor().brighter().brighter()

                    }
                    else
                    {
                        this.darkColor = Misc.getDarkPlayerColor()
                    }
                }

                onHover {
                    borderAlpha = 1f
                }
                onNotHover {
                    borderAlpha = 0.75f
                }

                onHoverEnter {
                    Global.getSoundPlayer().playUISound("ui_number_scrolling", 1f, 0.8f)
                }

                onSelect {
                    if (!saving) setUnsavedData()
                    selectedMod = mod
                    lastSelectedMod = mod.id
                }
            }

            var spriteElement = cardPanel.lunaElement!!.createUIElement(width - 15, 60f, false)
            spriteElement.position.inTL(0f,0f)

            cardPanel.lunaElement!!.addUIElement(spriteElement)

            var icon = "graphics/icons/default_mod_icon.png"
            var potentialIcon = LunaSettingsConfigLoader.getIconPath(mod.id)
            if (potentialIcon != "") icon = potentialIcon

            // Backward compatibility: if icon is a static image and there is a matching .gif file, use the .gif instead!
            if (!icon.lowercase().endsWith(".gif")) {
                val gifPath = icon.substringBeforeLast(".") + ".gif"
                try {
                    val stream = Global.getSettings().openStream(gifPath)
                    stream.close()
                    icon = gifPath
                } catch (e: Throwable) {}
            }

            // Only call loadTexture for static images; GIF is handled by GifAnimationManager.
            if (!icon.lowercase().endsWith(".gif")) {
                Global.getSettings().loadTexture(icon)
            }

            var sprite = LunaUIAnimatedSprite(icon,
                40f,  // maxX — GIFs and large images cap here
                40f,  // maxY
                40f,  // minX — small icons get upscaled to 40x40
                40f,  // minY
                40f,  // desired width
                40f,  // desired height
                "",
                "Group",
                cardPanel.lunaElement!!,
                spriteElement!!)
            sprite.position!!.inTL(5f, spriteElement.position!!.height / 2 - sprite.height / 2)

            var paragraphElement = cardPanel.lunaElement!!.createUIElement(width - 70, 60f, false)
            paragraphElement.position.inTL(55f,0f)

            cardPanel.lunaElement!!.addUIElement(paragraphElement)

            var text = mod.name
            // Per-word match: each search word is highlighted independently in the mod name
            // (so "fleet build" highlights "Fleet" AND "Build" inside "FleetBuilder").
            // If the search matched via settings (not via mod name itself), the whole name stays
            // highlighted as before so the mod card still pops visually.
            val matches = if (currentSearchText.isNotEmpty()) extractMatches(text, currentSearchText) else emptyList()
            val nameHighlights = if (matches.isNotEmpty()) matches.toTypedArray() else arrayOf(text)
            // Search matches in green; the legacy whole-name highlight stays yellow.
            val nameHighlightColor = if (matches.isNotEmpty()) getSearchHighlightColor() else Misc.getHighlightColor()
            var para = paragraphElement.addPara(text, 0f, Misc.getBasePlayerColor(), nameHighlightColor, *nameHighlights)
            para.position.inTL(0f, (30f - para.position.height / 2))

            subpanelElement!!.addSpacer(5f)

            spacing += cardPanel.position!!.height

            if (lastSelectedMod == mod.id)
            {
                cardPanel.setSelected()
                cardPanel.backgroundAlpha = 1f
                selectedMod = mod

            }
            else if (lastSelectedMod == "")
            {
                lastSelectedMod = mod.id
                selectedMod = mod
                cardPanel.setSelected()
            }
        }

        subpanel!!.addUIElement(subpanelElement)
        subpanelElement!!.externalScroller.yOffset = lastScroller
    }

    override fun positionChanged(position: PositionAPI?) {
        // When the settings panel is closed (position becomes null), free GIF GL textures.
        if (position == null) GifAnimationManager.clearAll()
    }

    override fun renderBelow(alphaMult: Float) {

    }

    override fun render(alphaMult: Float) {
    }

    override fun advance(amount: Float) {
        if (saving)
        {
            saveDelay--
            if (saveDelay < 1)
            {
                if (selectedMod == null) return
                setUnsavedData()
                LunaSettingsUISettingsPanel.unsaved = false
                LunaSettingsUISettingsPanel.unsavedCounter.clear()

                var changed = LunaSettingsUISettingsPanel.changedSettings
                var changedMods = changed.map { it.modID }.distinct()

                for (mod in changedMods)
                {
                    val data = JSONUtils.loadCommonJSON("LunaSettings/${mod}.json", "data/config/LunaSettingsDefault.default");

                    var changedFields = changed.filter { it.modID == mod }
                    for (field in changedFields)
                    {
                        if (field.data is Color)
                        {
                            var color = field.data as Color
                            var hex = String.format("#%02x%02x%02x", color!!.red, color!!.green, color.blue);
                            data.put(field.fieldID, hex)
                        }
                        else
                        {
                            data.put(field.fieldID, field.data)
                        }

                       /* var setting = (element.key as LunaSettingsData)
                        if (element is LunaUITextField<*>)
                        {
                            data.put(setting.fieldID, element.value)
                        }
                        if (element is LunaUITextFieldWithSlider<*>)
                        {
                            data.put(setting.fieldID, element.value)
                        }
                        if (element is LunaUIColorPicker)
                        {
                            var color = element.value
                            var hex = String.format("#%02x%02x%02x", color!!.red, color!!.green, color.blue);
                            data.put(setting.fieldID, hex)
                        }
                        if (element is LunaUIButton)
                        {
                            data.put(setting.fieldID, element.value)
                        }
                        if (element is LunaUIKeybindButton)
                        {
                            data.put(setting.fieldID, element.keycode)
                        }*/
                    }
                    data.save()
                    LunaSettingsLoader.Settings.put(mod, data)

                    if (!newGame) LunaSettings.reportSettingsChanged(mod)

                }


                saving = false
            }
        }

        if (subpanelElement != null)
        {
            if (subpanelElement!!.externalScroller != null)
            {
                lastScroller = subpanelElement!!.externalScroller.yOffset
            }
        }
    }

    override fun processInput(events: MutableList<InputEventAPI>) {


    }

    override fun buttonPressed(buttonId: Any?) {

    }

    private fun String.trimAfter(cap: Int, addText: Boolean = true) : String
    {
        return if (this.length <= cap)
        {
            this
        }
        else
        {
            var text = ""
            if (addText) text = "..."
            this.substring(0, cap).trim() + "..."
        }
    }
}