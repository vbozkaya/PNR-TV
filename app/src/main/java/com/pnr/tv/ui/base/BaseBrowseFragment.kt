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
import androidx.recyclerview.widget.RecyclerView
import com.pnr.tv.R
import com.pnr.tv.extensions.hide
import com.pnr.tv.extensions.show
import com.pnr.tv.model.CategoryItem
import com.pnr.tv.model.ContentItem
import com.pnr.tv.ui.browse.CategoryAdapter
import com.pnr.tv.ui.browse.ContentAdapter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

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
    // RecyclerView'lar - child fragment'larda initialize edilmeli
    protected lateinit var categoriesRecyclerView: RecyclerView
    protected lateinit var contentRecyclerView: RecyclerView
    protected lateinit var emptyStateTextView: TextView

    // Navbar view - focus yönetimi için
    protected var navbarView: View? = null

    // Generic adapters - BaseBrowseFragment manages these
    protected lateinit var categoryAdapter: CategoryAdapter
    protected lateinit var contentAdapter: ContentAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
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
    }

    /**
     * View'ları initialize eder ve setup işlemlerini başlatır.
     * Child fragments should call this in onViewCreated.
     */
    protected fun initializeViews(view: View) {
        // Initialize RecyclerViews and TextView
        categoriesRecyclerView = view.findViewById(getCategoriesRecyclerViewId())
        contentRecyclerView = view.findViewById(getContentRecyclerViewId())
        emptyStateTextView = view.findViewById(getEmptyStateTextViewId())

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
        val navbarDownListener = View.OnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                categoriesRecyclerView.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
                return@OnKeyListener true // Olayı Tüket!
            }
            false
        }

        backButton?.setOnKeyListener(navbarDownListener)

        // Listener for Home Button (manages both DOWN and RIGHT keys)
        homeButton?.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        // Aşağı tuşu: İlk kategoriye odaklan ve olayı tüket.
                        categoriesRecyclerView.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
                        return@setOnKeyListener true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        // Sağ tuşu: Hiçbir şey yapma, sadece olayı tüketerek odağın arama çubuğuna geçmesini engelle.
                        return@setOnKeyListener true
                    }
                }
            }
            // Diğer tüm tuşlar için varsayılan davranışa izin ver.
            false
        }

        // Listener for Search Bar (manages LEFT key)
        val searchEditText = navbarView?.findViewById<View>(R.id.edt_navbar_search)
        searchEditText?.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        // Sol tuşu: Hiçbir şey yapma, sadece olayı tüketerek odağın Home butonuna geçmesini engelle.
                        return@setOnKeyListener true
                    }
                }
            }
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
     */
    private fun createAdapters() {
        categoryAdapter =
            CategoryAdapter(
                onCategoryClick = { category ->
                    onCategoryClicked(category)
                },
                onNavigateToContent = ::navigateFocusToContent,
                onNavigateToNavbar = ::navigateFocusToNavbar,
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
                onFocusLeftFromGrid = ::navigateFocusToCategories,
                onNavigateUpFromTopRow = ::navigateFocusToNavbar,
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
        // Custom GridLayoutManager kullan - son satır kontrolü için
        contentRecyclerView.layoutManager =
            CustomGridLayoutManager(
                requireContext(),
                getGridColumnCount(),
                contentAdapter,
            )
        contentRecyclerView.adapter = contentAdapter
        contentRecyclerView.setHasFixedSize(true)
        contentRecyclerView.isDrawingCacheEnabled = true
        contentRecyclerView.itemAnimator = null

        // Odak sınırlarını kontrol etme işini artık sadece CustomGridLayoutManager yapacak
        // Gereksiz ve hatalı key listener ve focus change listener blokları kaldırıldı
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
                        // Bu metodun tek görevi, gelen listeyi adaptöre göndermek
                        // Odak işine asla karışmamalı
                        
                        // Anahtar, fragment'larda tanımlananla aynı olmalı
                        val isInitialLaunch = arguments?.getBoolean("is_initial_launch", false) ?: false

                        // Bu mantık, sadece ana menüden ilk kez gelindiğinde ve kategoriler hazır olduğunda çalışır.
                        if (isInitialLaunch && categories.isNotEmpty()) {
                            val firstCategory = categories.first()

                            // 1. ViewModel'i uyararak ilk kategorinin içeriğini yüklemesini sağla.
                            //    onCategoryClicked bunu bizim için zaten yapıyor.
                            onCategoryClicked(firstCategory)

                            // 2. UI'a ilk kategoriye odaklanmasını söyle.
                            //    RecyclerView'ın tamamen hazır olduğundan emin olmak için ViewTreeObserver kullan
                            view?.let { fragmentView ->
                                val observer = categoriesRecyclerView.viewTreeObserver
                                observer.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
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
                                })
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
                    contentAdapter.submitList(contents)
                    // Get current selected category for empty state
                    val selectedCategoryId = selectedCategoryIdFlow.firstOrNull()
                    updateEmptyState(contents, selectedCategoryId)
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

    /**
     * Focus'u kategori listesine taşır.
     */
    private fun navigateFocusToCategories() {
        categoriesRecyclerView.requestFocus()
    }

    /**
     * Focus'u içerik grid'ine taşır.
     */
    private fun navigateFocusToContent() {
        contentRecyclerView.requestFocus()
    }

    /**
     * Focus'u navbar'daki home butonuna taşır.
     */
    private fun navigateFocusToNavbar() {
        navbarView?.findViewById<View>(R.id.btn_navbar_home)?.requestFocus()
    }

    /**
     * Empty state'i gösterir.
     */
    protected fun showEmptyState(message: String) {
        emptyStateTextView.show()
        emptyStateTextView.text = message
        contentRecyclerView.hide()
    }

    /**
     * Normal state'i gösterir (empty state'i gizler).
     */
    protected fun showContentState() {
        emptyStateTextView.hide()
        contentRecyclerView.show()
    }
}
