package com.pnr.tv.ui.livestreams

import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.pnr.tv.R
import com.pnr.tv.db.entity.LiveStreamCategoryEntity

/**
 * Kategoriler için RecyclerView adapter.
 */
class CategoriesAdapter(
    private val onCategoryClick: (LiveStreamCategoryEntity) -> Unit,
    private val onNavigateToChannels: () -> Unit,
) : ListAdapter<LiveStreamCategoryEntity, CategoriesAdapter.CategoryViewHolder>(CategoryDiffCallback()) {
    private var onItemFocusListener: ((LiveStreamCategoryEntity) -> Unit)? = null
    private var selectedPosition: Int = -1

    fun setOnItemFocusListener(listener: (LiveStreamCategoryEntity) -> Unit) {
        onItemFocusListener = listener
    }

    /**
     * Seçili kategoriyi günceller ve görsel durumu yeniden çizer.
     */
    fun updateSelectedItem(selectedCategoryId: Int?) {
        val oldPosition = selectedPosition

        // Yeni seçili pozisyonu bul
        val newPosition =
            if (selectedCategoryId != null) {
                currentList.indexOfFirst { it.categoryIdInt == selectedCategoryId }
            } else {
                -1
            }

        // Eski pozisyonu güncelle (seçili değil olarak işaretle)
        if (oldPosition >= 0 && oldPosition < currentList.size) {
            notifyItemChanged(oldPosition)
        }

        // Yeni pozisyonu güncelle
        selectedPosition = newPosition
        if (newPosition >= 0 && newPosition < currentList.size) {
            notifyItemChanged(newPosition)
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): CategoryViewHolder {
        val view =
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_category, parent, false)
        return CategoryViewHolder(view, onCategoryClick, onItemFocusListener, onNavigateToChannels)
    }

    override fun onBindViewHolder(
        holder: CategoryViewHolder,
        position: Int,
    ) {
        val category = getItem(position)
        val isSelected = position == selectedPosition
        holder.bind(category, isSelected)
    }

    class CategoryViewHolder(
        itemView: View,
        private val onCategoryClick: (LiveStreamCategoryEntity) -> Unit,
        private val onItemFocusListener: ((LiveStreamCategoryEntity) -> Unit)?,
        private val onNavigateToChannels: () -> Unit,
    ) : RecyclerView.ViewHolder(itemView) {
        private val categoryNameText: TextView = itemView.findViewById(R.id.text_category_name)
        private var currentCategory: LiveStreamCategoryEntity? = null

        fun bind(
            category: LiveStreamCategoryEntity,
            isSelected: Boolean,
        ) {
            currentCategory = category
            // Kategori ismini yerelleştir
            val localizedName = com.pnr.tv.util.CategoryNameHelper.getLocalizedCategoryName(
                itemView.context,
                category.categoryName
            )
            categoryNameText.text = localizedName

            // Seçili durumunu ayarla
            categoryNameText.isSelected = isSelected

            // TextView'a focus ve click listener ekle
            categoryNameText.setOnClickListener {
                onCategoryClick(category)
            }

            // Focus listener ekle
            categoryNameText.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus && currentCategory != null) {
                    onItemFocusListener?.invoke(currentCategory!!)
                }
            }

            // Key listener ekle - sağ ok ve merkez tuşu için kanal listesine geçiş
            categoryNameText.setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN) {
                    when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_RIGHT,
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        -> {
                            onNavigateToChannels()
                            true
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

    private class CategoryDiffCallback : DiffUtil.ItemCallback<LiveStreamCategoryEntity>() {
        override fun areItemsTheSame(
            oldItem: LiveStreamCategoryEntity,
            newItem: LiveStreamCategoryEntity,
        ): Boolean = oldItem.categoryIdInt == newItem.categoryIdInt

        override fun areContentsTheSame(
            oldItem: LiveStreamCategoryEntity,
            newItem: LiveStreamCategoryEntity,
        ): Boolean = oldItem == newItem
    }
}
