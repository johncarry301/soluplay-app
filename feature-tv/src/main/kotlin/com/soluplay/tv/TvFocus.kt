package com.soluplay.tv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The focus on a television screen.
 *
 * The remote walks a grid of things: up and down move between rows, left and
 * right move inside a row. A row that holds nothing is stepped over instead
 * of stopping the walk, and the walk stops at the ends rather than wrapping
 * round to the other side.
 *
 * The rows are given again at every step, because a screen draws more of
 * them as the guide is read: a rail that was not there a moment ago is not a
 * rail the focus can be on.
 */
class TvFocus {

    /** The row the focus is on. */
    var row by mutableIntStateOf(0)
        private set

    /** The thing inside that row. */
    var column by mutableIntStateOf(0)
        private set

    /**
     * Moves the focus one step and asks for it.
     *
     * The focus is asked for after the screen has drawn, because a thing that
     * has not been drawn cannot be focused.
     */
    fun move(rows: List<List<FocusRequester>>, dRow: Int, dColumn: Int, scope: CoroutineScope) {
        if (rows.isEmpty()) return

        var nextRow = row
        var nextColumn = column

        if (dColumn != 0) {
            val size = rows.getOrNull(nextRow)?.size ?: 0
            if (size == 0) return
            nextColumn = (nextColumn + dColumn).coerceIn(0, size - 1)
        }

        if (dRow != 0) {
            val found = rowInDirection(rows, nextRow, dRow)
            if (found < 0) return
            nextRow = found
            nextColumn = nextColumn.coerceIn(0, rows[nextRow].lastIndex)
        }

        row = nextRow
        column = nextColumn
        rows.getOrNull(nextRow)?.getOrNull(nextColumn)?.askForFocus(scope)
    }

    /**
     * Puts the focus on a thing that is known to be there.
     *
     * Used when a screen is drawn again and the focus has to be put back
     * where the viewer left it.
     */
    fun place(row: Int, column: Int, rows: List<List<FocusRequester>>, scope: CoroutineScope) {
        val requester = rows.getOrNull(row)?.getOrNull(column) ?: return
        this.row = row
        this.column = column
        requester.askForFocus(scope)
    }

    /** The row the focus would land on, or -1 when there is none. */
    private fun rowInDirection(rows: List<List<FocusRequester>>, from: Int, step: Int): Int {
        var candidate = from
        while (true) {
            val next = candidate + step
            if (next < 0 || next > rows.lastIndex) return -1
            candidate = next
            if (rows[candidate].isNotEmpty()) return candidate
        }
    }
}

/**
 * Puts the focus on a thing, once the screen has drawn it.
 *
 * A focus that is asked for before the thing exists is refused, and a refusal
 * must not take the screen down: the walk simply carries on from where it
 * was.
 */
internal fun FocusRequester.askForFocus(scope: CoroutineScope) {
    scope.launch { runCatching { requestFocus() } }
}
