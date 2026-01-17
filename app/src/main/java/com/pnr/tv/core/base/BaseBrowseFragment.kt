package com.pnr.tv.core.base

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.pnr.tv.R
import com.pnr.tv.model.CategoryItem
import com.pnr.tv.model.ContentItem
import com.pnr.tv.premium.PremiumManager
import com.pnr.tv.ui.browse.CategoryAdapter
import com.pnr.tv.ui.browse.ContentAdapter
import com.pnr.tv.util.error.ErrorSeverity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

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
@AndroidEntryPoint
abstract class BaseBrowseFragment : Fragment() {
    @Inject
    lateinit var premiumManager: PremiumManager

    @Inject
    lateinit var dataHandler: BrowseDataHandler

    @Inject
    lateinit var focusManager: BrowseFocusManager

    // View container - tüm view referanslarını içerir
    private lateinit var viewContainer: BrowseViewContainer

    // Component references - lazy initialization ile oluşturulur
    private var components: BrowseComponents? = null

    // Lifecycle observer - lifecycle metodlarını otomatik yönetir
    private var lifecycleObserver: BrowseLifecycleObserver? = null

    // Generic adapters - BaseBrowseFragment manages these (created by setupDelegate)
    protected lateinit var categoryAdapter: CategoryAdapter
    protected lateinit var contentAdapter: ContentAdapter

    /**
     * Alt fragment'ların kendi ViewModel'ını bu base sınıfa tanıtmasını sağlayan soyut değişken.
     */
    protected abstract val viewModel: BaseViewModel

    // Backward compatibility - child fragments için protected access
    protected val categoriesRecyclerView: CustomCategoriesRecyclerView
        get() = viewContainer.categoriesRecyclerView

    protected val contentRecyclerView: CustomContentRecyclerView
        get() = viewContainer.contentRecyclerView

    protected val navbarView: View?
        get() = viewContainer.navbarView

    protected val errorContainer: View?
        get() = viewContainer.errorContainer

    protected val errorMessage: android.widget.TextView?
        get() = viewContainer.errorMessage

    protected val retryButton: android.widget.Button?
        get() = viewContainer.retryButton

    protected val loadingContainer: View?
        get() = viewContainer.loadingContainer

    protected val emptyStateContainer: View?
        get() = viewContainer.emptyStateContainer

    /**
     * savedLastSelectedCategoryId değişkenini temizler.
     * BrowseDataObserver tarafından çağrılır.
     */
    internal fun clearSavedLastSelectedCategoryId() {
        focusManager.clearSavedLastSelectedCategoryId()
    }

    /**
     * checkDataAndShowWarningIfNeeded metoduna erişim sağlar.
     * Child fragment'lar bu metod üzerinden veri kontrolü yapabilir.
     */
    protected suspend fun checkDataAndShowWarningIfNeeded(): Boolean {
        return dataHandler.checkDataAndShowWarningIfNeeded()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Focus manager'ı initialize et
        focusManager.onCreate(savedInstanceState, arguments)
        
        // Lifecycle observer oluştur ve lifecycle'a observe et
        lifecycleObserver = BrowseLifecycleObserver(focusManager, this, arguments)
        lifecycle.addObserver(lifecycleObserver!!)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // onSaveInstanceState Fragment lifecycle'a özgü, observer'da yok
        focusManager.onSaveInstanceState(outState)
    }

    /**
     * Abstract properties - child fragments must provide these
     * Flow of categories to display in the category list.
     */
    internal abstract val categoriesFlow: Flow<List<CategoryItem>>

    /**
     * Flow of content items to display in the content grid.
     */
    internal abstract val contentsFlow: Flow<List<ContentItem>>

    /**
     * Flow of selected category ID (String).
     */
    internal abstract val selectedCategoryIdFlow: Flow<String?>

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

    /**
     * Premium yazısının gösterilip gösterilmeyeceğini belirler.
     * Sadece film ve dizi sayfalarında (ContentBrowseFragment) true döndürülmelidir.
     * Varsayılan olarak false döndürür.
     */
    protected open fun shouldShowPremiumText(): Boolean = false

    // Overridable hooks for child fragments

    /**
     * Called when a category is clicked.
     * Child fragments should override this to handle category selection.
     */
    internal open fun onCategoryClicked(item: CategoryItem) {
        // Default: no-op, child should override
    }

    /**
     * Kategori ID'sine göre kategori seçer.
     * Child fragments should override this to handle category selection by ID.
     */
    internal open fun selectCategoryById(categoryId: String?) {
        // Default: no-op, child should override
    }

