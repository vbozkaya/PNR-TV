package com.pnr.tv.core.base

import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewParent
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.pnr.tv.model.CategoryItem
import com.pnr.tv.model.ContentItem
import com.pnr.tv.ui.browse.CategoryAdapter
import com.pnr.tv.ui.browse.ContentAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * BaseBrowseFragment içindeki veri gözlemleme mantığını yöneten sınıf.
 *
 * Bu sınıf, BaseBrowseFragment'tan veri akış yönetimi sorumluluklarını ayırarak
 * Fragment'ın daha temiz ve bakımı kolay olmasını sağlar.
 *
 * Sorumlulukları:
 * - Kategoriler flow'unu dinleme ve adapter'a submit etme
 * - İçerik flow'unu dinleme ve adapter'a submit etme
 * - Seçili kategori flow'unu dinleme
 * - Focus restore işlemleri
 * - Empty state güncellemeleri
 */
@FlowPreview
class BrowseDataObserver(
    private val lifecycleOwner: LifecycleOwner,
    private val viewModel: BaseViewModel,
    private val categoryAdapter: CategoryAdapter,
    private val contentAdapter: ContentAdapter,
    private val focusDelegate: BrowseFocusDelegate,
    private val uiHandler: BrowseUiHandler,
    private val fragment: BaseBrowseFragment,
    private val categoriesRecyclerView: CustomCategoriesRecyclerView,
    private val contentRecyclerView: CustomContentRecyclerView,
) {
    // Son submit edilen kategoriler (referans eşitliği kontrolü için)
    private var lastSubmittedCategories: List<CategoryItem>? = null

    // Son submit edilen içerikler (referans eşitliği kontrolü için)
    private var lastSubmittedContents: List<ContentItem>? = null

    // Mevcut seçili kategori ID'si (submitList callback'inde blocking çağrı yapmamak için)
    var currentSelectedCategoryId: String? = null

    // Geri dönüşte restore edilecek pozisyon (onResume'da set edilir, submitList callback'inde kullanılır)
    var pendingRestorePosition: Int? = null

    // Önceki seçili kategori ID'si (observeSelectedCategory için)
    private var previousSelectedCategoryId: String? = null

    /**
     * Tüm flow'ları dinlemeye başlar.
     * BaseBrowseFragment'tan initializeViews() sonunda çağrılmalıdır.
     */
    fun startObserving() {
        observeCategories()
        observeContents()
        observeSelectedCategory()
    }

    /**
     * Kategoriler flow'unu dinler ve adapter'a submit eder.
     * OPTİMİZASYON: repeatOnLifecycle yerine tek seferlik collect - veriler sadece "Güncelle" butonuna basıldığında değişir
     */
    private fun observeCategories() {
        Timber.tag("FOCUS_INIT").d("═══════════════════════════════════════════════════════")
        Timber.tag("FOCUS_INIT").d("observeCategories() başlatıldı")
        lifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
            // repeatOnLifecycle yerine tek seferlik collect - veriler sadece "Güncelle" butonuna basıldığında değişir
            fragment.categoriesFlow
                .distinctUntilChanged { old, new ->
                    // Referans eşitliği kontrolü (aynı liste instance'ı ise değişiklik yok)
                    if (old === new) {
                        return@distinctUntilChanged true
                    }
                    // Size ve ID'leri kontrol et
                    val isEqual = old.size == new.size && old.map { it.categoryId } == new.map { it.categoryId }
                    isEqual
                }
                .debounce(150) // Çok hızlı emit'leri birleştir (150ms)
                .collectLatest { categories ->
                    Timber.d("Kategoriler yüklendi: count = ${categories.size}")
                    Timber.tag("FOCUS_INIT").d("→ categoriesFlow.collectLatest çağrıldı")
                    Timber.tag("FOCUS_INIT").d("→ categories.size: ${categories.size}")
                    Timber.tag("FOCUS_INIT").d("→ lastSubmittedCategories: ${lastSubmittedCategories != null}")

                    // submitList() öncesi kontrol: Aynı liste instance'ı ise submitList() çağrılmaz - Yanıp sönme önlendi
                    if (categories === lastSubmittedCategories) {
                        Timber.tag("FOCUS_INIT").d("→ Aynı liste instance'ı, submitList atlanıyor")
                        return@collectLatest
                    }

                    // İlk yükleme kontrolü: Kategoriler listesi boş değilse ve ilk kez yükleniyorsa fade-in animasyonu için alpha'yı 0 yap
                    val isFirstLoad = lastSubmittedCategories == null && categories.isNotEmpty()
                    val lastSubmitted = lastSubmittedCategories
                    val isSizeChange = lastSubmitted != null && lastSubmitted.size != categories.size
                    Timber.tag("FOCUS_INIT").d("→ isFirstLoad: $isFirstLoad")
                    Timber.tag("FOCUS_INIT").d("→ isSizeChange: $isSizeChange")

                    // İlk yükleme veya kategori sayısı değiştiğinde (1'den 108'e çıktığı an) alpha'yı 0 yap
                    if (isFirstLoad || isSizeChange) {
                        categoriesRecyclerView.alpha = 0f
                    }

                    // Visibility check: Ensure categoriesRecyclerView is always VISIBLE when categories are loaded
                    categoriesRecyclerView.visibility = View.VISIBLE

                    lastSubmittedCategories = categories
                    Timber.tag("FOCUS_INIT").d("→ categoryAdapter.submitList() çağrılıyor")
                    
                    // Fallback: If list is empty, ensure adapter is notified
                    if (categories.isEmpty()) {
                        Timber.tag("FOCUS_INIT").d("→ categories.isEmpty(), fallback notifyDataSetChanged")
                        try {
                            // ListAdapter doesn't expose notifyDataSetChanged, but we can submit empty list
                            categoryAdapter.submitList(emptyList()) {
                                categoriesRecyclerView.requestLayout()
                                categoriesRecyclerView.invalidate()
                                categoriesRecyclerView.visibility = View.VISIBLE
                                categoriesRecyclerView.alpha = 1f
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Error submitting empty categories list")
                        }
                        return@collectLatest
                    }
                    
                    categoryAdapter.submitList(categories) {
                        Timber.tag("FOCUS_INIT").d("→ submitList callback çağrıldı")
                        // submitList callback - DiffUtil tamamlandığında çağrılır
                        // Bu callback içinde kategori seçimi yapılmalı (yanıp sönmeyi önlemek için)

                        // Force layout update: Ensure RecyclerView is rendered
                        categoriesRecyclerView.requestLayout()
                        categoriesRecyclerView.invalidate()
                        
                        // Visibility check: Ensure categoriesRecyclerView is always VISIBLE and alpha is 1f
                        categoriesRecyclerView.visibility = View.VISIBLE
                        // Only set alpha to 1f if not animating (if not first load or size change)
                        if (!isFirstLoad && !isSizeChange) {
                            categoriesRecyclerView.alpha = 1f
                        }
                        
                        // Post-delayed check: Force refresh if RecyclerView still not rendering after 100ms
                        Handler(Looper.getMainLooper()).postDelayed({
                            if (categoryAdapter.itemCount > 0 && categoriesRecyclerView.childCount == 0) {
                                Timber.tag("FOCUS_INIT").w("→ RecyclerView not rendering after submitList, forcing refresh")
                                categoriesRecyclerView.requestLayout()
                                categoriesRecyclerView.invalidate()
                                categoriesRecyclerView.visibility = View.VISIBLE
                                categoriesRecyclerView.alpha = 1f
                            }
                        }, 100)

                        // İlk yükleme fade-in animasyonu kaldırıldı - odaklama işlemi bittikten sonra yapılacak

                        // INITIAL LAUNCH kontrolü: Ana menüden ilk kez açılıyorsa restore yapma
                        val isInitialLaunch = fragment.arguments?.getBoolean("is_initial_launch", false) ?: false
                        Timber.tag("FOCUS_INIT").d("→ isInitialLaunch: $isInitialLaunch")

                        // Kategori restore: ViewModel'den kategori ID'sini al ve seç
                        val categoryIdToRestore = viewModel.lastSelectedCategoryId
                        Timber.tag("FOCUS_INIT").d("→ categoryIdToRestore: $categoryIdToRestore")
                        Timber.tag("FOCUS_INIT").d("→ categories.isNotEmpty(): ${categories.isNotEmpty()}")

                        // Ana sayfadan geldiğinde categoryIdToRestore = "0" ise restore yapma, direkt ilk kategoriye focus ver
                        val shouldRestore =
                            categoryIdToRestore != null &&
                                categoryIdToRestore != "0" &&
                                categories.isNotEmpty() &&
                                !isInitialLaunch

                        if (shouldRestore) {
                            Timber.tag("FOCUS_INIT").d("→ Restore işlemi yapılıyor (categoryIdToRestore: $categoryIdToRestore)")
                            // Kategoriyi seç
                            fragment.selectCategoryById(categoryIdToRestore)

                            // Restore edilecek kategoriyi bul ve seçili olarak işaretle
                            val restoredCategory =
                                categories.find {
                                    val catId = categoryIdToRestore
                                    it.categoryId == catId ||
                                        it.categoryId == catId?.toIntOrNull()?.toString() ||
                                        it.categoryId.toIntOrNull()?.toString() == catId
                                }

                            if (restoredCategory != null) {
                                categoryAdapter.updateSelectedItem(categoryIdToRestore)
                                focusDelegate.currentSelectedCategoryId = categoryIdToRestore
                            }

                            // ViewModel'deki kategoriyi temizle
                            viewModel.lastSelectedCategoryId = null
                            fragment.clearSavedLastSelectedCategoryId()
                        } else if (categories.isNotEmpty()) {
                            // Ana sayfadan geldiğinde "0" değerini temizle (restore yapılmasın)
                            if (categoryIdToRestore == "0") {
                                Timber.tag("FOCUS_INIT").d("→ categoryIdToRestore = '0' (ana sayfadan geliş), restore atlanıyor")
                                viewModel.lastSelectedCategoryId = null
                            }
                            Timber.tag("FOCUS_INIT").d("→ else if (categories.isNotEmpty()) bloğuna girildi")

                            // Gereksiz odaklanmaları filtrele: Liste boyutu 1 veya daha az ise odak verme işlemini tetikleme
                            if (categories.size <= 1) {
                                Timber.tag(
                                    "FOCUS_INIT",
                                ).d("→ categories.size <= 1 (size: ${categories.size}), focus verme işlemi atlanıyor (geçici veri)")
                                return@submitList
                            }

                            // [FIX]: Eğer içerik tarafında bekleyen bir restore işlemi varsa (pendingRestorePosition veya viewModel'de kayıtlıysa),
                            // Kategori listesi odağı ÇALMAMALI.
                            val hasPendingContentRestore = pendingRestorePosition != null || viewModel.lastFocusedContentPosition != null
                            Timber.tag("FOCUS_INIT").d("→ hasPendingContentRestore: $hasPendingContentRestore")
                            Timber.tag("FOCUS_INIT").d("→ pendingRestorePosition: $pendingRestorePosition")
                            Timber.tag("FOCUS_INIT").d("→ viewModel.lastFocusedContentPosition: ${viewModel.lastFocusedContentPosition}")

                            if (!hasPendingContentRestore) {
                                // Ana sayfadan geldi veya isInitialLaunch - ilk kategoriye focus ver
                                Timber.tag("FOCUS_INIT").d("═══════════════════════════════════════════════════════")
                                Timber.tag("FOCUS_INIT").d("İlk kategoriye focus verme işlemi başlatılıyor")
                                Timber.tag("FOCUS_INIT").d("isInitialLaunch: $isInitialLaunch")
                                Timber.tag("FOCUS_INIT").d("hasPendingContentRestore: $hasPendingContentRestore")
                                Timber.tag("FOCUS_INIT").d("categories.isNotEmpty(): ${categories.isNotEmpty()}")
                                Timber.tag("FOCUS_INIT").d("categories.count(): ${categories.count()}")

                                if (isInitialLaunch) {
                                    Timber.tag("FOCUS_INIT").d("→ isInitialLaunch=true, doOnPreDraw kullanılıyor")
                                    // İlk açılışta: Listenin tamamen çizilmesini bekle (doOnPreDraw)
                                    categoriesRecyclerView.doOnPreDraw {
                                        Timber.tag("FOCUS_INIT").d("→ doOnPreDraw callback çağrıldı, postDelayed(150ms) başlatılıyor")
                                        // Eğer hala Selected position: -1 alınıyorsa, sistemin odak arama algoritmasına zaman tanı
                                        Handler(Looper.getMainLooper()).postDelayed({
                                            Timber.tag(
                                                "FOCUS_INIT",
                                            ).d("→ postDelayed(150ms) callback çağrıldı, focusFirstCategory() çağrılıyor")
                                            focusDelegate.focusFirstCategory(
                                                categories.first(),
                                                onCategoryClicked = { category -> fragment.onCategoryClicked(category) },
                                            ) {
                                                // Odaklama işlemi bittikten sonra kategori listesini yumuşakça göster
                                                if (isFirstLoad || isSizeChange) {
                                                    categoriesRecyclerView.visibility = View.VISIBLE
                                                    categoriesRecyclerView.animate()
                                                        .alpha(1f)
                                                        .setDuration(300)
                                                        .start()
                                                } else {
                                                    // Ensure visibility and alpha even if not animating
                                                    categoriesRecyclerView.visibility = View.VISIBLE
                                                    categoriesRecyclerView.alpha = 1f
                                                }
                                                // Force layout update after focus
                                                categoriesRecyclerView.requestLayout()
                                                categoriesRecyclerView.invalidate()
                                            }
                                        }, 150)
                                    }
                                } else {
                                    Timber.tag("FOCUS_INIT").d("→ isInitialLaunch=false, doğrudan focusFirstCategory() çağrılıyor")
                                    focusDelegate.focusFirstCategory(
                                        categories.first(),
                                        onCategoryClicked = { category -> fragment.onCategoryClicked(category) },
                                    ) {
                                        // Odaklama işlemi bittikten sonra kategori listesini yumuşakça göster
                                        if (isFirstLoad || isSizeChange) {
                                            categoriesRecyclerView.visibility = View.VISIBLE
                                            categoriesRecyclerView.animate()
                                                .alpha(1f)
                                                .setDuration(300)
                                                .start()
                                        } else {
                                            // Ensure visibility and alpha even if not animating
                                            categoriesRecyclerView.visibility = View.VISIBLE
                                            categoriesRecyclerView.alpha = 1f
                                        }
                                        // Force layout update after focus
                                        categoriesRecyclerView.requestLayout()
                                        categoriesRecyclerView.invalidate()
                                    }
                                }
                                Timber.tag("FOCUS_INIT").d("═══════════════════════════════════════════════════════")
                            } else {
                                Timber.tag("FOCUS_INIT").d("⚠️ hasPendingContentRestore=true, focus verilmiyor")
                                // Even if not focusing, ensure visibility and render
                                categoriesRecyclerView.visibility = View.VISIBLE
                                if (!isFirstLoad && !isSizeChange) {
                                    categoriesRecyclerView.alpha = 1f
                                }
                                // Force layout update
                                categoriesRecyclerView.requestLayout()
                                categoriesRecyclerView.invalidate()
                            }
                        } else {
                            // Categories list is empty - still ensure visibility
                            categoriesRecyclerView.visibility = View.VISIBLE
                            categoriesRecyclerView.alpha = 1f
                            categoriesRecyclerView.requestLayout()
                            categoriesRecyclerView.invalidate()
                        }
                    } // submitList callback kapanışı
                } // collectLatest lambda kapanışı
        } // launch kapanışı
    } // observeCategories() kapanışı

    /**
     * İçerik flow'unu dinler ve adapter'a submit eder.
     *
     * OPTİMİZASYON:
     * - Boş listeleri filtrele (geçiş anlarında yayınlanan 0 boyutlu listeleri engelle)
     * - distinctUntilChanged() ile aynı içeriğin tekrar submit edilmesini engelle
     * - Referans eşitliği kontrolü: Aynı liste instance'ı ise submitList() çağrılmaz
     * - repeatOnLifecycle yerine tek seferlik collect: Fragment resume olduğunda gereksiz yere Flow tekrar collect edilmez
     * - Veri seti içeriği değişmediği sürece submitList asla çağrılmamalı
     */
    private fun observeContents() {
        lifecycleOwner.lifecycleScope.launch {
            fragment.contentsFlow
                .filter { it.isNotEmpty() }
                .distinctUntilChanged()
                .debounce(300L) // Add debounce to buffer rapid updates
                .collectLatest { contents ->
                    if (contents === lastSubmittedContents) {
                        return@collectLatest
                    }

                    // İlk yükleme kontrolü: İçerik listesi boş değilse ve ilk kez yükleniyorsa fade-in animasyonu için alpha'yı 0 yap
                    val isFirstLoad = lastSubmittedContents == null && contents.isNotEmpty()
                    if (isFirstLoad) {
                        contentRecyclerView.alpha = 0f
                    }

                    lastSubmittedContents = contents

                    // submitList callback ile fade-in animasyonu
                    contentAdapter.submitList(contents) {
                        // İlk yüklemede fade-in animasyonu
                        if (isFirstLoad) {
                            contentRecyclerView.animate()
                                .alpha(1f)
                                .setDuration(250)
                                .start()
                        }
                    }

                    // Update empty state and handle focus restoration after list is submitted
                    handleContentUpdate(contents)
                }
        }
    }

    /**
     * İçerik güncellemesini işler.
     * Empty state güncellemesi ve focus restore mantığını içerir.
     */
    private fun handleContentUpdate(contents: List<ContentItem>) {
        // Use a non-blocking way to get the selected category ID
        val selectedCategoryId = currentSelectedCategoryId
        fragment.updateEmptyState(contents, selectedCategoryId)

        // Handle focus restoration logic here, outside of the submitList callback
        val pendingPos = pendingRestorePosition
        val itemCount = contentAdapter.itemCount
        val lastSelectedCategoryId = viewModel.lastSelectedCategoryId

        val shouldRestore =
            lastSelectedCategoryId != null && selectedCategoryId != null &&
                (
                    lastSelectedCategoryId == selectedCategoryId ||
                        lastSelectedCategoryId == selectedCategoryId.toIntOrNull()?.toString() ||
                        lastSelectedCategoryId.toIntOrNull()?.toString() == selectedCategoryId
                )

        if (pendingPos != null && pendingPos < itemCount && shouldRestore) {
            contentRecyclerView.doOnPreDraw {
                val viewHolder = contentRecyclerView.findViewHolderForAdapterPosition(pendingPos)
                viewHolder?.itemView?.requestFocus()
                // Player/detay sayfasından dönüşte focus'un tam olarak güncellenmesini garanti altına al
                contentRecyclerView.post {
                    // Focus'un sistem tarafından tamamen güncellendiğinden emin olmak için requestFocus çağrısını yinele
                    val currentFocus = fragment.activity?.window?.currentFocus
                    if (currentFocus != null) {
                        // View'un içerik grid hiyerarşisi içinde olup olmadığını kontrol et
                        var parent: ViewParent? = currentFocus.parent
                        var isInContentGrid = false
                        while (parent != null) {
                            if (parent is View && parent === contentRecyclerView) {
                                isInContentGrid = true
                                break
                            }
                            parent = parent.parent
                        }
                        if (isInContentGrid) {
                            // Focus zaten içerik grid'inde, bir kez daha requestFocus ile garanti altına al
                            contentRecyclerView.requestFocus()
                        }
                    }
                }
                pendingRestorePosition = null
                viewModel.lastFocusedContentPosition = null
            }
        }
    }

    /**
     * Seçili kategori flow'unu dinler ve adapter'ı günceller.
     */
    private fun observeSelectedCategory() {
        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                fragment.selectedCategoryIdFlow.collectLatest { selectedCategoryId ->
                    // Seçili kategori ID'sini sakla (navigateFocusToCategories ve submitList callback için)

                    // State variable'ı güncelle (submitList callback'inde blocking çağrı yapmamak için)
                    currentSelectedCategoryId = selectedCategoryId

                    // Restore işlemi devam ediyorsa, updateSelectedItem() çağrısını yap (kategori seçimi değişiyor)
                    // Ama sadece kategori ID'si değiştiyse (gereksiz güncellemeyi önlemek için)
                    val previousCategoryId = focusDelegate.currentSelectedCategoryId
                    focusDelegate.currentSelectedCategoryId = selectedCategoryId
                    previousSelectedCategoryId = selectedCategoryId

                    // Kategori seçim durumu: Her zaman updateSelectedItem çağrılmalı (persistent selection için)
                    if (selectedCategoryId != previousCategoryId) {
                        categoryAdapter.updateSelectedItem(selectedCategoryId)
                    }

                    // Not: Initial focus logic artık observeCategories() içinde yapılıyor
                    // Burada sadece updateSelectedItem() çağrılıyor
                }
            }
        }
    }
}
