package com.soluplay.core.ui

import androidx.compose.ui.graphics.Color

/**
 * The site's palette, shared by both surfaces so the app and the website read
 * as one product.
 */
val Accent = Color(0xFF00C978)
val Background = Color(0xFF060B13)
val Panel = Color(0xFF0A1222)
val Muted = Color(0xFF9AA7B8)

/**
 * The plate a channel logo stands on.
 *
 * It is white, and it is the same white for every channel: some logos are
 * published with a white background of their own and some are published
 * transparent, and without a plate of one colour the grid shows two different
 * kinds of tile instead of one.
 */
val LogoPlateBackground = Color(0xFFFFFFFF)

/** The letter that stands in for a logo that could not be fetched. */
val OnLogoPlate = Color(0xFF16202E)
