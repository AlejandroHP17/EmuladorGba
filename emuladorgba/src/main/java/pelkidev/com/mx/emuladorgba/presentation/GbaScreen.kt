package pelkidev.com.mx.emuladorgba.presentation

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import pelkidev.com.mx.emuladorgba.domain.GbaFrame

@Composable
fun GbaScreen(
    frame: GbaFrame,
    modifier: Modifier = Modifier,
) {
    val ratio = GbaFrame.WIDTH.toFloat() / GbaFrame.HEIGHT
    val bitmap = remember(frame) {
        Bitmap.createBitmap(
            frame.pixels,
            GbaFrame.WIDTH,
            GbaFrame.HEIGHT,
            Bitmap.Config.ARGB_8888,
        ).asImageBitmap()
    }
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        val width = minOf(maxWidth, maxHeight * ratio)
        val height = width / ratio
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = Modifier.size(width, height),
            contentScale = ContentScale.FillBounds,
        )
    }
}
