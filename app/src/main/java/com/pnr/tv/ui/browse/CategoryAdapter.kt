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
import timber.log.Timber

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
    private val onCategoryFocused: (CategoryItem) -> Unit = {},
) : ListAdapter<CategoryItem, CategoryAdapter.ViewHolder>(CategoryDiff) {
    private var selectedPosition: Int = -1

    /**
     * Returns the currently selected position.
     */
    fun getSelectedPosition(): Int = selectedPosition

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

        val categoryName =
            if (newPosition >= 0 && newPosition < currentList.size) {
                currentList[newPosition].categoryName
            } else {
                "BULUNAMADI"
            }
        Timber.tag(
            "FOCUS_DEBUG",
        ).d(
            "🔄 updateSelectedItem() - selectedCategoryId: $selectedCategoryId, Eski pozisyon: $oldPosition, Yeni pozisyon: $newPosition, Kategori: $categoryName",
        )

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
        return ViewHolder(view, onCategoryClick, onNavigateToContent, onNavigateToNavbar, onCategoryFocused)
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
        private val onCategoryFocused: (CategoryItem) -> Unit,
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
            val localizedName =
                CategoryNameHelper.getLocalizedCategoryName(
                    itemView.context,
                    category.categoryName,
                )
            categoryNameText.text = localizedName

            // Set selected state
            categoryNameText.isSelected = isSelected

            // Seçili kategori için text size'ı 1.4 kat artır (10sp -> 14sp)
            val baseTextSize = 10f
            categoryNameText.textSize =
                if (isSelected) {
                    baseTextSize * 1.4f // 14sp
                } else {
                    baseTextSize // 10sp
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

            // Focus değişikliklerini log'la ve callback çağır
            categoryNameText.setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    val position = bindingAdapterPosition
                    val categoryName = currentCategory?.categoryName ?: "UNKNOWN"
                    Timber.tag(
                        "FOCUS_DEBUG",
                    ).d("✨ FOCUS ALINDI - Pozisyon: $position, Kategori: $categoryName, View: ${view.javaClass.simpleName}")
                    // Focus geldiğinde kategoriyi seç ve içerikleri yükle
                    currentCategory?.let { onCategoryFocused(it) }
                } else {
                    val position = bindingAdapterPosition
                    Timber.tag("FOCUS_DEBUG").d("💨 FOCUS KAYBEDİLDİ - Pozisyon: $position")
                }
            }

            categoryNameText.setOnKeyListener { _, keyCode, event ->
                if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false

                val currentPosition = bindingAdapterPosition
                if (currentPosition == RecyclerView.NO_POSITION) return@setOnKeyListener false

                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        if (currentPosition == 0) {
                            onNavigateToNavbar()
                            return@setOnKeyListener true // Olayı tüket
                        }
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        val adapter = bindingAdapter as? CategoryAdapter
                        if (adapter != null && currentPosition == adapter.itemCount - 1) {
                            return@setOnKeyListener true // En alttaysan olayı tüket, aşağı gitme
                        }
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        // Sağ yön tuşu: Focus olan kategorideyse içerik grid'ine geç
                        onNavigateToContent()
                        return@setOnKeyListener true // Her durumda sağa gitmeyi engelle ve olayı tüket
                    }
                    KeyEvent.KEYCODE_DPAD_CENTER -> {
                        // OK tuşu: Focus olan kategorideyse içerik grid'ine geç
                        onNavigateToContent()
                        return@setOnKeyListener true // Olayı tüket
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        return@setOnKeyListener true // Sola gitmeyi her zaman engelle ve olayı tüket
                    }
                }
                // Yukarı/aşağı oklar için (sınırda değilse), RecyclerView'ın kendi akıcı
                // kaydırma davranışına izin vermek için 'false' döndür.
                return@setOnKeyListener false
            }
        }
    }
}
