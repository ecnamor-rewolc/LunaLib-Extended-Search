package lunalib.backend.ui.components.base

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.LabelAPI
import com.fs.starfarer.api.ui.PositionAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.IntervalUtil
import com.fs.starfarer.api.util.Misc
import org.lwjgl.input.Keyboard
import org.lwjgl.opengl.GL11
import org.lwjgl.util.vector.Vector2f
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.util.regex.Pattern


enum class Filters {
    None, Double, Int
}

class LunaUITextField<T>(var value: T, var minValue: Float, var maxValue: Float, width: Float, height: Float, key: Any, group: String, panel: CustomPanelAPI, uiElement: TooltipMakerAPI) : LunaUIBaseElement(width, height, key, group, panel, uiElement) {

    var borderColor = Misc.getDarkPlayerColor()
    var slider: LunaUISlider<T>? = null
    var paragraph: LabelAPI? = null
    var resetParagraphIfEmpty = true
    private var default = value
    private var init = false

    var fakeParagraph: LabelAPI? = null
    var blinkInterval = IntervalUtil(1f, 1f)
    var blink = false

    init {
        onClick {
            if (it.eventValue == 0)
            {
                setSelected()
                Global.getSoundPlayer().playUISound("ui_button_pressed", 1f, 1f)
            }
        }
        onClickOutside {
            unselect()
        }

        onHoverEnter {
            Global.getSoundPlayer().playUISound("ui_number_scrolling", 1f, 0.8f)
        }
        onHover {
            darkColor = Misc.getDarkPlayerColor().brighter()
        }
        onNotHover {
            darkColor = Misc.getDarkPlayerColor()
        }
        onHeld {
            baseColor = Misc.getBasePlayerColor().brighter()
        }
        onNotHeld {
            baseColor = Misc.getBasePlayerColor()
        }
        onUpdate {
            if (isSelected())
            {
                borderColor = Misc.getDarkPlayerColor().brighter()
            }
            else
            {
                borderColor = Misc.getDarkPlayerColor()
            }
        }
        onSelect {

        }
    }

    fun updateValue(newValue: Any)
    {
        value = newValue as T
        paragraph!!.text = (newValue as T).toString()
        fakeParagraph!!.text = (newValue as T).toString()
    }

    override fun positionChanged(position: PositionAPI) {
        super.positionChanged(position)

        if (paragraph == null && lunaElement != null)
        {
            var pan = lunaElement!!.createUIElement(width, height, false)
            //uiElement.addComponent(pan)
            lunaElement!!.addUIElement(pan)
            pan.position.inTL(0f, 0f)
            paragraph = pan.addPara("", 1f, Misc.getBasePlayerColor(), Misc.getBasePlayerColor())
            fakeParagraph = pan.addPara("", 1f, Misc.getBasePlayerColor(), Misc.getBasePlayerColor())
        }

        if (paragraph != null)
        {
            paragraph!!.position.inTL(5f, height / 2 - paragraph!!.position.height / 2)
            fakeParagraph!!.position.inTL(5f, height / 2 - fakeParagraph!!.position.height / 2)
        }
    }



    override fun renderBelow(alphaMult: Float) {
        if (position != null)
        {
            createGLRectangle(darkColor, alphaMult)  {
                GL11.glRectf(posX, posY, posX + width, posY + height)
            }

        }
    }


    override fun render(alphaMult: Float) {
        if (position != null)
        {
            createGLLines(borderColor, alphaMult) {

                GL11.glVertex2f(posX, posY)
                GL11.glVertex2f(posX, posY + height)
                GL11.glVertex2f(posX + width, posY + height)
                GL11.glVertex2f(posX + width, posY)
                GL11.glVertex2f(posX, posY)
            }
        }
    }

