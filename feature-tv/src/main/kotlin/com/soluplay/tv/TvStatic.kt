package com.soluplay.tv

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Random

/**
 * The screen a channel that is not playing leaves behind: the static of a
 * television with nothing on it.
 *
 * The noise is a small picture of random grey, drawn big and unfiltered so
 * the grain stays square, and remade a few times a second so it crawls the
 * way static crawls.
 */
@Composable
fun TvStatic(modifier: Modifier = Modifier) {
    val noise by produceState(initialValue = staticFrame(), key1 = Unit) {
        while (true) {
            delay(90)
            value = withContext(Dispatchers.Default) { staticFrame() }
        }
    }

    Image(
        bitmap = noise,
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        filterQuality = FilterQuality.None,
        modifier = modifier,
    )
}

/** One frame of static: a small field of random grey. */
private fun staticFrame(): ImageBitmap {
    val pixels = IntArray(WIDTH * HEIGHT)
    val random = Random()
    for (index in pixels.indices) {
        val grey = random.nextInt(256)
        pixels[index] = (0xFF shl 24) or (grey shl 16) or (grey shl 8) or grey
    }
    return Bitmap.createBitmap(pixels, WIDTH, HEIGHT, Bitmap.Config.ARGB_8888).asImageBitmap()
}

/** The size of the noise field; drawn big, so it need not be big itself. */
private const val WIDTH = 160
private const val HEIGHT = 90