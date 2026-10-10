/*
    Copyright 2025 AbleMusicPlayer contributors

    This file is part of AbleMusicPlayer.

    AbleMusicPlayer is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, version 3 of the License.

    AbleMusicPlayer is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with AbleMusicPlayer.  If not, see <https://www.gnu.org/licenses/>.
*/

package io.github.uditkarode.able.utils

import android.content.Context
import androidx.preference.PreferenceManager
import io.github.uditkarode.able.R
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Global list/grid view preference. Every screen with a list reads this to
 * decide which item layout and layout manager to use; the inline toggle
 * buttons and the Settings switch both go through here.
 */
object ViewMode {
    private const val KEY = "view_mode_grid"

    fun isGrid(context: Context): Boolean =
        PreferenceManager.getDefaultSharedPreferences(context).getBoolean(KEY, false)

    fun setGrid(context: Context, grid: Boolean) =
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit().putBoolean(KEY, grid).apply()

    /** Toggles the preference and returns the new grid state. */
    fun toggle(context: Context): Boolean {
        val grid = !isGrid(context)
        setGrid(context, grid)
        return grid
    }

    /** Layout manager matching the current preference (2 columns in grid mode). */
    fun layoutManagerFor(context: Context): RecyclerView.LayoutManager =
        if (isGrid(context)) GridLayoutManager(context, 2) else LinearLayoutManager(context)

    /** Icon a toggle should show: it reflects the *currently active* view. */
    fun toggleIcon(context: Context): Int =
        if (isGrid(context)) R.drawable.ic_grid else R.drawable.ic_list
}
