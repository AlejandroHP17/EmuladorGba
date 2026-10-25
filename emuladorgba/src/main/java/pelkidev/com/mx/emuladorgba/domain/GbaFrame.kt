package pelkidev.com.mx.emuladorgba.domain

class GbaFrame(pixels: IntArray) {
    val pixels: IntArray = pixels.copyOf()

    init {
        require(pixels.size == WIDTH * HEIGHT) {
            "Un cuadro mide $WIDTH×$HEIGHT"
        }
    }

    fun pixel(x: Int, y: Int): Int = pixels[y * WIDTH + x]

    companion object {
        const val WIDTH = 240
        const val HEIGHT = 160
    }
}

private const val STRIPE_HEIGHT = 8
private const val STRIPE_DARK = 0xFF000000.toInt()
private const val STRIPE_LIGHT = 0xFFFFFFFF.toInt()

fun stripeFrame(): GbaFrame {
    val pixels = IntArray(GbaFrame.WIDTH * GbaFrame.HEIGHT)
    for (y in 0 until GbaFrame.HEIGHT) {
        val color = if ((y / STRIPE_HEIGHT) % 2 == 0) STRIPE_DARK else STRIPE_LIGHT
        pixels.fill(color, y * GbaFrame.WIDTH, (y + 1) * GbaFrame.WIDTH)
    }
    return GbaFrame(pixels)
}
