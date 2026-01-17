package com.pnr.tv.ui.browse

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.pnr.tv.R
import com.pnr.tv.core.constants.UIConstants
import com.pnr.tv.model.CategoryItem
import com.pnr.tv.model.ContentItem
import com.pnr.tv.model.ContentType
import com.pnr.tv.core.base.BaseBrowseFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * İçerik türüne göre içerikleri gösteren Fragment.
 * BaseBrowseFragment'tan türetilmiştir ve tüm ortak mantık base'de yönetilir.
 */
@AndroidEntryPoint
class ContentBrowseFragment : BaseBrowseFragment() {
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

    private var contentType: ContentType? = null
    private val movieViewModel: com.pnr.tv.ui.movies.MovieViewModel by activityViewModels()
    private val seriesViewModel: com.pnr.tv.ui.series.SeriesViewModel by activityViewModels()

    @javax.inject.Inject
    lateinit var contentRepository: com.pnr.tv.repository.ContentRepository

    private var stateHelper: BrowseStateHelper? = null

    // BaseBrowseFragment requires BaseViewModel
    override val viewModel: com.pnr.tv.core.base.BaseViewModel
        get() =
            when (contentType) {
                ContentType.MOVIES -> movieViewModel
                ContentType.SERIES -> seriesViewModel
                else -> movieViewModel // fallback (bu durum olmamalı)
            }

