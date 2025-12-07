package com.pnr.tv.ui.browse

import androidx.recyclerview.widget.DiffUtil
import com.pnr.tv.model.CategoryItem

/**
 * DiffUtil.ItemCallback implementation for CategoryItem.
 * Used by CategoryAdapter to efficiently update the RecyclerView.
 */
object CategoryDiff : DiffUtil.ItemCallback<CategoryItem>() {
    override fun areItemsTheSame(
        oldItem: CategoryItem,
        newItem: CategoryItem,
    ): Boolean = oldItem.categoryId == newItem.categoryId

    override fun areContentsTheSame(
        oldItem: CategoryItem,
        newItem: CategoryItem,
    ): Boolean = oldItem == newItem
}



