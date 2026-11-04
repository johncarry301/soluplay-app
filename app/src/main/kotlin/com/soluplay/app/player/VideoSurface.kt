package com.soluplay.app.player

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.TextureView
import android.widget.FrameLayout
import androidx.media3.common.VideoSize

/**
 * The picture, as a texture.
 *
 * A texture is used rather than a surface, and the reason is the race: a
 * surface is drawn in a layer of its own, behind the window, so it cannot be
 * put in front of a web view — and a YouTube mirror is played by a web view.
 * With a surface, a YouTube mirror that started first would sit on top of the
 * Media3 mirror that won the race, and the winner's picture would never be
 * seen. A texture is an ordinary view, so the ordinary rule applies: the view
 * that is brought to the front is the one on the screen.
 *
 * The frame is letterboxed the way the web player letterboxes it: the picture
 * keeps its own proportions and is fitted inside the frame, never cropped.
 */
class VideoSurface(context: Context) : FrameLayout(context) {

    private val texture = TextureView(context)

    private var videoWidth = 0
    private var videoHeight = 0

    init {
        setBackgroundColor(Color.BLACK)
        addView(
            texture,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
    }

    /** The texture the player draws into. */
    val textureView: TextureView get() = texture

    /**
     * The picture changed size, so the frame has to fit it again.
     */
    fun onVideoSize(videoSize: VideoSize) {
        if (videoSize.width <= 0 || videoSize.height <= 0) return
        videoWidth = videoSize.width
        videoHeight = videoSize.height
        fit()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        fit()
    }

    /** Fits the picture inside the frame, keeping its proportions. */
    private fun fit() {
        if (videoWidth <= 0 || videoHeight <= 0) return
        if (width <= 0 || height <= 0) return

        val scale = minOf(width.toFloat() / videoWidth, height.toFloat() / videoHeight)
        val fittedWidth = (videoWidth * scale).toInt().coerceAtLeast(1)
        val fittedHeight = (videoHeight * scale).toInt().coerceAtLeast(1)

        val params = texture.layoutParams as LayoutParams
        if (params.width == fittedWidth && params.height == fittedHeight) return

        params.width = fittedWidth
        params.height = fittedHeight
        params.gravity = Gravity.CENTER
        texture.layoutParams = params
    }
}
