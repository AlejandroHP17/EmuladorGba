package pelkidev.com.mx.emuladorgba.presentation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.provider.OpenableColumns
import java.util.concurrent.atomic.AtomicBoolean
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import pelkidev.com.mx.emuladorgba.R
import pelkidev.com.mx.emuladorgba.domain.GbaButton

@Composable
fun EmuladorStartScreen(
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EmuladorStartViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val frame = viewModel.frame.collectAsStateWithLifecycle()
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        val previousOrientation = activity?.requestedOrientation
            ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        onDispose {
            activity?.requestedOrientation = previousOrientation
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> viewModel.onAppStopped()
                Lifecycle.Event.ON_START -> viewModel.onAppStarted()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    BackHandler {
        viewModel.onLeave()
        onExit()
    }
    GbaSpeaker(uiState.samples)
    val pickGbaFile = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        viewModel.onFileChosen(displayName(context, uri)) {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Emulador Game Boy Advance",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = { pickGbaFile.launch(arrayOf("*/*")) },
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Text(text = stringResource(R.string.emuladorgba_choose_file))
            }
            if (uiState.showInvalidFileMessage) {
                Text(
                    text = stringResource(R.string.emuladorgba_invalid_file),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            uiState.loadedFileName?.let { fileName ->
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            if (uiState.loadedFileName != null) {
                GbaScreen(
                    frame = { frame.value },
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .fillMaxWidth()
                        .weight(1f),
                )
                GbaControls(
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                    onChanged = viewModel::onControlChanged,
                )
            }
        }
    }
}

@Composable
private fun GbaControls(
    onChanged: (GbaButton, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Control("L", GbaButton.L, onChanged)
            Control("R", GbaButton.R, onChanged)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Dpad(onChanged)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Control("Select", GbaButton.SELECT, onChanged)
                Control("Start", GbaButton.START, onChanged)
            }
            Row {
                Control("B", GbaButton.B, onChanged)
                Control("A", GbaButton.A, onChanged)
            }
        }
    }
}

@Composable
private fun Dpad(onChanged: (GbaButton, Boolean) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Control("↑", GbaButton.UP, onChanged)
        Row {
            Control("←", GbaButton.LEFT, onChanged)
            Control("→", GbaButton.RIGHT, onChanged)
        }
        Control("↓", GbaButton.DOWN, onChanged)
    }
}

@Composable
private fun Control(
    label: String,
    button: GbaButton,
    onChanged: (GbaButton, Boolean) -> Unit,
) {
    DisposableEffect(button) {
        onDispose { onChanged(button, false) }
    }
    Text(
        text = label,
        modifier = Modifier
            .pointerInput(button) {
                awaitEachGesture {
                    awaitFirstDown()
                    onChanged(button, true)
                    try {
                        waitForUpOrCancellation()
                    } finally {
                        onChanged(button, false)
                    }
                }
            }
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

@Composable
private fun GbaSpeaker(samples: ByteArray) {
    val player = remember { GbaPcmPlayer() }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    LaunchedEffect(samples) {
        player.submit(samples)
    }
}

internal fun gbaPcm16(samples: ByteArray): ByteArray {
    val pcm = ByteArray(samples.size * 2)
    samples.forEachIndexed { index, sample ->
        val wide = sample.toInt() shl 8
        pcm[index * 2] = wide.toByte()
        pcm[index * 2 + 1] = (wide ushr 8).toByte()
    }
    return pcm
}

private class GbaPcmPlayer {
    private val stop = AtomicBoolean(false)
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var track: AudioTrack? = null

    fun submit(samples: ByteArray) {
        if (samples.isEmpty()) {
            stop.set(true)
            handler?.post { pause() } ?: return
            return
        }
        stop.set(false)
        val copy = samples.copyOf()
        ensureHandler().post { play(copy) }
    }

    fun release() {
        stop.set(true)
        val handler = handler ?: return
        val thread = thread ?: return
        handler.post {
            pause()
            track?.release()
            track = null
        }
        thread.quitSafely()
    }

    private fun ensureHandler(): Handler {
        handler?.let { return it }
        val thread = HandlerThread("gba-pcm").also { it.start() }
        return Handler(thread.looper).also {
            this.thread = thread
            handler = it
        }
    }

    private fun play(samples: ByteArray) {
        if (stop.get()) return
        val pcm = gbaPcm16(samples)
        val track = ensureTrack()
        if (track.state != AudioTrack.STATE_INITIALIZED) return
        track.play()
        var offset = 0
        while (offset < pcm.size) {
            if (stop.get()) {
                pause()
                return
            }
            val count = minOf(WRITE_CHUNK, pcm.size - offset)
            val written = track.write(pcm, offset, count)
            if (written <= 0) return
            offset += written
        }
    }

    private fun pause() {
        val track = track ?: return
        if (track.playState != AudioTrack.PLAYSTATE_PLAYING) return
        track.pause()
        track.flush()
    }

    private fun ensureTrack(): AudioTrack {
        track?.let { return it }
        val min = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val size = if (min > 0) min else 1024
        return AudioTrack.Builder()
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
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(size)
            .build()
            .also { track = it }
    }

    private companion object {
        const val SAMPLE_RATE = 22050
        const val WRITE_CHUNK = 1024
    }
}

private fun displayName(context: Context, uri: Uri): String {
    context.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) {
                return cursor.getString(index).orEmpty()
            }
        }
    }
    return uri.lastPathSegment.orEmpty()
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