    /**
     * Called when a category receives focus.
     * Child fragments can override this for custom behavior.
     */
    protected open fun onCategoryFocused(item: CategoryItem) {
        // Kategori değiştiğinde pending restore pozisyonunu ve ViewModel'deki restore bilgilerini temizle
        // Çünkü restore sadece aynı kategoride geri dönüşte geçerli olmalı
        // Note: pendingRestorePosition artık BrowseDataObserver içinde yönetiliyor
        // ViewModel'deki restore bilgilerini de temizle (kategori değiştiğinde restore yapılmamalı)
        viewModel.lastFocusedContentPosition = null
        viewModel.lastSelectedCategoryId = null
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
     * Called when content should be loaded initially.
     * Child fragments should override this to trigger initial data loading.
     */
    protected open fun onInitialLoad() {
        // Default: no-op, child should override if needed
    }

    /**
     * Veritabanında içerik verisi olup olmadığını kontrol eden abstract metod.
     * Child fragment'lar kendi ViewModel'lerini kullanarak veri kontrolü yapmalı.
     */
    internal abstract suspend fun hasData(): Boolean

    /**
     * Called to update empty state based on current content and selected category.
     * Child fragments can override this for custom empty state logic.
     */
    internal open fun updateEmptyState(
        contents: List<ContentItem>,
        selectedCategoryId: String?,
    ) {
        if (contents.isEmpty()) {
            // Varsayılan mesaj - child fragment'lar override edebilir
            showEmptyState(getString(R.string.empty_category_content))
        } else {
            showContentState()
        }
    }

    /**
     * ContentRecyclerView'ı refresh eder (Player'dan dönüş için).
     * LifecycleObserver tarafından çağrılır.
     */
    internal fun refreshContentRecyclerViewIfNeeded() {
        if (::viewContainer.isInitialized) {
            viewContainer.contentRecyclerView.requestLayout()
        }
    }

    /**
     * View'ları initialize eder ve setup işlemlerini başlatır.
     * Child fragments should call this in onViewCreated.
     */
    protected fun initializeViews(view: View) {
        // View container oluştur
        viewContainer = BrowseViewContainer.from(
            view = view,
            categoriesRecyclerViewId = getCategoriesRecyclerViewId(),
            contentRecyclerViewId = getContentRecyclerViewId(),
            emptyStateTextViewId = getEmptyStateTextViewId(),
        )

        // Component factory ile tüm handler ve delegate'leri oluştur
        components = BrowseComponentFactory(
            viewContainer = viewContainer,
            context = requireContext(),
            lifecycleOwner = viewLifecycleOwner,
            viewLifecycleOwner = viewLifecycleOwner,
            premiumManager = premiumManager,
            parentFragmentManager = parentFragmentManager,
            activity = activity,
            onBackPressedDispatcher = requireActivity().onBackPressedDispatcher,
            getNavbarTitle = { getNavbarTitle() },
            getGridColumnCount = { getGridColumnCount() },
            shouldShowPremiumText = { shouldShowPremiumText() },
            setupFilterButton = { setupFilterButton() },
            onCategoryClicked = { category -> onCategoryClicked(category) },
            onCategoryFocused = { category -> onCategoryFocused(category) },
            onContentClicked = { content -> onContentClicked(content) },
            onContentLongPressed = { content -> onContentLongPressed(content) },
            onRetryClicked = { onRetryClicked() },
            toastEventFlow = toastEventFlow,
        ).create()

        // Adapter'ları sakla (backward compatibility için)
        categoryAdapter = components!!.categoryAdapter
        contentAdapter = components!!.contentAdapter

        // Setup data handler (initializes data observer and starts observing)
        dataHandler.setup(
            viewModel = viewModel,
            categoryAdapter = categoryAdapter,
            contentAdapter = contentAdapter,
            uiHandler = components!!.uiHandler,
            fragment = this,
            categoriesRecyclerView = viewContainer.categoriesRecyclerView,
            contentRecyclerView = viewContainer.contentRecyclerView,
            focusDelegate = components!!.focusDelegate,
            lifecycleOwner = viewLifecycleOwner,
        )

        // Setup focus manager (needs all references from above)
        focusManager.setup(
            viewModel = viewModel,
            dataHandler = dataHandler,
            focusHandler = components!!.focusHandler,
            contentRecyclerView = viewContainer.contentRecyclerView,
            contentAdapter = contentAdapter,
            focusDelegate = components!!.focusDelegate,
        )
    }

    /**
     * Filter butonunu setup eder. Child fragment'lar override ederek görünürlüğü ayarlar.
     * Butonlar artık her zaman görünür (premium kontrolü ile aktif/pasif olacak).
     */
    protected open fun setupFilterButton() {
        // Varsayılan olarak görünür - child fragment'lar override ederek özelleştirebilir
        // Premium kontrolü setupNavbarPremiumControls() içinde yapılıyor
    }

    /**
     * Empty state'i gösterir.
     * @param message Gösterilecek boş durum mesajı
     */
    internal fun showEmptyState(message: String) {
        components?.uiHandler?.show(BrowseUiState.Empty(message))
    }

    /**
     * Normal state'i gösterir (empty state'i gizler).
     */
    internal fun showContentState() {
        components?.uiHandler?.show(BrowseUiState.Content)
    }

    /**
     * Loading state'i gösterir.
     * Skeleton loading kullanarak içerik grid'inde placeholder gösterir.
     */
    internal fun showLoadingState() {
        components?.uiHandler?.show(BrowseUiState.Loading)
    }

    /**
     * Error state'i gösterir.
     * Tüm ekranlarda tutarlı error gösterimi sağlar.
     * Hata severity'ye göre otomatik kapanma süresi belirlenir.
     *
     * @param message Hata mesajı (kullanıcı dostu formatlanmış olmalı)
     * @param severity Hata şiddeti (varsayılan: MEDIUM - 5 saniye)
     */
    internal fun showErrorState(
        message: String,
        severity: ErrorSeverity = ErrorSeverity.MEDIUM,
    ) {
        components?.uiHandler?.show(BrowseUiState.Error(message, severity))
    }

    /**
     * Error state'i gizler.
     * Otomatik kapanma veya manuel kapatma için kullanılır.
     * Fade-out animasyonu ile kapanır.
     */
    internal fun hideErrorState() {
        components?.uiHandler?.hideErrorState()
    }

    /**
     * Retry butonuna tıklandığında çağrılır.
     * Child fragment'lar override edebilir.
     */
    protected open fun onRetryClicked() {
        onInitialLoad()
    }

    override fun onDestroyView() {
        // Lifecycle güvenliği: component referanslarını temizle
        focusManager.onDestroyView()
        components = null
        super.onDestroyView()
    }

    override fun onDestroy() {
        // Lifecycle observer'ı kaldır
        lifecycleObserver?.let { lifecycle.removeObserver(it) }
        lifecycleObserver = null
        super.onDestroy()
    }
}
