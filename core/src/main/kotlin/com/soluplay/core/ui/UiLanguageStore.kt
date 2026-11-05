package com.soluplay.core.ui

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The language the chrome speaks, and the choice that is remembered.
 *
 * Bengali is the first language of the app and English sits beside it, so
 * the switch is a switch and not a rewrite. The choice is kept on the device,
 * because a viewer who turned the words into English once expects to find
 * them in English the next time.
 */
class UiLanguageStore(context: Context) {

    private val preferences = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    private val _language = MutableStateFlow(read())
    val language: StateFlow<UiLanguage> = _language.asStateFlow()

    /** The other language. */
    fun toggle() {
        val next = when (_language.value) {
            UiLanguage.BENGALI -> UiLanguage.ENGLISH
            UiLanguage.ENGLISH -> UiLanguage.BENGALI
        }
        _language.value = next
        preferences.edit().putString(KEY, next.name).apply()
    }

    private fun read(): UiLanguage {
        val stored = preferences.getString(KEY, null) ?: return UiLanguage.BENGALI
        return runCatching { UiLanguage.valueOf(stored) }.getOrDefault(UiLanguage.BENGALI)
    }

    private companion object {
        const val NAME = "soluplay"
        const val KEY = "language"
    }
}
