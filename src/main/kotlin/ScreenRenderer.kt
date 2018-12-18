import util.ColorUtil
import java.awt.image.BufferedImage
import java.awt.image.BufferedImage.TYPE_INT_ARGB_PRE
import java.awt.image.DataBufferInt
import java.awt.image.WritableRaster

class ScreenRenderer {

    private val clip: Int = 8
    private val frameWidth: Int = 256
    private val frameHeight: Int = 240 - 2 * clip
    private val images = Array(4) { BufferedImage(frameWidth, frameHeight, TYPE_INT_ARGB_PRE) }
    private var imageControl: Int = 0

    fun render(pixels: IntArray): BufferedImage {
        for (i in pixels.indices) {
            pixels[i] = ColorUtil.colorPalette[(pixels[i] and 0x1c0) shr 6][pixels[i] and 0x3f]
        }
        return getBufferedImage(pixels)
    }

    private fun getBufferedImage(frame: IntArray): BufferedImage {
        val image: BufferedImage = images[++imageControl % images.size]
        val raster: WritableRaster = image.raster
        val pixels: IntArray = (raster.dataBuffer as DataBufferInt).data
        System.arraycopy(frame, frameWidth * clip, pixels, 0, frameWidth * frameHeight)
        return image
    }
}