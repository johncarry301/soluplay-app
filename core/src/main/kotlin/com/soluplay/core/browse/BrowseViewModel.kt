package com.soluplay.core.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Holds the guide for both surfaces.
 *
 * The screen never waits for the network: it shows what the repository
 * returns, and a refresh that fails keeps the guide that is already on the
 * screen instead of blanking it.
 */
class BrowseViewModel(private val repository: CatalogueRepository) : ViewModel() {

    private val _snapshot = MutableStateFlow(BrowseSnapshot(loading = true))
    val snapshot: StateFlow<BrowseSnapshot> = _snapshot.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _snapshot.update { it.copy(loading = true) }

            val next = repository.load()
            _snapshot.update { current -> mergeSnapshot(current, next).copy(loading = false) }
        }
    }
}

/** The view model is built from the app's own graph, not by reflection. */
class BrowseViewModelFactory(
    private val repository: CatalogueRepository,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BrowseViewModel::class.java)) {
            return BrowseViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown view model ${modelClass.name}")
    }
}
