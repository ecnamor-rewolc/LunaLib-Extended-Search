package lunalib.backend.ui.components.base

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.PositionAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI
import lunalib.backend.util.GifAnimationManager
import org.lwjgl.opengl.GL11

/**
 * Drop-in replacement for LunaUISprite that transparently supports animated .gif files.
 *
 * - If [spritePath] ends with ".gif", the GIF is decoded via GifDecoder and rendered
 *   frame-by-frame using raw GL11 textured quads.
 * - Otherwise, delegates to the same logic as the original LunaUISprite (SpriteAPI).
 *
 * Usage is identical to LunaUISprite — just swap the class name.
 */
class LunaUIAnimatedSprite(
    var spritePath: String,
    var maxX: Float,
    var maxY: Float,
    minX: Float,
    minY: Float,
    width: Float,
    height: Float,
    key: Any,
    group: String,
    panel: CustomPanelAPI,
    uiElement: TooltipMakerAPI
) : LunaUIBaseElement(width, height, key, group, panel, uiElement) {

    private val isGif = spritePath.lowercase().endsWith(".gif")

    // Static sprite path (non-gif)
    private val sprite = if (!isGif) Global.getSettings().getSprite(spritePath) else null

    // Dimensions used for rendering
    var textureWidth = 0f
    var textureHeight = 0f

    init {
        if (isGif) {
            textureWidth = width.coerceIn(minX, maxX)
            textureHeight = height.coerceIn(minY, maxY)
        } else if (sprite != null) {
            textureWidth = sprite.width ?: 0f
            textureHeight = sprite.height ?: 0f
            if (textureWidth > maxX) textureWidth = maxX
            if (textureHeight > maxY) textureHeight = maxY
            if (textureWidth < minX) textureWidth = minX
            if (textureHeight < minY) textureHeight = minY
            sprite.setSize(textureWidth, textureHeight)
        }
        this.width = textureWidth
        this.height = textureHeight
        position?.setSize(textureWidth, textureHeight)
    }

    override fun positionChanged(position: PositionAPI) {
        super.positionChanged(position)
        if (this.position != null) {
            if (!isGif && sprite != null) {
                sprite.setSize(textureWidth, textureHeight)
                position.setSize(textureWidth, textureHeight)
            } else if (isGif) {
                position.setSize(textureWidth, textureHeight)
            }
        }
    }

    override fun renderBelow(alphaMult: Float) {}

    override fun render(alphaMult: Float) {
        if (position == null) return

        if (isGif) {
            renderGif(alphaMult)
        } else if (sprite != null) {
            sprite.alphaMult = 1f
            sprite.render(posX, posY)
            sprite.setSize(textureWidth, textureHeight)
            position!!.setSize(textureWidth, textureHeight)
        }
    }

    private fun renderGif(alphaMult: Float) {
        val anim = GifAnimationManager.get(spritePath) ?: return
        val texId = anim.currentTextureId()
        if (texId == 0) return

        val x = posX
        val y = posY
        val w = textureWidth
        val h = textureHeight

        GL11.glPushMatrix()
        GL11.glEnable(GL11.GL_TEXTURE_2D)
        GL11.glEnable(GL11.GL_BLEND)
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
        GL11.glColor4f(1f, 1f, 1f, alphaMult)

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texId)

        GL11.glBegin(GL11.GL_QUADS)
        GL11.glTexCoord2f(0f, 1f); GL11.glVertex2f(x, y)
        GL11.glTexCoord2f(1f, 1f); GL11.glVertex2f(x + w, y)
        GL11.glTexCoord2f(1f, 0f); GL11.glVertex2f(x + w, y + h)
        GL11.glTexCoord2f(0f, 0f); GL11.glVertex2f(x, y + h)
        GL11.glEnd()

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0)
        GL11.glDisable(GL11.GL_TEXTURE_2D)
        GL11.glPopMatrix()
    }
}
