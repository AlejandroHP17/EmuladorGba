package pelkidev.com.mx.emuladorgba.data

import pelkidev.com.mx.emuladorgba.domain.GbaButton
import pelkidev.com.mx.emuladorgba.domain.GbaFrame

interface GbaCore {
    fun step()
    fun frame(): GbaFrame
    fun samples(): ByteArray
    fun hold(buttons: Set<GbaButton>)
    fun close()
}

class MgbaCore(rom: ByteArray) : GbaCore {
    private val pixels = IntArray(GbaFrame.WIDTH * GbaFrame.HEIGHT) { BLACK }
    private var handle: Long = nativeCreate(rom)

    @Volatile
    private var keys = 0

    init {
        check(handle != 0L) { "mGBA no pudo cargar el ROM" }
    }

    override fun step() {
        if (handle == 0L) return
        nativeRunFrame(handle, pixels, keys)
    }

    override fun frame(): GbaFrame = GbaFrame(pixels)

    override fun samples(): ByteArray = byteArrayOf()

    override fun hold(buttons: Set<GbaButton>) {
        var mask = 0
        for (button in buttons) mask = mask or (1 shl button.ordinal)
        keys = mask
    }

    override fun close() {
        if (handle == 0L) return
        nativeDestroy(handle)
        handle = 0L
    }

    private external fun nativeCreate(rom: ByteArray): Long
    private external fun nativeRunFrame(handle: Long, out: IntArray, keys: Int)
    private external fun nativeDestroy(handle: Long)

    private companion object {
        const val BLACK = 0xFF000000.toInt()

        init {
            System.loadLibrary("emuladorgba_mgba")
        }
    }
}
