package com.pnr.tv.ui.base

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import coil.imageLoader
import com.pnr.tv.R
import com.pnr.tv.extensions.hide
import com.pnr.tv.extensions.show
import com.pnr.tv.model.CategoryItem
import com.pnr.tv.model.ContentItem
import com.pnr.tv.ui.browse.CategoryAdapter
import com.pnr.tv.ui.browse.ContentAdapter
import com.pnr.tv.util.BackgroundManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Toolbar kontrolü için arayüz.
 * Activity'lerin toolbar'ı gizleme/gösterme işlevlerini standartlaştırır.
 */
interface ToolbarController {
    fun hideTopMenu()

    fun showTopMenu()
}

/**
 * Tüm browse fragment'ları için ortak işlevleri içeren base sınıf.
 *
 * Bu sınıf, kategori listesi ve içerik grid'i olan tüm sayfalar için ortak kodları içerir:
 * - Navbar setup
 * - Kategori listesi setup (CategoryAdapter ile)
 * - İçerik grid setup (ContentAdapter ile)
 * - Flow collection ve adapter updates
 * - Toast observer setup
 * - Focus yönetimi (DPAD navigation between categories and content)
 * - Empty state yönetimi
 *
 * BaseBrowseFragment owns and manages CategoryAdapter and ContentAdapter instances.
 * It does NOT know about specific entities (MovieEntity, LiveStreamEntity, etc.).
 *
 * Child fragment'lar sadece:
 * - categoriesFlow ve contentsFlow sağlamalı (Flow<List<CategoryItem>> / Flow<List<ContentItem>>)
 * - onCategoryClicked, onContentClicked, onContentLongPressed implement etmeli
 * - View ID'lerini sağlamalı (getCategoriesRecyclerViewId, getContentRecyclerViewId, etc.)
 * - Optional: onCategoryFocused, onNavigateFromCategoriesToContent, onFocusLeftFromContentGrid
 */
abstract class BaseBrowseFragment : Fragment() {
    companion object {
        private const val KEY_LAST_FOCUSED_CONTENT_POSITION = "last_focused_content_position"
    }

    // RecyclerView'lar - child fragment'larda initialize edilmeli
    protected lateinit var categoriesRecyclerView: CustomCategoriesRecyclerView
    protected lateinit var contentRecyclerView: CustomContentRecyclerView
    protected lateinit var emptyStateTextView: TextView

    // Error and Loading containers
    protected var errorContainer: View? = null
    protected var errorMessage: TextView? = null
    protected var retryButton: android.widget.Button? = null
    protected var loadingContainer: View? = null

    // Navbar view - focus yönetimi için
    protected var navbarView: View? = null

    // Generic adapters - BaseBrowseFragment manages these
    protected lateinit var categoryAdapter: CategoryAdapter
    protected lateinit var contentAdapter: ContentAdapter

    /**
     * Alt fragment'ların kendi ViewModel'ını bu base sınıfa tanıtmasını sağlayan soyut değişken.
     */
    protected abstract val viewModel: BaseViewModel

    // Seçili kategori ID'sini saklamak için (navigateFocusToCategories için)
    private var currentSelectedCategoryId: String? = null

    // Fragment replace edildiğinde geri yüklenecek pozisyon ve kategori
    // onPause() içinde kaydedilir, kategoriler yüklendikten sonra geri yüklenir
    private var savedLastFocusedPosition: Int? = null
    private var savedLastSelectedCategoryId: String? = null

    // Bundle'dan okunan pozisyon (sistem tarafından destroy edildiyse)
    private var bundleSavedPosition: Int? = null

    // Kategoriler yüklendikten sonra restore edilecek kategori ID
    private var pendingCategoryIdToRestore: String? = null

    // Kategoriler yüklendikten sonra restore edilecek pozisyon
    private var pendingPositionToRestore: Int? = null

    // OK veya sağ yön tuşuna basıldığında içerik grid'ine geçiş yapılacak mı?
    private var pendingNavigationToContent: Boolean = false

