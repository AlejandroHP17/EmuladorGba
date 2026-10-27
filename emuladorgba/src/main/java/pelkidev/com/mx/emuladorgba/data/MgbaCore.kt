package pelkidev.com.mx.emuladorgba.data

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import pelkidev.com.mx.emuladorgba.domain.GbaButton
import pelkidev.com.mx.emuladorgba.domain.GbaFrame

interface GbaCore {
    fun step()
    fun frame(): GbaFrame
    fun samples(): ByteArray
    fun hold(buttons: Set<GbaButton>)
    fun pause() {}
    fun resume() {}
    fun exportState(): ByteArray = byteArrayOf()
    fun importState(state: ByteArray): Boolean = false
    fun exportCartridge(): ByteArray = byteArrayOf()
    fun importCartridge(savedata: ByteArray): Boolean = false
    fun close()
}

class MgbaCore(rom: ByteArray) : GbaCore {
    private val pixels = IntArray(GbaFrame.WIDTH * GbaFrame.HEIGHT) { BLACK }
    private val pcm = ShortArray(AUDIO_BUFFER_SAMPLES * 2)
    private var handle: Long = nativeCreate(rom)
    private var track = openTrack()

    @Volatile
    private var keys = 0
    private var produced = 0

    init {
        check(handle != 0L) { "mGBA no pudo cargar el ROM" }
    }

    override fun step() {
        if (handle == 0L) return
        produced = nativeRunFrame(handle, pixels, keys, pcm)
        playProduced()
    }

    override fun frame(): GbaFrame = GbaFrame(pixels)

    override fun samples(): ByteArray = byteArrayOf()

    private fun playProduced() {
        val track = track ?: return
        if (produced <= 0) return
        if (track.playState != AudioTrack.PLAYSTATE_PLAYING) track.play()
        track.write(pcm, 0, produced * 2, AudioTrack.WRITE_BLOCKING)
    }

    override fun pause() {
        track?.let { playing ->
            if (playing.playState == AudioTrack.PLAYSTATE_PLAYING) playing.pause()
            playing.flush()
        }
    }

    override fun resume() = Unit

    override fun hold(buttons: Set<GbaButton>) {
        var mask = 0
        for (button in buttons) mask = mask or (1 shl button.ordinal)
        keys = mask
    }

    override fun exportState(): ByteArray {
        if (handle == 0L) return byteArrayOf()
        return nativeExportState(handle) ?: byteArrayOf()
    }

    override fun importState(state: ByteArray): Boolean {
        if (handle == 0L || state.isEmpty()) return false
        return nativeImportState(handle, state)
    }

    override fun exportCartridge(): ByteArray {
        if (handle == 0L) return byteArrayOf()
        return nativeExportCartridge(handle) ?: byteArrayOf()
    }

    override fun importCartridge(savedata: ByteArray): Boolean {
        if (handle == 0L || savedata.isEmpty()) return false
        return nativeImportCartridge(handle, savedata)
    }

    override fun close() {
        track?.let { playing ->
            if (playing.playState == AudioTrack.PLAYSTATE_PLAYING) playing.pause()
            playing.flush()
            playing.release()
        }
        track = null
        if (handle == 0L) return
        nativeDestroy(handle)
        handle = 0L
    }

    private external fun nativeCreate(rom: ByteArray): Long
    private external fun nativeRunFrame(handle: Long, out: IntArray, keys: Int, audioOut: ShortArray): Int
    private external fun nativeExportCartridge(handle: Long): ByteArray?
    private external fun nativeImportCartridge(handle: Long, savedata: ByteArray): Boolean
    private external fun nativeExportState(handle: Long): ByteArray?
    private external fun nativeImportState(handle: Long, state: ByteArray): Boolean
    private external fun nativeDestroy(handle: Long)

    private companion object {
        const val BLACK = 0xFF000000.toInt()
        const val AUDIO_BUFFER_SAMPLES = 1608
        const val SAMPLE_RATE = 48000
        const val BYTES_PER_STEREO_FRAME = 4

        init {
            System.loadLibrary("emuladorgba_mgba")
        }

        fun openTrack(): AudioTrack? {
            val min = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            if (min <= 0) return null
            val size = maxOf(min, AUDIO_BUFFER_SAMPLES * BYTES_PER_STEREO_FRAME)
            return try {
                val created = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build(),
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                            .build(),
                    )
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setBufferSizeInBytes(size)
                    .build()
                if (created.state == AudioTrack.STATE_INITIALIZED) created else {
                    created.release()
                    null
                }
            } catch (_: RuntimeException) {
                null
            }
        }
    }
}
