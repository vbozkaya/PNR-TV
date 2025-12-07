package com.pnr.tv.ui.base

import android.os.Bundle
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

    // Focus yönetimi için flag'ler
    private var shouldFocusFirstCategory = false
    private var hasFocusedFirstCategory = false
    private var isFirstTimeOpening = true // İlk kez açılıp açılmadığını takip et
    private var lastFocusedContentPosition = -1 // Son focus edilen içerik pozisyonu
    private var lastFocusedCategoryPosition = -1 // Son focus edilen kategori pozisyonu

    // State kaydetme için anahtarlar
    private companion object {
        const val KEY_LAST_FOCUSED_CONTENT_POSITION = "base_browse_fragment_last_focused_content_position"
        const val KEY_LAST_FOCUSED_CATEGORY_POSITION = "base_browse_fragment_last_focused_category_position"
        const val KEY_IS_FIRST_TIME_OPENING = "base_browse_fragment_is_first_time_opening"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Fragment yeniden yaratıldığında kaydedilen hafızayı geri yükle
        if (savedInstanceState != null) {
            // Son focus edilen içerik pozisyonunu geri yükle
            lastFocusedContentPosition = savedInstanceState.getInt(KEY_LAST_FOCUSED_CONTENT_POSITION, -1)

            // Son focus edilen kategori pozisyonunu geri yükle
            lastFocusedCategoryPosition = savedInstanceState.getInt(KEY_LAST_FOCUSED_CATEGORY_POSITION, -1)

            // İlk kez açılıp açılmadığını geri yükle
            isFirstTimeOpening = savedInstanceState.getBoolean(KEY_IS_FIRST_TIME_OPENING, true)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        // Fragment yok edilmeden önce hafızayı kaydet
        // Son focus edilen içerik pozisyonunu kaydet
        outState.putInt(KEY_LAST_FOCUSED_CONTENT_POSITION, lastFocusedContentPosition)

        // Son focus edilen kategori pozisyonunu kaydet
        outState.putInt(KEY_LAST_FOCUSED_CATEGORY_POSITION, lastFocusedCategoryPosition)

        // İlk kez açılıp açılmadığını kaydet
        outState.putBoolean(KEY_IS_FIRST_TIME_OPENING, isFirstTimeOpening)
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

        // Fragment geri geldiğinde focus yönetimi
        if (::categoriesRecyclerView.isInitialized && ::contentRecyclerView.isInitialized) {
            viewLifecycleOwner.lifecycleScope.launch {
                // Öncelik sırası: lastFocusedContentPosition > lastFocusedCategoryPosition > ilk kategori (sadece ilk açılışta)
                when {
                    // Önce: Son focus edilen içerik pozisyonu geçerliyse, o içerik kartına odak ver
                    lastFocusedContentPosition >= 0 && lastFocusedContentPosition < contentAdapter.itemCount -> {
                        contentRecyclerView.post {
                            val viewHolder = contentRecyclerView.findViewHolderForAdapterPosition(lastFocusedContentPosition)
                            if (viewHolder != null) {
                                viewHolder.itemView.requestFocus()
                            }
                        }
                    }
                    // Değilse: Son focus edilen kategori pozisyonu geçerliyse, o kategoriye odak ver
                    lastFocusedCategoryPosition >= 0 && lastFocusedCategoryPosition < categoryAdapter.itemCount -> {
                        categoriesRecyclerView.post {
                            val viewHolder = categoriesRecyclerView.findViewHolderForAdapterPosition(lastFocusedCategoryPosition)
                            if (viewHolder != null) {
                                val categoryView = viewHolder.itemView.findViewById<View>(R.id.text_category_name)
                                categoryView?.requestFocus()
                            }
                        }
                    }
                    // Hiçbiri değilse: Ve sayfa ilk kez açılıyorsa, ancak o zaman ilk kategoriye odak ver
                    isFirstTimeOpening -> {
                        // Coroutine kullanarak categoriesFlow'dan ilk kategori listesinin gelmesini bekle
                        val categories = categoriesFlow.firstOrNull()
                        if (categories != null && categories.isNotEmpty()) {
                            // Liste geldiği anda, categoriesRecyclerView'a bir post komutu göndererek odağı listenin 0. pozisyonundaki öğeye ver
                            categoriesRecyclerView.post {
                                val firstItem = categoriesRecyclerView.findViewHolderForAdapterPosition(0)
                                if (firstItem != null) {
                                    val categoryView = firstItem.itemView.findViewById<View>(R.id.text_category_name)
                                    if (categoryView != null) {
                                        categoryView.requestFocus()
                                        // En önemlisi: requestFocus() komutundan hemen sonra flag'leri ayarla
                                        // Bu, bu kodun bir daha asla çalışmamasını garanti altına alır
                                        isFirstTimeOpening = false
                                        hasFocusedFirstCategory = true
                                        lastFocusedCategoryPosition = 0
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
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

        // Focus flag'lerini reset et (sadece ilk açılışta)
        // Geri dönüldüğünde flag'leri koru, böylece focus son durumda kalır
        if (isFirstTimeOpening) {
            shouldFocusFirstCategory = false
            hasFocusedFirstCategory = false
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

        // --- Navbar Butonlarının Davranışını Yönet ---
        val searchEditText = navbarView?.findViewById<View>(R.id.edt_navbar_search)
        val filterButton = navbarView?.findViewById<View>(R.id.btn_navbar_filter)

        // Arama ve Filtre için ortak aşağı yön davranışı
        val searchAndFilterDownListener =
            View.OnKeyListener { v, keyCode, event ->
                if (event.action == android.view.KeyEvent.ACTION_DOWN) {
                    when (keyCode) {
                        android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (contentAdapter.itemCount > 0) {
                                contentRecyclerView.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
                            } else {
                                categoriesRecyclerView.requestFocus()
                            }
                            return@OnKeyListener true
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_CENTER -> {
                            if (v.id == R.id.edt_navbar_search) {
                                v.isFocusableInTouchMode = true
                                v.requestFocus()
                                val imm =
                                    requireContext().getSystemService(
                                        android.content.Context.INPUT_METHOD_SERVICE,
                                    ) as android.view.inputmethod.InputMethodManager
                                imm.showSoftInput(v, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                                return@OnKeyListener true
                            }
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                            // SOL: Eğer odak arama çubuğundaysa, olayı tüket ve hiçbir şey yapma.
                            // Bu, odağın Home butonuna gitmesini engeller.
                            if (v.id == R.id.edt_navbar_search) {
                                return@OnKeyListener true // Olayı tüket.
                            }
                            // Eğer odak filtre butonundaysa, bu blok çalışmaz ve varsayılan davranış (arama çubuğuna gitme) korunur.
                        }
                    }
                }
                return@OnKeyListener false
            }
        searchEditText?.setOnKeyListener(searchAndFilterDownListener)
        filterButton?.setOnKeyListener(searchAndFilterDownListener)
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
        // Navbar'dan aşağı yön tuşuyla ilk kategoriye git
        backButton?.setOnKeyListener { _, keyCode, event ->
            if (event.action == android.view.KeyEvent.ACTION_DOWN && keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
                navigateFocusToFirstCategory()
                true
            } else {
                false
            }
        }

        val homeButton = navbarView?.findViewById<View>(R.id.btn_navbar_home)
        homeButton?.setOnClickListener {
            parentFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }
        // Home butonundan aşağı ve sağ yön tuşlarını yönet
        homeButton?.setOnKeyListener { _, keyCode, event ->
            if (event.action == android.view.KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    // AŞAĞI: Odağı her zaman ilk kategoriye gönder.
                    android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                        navigateFocusToFirstCategory()
                        return@setOnKeyListener true // Olayı tüket.
                    }
                    // SAĞA: Olayı tüket, böylece arama kutusuna atlayamaz.
                    android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        return@setOnKeyListener true // Olayı tüket.
                    }
                }
            }
            // Diğer tüm tuşlar (sol, yukarı, ok) için sisteme izin ver.
            return@setOnKeyListener false
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
                onCategoryFocused = { category ->
                    // Kategori focus aldığında pozisyonu kaydet
                    if (::categoriesRecyclerView.isInitialized) {
                        val focusedChild = categoriesRecyclerView.focusedChild
                        if (focusedChild != null) {
                            val position = categoriesRecyclerView.getChildAdapterPosition(focusedChild)
                            if (position != RecyclerView.NO_POSITION) {
                                lastFocusedCategoryPosition = position
                            }
                        }
                    }
                    onCategoryFocused(category)
                },
                onNavigateToContent = {
                    onNavigateFromCategoriesToContent()
                    navigateFocusToContent()
                },
                onNavigateToNavbar = {
                    navigateFocusToNavbar()
                },
            )

        contentAdapter =
            ContentAdapter(
                onContentClick = { content ->
                    // İçerik kartına tıklandığında pozisyonu kaydet
                    if (::contentRecyclerView.isInitialized) {
                        val focusedChild = contentRecyclerView.focusedChild
                        if (focusedChild != null) {
                            val position = contentRecyclerView.getChildAdapterPosition(focusedChild)
                            if (position != RecyclerView.NO_POSITION) {
                                lastFocusedContentPosition = position
                            }
                        }
                    }
                    onContentClicked(content)
                },
                onContentLongPress = { content ->
                    onContentLongPressed(content)
                },
                onFocusLeftFromGrid = {
                    onFocusLeftFromContentGrid()
                    navigateFocusToCategories()
                },
                onNavigateUpFromTopRow = {
                    // Adapter haber verdiğinde, odağı doğrudan arama çubuğuna taşı.
                    navbarView?.findViewById<View>(R.id.edt_navbar_search)?.requestFocus()
                },
                gridColumnCount = getGridColumnCount(),
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
        viewLifecycleOwner.lifecycleScope.launch {
            val selectedCategoryId = selectedCategoryIdFlow.firstOrNull()
            if (selectedCategoryId != null) {
                val categories = categoriesFlow.firstOrNull() ?: emptyList()
                val categoryPosition = categories.indexOfFirst { it.categoryId == selectedCategoryId }
                if (categoryPosition >= 0) {
                    lastFocusedCategoryPosition = categoryPosition
                    categoriesRecyclerView.post {
                        val viewHolder = categoriesRecyclerView.findViewHolderForAdapterPosition(categoryPosition)
                        val categoryView = viewHolder?.itemView?.findViewById<View>(R.id.text_category_name)
                        categoryView?.requestFocus()
                    }
                }
            }
        }
    }

    /**
     * Focus'u içerik grid'inin ilk öğesine taşır.
     * Kategori listesindeki focus'u temizler, böylece seçili kategori sadece görsel olarak işaretli kalır.
     */
    private fun navigateFocusToContent() {
        // Focus'u içerik grid'ine taşı
        contentRecyclerView.post {
            if (contentAdapter.itemCount > 0) {
                // Eğer son focus edilen pozisyon varsa, ona git, yoksa ilk item'a git
                val position =
                    if (lastFocusedContentPosition >= 0 && lastFocusedContentPosition < contentAdapter.itemCount) {
                        lastFocusedContentPosition
                    } else {
                        0
                    }
                val viewHolder = contentRecyclerView.findViewHolderForAdapterPosition(position)
                viewHolder?.itemView?.requestFocus()
                lastFocusedContentPosition = position
            }
        }
    }

    /**
     * Focus'u navbar'daki geri butonuna taşır.
     */
    private fun navigateFocusToNavbar() {
        navbarView?.post {
            val backButton = navbarView?.findViewById<View>(R.id.btn_navbar_back)
            backButton?.requestFocus()
        }
    }

    /**
     * Focus'u ilk kategoriye taşır.
     */
    private fun navigateFocusToFirstCategory() {
        categoriesRecyclerView.post {
            if (categoryAdapter.itemCount > 0) {
                val viewHolder = categoriesRecyclerView.findViewHolderForAdapterPosition(0)
                val categoryView = viewHolder?.itemView?.findViewById<View>(R.id.text_category_name)
                categoryView?.requestFocus()
            }
        }
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
