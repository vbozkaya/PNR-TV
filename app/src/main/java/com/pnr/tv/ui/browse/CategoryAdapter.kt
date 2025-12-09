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
    private val onNavigateToContent: () -> Unit,
    private val onNavigateToNavbar: () -> Unit,
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
        return ViewHolder(view, onCategoryClick, onNavigateToContent, onNavigateToNavbar, parent as RecyclerView)
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
        private val onNavigateToContent: () -> Unit,
        private val onNavigateToNavbar: () -> Unit,
        private val recyclerView: RecyclerView,
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

            categoryNameText.isFocusable = true
            categoryNameText.isFocusableInTouchMode = true

            // Add comprehensive key listener with manual focus management
            categoryNameText.setOnKeyListener { _, keyCode, event ->
                if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false

                val currentPosition = bindingAdapterPosition
                if (currentPosition == RecyclerView.NO_POSITION) return@setOnKeyListener false

                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        if (currentPosition == 0) {
                            // En üstteyiz, Navbar'a gitmek için sinyal ver.
                            onNavigateToNavbar()
                        } else {
                            // Önce scroll yap, sonra odak ver
                            val targetPosition = currentPosition - 1
                            recyclerView.smoothScrollToPosition(targetPosition)
                            
                            // ViewTreeObserver ile ViewHolder'ın hazır olduğundan emin ol
                            val observer = recyclerView.viewTreeObserver
                            observer.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                                override fun onGlobalLayout() {
                                    val previousViewHolder = recyclerView.findViewHolderForAdapterPosition(targetPosition)
                                    if (previousViewHolder != null) {
                                        previousViewHolder.itemView.requestFocus()
                                        recyclerView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                                    } else {
                                        // ViewHolder henüz hazır değil, bir kez daha dene
                                        recyclerView.postDelayed({
                                            val retryViewHolder = recyclerView.findViewHolderForAdapterPosition(targetPosition)
                                            retryViewHolder?.itemView?.requestFocus()
                                            recyclerView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                                        }, 50)
                                    }
                                }
                            })
                        }
                        // Olayı her durumda tüket, çünkü yönetimi biz yaptık.
                        return@setOnKeyListener true
                    }

                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        val adapter = bindingAdapter as? CategoryAdapter ?: return@setOnKeyListener true
                        if (currentPosition == adapter.itemCount - 1) {
                            // En alttayız, olayı tüket ve hiçbir şey yapma.
                        } else {
                            // Önce scroll yap, sonra odak ver
                            val targetPosition = currentPosition + 1
                            recyclerView.smoothScrollToPosition(targetPosition)
                            
                            // ViewTreeObserver ile ViewHolder'ın hazır olduğundan emin ol
                            val observer = recyclerView.viewTreeObserver
                            observer.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                                override fun onGlobalLayout() {
                                    val nextViewHolder = recyclerView.findViewHolderForAdapterPosition(targetPosition)
                                    if (nextViewHolder != null) {
                                        nextViewHolder.itemView.requestFocus()
                                        recyclerView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                                    } else {
                                        // ViewHolder henüz hazır değil, bir kez daha dene
                                        recyclerView.postDelayed({
                                            val retryViewHolder = recyclerView.findViewHolderForAdapterPosition(targetPosition)
                                            retryViewHolder?.itemView?.requestFocus()
                                            recyclerView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                                        }, 50)
                                    }
                                }
                            })
                        }
                        // Olayı her durumda tüket, çünkü yönetimi biz yaptık.
                        return@setOnKeyListener true
                    }

                    KeyEvent.KEYCODE_DPAD_RIGHT,
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        // Yatay hareketi tamamen engelle ve olayı tüket.
                        return@setOnKeyListener true
                    }
                }
                // Bu noktaya asla gelinmemeli, ancak güvenlik için false döndür.
                return@setOnKeyListener false
            }
        }
    }
}
