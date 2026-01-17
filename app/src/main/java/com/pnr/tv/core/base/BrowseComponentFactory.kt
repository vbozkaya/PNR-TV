package com.pnr.tv.core.base

import android.app.Activity
import android.content.Context
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.OnBackPressedDispatcher
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleOwner
import com.pnr.tv.model.CategoryItem
import com.pnr.tv.model.ContentItem
import com.pnr.tv.premium.PremiumManager
import com.pnr.tv.ui.browse.CategoryAdapter
import com.pnr.tv.ui.browse.ContentAdapter
import kotlinx.coroutines.flow.Flow

/**
 * Browse ekranları için gerekli tüm component'leri (Handler, Delegate, Manager) oluşturan factory sınıfı.
 *
 * Bu sınıf, BaseBrowseFragment içindeki component kurulum kalabalığını temizler ve
 * tüm setup işlemlerini merkezi bir yerde toplar.
 *
 * Oluşturduğu component'ler:
 * - BrowseNavbarHandler: Navbar yönetimi
 * - BrowseSetupDelegate: RecyclerView setup ve adapter oluşturma
 * - BrowseFocusDelegate: Focus ve navigasyon yönetimi
 * - BrowseUiHandler: UI state yönetimi
 * - BrowseFocusHandler: Back press yönetimi
 */