    // onCategoryFocused için debounce - gereksiz çağrıları önlemek için
    private var categoryFocusDebounceJob: kotlinx.coroutines.Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Bundle'dan kaydedilmiş pozisyonu oku (sistem tarafından destroy edildiyse)
        bundleSavedPosition = savedInstanceState?.getInt(KEY_LAST_FOCUSED_CONTENT_POSITION, -1)?.takeIf { it != -1 }
    }

    override fun onPause() {
        super.onPause()
        // Fragment replace edilmeden önce hem kategoriyi hem de pozisyonu kaydet
        // onPause() çağrıldığında ViewModel hala mevcut olduğu için
        // değerleri kaydedebiliriz
        viewModel.lastFocusedContentPosition?.let { position ->
            savedLastFocusedPosition = position
        }
        viewModel.lastSelectedCategoryId?.let { categoryId ->
            savedLastSelectedCategoryId = categoryId
        }
        // Ayrıca currentSelectedCategoryId'yi de kaydet (eğer ViewModel'de yoksa)
        if (savedLastSelectedCategoryId == null && currentSelectedCategoryId != null) {
            savedLastSelectedCategoryId = currentSelectedCategoryId
            viewModel.lastSelectedCategoryId = currentSelectedCategoryId
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Sistem tarafından destroy edildiğinde de kaydet (configuration change vb.)
        viewModel.lastFocusedContentPosition?.let { position ->
            outState.putInt(KEY_LAST_FOCUSED_CONTENT_POSITION, position)
        }
    }

    /**
     * Abstract properties - child fragments must provide these
     * Flow of categories to display in the category list.
     */
    protected abstract val categoriesFlow: Flow<List<CategoryItem>>

    /**
     * Flow of content items to display in the content grid.
     */
    protected abstract val contentsFlow: Flow<List<ContentItem>>

    /**
     * Flow of selected category ID (String).
     */
    protected abstract val selectedCategoryIdFlow: Flow<String?>

    /**
     * Flow of toast messages to display.
     */
    protected abstract val toastEventFlow: Flow<String>

    // Abstract methods - child fragments must provide these

    /**
     * Returns the title to display in the navbar.
     */
    protected abstract fun getNavbarTitle(): String

    /**
     * Returns the RecyclerView ID for categories list.
     */
    protected abstract fun getCategoriesRecyclerViewId(): Int

    /**
     * Returns the RecyclerView ID for content grid.
     */
    protected abstract fun getContentRecyclerViewId(): Int

    /**
     * Returns the TextView ID for empty state.
     */
    protected abstract fun getEmptyStateTextViewId(): Int

    /**
     * Returns the number of columns for the content grid.
     */
    protected abstract fun getGridColumnCount(): Int

    // Overridable hooks for child fragments

    /**
     * Called when a category is clicked.
     * Child fragments should override this to handle category selection.
     */
    protected open fun onCategoryClicked(item: CategoryItem) {
        // Default: no-op, child should override
    }

    /**
     * Kategori ID'sine göre kategori seçer.
     * Child fragments should override this to handle category selection by ID.
     */
    protected open fun selectCategoryById(categoryId: String?) {
        // Default: no-op, child should override
    }

    /**
     * Called when a category receives focus.
     * Child fragments can override this for custom behavior.
     */
    protected open fun onCategoryFocused(item: CategoryItem) {
        // Default: no-op, child can override
    }

    /**
     * Called when user navigates from categories to content (e.g., DPAD_RIGHT).
     * Child fragments can override this for custom behavior.
     */
    protected open fun onNavigateFromCategoriesToContent() {
        // Default: no-op, child can override
    }

    /**
     * Called when a content item is clicked.
     * Child fragments must override this to handle navigation.
     */
    protected open fun onContentClicked(item: ContentItem) {
        // Default: no-op, child must override
    }

    /**
     * Called when a content item is long-pressed.
     * Child fragments can override this for custom actions (e.g., add to favorites).
     */
    protected open fun onContentLongPressed(item: ContentItem) {
        // Default: no-op, child can override
    }

    /**
     * Called when focus leaves the content grid (e.g., DPAD_LEFT from leftmost column).
     * Child fragments can override this for custom behavior.
     */
    protected open fun onFocusLeftFromContentGrid() {
        // Default: no-op, child can override
    }

    /**
     * Arka plan görselini güvenli bir şekilde yükler.
     * BackgroundManager kullanarak cache'lenmiş görseli yükler.
     * Tüm browse fragment'larında otomatik olarak çağrılır.
     */
    private fun loadBackground(view: View) {
        timber.log.Timber.tag("BACKGROUND").d("🎬 BaseBrowseFragment.loadBackground() çağrıldı - Fragment: ${this.javaClass.simpleName}")
        viewLifecycleOwner.lifecycleScope.launch {
            // Fragment'ın kendi root view'ına arkaplan ekle (view.rootView yerine view)
            timber.log.Timber.tag(
                "BACKGROUND",
            ).d("📐 Fragment View - View: ${view.javaClass.simpleName}, Width: ${view.width}, Height: ${view.height}")

            // Önce cache'den kontrol et (hızlı)
            val cached = BackgroundManager.getCachedBackground()
            if (cached != null) {
                timber.log.Timber.tag("BACKGROUND").d("✅ Cache'den arkaplan uygulanıyor (Fragment view)")
                view.background = cached
                timber.log.Timber.tag(
                    "BACKGROUND",
                ).d("✅ Arkaplan uygulandı - Fragment view background: ${view.background?.javaClass?.simpleName}")
                return@launch
            }

            timber.log.Timber.tag("BACKGROUND").d("⏳ Cache'de yok, yükleme başlatılıyor...")

            // Cache'de yoksa yükle
            BackgroundManager.loadBackground(
                context = requireContext(),
                imageLoader = requireContext().imageLoader,
                onSuccess = { drawable ->
                    timber.log.Timber.tag("BACKGROUND").d("✅ onSuccess callback çağrıldı - Drawable: ${drawable.javaClass.simpleName}")
                    view.background = drawable
                    timber.log.Timber.tag(
                        "BACKGROUND",
                    ).d("✅ Arkaplan uygulandı - Fragment view background: ${view.background?.javaClass?.simpleName}")
                },
                onError = {
                    timber.log.Timber.tag("BACKGROUND").w("⚠️ onError callback çağrıldı, fallback deneniyor...")
                    // Hata durumunda fallback kullan (theme'de zaten tanımlı)
                    val fallback = BackgroundManager.getFallbackBackground(requireContext())
                    if (fallback != null) {
                        view.background = fallback
                        timber.log.Timber.tag("BACKGROUND").d("✅ Fallback arkaplan uygulandı")
                    } else {
                        timber.log.Timber.tag("BACKGROUND").e("❌ Fallback arkaplan da null!")
                    }
                },
            )
        }
    }

    /**
     * Called when content should be loaded initially.
     * Child fragments should override this to trigger initial data loading.
     */
    protected open fun onInitialLoad() {
        // Default: no-op, child should override if needed
    }

    /**
     * Called to update empty state based on current content and selected category.
     * Child fragments can override this for custom empty state logic.
     */
    protected open fun updateEmptyState(
        contents: List<ContentItem>,
        selectedCategoryId: String?,
    ) {
        if (contents.isEmpty()) {
            showEmptyState("") // Empty string, child fragments can override for custom messages
        } else {
            showContentState()
        }
    }

    override fun onResume() {
        super.onResume()
        (activity as? ToolbarController)?.hideTopMenu()

        // Önce onPause()'da kaydedilmiş kategoriyi kontrol et (fragment replace edildiyse)
        // Sonra ViewModel'den kontrol et (normal durum - fragment destroy edilmediyse)
        val categoryIdToRestore =
            savedLastSelectedCategoryId
                ?: viewModel.lastSelectedCategoryId

        // Kategoriyi pending olarak işaretle (observeCategories() içinde restore yapılacak)
        // Bu şekilde kategoriler kesinlikle yüklendikten sonra restore yapılır
        if (categoryIdToRestore != null) {
            // onPause()'da kaydedilmiş kategoriyi ViewModel'e set et (eğer varsa)
            if (savedLastSelectedCategoryId != null) {
                viewModel.lastSelectedCategoryId = savedLastSelectedCategoryId
            }
            // Her zaman pending olarak işaretle, observeCategories() içinde restore yapılacak
            pendingCategoryIdToRestore = categoryIdToRestore
        }

        // Önce onPause()'da kaydedilmiş pozisyonu kontrol et (fragment replace edildiyse)
        // Sonra ViewModel'den kontrol et (normal durum - fragment destroy edilmediyse)
        // Son olarak Bundle'dan kontrol et (sistem tarafından destroy edildiyse)
        val positionToRestore =
            savedLastFocusedPosition
                ?: viewModel.lastFocusedContentPosition
                ?: bundleSavedPosition

        // Pozisyonu pending olarak işaretle (observeContents() içinde restore yapılacak)
        // Bu şekilde içerikler kesinlikle yüklendikten sonra restore yapılır
        if (positionToRestore != null) {
            // onPause()'da kaydedilmiş pozisyonu ViewModel'e set et (eğer varsa)
            if (savedLastFocusedPosition != null) {
                viewModel.lastFocusedContentPosition = savedLastFocusedPosition
            }
            // Her zaman pending olarak işaretle, observeContents() içinde restore yapılacak
            pendingPositionToRestore = positionToRestore
        }
    }

    /**
     * View'ları initialize eder ve setup işlemlerini başlatır.
     * Child fragments should call this in onViewCreated.
     */
    protected fun initializeViews(view: View) {
        // Background'u yükle (tüm browse fragment'larında)
        loadBackground(view)

        // Initialize RecyclerViews and TextView
        categoriesRecyclerView = view.findViewById(getCategoriesRecyclerViewId())
        contentRecyclerView = view.findViewById(getContentRecyclerViewId())
        emptyStateTextView = view.findViewById(getEmptyStateTextViewId())

        // Initialize error and loading containers (optional - may not exist in all layouts)
        errorContainer = view.findViewById(R.id.error_container)
        errorMessage = view.findViewById(R.id.txt_error_message)
        retryButton = view.findViewById(R.id.btn_retry)
        loadingContainer = view.findViewById(R.id.loading_container)

        // Setup retry button click listener
        retryButton?.setOnClickListener {
            onRetryClicked()
        }

        // Setup navbar
        setupNavbar(view)

        // Create adapters
        createAdapters()

        // Setup RecyclerViews with adapters
        setupCategoriesRecyclerView()
        setupContentRecyclerView()

        // Setup flow observers
        observeCategories()
        observeContents()
        observeSelectedCategory()
        setupToastObserver()
        setupBackPressListener()
    }

    /**
     * Navbar'ı setup eder.
     */
    private fun setupNavbar(view: View) {
        navbarView = view.findViewById<View>(R.id.navbar)
        val titleTextView = navbarView?.findViewById<TextView>(R.id.txt_navbar_title)
        titleTextView?.text = getNavbarTitle()

        val backButton = navbarView?.findViewById<View>(R.id.btn_navbar_back)
        backButton?.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        val homeButton = navbarView?.findViewById<View>(R.id.btn_navbar_home)
        homeButton?.setOnClickListener {
            parentFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }

        // Navbar -> Kategori geçişi için listener (sadece back button için)
        val navbarDownListener =
            View.OnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    categoriesRecyclerView.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
                    return@OnKeyListener true // Olayı Tüket!
                }
                false
            }

        backButton?.setOnKeyListener(navbarDownListener)

        // Listener for Home Button (manages DOWN key)
        homeButton?.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        // Aşağı tuşu: İlk kategoriye odaklan ve olayı tüket.
                        categoriesRecyclerView.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
                        return@setOnKeyListener true
                    }
                }
            }
            // Diğer tüm tuşlar için varsayılan davranışa izin ver (sağa basıldığında arama çubuğuna geçiş yapılabilir).
            false
        }

        // Listener for Search Bar (manages DOWN key for empty state navigation)
        val searchEditText = navbarView?.findViewById<View>(R.id.edt_navbar_search)
        searchEditText?.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        // Aşağı tuşu: Eğer içerik listesi boşsa ve empty state görünürse, focus'u empty state'e taşı
                        if (emptyStateTextView.visibility == View.VISIBLE && contentRecyclerView.visibility != View.VISIBLE) {
                            emptyStateTextView.requestFocus()
                            return@setOnKeyListener true
                        }
                        // İçerik varsa varsayılan davranışa izin ver (kategorilere veya grid'e geçiş)
                    }
                }
            }
            // Sol tuşu ve diğer tuşlar için varsayılan davranışa izin ver (sola basıldığında Home butonuna geçiş yapılabilir).
            false
        }

        // Filter butonunu setup et
        setupFilterButton()
    }

    /**
     * Filter butonunu setup eder. Sadece filmler ve diziler sayfalarında gösterilir.
     */
    protected open fun setupFilterButton() {
        val filterButton = navbarView?.findViewById<View>(R.id.btn_navbar_filter)
        val searchEditText = navbarView?.findViewById<android.widget.EditText>(R.id.edt_navbar_search)
        // Varsayılan olarak gizli - child fragment'lar override ederek gösterir
        filterButton?.visibility = View.GONE
        searchEditText?.visibility = View.GONE
    }

    /**
     * Generic adapters oluşturur.
     * BaseBrowseFragment creates and owns the CategoryAdapter and ContentAdapter instances.
     * Child fragments can override this to customize adapter creation (e.g., add OK button long press support).
     */
    protected open fun createAdapters() {
        categoryAdapter =
            CategoryAdapter(
                onCategoryClick = { category ->
                    onCategoryClicked(category)
                },
                onNavigateToContent = ::navigateFocusToContent,
                onNavigateToNavbar = ::navigateFocusToNavbar,
                onCategoryFocused = { category ->
                    // Debounce: Önceki çağrıyı iptal et ve yeni bir delay başlat
                    categoryFocusDebounceJob?.cancel()
                    categoryFocusDebounceJob = viewLifecycleOwner.lifecycleScope.launch {
                        kotlinx.coroutines.delay(100) // 100ms debounce (daha hızlı tepki için azaltıldı)
                        // Hala fragment aktifse ve kategori hala aynıysa callback çağır
                        if (viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                            // Kategori focus'unu koru - callback çağrılmadan önce focus'un kaybolmadığından emin ol
                            val currentFocusedView = categoriesRecyclerView.findFocus()
                            onCategoryFocused(category)
                            // Callback sonrası focus'u koru
                            if (currentFocusedView != null && !currentFocusedView.hasFocus()) {
                                currentFocusedView.post {
                                    if (!currentFocusedView.hasFocus()) {
                                        currentFocusedView.requestFocus()
                                    }
                                }
                            }
                        }
                    }
                },
            )

        contentAdapter =
            ContentAdapter(
                onContentClick = { content ->
                    onContentClicked(content)
                },
                onContentLongPress = { content ->
                    onContentLongPressed(content)
                },
                gridColumnCount = getGridColumnCount(),
                onOkButtonLongPress = null, // Default: no OK button long press support
            )
    }

    /**
     * Kategori RecyclerView'ı setup eder.
     */
    private fun setupCategoriesRecyclerView() {
        categoriesRecyclerView.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
        categoriesRecyclerView.adapter = categoryAdapter
        categoriesRecyclerView.setHasFixedSize(true)
        categoriesRecyclerView.isDrawingCacheEnabled = true
        categoriesRecyclerView.itemAnimator = null
    }

    /**
     * İçerik RecyclerView'ı setup eder.
     */
    private fun setupContentRecyclerView() {
        // Sol yön tuşu ile kategorilere geçiş için callback
        contentRecyclerView.onNavigateToCategoriesCallback = {
            navigateFocusToCategories()
        }
        // Custom GridLayoutManager kullan
        contentRecyclerView.layoutManager =
            CustomGridLayoutManager(
                requireContext(),
                getGridColumnCount(),
            )
        contentRecyclerView.adapter = contentAdapter
        contentRecyclerView.setHasFixedSize(true)
        contentRecyclerView.isDrawingCacheEnabled = true
        contentRecyclerView.itemAnimator = null

        // View cache ayarları - performans için
        contentRecyclerView.setItemViewCacheSize(20) // Önceden oluşturulmuş view'ları cache'le
        contentRecyclerView.recycledViewPool.setMaxRecycledViews(0, 15) // View pool boyutu

        // Odak sınırlarını kontrol etme işini artık sadece CustomGridLayoutManager yapacak
        // Gereksiz ve hatalı key listener ve focus change listener blokları kaldırıldı

        // CustomContentRecyclerView zaten dispatchKeyEvent'i override ediyor, ekstra setup gerekmiyor
    }

    /**
     * Kategoriler flow'unu dinler ve adapter'a submit eder.
     */
    private fun observeCategories() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                categoriesFlow
                    .distinctUntilChanged { old, new ->
                        old.size == new.size && old.map { it.categoryId } == new.map { it.categoryId }
                    }
                    .collectLatest { categories ->
                        categoryAdapter.submitList(categories)

                        // Eğer restore edilecek bir kategori varsa, önce onu seç
                        // Kategoriler yüklendikten sonra hemen restore yap
                        if (pendingCategoryIdToRestore != null && categories.isNotEmpty()) {
                            val categoryIdToRestore = pendingCategoryIdToRestore
                            pendingCategoryIdToRestore = null

                            Timber.tag("FOCUS_DEBUG").d("🔄 observeCategories() - Kategori restore ediliyor: $categoryIdToRestore")

                            // Kategoriyi seç (child fragment'lar bu metodu override ederek ViewModel'e bildirecek)
                            selectCategoryById(categoryIdToRestore)

                            // ViewModel'deki kategoriyi temizle
                            viewModel.lastSelectedCategoryId = null
                            savedLastSelectedCategoryId = null

                            // Kategori seçildikten sonra içerikler yüklenecek,
                            // içerikler yüklendikten sonra pozisyon restore edilecek
                            // (observeContents içinde yapılacak)
                        }

                        // Anahtar, fragment'larda tanımlananla aynı olmalı
                        val isInitialLaunch = arguments?.getBoolean("is_initial_launch", false) ?: false

                        // Bu mantık, sadece ana menüden ilk kez gelindiğinde ve kategoriler hazır olduğunda çalışır.
                        if (isInitialLaunch && categories.isNotEmpty() && pendingCategoryIdToRestore == null) {
                            val firstCategory = categories.first()

                            // 1. ViewModel'i uyararak ilk kategorinin içeriğini yüklemesini sağla.
                            //    onCategoryClicked bunu bizim için zaten yapıyor.
                            onCategoryClicked(firstCategory)

                            // 2. UI'a ilk kategoriye odaklanmasını söyle.
                            //    RecyclerView'ın tamamen hazır olduğundan emin olmak için ViewTreeObserver kullan
                            view?.let { fragmentView ->
                                val observer = categoriesRecyclerView.viewTreeObserver
                                observer.addOnGlobalLayoutListener(
                                    object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                                        override fun onGlobalLayout() {
                                            // ViewHolder'ın hazır olduğundan emin ol
                                            val viewHolder = categoriesRecyclerView.findViewHolderForAdapterPosition(0)
                                            if (viewHolder != null) {
                                                // ViewHolder hazır, odak ver
                                                viewHolder.itemView.requestFocus()
                                                // Listener'ı kaldır
                                                categoriesRecyclerView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                                            } else {
                                                // ViewHolder henüz hazır değil, bir kez daha dene
                                                fragmentView.postDelayed({
                                                    val retryViewHolder = categoriesRecyclerView.findViewHolderForAdapterPosition(0)
                                                    retryViewHolder?.itemView?.requestFocus()
                                                    categoriesRecyclerView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                                                }, 100)
                                            }
                                        }
                                    },
                                )
                            }

                            // 3. Bu kurulumun tekrar çalışmasını önlemek için işareti sıfırla.
                            arguments?.putBoolean("is_initial_launch", false)
                        }
                    }
            }
        }
    }

    /**
     * İçerik flow'unu dinler ve adapter'a submit eder.
     */
    private fun observeContents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                contentsFlow.collectLatest { contents ->
                    val currentItemCount = contentAdapter.itemCount
                    Timber.tag(
                        "GRID_UPDATE",
                    ).d("📦 Yeni içerikler geldi: ${contents.size} item, mevcut adapter itemCount: $currentItemCount")

                    // Performans optimizasyonu: Büyük listelerden küçük listelere geçişte DiffUtil çok yavaş
                    // Eğer mevcut liste çok büyükse (1000+) ve yeni liste çok küçükse (500-),
                    // DiffUtil'i atla ve direkt güncelle
                    val shouldSkipDiffUtil =
                        currentItemCount > 1000 && contents.size < 500 &&
                            (currentItemCount - contents.size) > 1000

                    if (shouldSkipDiffUtil) {
                        Timber.tag("GRID_UPDATE").d("⚡ Performans optimizasyonu: DiffUtil atlanıyor ($currentItemCount → ${contents.size})")
                        // DiffUtil'i atla: Önce listeyi temizle, sonra yeni listeyi ekle
                        // Bu, DiffUtil'in tüm item'ları karşılaştırmasını önler
                        contentAdapter.submitList(null) // Önce temizle
                        contentRecyclerView.post {
                            // Temizleme tamamlandıktan sonra yeni listeyi ekle
                            contentAdapter.submitList(contents) {
                                Timber.tag("GRID_UPDATE").d("✅ Direkt güncelleme tamamlandı, yeni itemCount: ${contentAdapter.itemCount}")
                            }
                        }
                    } else {
                        contentAdapter.submitList(contents) {
                            // submitList callback - DiffUtil tamamlandığında çağrılır
                            Timber.tag(
                                "GRID_UPDATE",
                            ).d("✅ submitList callback - DiffUtil tamamlandı, yeni itemCount: ${contentAdapter.itemCount}")
                        }
                    }

                    // RecyclerView'ı zorla güncelle (UI refresh için)
                    // Ancak kategori focus'unu koru - içerik yükleme sırasında focus kaybını önle
                    val currentFocusedCategoryPosition = categoryAdapter.getSelectedPosition()
                    contentRecyclerView.post {
                        Timber.tag("GRID_UPDATE").d("🔄 RecyclerView post - UI güncellemesi tetiklendi")
                        contentRecyclerView.invalidate()
                        
                        // Kategori focus'unu koru - içerik yükleme sırasında focus kaybını önle
                        if (currentFocusedCategoryPosition >= 0 && currentFocusedCategoryPosition < categoryAdapter.itemCount) {
                            val categoryViewHolder = categoriesRecyclerView.findViewHolderForAdapterPosition(currentFocusedCategoryPosition)
                            val categoryTextView = categoryViewHolder?.itemView?.findViewById<android.widget.TextView>(R.id.text_category_name)
                            if (categoryTextView != null && !categoryTextView.hasFocus()) {
                                // Eğer kategori focus'u kaybolduysa, geri ver
                                categoryTextView.post {
                                    if (!categoryTextView.hasFocus()) {
                                        categoryTextView.requestFocus()
                                        Timber.tag("FOCUS_DEBUG").d("🔧 İçerik yükleme sonrası kategori focus geri verildi: pozisyon $currentFocusedCategoryPosition")
                                    }
                                }
                            }
                        }
                    }
                    // Get current selected category for empty state
                    val selectedCategoryId = selectedCategoryIdFlow.firstOrNull()
                    updateEmptyState(contents, selectedCategoryId)

                    // Eğer OK veya sağ yön tuşuna basıldıysa ve içerikler yüklendiyse, focus'u içerik grid'ine ver
                    if (pendingNavigationToContent && contents.isNotEmpty()) {
                        pendingNavigationToContent = false
                        // RecyclerView'ın ilk elemanı için ViewHolder'ı bul
                        contentRecyclerView.post {
                            val firstViewHolder = contentRecyclerView.findViewHolderForAdapterPosition(0)
                            if (firstViewHolder != null) {
                                firstViewHolder.itemView.requestFocus()
                            } else {
                                // ViewHolder hemen bulunamadıysa, kısa bir gecikme ile tekrar dene
                                contentRecyclerView.post {
                                    contentRecyclerView.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
                                }
                            }
                        }
                    }

                    // Eğer restore edilecek bir pozisyon varsa ve içerikler yüklendiyse, onu restore et
                    pendingPositionToRestore?.let { positionToRestore ->
                        if (contents.isNotEmpty()) {
                            pendingPositionToRestore = null

                            Timber.tag(
                                "FOCUS_DEBUG",
                            ).d("🔄 observeContents() - Pozisyon restore ediliyor: $positionToRestore, İçerik sayısı: ${contents.size}")

                            // ViewModel'deki pozisyonu temizle
                            viewModel.lastFocusedContentPosition = null
                            savedLastFocusedPosition = null
                            bundleSavedPosition = null

                            // Pozisyonu geçerli aralıkta kontrol et
                            if (positionToRestore >= 0 && positionToRestore < contents.size) {
                                Timber.tag("FOCUS_DEBUG").d("✅ observeContents() - Pozisyon geçerli, scroll ve focus yapılıyor")
                                // Grid'i hedef pozisyona kaydır ve odaklan.
                                // post içinde post kullanarak, scroll işleminin bitmesini bekleyip
                                // sonra odaklanmayı garantiliyoruz.
                                contentRecyclerView.post {
                                    contentRecyclerView.scrollToPosition(positionToRestore)
                                    contentRecyclerView.post {
                                        val viewHolder = contentRecyclerView.findViewHolderForAdapterPosition(positionToRestore)
                                        if (viewHolder != null) {
                                            viewHolder.itemView.requestFocus()
                                            Timber.tag("FOCUS_DEBUG").d("✅ observeContents() - Focus verildi, pozisyon: $positionToRestore")
                                        } else {
                                            Timber.tag(
                                                "FOCUS_DEBUG",
                                            ).w("⚠️ observeContents() - ViewHolder bulunamadı, pozisyon: $positionToRestore")
                                        }
                                    }
                                }
                            } else {
                                Timber.tag(
                                    "FOCUS_DEBUG",
                                ).w("⚠️ observeContents() - Pozisyon geçersiz: $positionToRestore, İçerik sayısı: ${contents.size}")
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Seçili kategori flow'unu dinler ve adapter'ı günceller.
     */
    private fun observeSelectedCategory() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                selectedCategoryIdFlow.collectLatest { selectedCategoryId ->
                    // Seçili kategori ID'sini sakla (navigateFocusToCategories için)
                    Timber.tag("FOCUS_DEBUG").d("📥 observeSelectedCategory() - Yeni selectedCategoryId: $selectedCategoryId")
                    currentSelectedCategoryId = selectedCategoryId
                    categoryAdapter.updateSelectedItem(selectedCategoryId)
                }
            }
        }
    }

    /**
     * Toast olaylarını dinler ve gösterir.
     */
    private fun setupToastObserver() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                toastEventFlow.collect { message ->
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun navigateFocusToCategories() {
        // Seçili kategoriye focus ver
        val selectedPosition = categoryAdapter.getSelectedPosition()

        if (selectedPosition >= 0 && selectedPosition < categoryAdapter.itemCount) {
            // Seçili kategori pozisyonu geçerli, o pozisyondaki ViewHolder'ı bul ve focus ver
            val viewHolder = categoriesRecyclerView.findViewHolderForAdapterPosition(selectedPosition)
            if (viewHolder != null) {
                // ViewHolder bulunduysa, onun itemView'ına odaklan
                viewHolder.itemView.findViewById<View>(R.id.text_category_name)?.requestFocus() ?: viewHolder.itemView.requestFocus()
                Timber.tag("ContentGrid").d("✅ navigateFocusToCategories: Seçili kategoriye focus verildi (pos=$selectedPosition)")
            } else {
                // ViewHolder hemen bulunamadıysa (çizim bekleniyorsa), scroll yap ve focus ver
                categoriesRecyclerView.scrollToPosition(selectedPosition)
                categoriesRecyclerView.post {
                    val retryViewHolder = categoriesRecyclerView.findViewHolderForAdapterPosition(selectedPosition)
                    retryViewHolder?.itemView?.findViewById<View>(R.id.text_category_name)?.requestFocus()
                        ?: retryViewHolder?.itemView?.requestFocus()
                    Timber.tag(
                        "ContentGrid",
                    ).d("✅ navigateFocusToCategories: Scroll yapıldı ve seçili kategoriye focus verildi (pos=$selectedPosition)")
                }
            }
        } else {
            // Seçili kategori pozisyonu geçersizse, ilk kategoriye focus ver
            Timber.tag(
                "ContentGrid",
            ).w("⚠️ navigateFocusToCategories: Seçili pozisyon geçersiz ($selectedPosition), ilk kategoriye focus veriliyor")
            val firstViewHolder = categoriesRecyclerView.findViewHolderForAdapterPosition(0)
            firstViewHolder?.itemView?.findViewById<View>(R.id.text_category_name)?.requestFocus()
                ?: firstViewHolder?.itemView?.requestFocus()
                ?: categoriesRecyclerView.requestFocus()
        }
    }

    /**
     * Focus'u içerik grid'ine taşır.
     * Grid'de eleman varsa ilk elemana odaklanır.
     * İçerikler henüz yüklenmemişse, yüklendikten sonra otomatik olarak focus'u içerik grid'ine verir.
     */
    private fun navigateFocusToContent() {
        if (contentAdapter.itemCount > 0) {
            // İçerikler yüklendi, focus'u içerik grid'ine ver
            pendingNavigationToContent = false
            // RecyclerView'ın ilk elemanı için ViewHolder'ı bul
            val firstViewHolder = contentRecyclerView.findViewHolderForAdapterPosition(0)
            if (firstViewHolder != null) {
                // ViewHolder bulunduysa, onun itemView'ına odaklan
                firstViewHolder.itemView.requestFocus()
            } else {
                // ViewHolder hemen bulunamadıysa (çizim bekleniyorsa), kısa bir gecikme ile tekrar dene
                contentRecyclerView.post {
                    contentRecyclerView.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
                }
            }
        } else {
            // İçerikler henüz yüklenmemiş, yüklendikten sonra otomatik olarak focus'u içerik grid'ine ver
            pendingNavigationToContent = true
        }
    }

    /**
     * Focus'u navbar'daki home butonuna taşır.
     */
    private fun navigateFocusToNavbar() {
        navbarView?.findViewById<View>(R.id.btn_navbar_home)?.requestFocus()
    }

    /**
     * Geri tuşu basıldığındaki davranışı yönetir.
     * Odak content grid'de ise kategorilere döner, değilse varsayılan davranışı uygular.
     */
    private fun setupBackPressListener() {
        val onBackPressedCallback =
            object : androidx.activity.OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    // Odak content grid'in içindeyse...
                    if (contentRecyclerView.hasFocus()) {
                        // ...odağı kategorilere geri taşı.
                        navigateFocusToCategories()
                    } else {
                        // Değilse, varsayılan geri tuşu davranışını uygula (fragment'ı kapat).
                        // Callback'i geçici olarak devre dışı bırakıp geri tuşunu tekrar tetikliyoruz.
                        isEnabled = false
                        requireActivity().onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, onBackPressedCallback)
    }

    /**
     * Empty state'i gösterir.
     */
    protected fun showEmptyState(message: String) {
        emptyStateTextView.show()
        emptyStateTextView.text = message
        contentRecyclerView.hide()

        // Empty state TextView'ı focusable yap ve focus ver
        emptyStateTextView.isFocusable = true
        emptyStateTextView.isFocusableInTouchMode = true
        emptyStateTextView.requestFocus()
    }

    /**
     * Normal state'i gösterir (empty state'i gizler).
     */
    protected fun showContentState() {
        emptyStateTextView.hide()
        contentRecyclerView.show()
        errorContainer?.hide()
        loadingContainer?.hide()

        // Empty state TextView'ın focus özelliğini kapat
        emptyStateTextView.isFocusable = false
        emptyStateTextView.isFocusableInTouchMode = false
    }

    /**
     * Loading state'i gösterir.
     */
    protected fun showLoadingState() {
        loadingContainer?.show()
        errorContainer?.hide()
        contentRecyclerView.hide()
        emptyStateTextView.hide()
    }

    /**
     * Error state'i gösterir.
     * @param message Hata mesajı
     */
    protected fun showErrorState(message: String) {
        errorContainer?.show()
        errorMessage?.text = message
        loadingContainer?.hide()
        contentRecyclerView.hide()
        emptyStateTextView.hide()
        retryButton?.requestFocus()
    }

    /**
     * Retry butonuna tıklandığında çağrılır.
     * Child fragment'lar override edebilir.
     */
    protected open fun onRetryClicked() {
        onInitialLoad()
    }
}
