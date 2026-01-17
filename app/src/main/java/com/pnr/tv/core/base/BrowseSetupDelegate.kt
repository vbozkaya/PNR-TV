package com.pnr.tv.core.base

import android.content.Context
import android.util.Log
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.pnr.tv.core.constants.DatabaseConstants
import com.pnr.tv.core.constants.UIConstants
import com.pnr.tv.model.CategoryItem
import com.pnr.tv.model.ContentItem
import com.pnr.tv.ui.browse.CategoryAdapter
import com.pnr.tv.ui.browse.ContentAdapter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * BaseBrowseFragment içindeki RecyclerView setup ve adapter oluşturma işlemlerini yöneten delegate sınıfı.
 *
 * Bu sınıf, BaseBrowseFragment içindeki setup mantığını dışarı çıkararak
 * Fragment'ın sorumluluklarını azaltır.
 *
 * Sorumlulukları:
 * - Adapter oluşturma (CategoryAdapter ve ContentAdapter)
 * - Categories RecyclerView setup
 * - Content RecyclerView setup
 * - Callback bağlantıları
 */
class BrowseSetupDelegate(
    private val categoriesRecyclerView: CustomCategoriesRecyclerView,
    private val contentRecyclerView: CustomContentRecyclerView,
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val gridColumnCount: Int,
    private val onCategoryClicked: (CategoryItem) -> Unit,
    private val onCategoryFocused: (CategoryItem) -> Unit,
    private val onContentClicked: (ContentItem) -> Unit,
    private val onContentLongPressed: (ContentItem) -> Unit,
    private val fragmentView: View?,
) {
    // Generic adapters - BrowseSetupDelegate manages these
    lateinit var categoryAdapter: CategoryAdapter
    lateinit var contentAdapter: ContentAdapter

    // onCategoryFocused için debounce - gereksiz çağrıları önlemek için
    private var categoryFocusDebounceJob: Job? = null

    /**
     * Adapter'ları oluşturur ve RecyclerView'ları setup eder.
     * BaseBrowseFragment'tan initializeViews() içinde çağrılmalıdır.
     */
    fun setup() {
        createAdapters()
        setupCategoriesRecyclerView()
        setupContentRecyclerView()
    }

    /**
     * Generic adapters oluşturur.
     * BaseBrowseFragment creates and owns the CategoryAdapter and ContentAdapter instances.
     * Child fragments can override this to customize adapter creation (e.g., add OK button long press support).
     */
    private fun createAdapters() {
        categoryAdapter =
            CategoryAdapter(
                onCategoryClick = { category ->
                    onCategoryClicked(category)
                },
                onCategoryFocused = { category ->
                    fragmentView ?: return@CategoryAdapter
                    // Basit debounce: Önceki çağrıyı iptal et
                    categoryFocusDebounceJob?.cancel()
                    categoryFocusDebounceJob =
                        lifecycleOwner.lifecycleScope.launch {
                            delay(UIConstants.DelayDurations.FOCUS_CHANGE_DELAY_MS)
                            if (fragmentView != null && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                                onCategoryFocused(category)
                            }
                        }
                },
            )
        // State restoration policy: RecyclerView veri yüklenene kadar state restore etmesin
        categoryAdapter.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY

        contentAdapter =
            ContentAdapter(
                onContentClick = { content ->
                    onContentClicked(content)
                },
                onContentLongPress = { content ->
                    onContentLongPressed(content)
                },
                gridColumnCount = gridColumnCount,
                onOkButtonLongPress = null, // Default: no OK button long press support
            )
        // State restoration policy: RecyclerView veri yüklenene kadar state restore etmesin
        contentAdapter.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
    }

    /**
     * Kategori RecyclerView'ı setup eder.
     */
    private fun setupCategoriesRecyclerView() {
        categoriesRecyclerView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        categoriesRecyclerView.adapter = categoryAdapter
        categoriesRecyclerView.setHasFixedSize(true)
        categoriesRecyclerView.isDrawingCacheEnabled = true
        categoriesRecyclerView.itemAnimator = null

        // Kategori -> İçerik geçişi callback'i BaseBrowseFragment'ta focusDelegate oluşturulduktan sonra ayarlanacak

        // Kategori seçimi: OK tuşuna basıldığında kategori seç (içerik grid'ine gitme)
        categoriesRecyclerView.onCategoryClickCallback = {
            // Focus edilmiş kategoriyi bul ve seç
            val focusedView = categoriesRecyclerView.findFocus()
            if (focusedView != null) {
                val layoutManager = categoriesRecyclerView.layoutManager
                if (layoutManager != null) {
                    try {
                        var parentView: View? = focusedView
                        while (parentView != null && parentView.parent != categoriesRecyclerView) {
                            parentView = parentView.parent as? View
                        }
                        if (parentView != null) {
                            val position = layoutManager.getPosition(parentView)
                            if (position != RecyclerView.NO_POSITION && position >= 0 && position < categoryAdapter.currentList.size) {
                                val category = categoryAdapter.currentList[position]
                                onCategoryClicked(category)
                            }
                        }
                    } catch (e: Exception) {
                        // Error handling: Log only in debug builds
                        if (com.pnr.tv.BuildConfig.DEBUG) {
                            Log.e("BrowseSetupDelegate", "onCategoryClickCallback error: ${e.message}", e)
                        }
                    }
                }
            }
        }
    }

    /**
     * İçerik RecyclerView'ı setup eder.
     */
    private fun setupContentRecyclerView() {
        // Sol yön tuşu ile kategorilere geçiş callback'i BaseBrowseFragment'ta focusDelegate oluşturulduktan sonra ayarlanacak

        // Premium callback kaldırıldı - Android TV'nin doğal focus sistemine izin ver

        // Custom GridLayoutManager kullan
        contentRecyclerView.layoutManager =
            CustomGridLayoutManager(
                context,
                gridColumnCount,
            )
        contentRecyclerView.adapter = contentAdapter
        contentRecyclerView.setHasFixedSize(true)
        contentRecyclerView.isDrawingCacheEnabled = true
        contentRecyclerView.itemAnimator = null

        // ODAK KİLİDİ: Başlangıçta descendantFocusability'yi normal ayarla
        contentRecyclerView.descendantFocusability = android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS

        // View cache ayarları - performans için
        contentRecyclerView.setItemViewCacheSize(
            DatabaseConstants.CacheSizes.RECYCLER_VIEW_ITEM_CACHE_SIZE,
        ) // Önceden oluşturulmuş view'ları cache'le
        contentRecyclerView.recycledViewPool.setMaxRecycledViews(0, 15) // View pool boyutu

        // Odak sınırlarını kontrol etme işini artık sadece CustomGridLayoutManager yapacak
        // Gereksiz ve hatalı key listener ve focus change listener blokları kaldırıldı

        // CustomContentRecyclerView zaten dispatchKeyEvent'i override ediyor, ekstra setup gerekmiyor
    }
}
