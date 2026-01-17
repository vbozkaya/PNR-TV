package com.pnr.tv.ui.browse

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.pnr.tv.R
import com.pnr.tv.model.CategoryItem
import com.pnr.tv.util.ui.CategoryNameHelper

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
    private val onCategoryFocused: (CategoryItem) -> Unit = {},
) : ListAdapter<CategoryItem, CategoryAdapter.ViewHolder>(CategoryDiff) {
    init {
        // TV performans optimizasyonu: Stable IDs kullanarak RecyclerView'ın sadece değişen satırları güncellemesini sağla
        // Bu, düşük işlemcili TV cihazlarında flickering'i önler
        setHasStableIds(true)
    }

    private var selectedPosition: Int = -1

    override fun getItemId(position: Int): Long {
        // CategoryItem'ın categoryId'sini Long'a çevir (stable ID)
        // Position kullanma - bu flickering'e neden olur
        // categoryId String olduğu için hash code kullanıyoruz
        val categoryId = getItem(position).categoryId
        // String'i Long'a çevirmek için hash code kullan (stable)
        return categoryId.hashCode().toLong()
    }

    /**
     * Returns the currently selected position.
     */
    fun getSelectedPosition(): Int = selectedPosition

    /**
     * Updates the selected category and redraws the visual state.
     * Focus kaybını önlemek için mevcut focus'u korur.
     */
    fun updateSelectedItem(selectedCategoryId: String?) {
        val oldPosition = selectedPosition

        // Find new selected position
        val newPosition =
            if (selectedCategoryId != null && currentList.isNotEmpty()) {
                // Kategori ID karşılaştırması - hem String hem de Int formatlarını kontrol et
                val foundIndex =
                    currentList.indexOfFirst {
                        it.categoryId == selectedCategoryId ||
                            it.categoryId == selectedCategoryId.toIntOrNull()?.toString() ||
                            it.categoryId.toIntOrNull()?.toString() == selectedCategoryId
                    }
                if (foundIndex == -1) {
                    // Kategori bulunamadı
                }
                foundIndex
            } else {
                -1
            }

        // Eğer pozisyon değişmediyse, güncelleme yapma (gereksiz focus kaybını önle)
        if (oldPosition == newPosition && newPosition >= 0) {
            // Sadece görsel durumu güncelle, focus'u koru
            return
        }

        // Focus koruma ViewHolder.bind() içinde yapılıyor

        // Update old position (mark as not selected) - güvenli kontrollerle
        if (oldPosition >= 0 && oldPosition != newPosition && currentList.isNotEmpty() && oldPosition < currentList.size) {
            try {
                notifyItemChanged(oldPosition, "SELECTION_PAYLOAD")
            } catch (e: Exception) {
                // Hata durumunda sessizce devam et
            }
        }

        // Update new position - güvenli kontrollerle
        selectedPosition = newPosition
        if (newPosition >= 0 && currentList.isNotEmpty() && newPosition < currentList.size) {
            try {
                // Payload kullanarak sadece görsel güncelleme yap, focus'u koru
                notifyItemChanged(newPosition, "SELECTION_PAYLOAD")
            } catch (e: Exception) {
                // Hata durumunda sessizce devam et
            }
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        val view =
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_category, parent, false)
        return ViewHolder(view, onCategoryClick, onCategoryFocused)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) {
        val category = getItem(position)
        val isSelected = position == selectedPosition
        holder.bind(category, isSelected)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
        payloads: MutableList<Any>,
    ) {
        if (payloads.isNotEmpty() && payloads.contains("SELECTION_PAYLOAD")) {
            val isSelected = position == selectedPosition
            holder.updateSelectionState(isSelected)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    class ViewHolder(
        itemView: View,
        private val onCategoryClick: (CategoryItem) -> Unit,
        private val onCategoryFocused: (CategoryItem) -> Unit,
    ) : RecyclerView.ViewHolder(itemView) {
        private val categoryNameText: TextView = itemView.findViewById(R.id.text_category_name)
        private val selectedIndicatorView: View = itemView.findViewById(R.id.view_selected_indicator)
        private var currentCategory: CategoryItem? = null

        fun bind(
            category: CategoryItem,
            isSelected: Boolean,
        ) {
            // Mevcut focus durumunu sakla (focus kaybını önlemek için)

            currentCategory = category
            // Kategori ismini yerelleştir
            val localizedName =
                CategoryNameHelper.getLocalizedCategoryName(
                    itemView.context,
                    category.categoryName,
                )
            categoryNameText.text = localizedName

            // Set selected state
            updateSelectionState(isSelected)

            // Add click listener
            categoryNameText.setOnClickListener {
                onCategoryClick(category)
            }

            categoryNameText.isFocusable = true
            categoryNameText.isFocusableInTouchMode = true

            // Focus değişikliklerini dinle ve callback çağır
            categoryNameText.setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    currentCategory?.let { onCategoryFocused(it) }
                }
            }
        }

        /**
         * Updates only the visual selection state without triggering a full rebind.
         * This method is called when using RecyclerView payloads to preserve focus.
         *
         * IMPORTANT: This method ONLY updates visual properties. It does NOT set
         * click listeners, focus listeners, or key listeners, as resetting these
         * can disrupt the focus state.
         */
        fun updateSelectionState(isSelected: Boolean) {
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

            // Seçili kategori için koyu sarı renk, seçili olmayan için beyaz
            // Güvenlik kontrolü: Renk kaynağı bulunamazsa fallback kullan
            val textColor =
                try {
                    if (isSelected) {
                        ContextCompat.getColor(itemView.context, R.color.category_selected)
                    } else {
                        ContextCompat.getColor(itemView.context, R.color.category_normal)
                    }
                } catch (e: android.content.res.Resources.NotFoundException) {
                    // Renk kaynağı bulunamazsa varsayılan renk kullan
                    android.graphics.Color.WHITE
                } catch (e: Exception) {
                    // Diğer hatalar için de varsayılan renk kullan
                    android.graphics.Color.WHITE
                }
            categoryNameText.setTextColor(textColor)

            // Alt çizgiyi kaldır - artık gösterilmiyor
            selectedIndicatorView.visibility = View.GONE
        }
    }
}
