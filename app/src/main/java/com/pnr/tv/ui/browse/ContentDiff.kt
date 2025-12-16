package com.pnr.tv.ui.browse

import androidx.recyclerview.widget.DiffUtil
import com.pnr.tv.model.ContentItem

/**
 * DiffUtil.ItemCallback implementation for ContentItem.
 * Used by ContentAdapter to efficiently update the RecyclerView.
 */
object ContentDiff : DiffUtil.ItemCallback<ContentItem>() {
    override fun areItemsTheSame(
        oldItem: ContentItem,
        newItem: ContentItem,
    ): Boolean = oldItem.id == newItem.id

    override fun areContentsTheSame(
        oldItem: ContentItem,
        newItem: ContentItem,
    ): Boolean = oldItem == newItem
}