class BrowseComponentFactory(
    // View container - tüm view referanslarını içerir
    private val viewContainer: BrowseViewContainer,
    // Context ve lifecycle
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val viewLifecycleOwner: LifecycleOwner,
    // Dependencies
    private val premiumManager: PremiumManager,
    private val parentFragmentManager: FragmentManager,
    private val activity: Activity?,
    private val onBackPressedDispatcher: OnBackPressedDispatcher,
    // Callbacks ve getters
    private val getNavbarTitle: () -> String,
    private val getGridColumnCount: () -> Int,
    private val shouldShowPremiumText: () -> Boolean,
    private val setupFilterButton: () -> Unit,
    private val onCategoryClicked: (CategoryItem) -> Unit,
    private val onCategoryFocused: (CategoryItem) -> Unit,
    private val onContentClicked: (ContentItem) -> Unit,
    private val onContentLongPressed: (ContentItem) -> Unit,
    private val onRetryClicked: () -> Unit,
    private val toastEventFlow: Flow<String>,
) {
    /**
     * Tüm browse component'lerini oluşturur ve döndürür.
     */
    fun create(): BrowseComponents {
        // 1. Navbar handler oluştur
        val navbarHandler = createNavbarHandler()

        // 2. Setup delegate oluştur ve setup et
        val setupDelegate = createSetupDelegate()
        setupDelegate.setup()

        // 3. Adapter'ları setup delegate'den al
        val categoryAdapter = setupDelegate.categoryAdapter
        val contentAdapter = setupDelegate.contentAdapter

        // 4. Focus delegate oluştur
        val focusDelegate = createFocusDelegate(navbarHandler, categoryAdapter, contentAdapter)

        // 5. RecyclerView callback'lerini ayarla
        setupRecyclerViewCallbacks(focusDelegate)

        // 6. UI handler oluştur
        val uiHandler = createUiHandler(contentAdapter)

        // 7. Focus handler oluştur
        val focusHandler = createFocusHandler(focusDelegate)

        return BrowseComponents(
            navbarHandler = navbarHandler,
            setupDelegate = setupDelegate,
            focusDelegate = focusDelegate,
            uiHandler = uiHandler,
            focusHandler = focusHandler,
            categoryAdapter = categoryAdapter,
            contentAdapter = contentAdapter,
        )
    }

    /**
     * Navbar handler oluşturur.
     */
    private fun createNavbarHandler(): BrowseNavbarHandler {
        val handler =
            BrowseNavbarHandler(
                navbarView = viewContainer.navbarView,
                premiumManager = premiumManager,
                lifecycleOwner = lifecycleOwner,
                getNavbarTitle = getNavbarTitle,
                onNavigateToFirstCategory = {
                    viewContainer.categoriesRecyclerView.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
                },
                onNavigateToEmptyState = {
                    viewContainer.emptyStateTextView.requestFocus()
                },
                onNavigateToContent = {
                    // Pasifize edildi: Android'in doğal Focus Search mekanizmasına bırakıldı
                    // nextFocusDown attribute'ları layout dosyalarında tanımlı
                },
                isContentRecyclerViewVisible = { viewContainer.contentRecyclerView.visibility == View.VISIBLE },
                isEmptyStateTextViewVisible = { viewContainer.emptyStateTextView.visibility == View.VISIBLE },
                parentFragmentManager = parentFragmentManager,
                shouldShowPremiumText = shouldShowPremiumText,
                setupFilterButton = setupFilterButton,
            )

        // Navbar setup
        handler.setup(viewContainer.rootView)

        return handler
    }

    /**
     * Setup delegate oluşturur.
     */
    private fun createSetupDelegate(): BrowseSetupDelegate {
        return BrowseSetupDelegate(
            categoriesRecyclerView = viewContainer.categoriesRecyclerView,
            contentRecyclerView = viewContainer.contentRecyclerView,
            context = context,
            lifecycleOwner = lifecycleOwner,
            gridColumnCount = getGridColumnCount(),
            onCategoryClicked = onCategoryClicked,
            onCategoryFocused = onCategoryFocused,
            onContentClicked = onContentClicked,
            onContentLongPressed = onContentLongPressed,
            fragmentView = viewContainer.rootView,
        )
    }

    /**
     * Focus delegate oluşturur.
     */
    private fun createFocusDelegate(
        navbarHandler: BrowseNavbarHandler?,
        categoryAdapter: CategoryAdapter,
        contentAdapter: ContentAdapter,
    ): BrowseFocusDelegate {
        return BrowseFocusDelegate(
            categoriesRecyclerView = viewContainer.categoriesRecyclerView,
            contentRecyclerView = viewContainer.contentRecyclerView,
            categoryAdapter = categoryAdapter,
            contentAdapter = contentAdapter,
            navbarHandler = navbarHandler,
            navbarView = viewContainer.navbarView,
            viewLifecycleOwner = viewLifecycleOwner,
        )
    }

    /**
     * RecyclerView callback'lerini ayarlar.
     */
    private fun setupRecyclerViewCallbacks(focusDelegate: BrowseFocusDelegate) {
        viewContainer.categoriesRecyclerView.onNavigateToContentCallback = {
            focusDelegate.navigateToContentStart()
        }
        viewContainer.contentRecyclerView.onNavigateToCategoriesCallback = {
            focusDelegate.navigateFocusToCategories()
        }
    }

    /**
     * UI handler oluşturur.
     */
    private fun createUiHandler(contentAdapter: ContentAdapter): BrowseUiHandler {
        val handler =
            BrowseUiHandler(
                emptyStateTextView = viewContainer.emptyStateTextView,
                contentRecyclerView = viewContainer.contentRecyclerView,
                errorContainer = viewContainer.errorContainer,
                errorMessage = viewContainer.errorMessage,
                retryButton = viewContainer.retryButton,
                loadingContainer = viewContainer.loadingContainer,
                emptyStateContainer = viewContainer.emptyStateContainer,
                contentAdapter = contentAdapter,
                context = context,
                lifecycleOwner = lifecycleOwner,
                gridColumnCount = getGridColumnCount(),
                onRetryClicked = onRetryClicked,
                rootView = viewContainer.rootView,
                toastEventFlow = toastEventFlow,
            )

        // Background yükle ve toast observer setup et
        handler.loadBackground()
        handler.setupToastObserver()

        return handler
    }

    /**
     * Focus handler oluşturur ve setup eder.
     */
    private fun createFocusHandler(focusDelegate: BrowseFocusDelegate): BrowseFocusHandler {
        val handler =
            BrowseFocusHandler(
                view = viewContainer.rootView,
                activity = activity as? androidx.fragment.app.FragmentActivity,
                contentRecyclerView = viewContainer.contentRecyclerView,
                categoriesRecyclerView = viewContainer.categoriesRecyclerView,
                navbarView = viewContainer.navbarView,
                focusDelegate = focusDelegate,
                parentFragmentManager = parentFragmentManager,
                viewLifecycleOwner = viewLifecycleOwner,
                getNavbarTitle = getNavbarTitle,
                onBackPressedDispatcher = onBackPressedDispatcher,
            )

        handler.setup()

        return handler
    }
}

/**
 * Browse component'lerini tutan data class.
 */
data class BrowseComponents(
    val navbarHandler: BrowseNavbarHandler?,
    val setupDelegate: BrowseSetupDelegate,
    val focusDelegate: BrowseFocusDelegate,
    val uiHandler: BrowseUiHandler,
    val focusHandler: BrowseFocusHandler,
    val categoryAdapter: CategoryAdapter,
    val contentAdapter: ContentAdapter,
)
