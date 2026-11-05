package com.soluplay.core.favourites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.soluplay.core.model.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The favourites list, as the screen sees it.
 *
 * The list on the device is the one the screen shows, and it is written the
 * moment a channel is marked. The server is asked only when the viewer asks
 * for it, and a server that cannot be reached never empties the list.
 */
class FavouritesViewModel(private val repository: FavouritesRepository) : ViewModel() {

    private val _snapshot = MutableStateFlow(FavouritesSnapshot())
    val snapshot: StateFlow<FavouritesSnapshot> = _snapshot.asStateFlow()

    init {
        viewModelScope.launch {
            _snapshot.value = repository.load()
        }
    }

    /** Marks a channel, or unmarks it. */
    fun toggle(channel: Channel) {
        _snapshot.value = repository.toggle(channel)
    }

    /** Asks the server for the list and keeps it if the server answers. */
    fun pull() {
        viewModelScope.launch {
            _snapshot.value = _snapshot.value.copy(syncing = true)
            val next = repository.pull()
            _snapshot.value = next.copy(syncing = false)
        }
    }

    /** Puts the list on the device onto the server. */
    fun push() {
        viewModelScope.launch {
            _snapshot.value = _snapshot.value.copy(syncing = true)
            val next = repository.push()
            _snapshot.value = next.copy(syncing = false)
        }
    }

    /**
     * Carries another device's list on this one.
     *
     * False when the code is not one the app could have written: nothing is
     * written in that case.
     */
    fun adopt(key: String): Boolean {
        val next = repository.adopt(key) ?: return false
        _snapshot.value = next
        return true
    }
}

/** The view model is built from the app's own graph, not by reflection. */
class FavouritesViewModelFactory(
    private val repository: FavouritesRepository,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FavouritesViewModel::class.java)) {
            return FavouritesViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown view model ${modelClass.name}")
    }
}
