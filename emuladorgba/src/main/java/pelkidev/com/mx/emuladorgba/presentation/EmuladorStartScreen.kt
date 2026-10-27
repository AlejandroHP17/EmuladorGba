package pelkidev.com.mx.emuladorgba.presentation

import android.app.Activity
import java.io.File
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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import pelkidev.com.mx.emuladorgba.R
import pelkidev.com.mx.emuladorgba.domain.GbaButton

@Composable
private fun rememberEmuladorStartViewModel(): EmuladorStartViewModel {
    val context = LocalContext.current
    return viewModel {
        EmuladorStartViewModel(
            stateDirectory = File(context.applicationContext.filesDir, "emuladorgba-states"),
        )
    }
}

@Composable
fun EmuladorStartScreen(
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EmuladorStartViewModel = rememberEmuladorStartViewModel(),
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
        if (viewModel.onLeave()) onExit()
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
        if (uiState.loadedFileName == null) {
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
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                GbaScreen(
                    frame = { frame.value },
                    modifier = Modifier.fillMaxSize(),
                )
                GbaControls(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                    onChanged = viewModel::onControlChanged,
                    selectedSlot = uiState.selectedSlot,
                    slotNames = uiState.slotNames,
                    onSelectSlot = viewModel::onSlotSelected,
                    onSlotNamed = viewModel::onSlotNamed,
                    onSave = viewModel::onSaveState,
                    onLoad = viewModel::onLoadState,
                    fastForward = uiState.fastForward,
                    onFastForward = viewModel::onFastForwardChanged,
                    latched = uiState.latched,
                )
                if (uiState.replaceAsk != null) {
                    ReplaceQuestion(
                        label = uiState.replaceLabel,
                        onConfirm = { if (viewModel.onReplaceConfirmed()) onExit() },
                        onReject = { if (viewModel.onReplaceRejected()) onExit() },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReplaceQuestion(
    label: String,
    onConfirm: () -> Unit,
    onReject: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.emuladorgba_replace_slot, label),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = onConfirm,
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Text(text = stringResource(R.string.emuladorgba_replace_confirm))
            }
            Button(
                onClick = onReject,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text(text = stringResource(R.string.emuladorgba_replace_reject))
            }
        }
    }
}

@Composable
private fun GbaControls(
    onChanged: (GbaButton, Boolean) -> Unit,
    selectedSlot: Int,
    slotNames: List<String>,
    onSelectSlot: (Int) -> Unit,
    onSlotNamed: (String) -> Unit,
    onSave: () -> Unit,
    onLoad: () -> Unit,
    fastForward: Boolean,
    onFastForward: (Boolean) -> Unit,
    latched: Set<GbaButton>,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Box(Modifier.fillMaxSize().padding(8.dp)) {
            Column(
                modifier = Modifier.align(Alignment.BottomStart),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Control(
                    "L",
                    GbaButton.L,
                    onChanged,
                    Modifier.padding(bottom = ShoulderGap),
                    style = ControlStyle.Face,
                    fill = FaceShoulder,
                    latched = latched,
                )
                Dpad(onChanged, latched)
            }
            Control(
                "R",
                GbaButton.R,
                onChanged,
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = DpadHeight + ShoulderGap),
                style = ControlStyle.Face,
                fill = FaceShoulder,
                latched = latched,
            )
        Row(
            modifier = Modifier.align(Alignment.BottomEnd),
            verticalAlignment = Alignment.Top,
        ) {
            Control(
                "B",
                GbaButton.B,
                onChanged,
                Modifier.padding(top = 36.dp, end = 12.dp),
                style = ControlStyle.Face,
                fill = FaceB,
                latched = latched,
            )
            Control(
                "A",
                GbaButton.A,
                onChanged,
                style = ControlStyle.Face,
                fill = FaceA,
                latched = latched,
            )
        }
        Row(modifier = Modifier.align(Alignment.BottomCenter)) {
            Control(
                "Select",
                GbaButton.SELECT,
                onChanged,
                Modifier.padding(end = 12.dp),
                style = ControlStyle.Menu,
                latched = latched,
            )
            Control(
                "Start",
                GbaButton.START,
                onChanged,
                style = ControlStyle.Menu,
                latched = latched,
            )
        }
        if (menuOpen) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0x66000000))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { menuOpen = false },
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
            horizontalAlignment = Alignment.End,
        ) {
            StateAction(
                label = stringResource(R.string.emuladorgba_menu),
                onClick = { menuOpen = !menuOpen },
                fill = if (menuOpen) FaceA else FaceShoulder,
            )
            if (menuOpen) {
                GameMenu(
                    selectedSlot = selectedSlot,
                    slotNames = slotNames,
                    onSelectSlot = onSelectSlot,
                    onSlotNamed = onSlotNamed,
                    onSave = onSave,
                    onLoad = onLoad,
                    fastForward = fastForward,
                    onFastForward = onFastForward,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        }
    }
}

