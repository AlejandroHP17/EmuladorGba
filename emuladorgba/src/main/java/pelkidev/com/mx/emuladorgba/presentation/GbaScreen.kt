package pelkidev.com.mx.emuladorgba.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import pelkidev.com.mx.emuladorgba.domain.GbaFrame

@Composable
fun GbaScreen(
    frame: GbaFrame,
    modifier: Modifier = Modifier,
) {
    val ratio = GbaFrame.WIDTH.toFloat() / GbaFrame.HEIGHT
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        val width = minOf(maxWidth, maxHeight * ratio)
        val height = width / ratio
        Box(
            modifier = Modifier
                .size(width, height)
                .background(Color.Black),
        )
    }
}
