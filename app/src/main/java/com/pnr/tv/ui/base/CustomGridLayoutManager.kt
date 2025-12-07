package com.pnr.tv.ui.base

import android.content.Context
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Custom GridLayoutManager that prevents focus from jumping outside the grid
 * except when navigating LEFT (to category list) or BACK button.
 * This ensures focus stays within the grid during key repeats and other navigation.
 *
 * Strategy: Instead of pre-checking boundaries, we wait for Android's focus search
 * to fail naturally, then prevent focus from escaping by returning the current focused view.
 */
class CustomGridLayoutManager(
    context: Context,
    spanCount: Int,
    private val adapter: RecyclerView.Adapter<*>,
) : GridLayoutManager(context, spanCount) {
    override fun onFocusSearchFailed(
        focused: View,
        focusDirection: Int,
        recycler: RecyclerView.Recycler,
        state: RecyclerView.State,
    ): View? {
        // Odağı HER ZAMAN mevcut öğede tut (kilitle).
        return focused
    }
}
