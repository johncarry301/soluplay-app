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

/**
 * The site's own greys.
 *
 * The app and the website are one product, and a border that is a shade
 * lighter on one of them is a border a viewer can see.
 */
val Slate800 = Color(0xFF1E293B)
val Slate700 = Color(0xFF334155)
val Slate500 = Color(0xFF64748B)
val Slate400 = Color(0xFF94A3B8)
val Slate200 = Color(0xFFE2E8F0)
val Slate950 = Color(0xFF020617)

/** The row of pills under the site's channel panel, and an inactive pill. */
val PanelDeep = Color(0xFF080E1B)
val PanelMid = Color(0xFF0D1628)

/** The bar the site keeps at the top of every page. */
val HeaderBackground = Color(0xFF070D18)

/** The emerald the site's live dot, its category pill and its count badge are drawn in. */
val LiveDot = Color(0xFF34D399)
val EmeraldText = Color(0xFF6EE7B7)

/** The red the site's LIVE badge is drawn in. */
val LiveBadge = Color(0xFFDC2626)

/** The red the site's LIVE badge writes its word in. */
val LiveText = Color(0xFFF87171)

/** The amber the site's countdown is drawn in. */
val Amber = Color(0xFFFCD34D)

/** The indigo the site's sport mark is drawn in. */
val IndigoMark = Color(0xFFA5B4FC)

/** The three stops of the site's match card's own gradient. */
val CardTop = Color(0xFF111D33)
val CardMiddle = Color(0xFF0B1322)
val CardBottom = Color(0xFF080D18)
