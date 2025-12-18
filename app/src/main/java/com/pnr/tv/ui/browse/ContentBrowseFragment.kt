package com.pnr.tv.ui.browse

import android.app.Dialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.pnr.tv.R
import com.pnr.tv.UIConstants
import com.pnr.tv.model.CategoryItem
import com.pnr.tv.model.ContentItem
import com.pnr.tv.model.ContentType
import com.pnr.tv.model.SortOrder
import com.pnr.tv.ui.base.BaseBrowseFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * İçerik türüne göre içerikleri gösteren Fragment.
 * BaseBrowseFragment'tan türetilmiştir ve tüm ortak mantık base'de yönetilir.
 */
@AndroidEntryPoint
class ContentBrowseFragment : BaseBrowseFragment() {
    private var contentType: ContentType? = null
    private val movieViewModel: com.pnr.tv.ui.movies.MovieViewModel by activityViewModels()
    private val seriesViewModel: com.pnr.tv.ui.series.SeriesViewModel by activityViewModels()

    // BaseBrowseFragment requires BaseViewModel
    override val viewModel: com.pnr.tv.ui.base.BaseViewModel
        get() =
            when (contentType) {
                ContentType.MOVIES -> movieViewModel
                ContentType.SERIES -> seriesViewModel
                else -> movieViewModel // fallback (bu durum olmamalı)
            }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        contentType = arguments?.getSerializable(ARG_CONTENT_TYPE) as? ContentType
        // Sadece ilk oluşturulduğunda yükle, savedInstanceState null ise
        if (savedInstanceState == null) {
            onInitialLoad()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_content_browse, container, false)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        // BaseBrowseFragment handles all setup via initializeViews
        initializeViews(view)
        observeErrorState()
        observeLoadingState()
    }

    private fun observeErrorState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                val errorFlow =
                    when (contentType) {
                        ContentType.MOVIES -> movieViewModel.moviesErrorMessage
                        ContentType.SERIES -> seriesViewModel.seriesErrorMessage
                        else -> kotlinx.coroutines.flow.flowOf(null)
                    }
                errorFlow.collect { errorMsg ->
                    errorMsg?.let {
                        showErrorState(it)
                    }
                }
            }
        }
    }

    private fun observeLoadingState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                val loadingFlow =
                    when (contentType) {
                        ContentType.MOVIES -> movieViewModel.isMoviesLoading
                        ContentType.SERIES -> seriesViewModel.isSeriesLoading
                        else -> kotlinx.coroutines.flow.flowOf(false)
                    }
                val errorFlow =
                    when (contentType) {
                        ContentType.MOVIES -> movieViewModel.moviesErrorMessage
                        ContentType.SERIES -> seriesViewModel.seriesErrorMessage
                        else -> kotlinx.coroutines.flow.flowOf(null)
                    }
                loadingFlow.collect { isLoading ->
                    if (isLoading) {
                        showLoadingState()
                    } else {
                        val hasError = errorFlow.firstOrNull() != null
                        if (!hasError) {
                            showContentState()
                        }
                    }
                }
            }
        }
    }

    // Abstract properties from BaseBrowseFragment
    override val categoriesFlow: Flow<List<CategoryItem>>
        get() =
            when (contentType) {
                ContentType.MOVIES -> movieViewModel.movieCategoriesFlow
                ContentType.SERIES -> seriesViewModel.seriesCategoriesFlow
                else -> kotlinx.coroutines.flow.flowOf(emptyList())
            }

    override val contentsFlow: Flow<List<ContentItem>>
        get() =
            when (contentType) {
                ContentType.MOVIES -> movieViewModel.moviesFlow
                ContentType.SERIES -> seriesViewModel.seriesFlow
                else -> kotlinx.coroutines.flow.flowOf(emptyList())
            }

    override val selectedCategoryIdFlow: Flow<String?>
        get() =
            when (contentType) {
                ContentType.MOVIES -> movieViewModel.selectedMovieCategoryId.map { it as? String }
                ContentType.SERIES -> seriesViewModel.selectedSeriesCategoryId.map { it as? String }
                else -> kotlinx.coroutines.flow.flowOf(null)
            }

    override val toastEventFlow: Flow<String>
        get() =
            when (contentType) {
                ContentType.MOVIES -> movieViewModel.toastEvent
                ContentType.SERIES -> seriesViewModel.toastEvent
                else -> movieViewModel.toastEvent // fallback
            }

    // Abstract methods from BaseBrowseFragment
    override fun getNavbarTitle(): String =
        when (contentType) {
            ContentType.MOVIES -> getString(R.string.page_movies)
            ContentType.SERIES -> getString(R.string.page_series)
            ContentType.LIVE_TV -> getString(R.string.page_live_streams)
            null -> ""
        }

    override fun getCategoriesRecyclerViewId(): Int = R.id.recycler_categories

    override fun getContentRecyclerViewId(): Int = R.id.recycler_content

    override fun getEmptyStateTextViewId(): Int = R.id.txt_empty_state

    override fun getGridColumnCount(): Int = UIConstants.GRID_COLUMN_COUNT

    // Override hooks from BaseBrowseFragment
    override fun onInitialLoad() {
        when (contentType) {
            ContentType.MOVIES -> movieViewModel.loadMoviesIfNeeded()
            ContentType.SERIES -> seriesViewModel.loadSeriesIfNeeded()
            else -> {}
        }
    }

    override fun onCategoryClicked(category: CategoryItem) {
        when (contentType) {
            ContentType.MOVIES -> movieViewModel.selectMovieCategory(category.categoryId)
            ContentType.SERIES -> seriesViewModel.selectSeriesCategory(category.categoryId)
            else -> {}
        }
    }

    override fun selectCategoryById(categoryId: String?) {
        // Kategori ID'sine göre kategori seç
        categoryId?.let { id ->
            when (contentType) {
                ContentType.MOVIES -> movieViewModel.selectMovieCategory(id)
                ContentType.SERIES -> seriesViewModel.selectSeriesCategory(id)
                else -> {}
            }
        }
    }

    override fun onCategoryFocused(category: CategoryItem) {
        // Focus geldiğinde kategoriyi seç ve içerikleri yükle
        val startTime = System.currentTimeMillis()
        timber.log.Timber.tag("GRID_UPDATE").d("🎯 Kategori focus: ${category.categoryName} (ID: ${category.categoryId})")
        when (contentType) {
            ContentType.MOVIES -> {
                timber.log.Timber.tag("GRID_UPDATE").d("🎬 selectMovieCategory çağrılıyor: ${category.categoryId}")
                movieViewModel.selectMovieCategory(category.categoryId)
            }
            ContentType.SERIES -> {
                timber.log.Timber.tag("GRID_UPDATE").d("📺 selectSeriesCategory çağrılıyor: ${category.categoryId}")
                seriesViewModel.selectSeriesCategory(category.categoryId)
            }
            else -> {}
        }
        val focusTime = System.currentTimeMillis() - startTime
        timber.log.Timber.tag("GRID_UPDATE").d("⚡ Kategori focus süresi: ${focusTime}ms")
    }

    override fun onContentClicked(item: ContentItem) {
        // Son seçili kategoriyi ve odaklanılan pozisyonu kaydet
        val position = contentAdapter.currentList.indexOf(item)
        if (position != -1) {
            viewModel.lastFocusedContentPosition = position
            // Mevcut seçili kategoriyi kaydet
            val categoryId =
                when (contentType) {
                    ContentType.MOVIES -> movieViewModel.selectedMovieCategoryId.value as? String
                    ContentType.SERIES -> seriesViewModel.selectedSeriesCategoryId.value as? String
                    else -> null
                }
            viewModel.lastSelectedCategoryId = categoryId
        }

        // Navigate based on contentType
        when (contentType) {
            ContentType.MOVIES -> {
                // Film detay sayfasına git
                val movieEntity = item as? com.pnr.tv.db.entity.MovieEntity
                movieEntity?.let {
                    val detailFragment = com.pnr.tv.ui.movies.MovieDetailFragment.newInstance(it.streamId)
                    parentFragmentManager.commit {
                        replace(R.id.fragment_container, detailFragment)
                        addToBackStack(null)
                    }
                }
            }
            ContentType.SERIES -> {
                // Dizi detay sayfasına git
                val seriesEntity = item as? com.pnr.tv.db.entity.SeriesEntity
                seriesEntity?.let {
                    val detailFragment = com.pnr.tv.ui.series.SeriesDetailFragment.newInstance(it.streamId)
                    parentFragmentManager.commit {
                        replace(R.id.fragment_container, detailFragment)
                        addToBackStack(null)
                    }
                }
            }
            else -> { /* No action */ }
        }
    }

    override fun onContentLongPressed(item: ContentItem) {
        // Filmler ve diziler için favori işlemi yapılmaz
        // Favoriye ekleme sadece detay sayfasından yapılabilir
    }

    override fun setupFilterButton() {
        super.setupFilterButton()
        val filterButton = navbarView?.findViewById<View>(R.id.btn_navbar_filter)
        val searchEditText = navbarView?.findViewById<android.widget.EditText>(R.id.edt_navbar_search)

        // Sadece filmler ve diziler sayfalarında göster
        when (contentType) {
            ContentType.MOVIES, ContentType.SERIES -> {
                filterButton?.visibility = View.VISIBLE
                filterButton?.setOnClickListener {
                    showSortDialog()
                }

                // Arama kutusu ayarları
                searchEditText?.visibility = View.VISIBLE
                searchEditText?.isFocusableInTouchMode = false

                // Metin değişikliklerini dinle
                searchEditText?.addTextChangedListener(
                    object : TextWatcher {
                        override fun beforeTextChanged(
                            s: CharSequence?,
                            start: Int,
                            count: Int,
                            after: Int,
                        ) {}

                        override fun onTextChanged(
                            s: CharSequence?,
                            start: Int,
                            before: Int,
                            count: Int,
                        ) {
                            when (contentType) {
                                ContentType.MOVIES -> movieViewModel.onMovieSearchQueryChanged(s?.toString() ?: "")
                                ContentType.SERIES -> seriesViewModel.onSeriesSearchQueryChanged(s?.toString() ?: "")
                                else -> {}
                            }
                        }

                        override fun afterTextChanged(s: Editable?) {}
                    },
                )
            }
            else -> {
                filterButton?.visibility = View.GONE
                searchEditText?.visibility = View.GONE
            }
        }
    }

    private fun showSortDialog() {
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_sort_options, null)
        dialog.setContentView(view)

        val window = dialog.window
        // Ekran genişliğinin yarısını hesapla
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val dialogWidth = screenWidth / 2

        window?.setLayout(
            dialogWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
        )
        window?.setBackgroundDrawableResource(android.R.color.transparent)

        val radioGroup = view.findViewById<RadioGroup>(R.id.radio_group_sort)
        val radioAtoZ = view.findViewById<RadioButton>(R.id.radio_a_to_z)
        val radioZtoA = view.findViewById<RadioButton>(R.id.radio_z_to_a)
        val radioRatingHighToLow = view.findViewById<RadioButton>(R.id.radio_rating_high_to_low)
        val radioRatingLowToHigh = view.findViewById<RadioButton>(R.id.radio_rating_low_to_high)
        val radioDateNewToOld = view.findViewById<RadioButton>(R.id.radio_date_new_to_old)
        val radioDateOldToNew = view.findViewById<RadioButton>(R.id.radio_date_old_to_new)
        val btnSave = view.findViewById<Button>(R.id.btn_save_sort)

        // Mevcut sıralama tercihini yükle ve seçili yap
        viewLifecycleOwner.lifecycleScope.launch {
            val sortOrderFlow =
                when (contentType) {
                    ContentType.MOVIES -> movieViewModel.getCurrentMovieSortOrder()
                    ContentType.SERIES -> seriesViewModel.getCurrentSeriesSortOrder()
                    else -> kotlinx.coroutines.flow.flowOf(null)
                }
            sortOrderFlow.firstOrNull()?.let { sortOrder ->
                when (sortOrder) {
                    SortOrder.A_TO_Z -> radioAtoZ.isChecked = true
                    SortOrder.Z_TO_A -> radioZtoA.isChecked = true
                    SortOrder.RATING_HIGH_TO_LOW -> radioRatingHighToLow.isChecked = true
                    SortOrder.RATING_LOW_TO_HIGH -> radioRatingLowToHigh.isChecked = true
                    SortOrder.DATE_NEW_TO_OLD -> radioDateNewToOld.isChecked = true
                    SortOrder.DATE_OLD_TO_NEW -> radioDateOldToNew.isChecked = true
                }
            } ?: run {
                // Varsayılan olarak A'dan Z'ye seçili
                radioAtoZ.isChecked = true
            }
        }

        btnSave.setOnClickListener {
            val selectedId = radioGroup.checkedRadioButtonId
            val sortOrder =
                when (selectedId) {
                    R.id.radio_a_to_z -> SortOrder.A_TO_Z
                    R.id.radio_z_to_a -> SortOrder.Z_TO_A
                    R.id.radio_rating_high_to_low -> SortOrder.RATING_HIGH_TO_LOW
                    R.id.radio_rating_low_to_high -> SortOrder.RATING_LOW_TO_HIGH
                    R.id.radio_date_new_to_old -> SortOrder.DATE_NEW_TO_OLD
                    R.id.radio_date_old_to_new -> SortOrder.DATE_OLD_TO_NEW
                    else -> SortOrder.A_TO_Z
                }

            when (contentType) {
                ContentType.MOVIES -> movieViewModel.saveMovieSortOrder(sortOrder)
                ContentType.SERIES -> seriesViewModel.saveSeriesSortOrder(sortOrder)
                else -> {}
            }
            dialog.dismiss()
        }

        // İlk focus'u ilk radio button'a ver
        radioAtoZ.requestFocus()

        dialog.show()
    }

    override fun updateEmptyState(
        contents: List<ContentItem>,
        selectedCategoryId: String?,
    ) {
        val isFavoritesEmpty =
            when (contentType) {
                ContentType.MOVIES -> {
                    selectedCategoryId == com.pnr.tv.ui.movies.MovieViewModel.VIRTUAL_CATEGORY_ID_FAVORITES_MOVIES && contents.isEmpty()
                }
                ContentType.SERIES -> {
                    selectedCategoryId == com.pnr.tv.ui.series.SeriesViewModel.VIRTUAL_CATEGORY_ID_FAVORITES_SERIES && contents.isEmpty()
                }
                else -> false
            }

        if (isFavoritesEmpty) {
            showEmptyState(getString(R.string.empty_favorites))
        } else {
            super.updateEmptyState(contents, selectedCategoryId)
        }
    }

    companion object {
        private const val ARG_CONTENT_TYPE = "content_type"
        private const val KEY_IS_INITIAL_LAUNCH = "is_initial_launch"

        /**
         * Yeni bir ContentBrowseFragment örneği oluşturur.
         *
         * @param contentType Gösterilecek içerik türü
         * @param isInitialLaunch Ana menüden ilk kez açılıyor mu
         * @return Yeni ContentBrowseFragment örneği
         */
        fun newInstance(
            contentType: ContentType,
            isInitialLaunch: Boolean = false,
        ): ContentBrowseFragment {
            return ContentBrowseFragment().apply {
                arguments =
                    Bundle().apply {
                        @Suppress("DEPRECATION")
                        putSerializable(ARG_CONTENT_TYPE, contentType)
                        putBoolean(KEY_IS_INITIAL_LAUNCH, isInitialLaunch)
                    }
            }
        }
    }
}
