package lunalib.backend.util

import com.fs.starfarer.api.Global
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import java.awt.image.BufferedImage
import java.io.InputStream

/**
 * Loads a .gif from disk (path relative to the game install root) and manages
 * per-frame OpenGL texture IDs for animated rendering.
 *
 * Thread-safety: must only be used on the GL/render thread.
 *
 * Usage:
 *   val anim = GifAnimationManager.get("graphics/icons/my_mod.gif") ?: return
 *   GL11.glBindTexture(GL11.GL_TEXTURE_2D, anim.currentTextureId())
 */
class GifAnimationManager private constructor(
    val frameTextureIds: IntArray,
    val frameDelays: IntArray   // milliseconds per frame
) {
    private val startMs: Long = System.currentTimeMillis()

    /** Returns the GL texture ID for the frame that should be shown right now. */
    fun currentTextureId(): Int {
        if (frameTextureIds.isEmpty()) return 0
        val total = frameDelays.sum()
        if (total <= 0) return frameTextureIds[0]
        val elapsed = ((System.currentTimeMillis() - startMs) % total).toInt()
        var acc = 0
        for (i in frameDelays.indices) {
            acc += frameDelays[i]
            if (elapsed < acc) return frameTextureIds[i]
        }
        return frameTextureIds.last()
    }

    companion object {
        private val cache = HashMap<String, GifAnimationManager>()

        /**
         * Returns a cached GifAnimationManager for the given path (relative to game root).
         * On first call, decodes the GIF and uploads all frames as GL textures.
         * Returns null if the file cannot be decoded.
         */
        fun get(relativePath: String): GifAnimationManager? {
            cache[relativePath]?.let { return it }

            // Use the game's own stream API — FileInputStream is blocked by the script sandbox.
            val stream: InputStream = try {
                Global.getSettings().openStream(relativePath)
            } catch (e: Exception) { return null }

            val decoder = GifDecoder()
            val status = decoder.read(stream)
            if (status != GifDecoder.STATUS_OK || decoder.frameCount == 0) return null

            val count = decoder.frameCount
            val texIds = IntArray(count)
            val delays = IntArray(count)

            // glGenTextures expects an IntBuffer
            val texBuf = BufferUtils.createIntBuffer(count)
            GL11.glGenTextures(texBuf)
            texBuf.get(texIds)

            for (i in 0 until count) {
                val img: BufferedImage = decoder.getFrame(i) ?: continue
                val delay = decoder.getDelay(i).let { if (it > 0) it else 100 }
                delays[i] = delay

                val w = img.width
                val h = img.height
                val pixels = IntArray(w * h)
                img.getRGB(0, 0, w, h, pixels, 0, w)

                // Convert ARGB int[] -> RGBA ByteBuffer for OpenGL
                val buf = BufferUtils.createByteBuffer(w * h * 4)
                for (px in pixels) {
                    buf.put(((px shr 16) and 0xFF).toByte()) // R
                    buf.put(((px shr 8)  and 0xFF).toByte()) // G
                    buf.put((px          and 0xFF).toByte()) // B
                    buf.put(((px shr 24) and 0xFF).toByte()) // A
                }
                buf.flip()

                GL11.glBindTexture(GL11.GL_TEXTURE_2D, texIds[i])
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR)
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR)
                GL11.glTexImage2D(
                    GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, w, h, 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf
                )
            }

            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0)

            val anim = GifAnimationManager(texIds, delays)
            cache[relativePath] = anim
            return anim
        }

        /** Call when the settings panel is closed to free GPU memory. */
        fun clearAll() {
            for (anim in cache.values) {
                val buf = BufferUtils.createIntBuffer(anim.frameTextureIds.size)
                buf.put(anim.frameTextureIds)
                buf.flip()
                GL11.glDeleteTextures(buf)
            }
            cache.clear()
        }
    }
}