    override fun onCreate(savedInstanceState: Bundle?) {
        // [FIX]: ViewModel getter'ı super.onCreate içinde çağrıldığı için
        // contentType değeri super.onCreate'ten önce atanmalı.
        @Suppress("DEPRECATION")
        contentType = arguments?.getSerializable(ARG_CONTENT_TYPE) as? ContentType

        super.onCreate(savedInstanceState)

        // Sadece ilk oluşturulduğunda yükle, savedInstanceState null ise
        if (savedInstanceState == null) {
            lifecycleScope.launch {
                // Veri kontrolü yap, eğer veri yoksa uyarı göster ve çık
                if (checkDataAndShowWarningIfNeeded()) {
                    onInitialLoad()
                }
            }
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

        // State helper'ı başlat
        stateHelper =
            BrowseStateHelper(
                lifecycleOwner = viewLifecycleOwner,
                contentType = contentType,
                movieViewModel = movieViewModel,
                seriesViewModel = seriesViewModel,
                stateCallbacks =
                    object : BrowseStateHelper.StateCallbacks {
                        override fun showErrorState(message: String) {
                            this@ContentBrowseFragment.showErrorState(message)
                        }

                        override fun showLoadingState() {
                            this@ContentBrowseFragment.showLoadingState()
                        }

                        override fun showContentState() {
                            this@ContentBrowseFragment.showContentState()
                        }

                        override fun showEmptyState(message: String) {
                            this@ContentBrowseFragment.showEmptyState(message)
                        }

                        override fun getString(resId: Int): String {
                            return this@ContentBrowseFragment.getString(resId)
                        }
                    },
            )

        // State gözlemlerini başlat
        stateHelper?.observeErrorState()
        stateHelper?.observeLoadingState()
    }

    override fun onResume() {
        super.onResume()
        // Reaktif yapıya güven: Veritabanı (Room) ve Flow yapısı zaten kullanılıyor.
        // Veri değişirse UI otomatik güncellenir. Manuel bir API isteğine onResume içinde gerek yok.
        // Yükleme işlemi sadece Fragment ilk oluşturulduğunda (onCreate / onInitialLoad) yapılır.
        // Kullanıcı sadece Ana Sayfadan manuel "Güncelle" dediğinde veriler yenilenir.
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

    override fun getCategoriesRecyclerViewId(): Int = R.id.categories_recycler_view

    override fun getContentRecyclerViewId(): Int = R.id.recycler_content

    override fun getEmptyStateTextViewId(): Int = R.id.txt_empty_state

    override fun getGridColumnCount(): Int = UIConstants.GRID_COLUMN_COUNT

    /**
     * Premium yazısı sadece film ve dizi sayfalarında görünür
     */
    override fun shouldShowPremiumText(): Boolean {
        return contentType == ContentType.MOVIES || contentType == ContentType.SERIES
    }

    // Override hooks from BaseBrowseFragment
    override suspend fun hasData(): Boolean {
        return try {
            when (contentType) {
                ContentType.MOVIES -> contentRepository.hasMovies()
                ContentType.SERIES -> contentRepository.hasSeries()
                else -> false
            }
        } catch (e: Exception) {
            // İçerik veri kontrolü hatası
            false
        }
    }

    override fun onInitialLoad() {
        when (contentType) {
            ContentType.MOVIES -> { /* ViewModel init bloğunda yükleniyor */ }
            ContentType.SERIES -> { /* ViewModel init bloğunda yükleniyor */ }
            else -> {}
        }
    }

    override fun onCategoryClicked(category: CategoryItem) {
        // Kategori loglama
        // Arama çubuğunu temizle (Kategoriye geçiş yapıldığında arama modundan çıkmak için)
        val searchEditText = navbarView?.findViewById<android.widget.EditText>(R.id.edt_navbar_search)
        if (!searchEditText?.text.isNullOrEmpty()) {
            searchEditText?.text?.clear()
        }

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
        // Sadece focus değişikliği - içerik yükleme YOK
        // İçerik yükleme sadece OK tuşuna basıldığında (onCategoryClicked) yapılır
        // Base sınıfın onCategoryFocused metodunu çağır (pendingRestorePosition temizlemek için)
        super.onCategoryFocused(category)

        // NOT: Kategori seçimi yapılmıyor - sadece focus değişikliği
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
                // Film detay sayfasına git (Activity)
                val movieEntity = item as? com.pnr.tv.db.entity.MovieEntity
                movieEntity?.let {
                    val intent = com.pnr.tv.ui.movies.MovieDetailActivity.newIntent(requireContext(), it.streamId)
                    startActivity(intent)
                }
            }
            ContentType.SERIES -> {
                // Dizi detay sayfasına git (Activity)
                val seriesEntity = item as? com.pnr.tv.db.entity.SeriesEntity
                seriesEntity?.let {
                    val intent = com.pnr.tv.ui.series.SeriesDetailActivity.newIntent(requireContext(), it.streamId)
                    startActivity(intent)
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
                    // Premium kontrolü
                    viewLifecycleOwner.lifecycleScope.launch {
                        val isPremium = premiumManager.isPremium().first()
                        if (isPremium) {
                            contentType?.let { type ->
                                val sortHandler =
                                    BrowseSortHandler(
                                        context = requireContext(),
                                        contentType = type,
                                        movieViewModel = movieViewModel,
                                        seriesViewModel = seriesViewModel,
                                        lifecycleOwner = viewLifecycleOwner,
                                    )
                                sortHandler.showSortDialog()
                            }
                        }
                        // Premium değilse tıklama işlemi yapılmaz (pasif)
                    }
                }

                // Arama kutusu ayarları
                searchEditText?.visibility = View.VISIBLE
                searchEditText?.isFocusable = true
                searchEditText?.isFocusableInTouchMode = true // TV remote için gerekli

                // Metin değişikliklerini dinle - premium durumuna göre
                viewLifecycleOwner.lifecycleScope.launch {
                    premiumManager.isPremium().collectLatest { isPremium ->
                        if (isPremium) {
                            // Premium ise TextWatcher ekle
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
                        } else {
                            // Premium değilse TextWatcher'ı kaldır ve metni temizle
                            searchEditText?.text?.clear()
                            searchEditText?.removeTextChangedListener(null)
                        }
                    }
                }
            }
            else -> {
                filterButton?.visibility = View.GONE
                searchEditText?.visibility = View.GONE
            }
        }
    }

    override fun updateEmptyState(
        contents: List<ContentItem>,
        selectedCategoryId: String?,
    ) {
        stateHelper?.updateEmptyState(contents, selectedCategoryId)
    }
}