    override fun advance(amount: Float) {
        super.advance(amount)

        blinkInterval.advance(amount)
        if (!isSelected()) {
            blinkInterval.forceIntervalElapsed()
            blink = false
        }
        if (blinkInterval.intervalElapsed())
        {
            blink = !blink
        }

        if (paragraph != null)
        {
            if (!isSelected())
            {
                if (paragraph!!.text == "" && resetParagraphIfEmpty)
                {
                    paragraph!!.text = value.toString()
                }
            }
            if (value is Int && !isSelected())
            {
                try {
                    if (value as Int > maxValue)
                    {
                        paragraph!!.text = maxValue.toInt().toString()
                    }
                    if (minValue > value as Int)
                    {
                        paragraph!!.text = minValue.toInt().toString()
                    }
                    value = paragraph!!.text.toInt() as T
                } catch (e: Throwable) {}
            }
            else if (value is Float && !isSelected())
            {
                try {
                    if (value as Float - 0.000001f > maxValue)
                    {
                        paragraph!!.text = maxValue.toString()
                    }
                    if (minValue > value as Float + 0.000001f)
                    {
                        paragraph!!.text = minValue.toString()
                    }
                    value = paragraph!!.text.toFloat() as T
                } catch (e: Throwable) {}
            }
            else if (value is Number && !isSelected())
            {
                try {
                    if (value as Double - 0.000001f > maxValue)
                    {
                        paragraph!!.text = maxValue.toString()
                    }
                    if (minValue > value as Double + 0.000001f)
                    {
                        paragraph!!.text = minValue.toString()
                    }
                    value = paragraph!!.text.toDouble() as T
                } catch (e: Throwable) {}
            }
        }


    }

    var cooldown = 10f
    // Wall-clock throttle for character deletion. Frame-based cooldown is framerate-dependent
    // (8 frames at 180 FPS is ~44 ms — short enough that a single physical tap can produce
    // multiple deletions). Tracking the last delete time in milliseconds gives a stable feel
    // across any FPS. The OS keyboard repeat already provides the initial delay before a
    // held key starts repeating, so a flat ~100 ms minimum gap between deletions is enough:
    // single taps always delete one character, holding deletes ~10 chars/sec.
    private var lastBackspaceMs: Long = 0L

    val Digits = "(\\p{Digit}+)"
    val HexDigits = "(\\p{XDigit}+)"

    val DOUBLE_PATTERN =
        Pattern.compile("[\\x00-\\x20]*[+-]?(NaN|Infinity|((((\\p{Digit}+)(\\.)?((\\p{Digit}+)?)" + "([eE][+-]?(\\p{Digit}+))?)|(\\.((\\p{Digit}+))([eE][+-]?(\\p{Digit}+))?)|" + "(((0[xX](\\p{XDigit}+)(\\.)?)|(0[xX](\\p{XDigit}+)?(\\.)(\\p{XDigit}+)))" + "[pP][+-]?(\\p{Digit}+)))[fFdD]?))[\\x00-\\x20]*")

