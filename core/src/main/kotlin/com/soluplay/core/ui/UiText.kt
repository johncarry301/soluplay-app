package com.soluplay.core.ui

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Which language the chrome speaks.
 *
 * Bengali is the first language of the app; English sits beside it so the
 * toggle is a switch and not a rewrite.
 */
enum class UiLanguage { BENGALI, ENGLISH }

val LocalUiLanguage = staticCompositionLocalOf { UiLanguage.BENGALI }
