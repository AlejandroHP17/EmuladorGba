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
