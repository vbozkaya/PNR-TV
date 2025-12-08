package com.pnr.tv.ui.browse

import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.pnr.tv.R
import com.pnr.tv.model.CategoryItem
import com.pnr.tv.util.CategoryNameHelper

/**
 * Generic category adapter that works with CategoryItem interface.
 * Replaces MovieCategoriesAdapter, SeriesCategoriesAdapter, and LiveStreamCategoriesAdapter.
 *
 * Supports:
 * - Remote control / DPAD navigation
 * - Click events
 * - Focus events
 * - Left/right directional navigation (category list ←→ content grid)
 */
class CategoryAdapter(
    private val onCategoryClick: (CategoryItem) -> Unit,
    private val onCategoryFocused: (CategoryItem) -> Unit,
    private val onNavigateToContent: () -> Unit,
    private val onNavigateToNavbar: () -> Unit = {},
) : ListAdapter<CategoryItem, CategoryAdapter.ViewHolder>(CategoryDiff) {
    private var selectedPosition: Int = -1

    /**
     * Updates the selected category and redraws the visual state.
     */
    fun updateSelectedItem(selectedCategoryId: String?) {
        val oldPosition = selectedPosition

        // Find new selected position
        val newPosition =
            if (selectedCategoryId != null) {
                currentList.indexOfFirst { it.categoryId == selectedCategoryId }
            } else {
                -1
            }

        // Update old position (mark as not selected)
        if (oldPosition >= 0 && oldPosition < currentList.size) {
            notifyItemChanged(oldPosition)
        }

        // Update new position
        selectedPosition = newPosition
        if (newPosition >= 0 && newPosition < currentList.size) {
            notifyItemChanged(newPosition)
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        val view =
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_category, parent, false)
        return ViewHolder(view, onCategoryClick, onCategoryFocused, onNavigateToContent, onNavigateToNavbar)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) {
        val category = getItem(position)
        val isSelected = position == selectedPosition
        holder.bind(category, isSelected)
    }

    class ViewHolder(
        itemView: View,
        private val onCategoryClick: (CategoryItem) -> Unit,
        private val onCategoryFocused: (CategoryItem) -> Unit,
        private val onNavigateToContent: () -> Unit,
        private val onNavigateToNavbar: () -> Unit,
    ) : RecyclerView.ViewHolder(itemView) {
        private val categoryNameText: TextView = itemView.findViewById(R.id.text_category_name)
        private val selectedIndicatorView: View = itemView.findViewById(R.id.view_selected_indicator)
        private var currentCategory: CategoryItem? = null

        fun bind(
            category: CategoryItem,
            isSelected: Boolean,
        ) {
            currentCategory = category
            // Kategori ismini yerelleştir
            val localizedName = CategoryNameHelper.getLocalizedCategoryName(
                itemView.context,
                category.categoryName
            )
            categoryNameText.text = localizedName

            // Set selected state
            categoryNameText.isSelected = isSelected

            // Seçili kategori için text size'ı 1.5 kat artır (12sp -> 18sp)
            val baseTextSize = 12f
            categoryNameText.textSize =
                if (isSelected) {
                    baseTextSize * 1.5f // 18sp
                } else {
                    baseTextSize // 12sp
                }

            // Seçili kategori için alt çizgiyi göster/gizle
            selectedIndicatorView.visibility =
                if (isSelected) {
                    View.VISIBLE
                } else {
                    View.GONE
                }

            // Add click listener
            categoryNameText.setOnClickListener {
                onCategoryClick(category)
            }

            // Add focus listener
            categoryNameText.setOnFocusChangeListener { focusedView, hasFocus ->
                if (hasFocus && currentCategory != null) {
                    onCategoryFocused(currentCategory!!)
                    
                    // Focus scroll: Focus alındığında item'ı görünür alana getir
                    val recyclerView = focusedView.parent as? RecyclerView
                    if (recyclerView != null) {
                        val layoutManager = recyclerView.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager
                        if (layoutManager != null) {
                            val focusedPosition = recyclerView.getChildAdapterPosition(focusedView)
                            if (focusedPosition != androidx.recyclerview.widget.RecyclerView.NO_POSITION) {
                                recyclerView.post {
                                    val firstVisible = layoutManager.findFirstVisibleItemPosition()
                                    val lastVisible = layoutManager.findLastVisibleItemPosition()
                                    
                                    var needsScroll = false
                                    if (focusedPosition < firstVisible || focusedPosition > lastVisible) {
                                        needsScroll = true
                                    } else {
                                        val viewHolder = recyclerView.findViewHolderForAdapterPosition(focusedPosition)
                                        viewHolder?.itemView?.let { view ->
                                            val top = view.top
                                            val bottom = view.bottom
                                            val recyclerTop = recyclerView.paddingTop
                                            val recyclerBottom = recyclerView.height - recyclerView.paddingBottom
                                            
                                            if (top < recyclerTop || bottom > recyclerBottom) {
                                                needsScroll = true
                                            }
                                        }
                                    }
                                    
                                    if (needsScroll || focusedPosition == firstVisible || focusedPosition == lastVisible) {
                                        layoutManager.scrollToPositionWithOffset(focusedPosition, recyclerView.paddingTop + 20)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Add key listener - right arrow and center button navigate to content, up arrow navigates to navbar, down arrow blocked on last item
            categoryNameText.setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN) {
                    when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_RIGHT,
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        -> {
                            onNavigateToContent()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            // İlk kategorideyken (position 0) navbar'a git
                            if (bindingAdapterPosition == 0) {
                                onNavigateToNavbar()
                                true
                            } else {
                                false
                            }
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            // Son kategorideyken aşağı yön tuşu ile odak atlamasını engelle
                            val adapter = bindingAdapter as? CategoryAdapter
                            if (adapter != null) {
                                val itemCount = adapter.itemCount
                                val currentPosition = bindingAdapterPosition
                                // Eğer bu öğe gerçekten de listenin son öğesi ise, tuş olayını tüket
                                if (currentPosition != RecyclerView.NO_POSITION && currentPosition == itemCount - 1) {
                                    return@setOnKeyListener true
                                }
                            }
                            false
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }

            categoryNameText.isFocusable = true
            categoryNameText.isFocusableInTouchMode = true
        }
    }
}