    override fun processInput(events: MutableList<InputEventAPI>) {
        super.processInput(events)


        if (paragraph != null)
        {
           //paragraph!!.text = paragraph!!.text.replace("_", "")
        }

        if (cooldown > 0)
        {
            cooldown--
            return

        }
        if (isSelected() && paragraph != null)
        {
            for (event in events) {
                if (cooldown > 0) continue
                if (event.isConsumed) continue
                if (event.eventValue == Keyboard.KEY_ESCAPE || event.eventValue == 28 || event.eventValue == 156)
                {
                    unselect()
                    event.consume()
                    Global.getSoundPlayer().playUISound("ui_typer_buzz", 1f, 1f)
                    continue
                }
                if (event.eventValue == Keyboard.KEY_LSHIFT) continue
                if (event.eventValue == Keyboard.KEY_X && Keyboard.isKeyDown(Keyboard.KEY_LCONTROL))
                {
                    paragraph!!.text = ""
                    Global.getSoundPlayer().playUISound("ui_target_reticle", 1f, 1f)
                    event.consume()
                    continue
                }
                if (event.eventValue == Keyboard.KEY_C && Keyboard.isKeyDown(Keyboard.KEY_LCONTROL))
                {
                    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(paragraph!!.text), StringSelection(""))
                    Global.getSoundPlayer().playUISound("ui_create_waypoint", 1f, 1f)
                    event.consume()
                    continue
                }
                if (event.eventValue == Keyboard.KEY_V && Keyboard.isKeyDown(Keyboard.KEY_LCONTROL))
                {
                    try {
                        paragraph!!.text = Toolkit.getDefaultToolkit().systemClipboard.getData(DataFlavor.stringFlavor) as String
                    }
                    catch (e :Throwable) {}
                    Global.getSoundPlayer().playUISound("ui_create_waypoint", 1f, 1f)
                    event.consume()
                    continue
                }
                if (event.isModifierKey) continue
                // Delete behaves the same as Backspace here — the text field has no caret, so the
                // user-mental model is just "remove a character," and listing both keys means
                // people who reach for Delete by reflex aren't left stuck.
                if (event.eventValue == Keyboard.KEY_BACK || event.eventValue == Keyboard.KEY_DELETE)
                {
                    if (event.isKeyboardEvent)
                    {
                        val now = System.currentTimeMillis()
                        if (now - lastBackspaceMs >= 150L) {
                            if (paragraph!!.text.isNotEmpty())
                            {
                                paragraph!!.text = paragraph!!.text.substring(0, paragraph!!.text.length - 1)
                                Global.getSoundPlayer().playUISound("ui_typer_type", 1f, 1f)
                            }
                            lastBackspaceMs = now
                        }
                        event.consume()
                        continue
                    }
                }
                if (event.eventValue == 15) continue
                if (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL)) continue
                if (event.isKeyboardEvent && !event.isKeyUpEvent)
                {
                    if (paragraph!!.computeTextWidth(paragraph!!.text) > (width - 20) - paragraph!!.computeTextWidth("_"))
                    {
                        Global.getSoundPlayer().playUISound("ui_typer_buzz", 1f, 1f)
                        continue
                    }
                    cooldown = 1f
                    var char = event.eventChar
                    // Universal "is this a printable character" gate. Caps Lock, Scroll Lock, the
                    // Print Screen / Pause / Menu keys, F1-F12, Num Lock, Insert, Home, End,
                    // PgUp/PgDn, the arrow keys, etc. all hand us either NUL (0) or another ASCII
                    // control code, which would otherwise get appended to the text as a junk
                    // character. Anything below ASCII 32 (control range) or exactly 127 (DEL) is
                    // by definition not a typeable character, so just drop it. Real letters,
                    // digits, symbols, and the space bar all sit at >= 32, so legitimate typing
                    // is unaffected.
                    if (char.code < 32 || char.code == 127) continue
                    if (char != '&' && char != '%')
                    {
                        if (value is String)
                        {
                            paragraph!!.text += char
                            event.consume()
                            Global.getSoundPlayer().playUISound("ui_typer_type", 1f, 1f)
                            value = paragraph!!.text.toString() as T
                            break
                        }
                        else if (value is Int)
                        {
                            event.consume()
                            if (char == '_') continue
                            if (char == '-' && paragraph!!.text.isNotEmpty())
                            {
                                Global.getSoundPlayer().playUISound("ui_typer_buzz", 1f, 1f)
                                break
                            }
                            paragraph!!.text += char
                            if (paragraph!!.text.contains("[^0-9-]".toRegex()))
                            {
                                paragraph!!.text = paragraph!!.text.replace("[^0-9-]".toRegex(), "")
                                Global.getSoundPlayer().playUISound("ui_typer_buzz", 1f, 1f)
                            }
                            else
                            {
                                Global.getSoundPlayer().playUISound("ui_typer_type", 1f, 1f)
                            }
                            break
                        }

                        else if (value is Number)
                        {
                            if (DOUBLE_PATTERN.matcher(paragraph!!.text + char).matches() || char == '-' && paragraph!!.text.isEmpty())
                            {
                                paragraph!!.text += char
                                paragraph!!.text = paragraph!!.text.replace("[^0-9.-]".toRegex(), "")
                                event.consume()
                                Global.getSoundPlayer().playUISound("ui_typer_type", 1f, 1f)

                                break
                            }
                            else
                            {
                                event.consume()
                                Global.getSoundPlayer().playUISound("ui_typer_buzz", 1f, 1f)
                                break
                            }
                        }
                    }
                }
            }
        }
        if (paragraph != null && fakeParagraph != null)
        {
            if (isSelected() && blink)
            {
                fakeParagraph!!.text = paragraph!!.text + "_"
            }
            else
            {
                fakeParagraph!!.text = paragraph!!.text + ""

            }

           /* var tempText = paragraph!!.text
            if (isSelected())
            {
                tempText += "_"
            }
            paragraph!!.text = tempText*/
        }
    }
}