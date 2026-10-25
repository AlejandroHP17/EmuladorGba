package pelkidev.com.mx.emuladorgba.presentation

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntSize
import pelkidev.com.mx.emuladorgba.domain.GbaFrame
import kotlin.math.roundToInt

@Composable
fun GbaScreen(
    frame: () -> GbaFrame?,
    modifier: Modifier = Modifier,
) {
    val ratio = GbaFrame.WIDTH.toFloat() / GbaFrame.HEIGHT
    val bitmap = remember {
        Bitmap.createBitmap(GbaFrame.WIDTH, GbaFrame.HEIGHT, Bitmap.Config.ARGB_8888)
    }
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        val width = minOf(maxWidth, maxHeight * ratio)
        val height = width / ratio
        Canvas(
            modifier = Modifier
                .size(width, height)
                .graphicsLayer(),
        ) {
            val current = frame() ?: return@Canvas
            bitmap.setPixels(current.pixels, 0, GbaFrame.WIDTH, 0, 0, GbaFrame.WIDTH, GbaFrame.HEIGHT)
            drawImage(
                image = image,
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
            )
        }
    }
}
