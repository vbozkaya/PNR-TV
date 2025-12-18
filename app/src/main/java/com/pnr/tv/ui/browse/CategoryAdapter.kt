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
     * Focus kaybını önlemek için mevcut focus'u korur.
     */
    fun updateSelectedItem(selectedCategoryId: String?) {
        val oldPosition = selectedPosition

        // Find new selected position
        val newPosition =
            if (selectedCategoryId != null && currentList.isNotEmpty()) {
                // Kategori ID karşılaştırması - hem String hem de Int formatlarını kontrol et
                val foundIndex = currentList.indexOfFirst { 
                    it.categoryId == selectedCategoryId || 
                    it.categoryId == selectedCategoryId.toIntOrNull()?.toString() ||
                    it.categoryId.toIntOrNull()?.toString() == selectedCategoryId
                }
                if (foundIndex == -1) {
                    // Debug için tüm kategori ID'lerini logla (güvenli şekilde)
                    try {
                        val categoryList = currentList.map { "${it.categoryId} (${it.categoryName})" }
                        Timber.tag("FOCUS_DEBUG").d("🔍 Kategori bulunamadı: $selectedCategoryId, Mevcut kategoriler: $categoryList")
                    } catch (e: Exception) {
                        Timber.tag("FOCUS_DEBUG").e(e, "🔍 Kategori bulunamadı: $selectedCategoryId, Liste boş veya hata")
                    }
                }
                foundIndex
            } else {
                -1
            }

        val categoryName =
            if (newPosition >= 0 && newPosition < currentList.size && currentList.isNotEmpty()) {
                try {
                    currentList[newPosition].categoryName ?: "UNKNOWN"
                } catch (e: Exception) {
                    Timber.tag("FOCUS_DEBUG").e(e, "❌ Kategori adı alınırken hata (pozisyon: $newPosition)")
                    "HATA"
                }
            } else {
                "BULUNAMADI"
            }
        Timber.tag(
            "FOCUS_DEBUG",
        ).d(
            "🔄 updateSelectedItem() - selectedCategoryId: $selectedCategoryId, Eski pozisyon: $oldPosition, Yeni pozisyon: $newPosition, Kategori: $categoryName",
        )

        // Eğer pozisyon değişmediyse, güncelleme yapma (gereksiz focus kaybını önle)
        if (oldPosition == newPosition && newPosition >= 0) {
            // Sadece görsel durumu güncelle, focus'u koru
            return
        }

        // Focus koruma ViewHolder.bind() içinde yapılıyor

        // Update old position (mark as not selected) - güvenli kontrollerle
        if (oldPosition >= 0 && oldPosition != newPosition && currentList.isNotEmpty() && oldPosition < currentList.size) {
            try {
                notifyItemChanged(oldPosition)
            } catch (e: Exception) {
                Timber.tag("FOCUS_DEBUG").e(e, "❌ notifyItemChanged hatası (eski pozisyon: $oldPosition)")
            }
        }

        // Update new position - güvenli kontrollerle
        selectedPosition = newPosition
        if (newPosition >= 0 && currentList.isNotEmpty() && newPosition < currentList.size) {
            try {
                // Sadece görsel güncelleme yap, focus'u koru
                // notifyItemChanged() çağrıldığında ViewHolder yeniden bind edilir
                // ama focus kaybını önlemek için ViewHolder.bind() içinde focus kontrolü yapacağız
                notifyItemChanged(newPosition)
            } catch (e: Exception) {
                Timber.tag("FOCUS_DEBUG").e(e, "❌ notifyItemChanged hatası (yeni pozisyon: $newPosition)")
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
            // Mevcut focus durumunu sakla (focus kaybını önlemek için)
            val hadFocus = categoryNameText.hasFocus()
            
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

            // Focus kaybını önle: Eğer önceden focus varsa, geri ver
            // İçerik yükleme sırasında focus kaybını önlemek için daha agresif bir yaklaşım
            if (hadFocus) {
                // Hemen dene
                categoryNameText.post {
                    if (!categoryNameText.hasFocus()) {
                        categoryNameText.requestFocus()
                    }
                }
                // Biraz gecikme ile tekrar dene (içerik yükleme tamamlanması için)
                categoryNameText.postDelayed({
                    if (!categoryNameText.hasFocus() && hadFocus) {
                        categoryNameText.requestFocus()
                        Timber.tag("FOCUS_DEBUG").d("🔧 Kategori focus geri verildi (gecikmeli): ${category.categoryName}")
                    }
                }, 200)
            }

            // Focus değişikliklerini log'la ve callback çağır
            categoryNameText.setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    val position = bindingAdapterPosition
                    val categoryName = currentCategory?.categoryName ?: "UNKNOWN"
                    Timber.tag(
                        "FOCUS_DEBUG",
                    ).d("✨ FOCUS ALINDI - Pozisyon: $position, Kategori: $categoryName, View: ${view.javaClass.simpleName}")
                    // Focus geldiğinde kategoriyi seç ve içerikleri yükle
                    // Debounce BaseBrowseFragment'ta yapılıyor, burada direkt çağır
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
                            // En alttaysan olayı kesinlikle tüket, navbar'a veya başka yere gitmesin
                            Timber.tag("FOCUS_DEBUG").d("🛑 DPAD_DOWN: Son kategori, olay tüketiliyor (pozisyon: $currentPosition)")
                            return@setOnKeyListener true
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