@Composable
private fun GameMenu(
    selectedSlot: Int,
    slotNames: List<String>,
    onSelectSlot: (Int) -> Unit,
    onSlotNamed: (String) -> Unit,
    onSave: () -> Unit,
    onLoad: () -> Unit,
    fastForward: Boolean,
    onFastForward: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xF0181C24))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row {
            SlotChoice(1, slotNames.getOrElse(0) { "" }, selectedSlot, onSelectSlot, onSlotNamed)
            SlotChoice(
                2,
                slotNames.getOrElse(1) { "" },
                selectedSlot,
                onSelectSlot,
                onSlotNamed,
                Modifier.padding(start = 8.dp),
            )
            SlotChoice(
                3,
                slotNames.getOrElse(2) { "" },
                selectedSlot,
                onSelectSlot,
                onSlotNamed,
                Modifier.padding(start = 8.dp),
            )
        }
        StateAction(
            stringResource(R.string.emuladorgba_save),
            onSave,
            Modifier.padding(top = 8.dp),
        )
        StateAction(
            stringResource(R.string.emuladorgba_load),
            onLoad,
            Modifier.padding(top = 8.dp),
        )
        FastForwardHold(
            stringResource(R.string.emuladorgba_fast_forward),
            fastForward,
            onFastForward,
            Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun FastForwardHold(
    label: String,
    held: Boolean,
    onChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    DisposableEffect(Unit) {
        onDispose { onChanged(false) }
    }
    Box(
        modifier = modifier
            .size(96.dp, 40.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(if (held) FaceA else FaceShoulder)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    onChanged(true)
                    try {
                        waitForUpOrCancellation()
                    } finally {
                        onChanged(false)
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SlotChoice(
    slot: Int,
    name: String,
    selectedSlot: Int,
    onSelectSlot: (Int) -> Unit,
    onSlotNamed: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = slot == selectedSlot
    val label = if (name.isBlank()) slot.toString() else name
    val shape = RoundedCornerShape(percent = 50)
    if (!selected) {
        Box(
            modifier = modifier
                .height(40.dp)
                .widthIn(min = 40.dp, max = 96.dp)
                .clip(shape)
                .background(FaceShoulder)
                .clickable { onSelectSlot(slot) }
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        return
    }
    BasicTextField(
        value = name,
        onValueChange = onSlotNamed,
        singleLine = true,
        textStyle = TextStyle(
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        ),
        modifier = modifier,
        decorationBox = { inner ->
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .width(160.dp)
                    .clip(shape)
                    .background(FaceA)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (name.isBlank()) {
                    Text(
                        text = slot.toString(),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                inner()
            }
        },
    )
}

@Composable
private fun StateAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fill: Color = FaceShoulder,
) {
    Box(
        modifier = modifier
            .size(96.dp, 40.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(fill)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Dpad(
    onChanged: (GbaButton, Boolean) -> Unit,
    latched: Set<GbaButton>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PadGap),
    ) {
        Control("▲", GbaButton.UP, onChanged, style = ControlStyle.Pad, fill = FacePad, latched = latched)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PadSideGap),
        ) {
            Control("◀", GbaButton.LEFT, onChanged, style = ControlStyle.Pad, fill = FacePad, latched = latched)
            Control("▶", GbaButton.RIGHT, onChanged, style = ControlStyle.Pad, fill = FacePad, latched = latched)
        }
        Control("▼", GbaButton.DOWN, onChanged, style = ControlStyle.Pad, fill = FacePad, latched = latched)
    }
}

private enum class ControlStyle {
    Face,
    Menu,
    Pad,
}

@Composable
private fun Control(
    label: String,
    button: GbaButton,
    onChanged: (GbaButton, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    style: ControlStyle = ControlStyle.Face,
    fill: Color = PadFill,
    latched: Set<GbaButton> = emptySet(),
) {
    var fingerDown by remember(button) { mutableStateOf(false) }
    val pressed = fingerDown || button in latched
    val sink = with(LocalDensity.current) { 2.dp.toPx() }
    DisposableEffect(button) {
        onDispose { onChanged(button, false) }
    }
    val look = style.look()
    Box(
        modifier = modifier
            .size(look.width, look.height)
            .graphicsLayer {
                val scale = if (pressed) 0.94f else 1f
                scaleX = scale
                scaleY = scale
                translationY = if (pressed) sink else 0f
            }
            .clip(look.shape)
            .background(if (pressed) fill.pressed() else fill)
            .pointerInput(button) {
                awaitEachGesture {
                    awaitFirstDown()
                    fingerDown = true
                    onChanged(button, true)
                    try {
                        waitForUpOrCancellation()
                    } finally {
                        onChanged(button, false)
                        fingerDown = false
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = look.fontSize,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}

private data class ControlLook(
    val width: Dp,
    val height: Dp,
    val shape: Shape,
    val fontSize: TextUnit,
)

private fun ControlStyle.look(): ControlLook = when (this) {
    ControlStyle.Face -> ControlLook(72.dp, 72.dp, CircleShape, 22.sp)
    ControlStyle.Menu -> ControlLook(88.dp, 40.dp, RoundedCornerShape(percent = 50), 13.sp)
    ControlStyle.Pad -> ControlLook(PadButton, PadButton, RoundedCornerShape(14.dp), 18.sp)
}

private val PadButton = 52.dp
private val PadGap = 6.dp
private val PadSideGap = 28.dp
private val ShoulderGap = 8.dp
private val DpadHeight = PadButton * 3 + PadGap * 2

private val PadFill = Color.White.copy(alpha = 0.28f)
private val FaceA = Color(0xCC3D8BFF)
private val FaceB = Color(0xCCFF5A5A)
private val FaceShoulder = Color(0xCC6E7A90)
private val FacePad = Color(0xE65A6478)

private fun Color.pressed(): Color = Color(
    red = red * 0.62f,
    green = green * 0.62f,
    blue = blue * 0.62f,
    alpha = alpha,
)

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
