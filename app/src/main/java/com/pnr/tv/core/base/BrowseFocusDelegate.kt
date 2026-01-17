package com.pnr.tv.core.base

import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.lifecycle.LifecycleOwner
import com.pnr.tv.R
import com.pnr.tv.model.CategoryItem
import timber.log.Timber

/**
 * BaseBrowseFragment içindeki odak (focus) ve navigasyon yönetimi mantığını yöneten delegate sınıfı.
 *
 * Bu sınıf, BaseBrowseFragment'tan focus ve navigasyon sorumluluklarını ayırarak
 * Fragment'ın daha temiz ve bakımı kolay olmasını sağlar.
 *
 * Sorumlulukları:
 * - Kategoriler ve içerik arasında focus navigasyonu
 * - Navbar'a focus navigasyonu
 * - Kategori ve içerik restore işlemleri
 * - İlk kategoriye focus verme
 * - Back press listener setup
 */
class BrowseFocusDelegate(
    private val categoriesRecyclerView: CustomCategoriesRecyclerView,
    private val contentRecyclerView: CustomContentRecyclerView,
    private val categoryAdapter: com.pnr.tv.ui.browse.CategoryAdapter,
    private val contentAdapter: com.pnr.tv.ui.browse.ContentAdapter,
    private val navbarHandler: BrowseNavbarHandler?,
    private val navbarView: View?,
    private val viewLifecycleOwner: LifecycleOwner,
) {
    // Seçili kategori ID'sini saklamak için (navigateFocusToCategories için)
    var currentSelectedCategoryId: String? = null

    /**
     * Focus'u kategorilere taşır.
     * Sistemin otomatik focus bulmasına izin ver. Eğer sistem bulamıyorsa findViewHolderForAdapterPosition kullan.
     */
    fun navigateFocusToCategories() {
        val selectedPosition = categoryAdapter.getSelectedPosition()
        Timber.tag("BACK_PRESS").d("navigateFocusToCategories() çağrıldı")
        Timber.tag("BACK_PRESS").d("Selected position: $selectedPosition, Item count: ${categoryAdapter.itemCount}")

        // Eğer selectedPosition -1 ise, varsayılan olarak 0. pozisyona (ilk kategoriye) odaklan
        val positionToFocus =
            if (selectedPosition >= 0 && selectedPosition < categoryAdapter.itemCount) {
                selectedPosition
            } else if (categoryAdapter.itemCount > 0) {
                Timber.tag("BACK_PRESS").d("→ Selected position -1 veya geçersiz, ilk kategoriye (0) odaklanılıyor")
                0
            } else {
                Timber.tag("BACK_PRESS").d("→ Kategori listesi boş, işlem yapılmıyor")
                return
            }

        // Sistemin otomatik focus bulmasına izin ver
        val viewHolder = categoriesRecyclerView.findViewHolderForAdapterPosition(positionToFocus)
        val textView = viewHolder?.itemView?.findViewById<View>(R.id.text_category_name)
        if (textView != null && textView.isAttachedToWindow) {
            Timber.tag("BACK_PRESS").d("→ ViewHolder bulundu, focus veriliyor (senkron)")
            textView.requestFocus()
            Timber.tag("BACK_PRESS").d("Focus verildi: ${textView.javaClass.simpleName}")
            return
        }

        // Eğer sistem bulamıyorsa, scroll yap ve odakla
        Timber.tag("BACK_PRESS").d("→ ViewHolder bulunamadı, scroll yapılıyor (async)")
        categoriesRecyclerView.scrollToPosition(positionToFocus)
        categoriesRecyclerView.post {
            val targetViewHolder = categoriesRecyclerView.findViewHolderForAdapterPosition(positionToFocus)
            val targetTextView = targetViewHolder?.itemView?.findViewById<View>(R.id.text_category_name)
            if (targetTextView != null && targetTextView.isAttachedToWindow) {
                Timber.tag("BACK_PRESS").d("→ post() callback: Focus veriliyor")
                targetTextView.requestFocus()
                Timber.tag("BACK_PRESS").d("Focus verildi: ${targetTextView.javaClass.simpleName}")
            } else {
                Timber.tag("BACK_PRESS").d("→ post() callback: ViewHolder hala bulunamadı")
            }
        }
    }

    /**
     * İçerik pozisyonuna scroll yap ve odakla.
     * Basit restore mekanizması: scrollToPositionWithOffset ve requestFocus.
     */
    fun restoreContentFocus(position: Int) {
        if (position < 0 || position >= contentAdapter.itemCount) {
            return
        }

        val layoutManager = contentRecyclerView.layoutManager as? androidx.recyclerview.widget.GridLayoutManager
        if (layoutManager != null) {
            // Öğeyi ekranın ortasına getirmek için offset ile scroll yap
            layoutManager.scrollToPositionWithOffset(position, 200)
        } else {
            contentRecyclerView.scrollToPosition(position)
        }

        // Scroll tamamlandığında odakla
        contentRecyclerView.post {
            val viewHolder = contentRecyclerView.findViewHolderForAdapterPosition(position)
            if (viewHolder != null) {
                viewHolder.itemView.requestFocus()
                // Player/detay sayfasından dönüşte focus'un sistem tarafından doğru algılanması için
                // RecyclerView'ın layout'unu yeniden hesaplat ve descendantFocusability'yi güncelle
                contentRecyclerView.requestLayout()
                // Geçici olarak descendantFocusability'yi değiştirerek sistemi uyar
                val originalFocusability = contentRecyclerView.descendantFocusability
                contentRecyclerView.descendantFocusability = android.view.ViewGroup.FOCUS_BEFORE_DESCENDANTS
                contentRecyclerView.post {
                    contentRecyclerView.descendantFocusability = originalFocusability
                }
            }
        }
    }

    /**
     * Kategori -> İçerik geçişinde kullanılır.
     * Her zaman 1. öğeye (Index 0) odaklanır.
     */
    fun navigateToContentStart() {
        if (contentAdapter.itemCount == 0) {
            return
        }

        contentRecyclerView.scrollToPosition(0)
        contentRecyclerView.post {
            val viewHolder = contentRecyclerView.findViewHolderForAdapterPosition(0)
            if (viewHolder != null) {
                viewHolder.itemView.requestFocus()
            }
        }
    }

    /**
     * İlk kategoriye focus verir (ilk açılış için).
     */
    fun focusFirstCategory(
        firstCategory: CategoryItem,
        onCategoryClicked: (CategoryItem) -> Unit,
        onFocusComplete: (() -> Unit)? = null,
    ) {
        Timber.tag("FOCUS_INIT").d("focusFirstCategory() çağrıldı")
        Timber.tag("FOCUS_INIT").d("categoryAdapter.itemCount: ${categoryAdapter.itemCount}")
        Timber.tag("FOCUS_INIT").d("firstCategory.categoryId: ${firstCategory.categoryId}")
        Timber.tag("FOCUS_INIT").d("firstCategory.categoryName: ${firstCategory.categoryName}")

        if (categoryAdapter.itemCount == 0) {
            Timber.tag("FOCUS_INIT").e("❌ categoryAdapter.itemCount == 0, focus verilemiyor!")
            return
        }

        // ViewModel'i uyararak ilk kategorinin içeriğini yüklemesini sağla
        Timber.tag("FOCUS_INIT").d("→ onCategoryClicked() çağrılıyor (içerik yüklenecek)")
        onCategoryClicked(firstCategory)

        // İlk kategoriye scroll yap ve odakla
        Timber.tag("FOCUS_INIT").d("→ scrollToPosition(0) çağrılıyor")
        categoriesRecyclerView.scrollToPosition(0)
        categoriesRecyclerView.post {
            Timber.tag("FOCUS_INIT").d("→ categoriesRecyclerView.post() callback çağrıldı")
            val viewHolder = categoriesRecyclerView.findViewHolderForAdapterPosition(0)
            Timber.tag("FOCUS_INIT").d("→ viewHolder bulundu: ${viewHolder != null}")

            val textView = viewHolder?.itemView?.findViewById<android.widget.TextView>(R.id.text_category_name)
            Timber.tag("FOCUS_INIT").d("→ textView bulundu: ${textView != null}")
            Timber.tag("FOCUS_INIT").d("→ textView.isAttachedToWindow: ${textView?.isAttachedToWindow}")

            if (textView != null && textView.isAttachedToWindow) {
                textView.isFocusable = true
                textView.isFocusableInTouchMode = true
                // UI thread'in nefes alması için kısa bir gecikme
                Handler(Looper.getMainLooper()).postDelayed({
                    Timber.tag("FOCUS_INIT").d("→ textView.requestFocus() çağrılıyor (postDelayed(50ms) sonrası)")
                    val focusResult = textView.requestFocus()
                    Timber.tag("FOCUS_INIT").d("→ textView.requestFocus() sonucu: $focusResult")
                    Timber.tag("FOCUS_INIT").d("✅ Focus başarıyla verildi: ${textView.text}")
                    // Odaklama işlemi bittikten sonra callback'i çağır
                    onFocusComplete?.invoke()
                }, 50)
            } else {
                Timber.tag("FOCUS_INIT").e("❌ textView null veya isAttachedToWindow=false, focus verilemedi!")
                // Hata durumunda da callback'i çağır
                onFocusComplete?.invoke()
            }
        }
    }

    /**
     * Back press listener setup.
     */
    fun setupBackPressListener(
        getLastSelectedCategoryId: () -> String?,
        onBackPressed: () -> Unit,
    ) {
        // Back press handling - See FUTURE_PLANS.md for details
    }
}
