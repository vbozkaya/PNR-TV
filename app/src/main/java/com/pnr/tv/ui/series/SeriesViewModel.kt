package com.pnr.tv.ui.series

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.pnr.tv.core.constants.ContentConstants
import com.pnr.tv.core.constants.DatabaseConstants
import com.pnr.tv.R
import com.pnr.tv.core.constants.UIConstants
import com.pnr.tv.db.entity.SeriesCategoryEntity
import com.pnr.tv.db.entity.SeriesEntity
import com.pnr.tv.model.CategoryItem
import com.pnr.tv.model.ContentItem
import com.pnr.tv.model.ContentType
import com.pnr.tv.model.SortOrder
import com.pnr.tv.premium.PremiumManager
import com.pnr.tv.repository.FavoriteRepository
import com.pnr.tv.repository.Result
import com.pnr.tv.repository.SeriesRepository
import com.pnr.tv.repository.ViewerRepository
import com.pnr.tv.core.base.BaseContentViewModel
import com.pnr.tv.core.base.CategoryBuilder
import com.pnr.tv.core.base.ContentFavoriteHandler
import com.pnr.tv.util.validation.AdultContentPreferenceManager
import com.pnr.tv.util.error.Resource
import com.pnr.tv.util.ui.SortPreferenceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Diziler için ViewModel.
 *
 * Dizi kategorileri, diziler, arama, sıralama ve favoriler işlemlerini yönetir.
 */
@HiltViewModel
class SeriesViewModel
    @Inject
    constructor(
        private val seriesRepository: SeriesRepository,
        favoriteRepository: FavoriteRepository,
        private val _viewerRepository: ViewerRepository,
        private val sortPreferenceManager: SortPreferenceManager,
        private val _adultContentPreferenceManager: AdultContentPreferenceManager,
        private val _premiumManager: PremiumManager,
        private val _contentFavoriteHandler: ContentFavoriteHandler,
        private val _categoryBuilder: CategoryBuilder,
        @ApplicationContext override val context: Context,
    ) : BaseContentViewModel() {
        override val favoriteRepository: FavoriteRepository = favoriteRepository
        override val contentFavoriteHandler: ContentFavoriteHandler = _contentFavoriteHandler
        override val categoryBuilder: CategoryBuilder = _categoryBuilder
        override val viewerRepository: ViewerRepository
            get() = _viewerRepository
        override val adultContentPreferenceManager: AdultContentPreferenceManager
            get() = _adultContentPreferenceManager
        override val premiumManager: PremiumManager
            get() = _premiumManager

        companion object {
            // Sanal kategori ID'leri - Diziler için
            val VIRTUAL_CATEGORY_ID_FAVORITES_SERIES = ContentConstants.VirtualCategoryIds.FAVORITES_STRING
            val VIRTUAL_CATEGORY_ID_RECENTLY_ADDED_SERIES = ContentConstants.VirtualCategoryIds.RECENTLY_ADDED_STRING
        }

        private val defaultViewerIdFlow: Flow<Int?> =
            viewerRepository.getAllViewers().map { viewers ->
                viewers.find { !it.isDeletable }?.id
            }

        private val seriesCategories: Flow<List<SeriesCategoryEntity>> =
            buildCategories().map { categories ->
                categories.mapNotNull { category ->
                    when (category) {
                        is SeriesCategoryEntity -> category
                        else -> null
                    }
                }
            }

        val seriesCategoriesFlow: Flow<List<CategoryItem>> = seriesCategories.map { it }

        private val _selectedSeriesCategoryId = MutableStateFlow<Any?>(null)
        val selectedSeriesCategoryId: StateFlow<Any?> = _selectedSeriesCategoryId.asStateFlow()

        private val _seriesSearchQuery = MutableStateFlow<String>("")
        val seriesSearchQuery: StateFlow<String> = _seriesSearchQuery.asStateFlow()

        private val _isSeriesLoading = MutableStateFlow(false)
        val isSeriesLoading: StateFlow<Boolean> = _isSeriesLoading.asStateFlow()

        // Error message artık BaseViewModel'den geliyor
        val seriesErrorMessage: StateFlow<String?> = errorMessage

        private val seriesSortOrderFlow: Flow<SortOrder?> = sortPreferenceManager.getSortOrder(ContentType.SERIES)

        // Search query için debounce'lu flow (sadece arama için gecikme)
        @OptIn(FlowPreview::class)
        private val debouncedSeriesSearchQuery: Flow<String> =
            _seriesSearchQuery.asStateFlow().debounce(
                UIConstants.DelayDurations.SEARCH_DEBOUNCE_MS,
            )

        // Önceki veriyi korumak için StateFlow
        private val _previousSeries = MutableStateFlow<List<SeriesEntity>>(emptyList())

        // Arama yapıldığında tüm dizileri, yapılmadığında seçili kategorideki dizileri getir
        @OptIn(ExperimentalCoroutinesApi::class)
        private val seriesWithSearch: Flow<List<SeriesEntity>> =
            combine(
                debouncedSeriesSearchQuery.onStart { emit("") },
                _selectedSeriesCategoryId.asStateFlow(),
            ) { query, categoryId ->
                // Arama yapılıyorsa (3+ karakter), tüm dizileri al
                if (query.length >= 3) {
                    Pair(true, query)
                } else {
                    // Arama yapılmıyorsa, seçili kategorideki dizileri al
                    Pair(false, categoryId as? String)
                }
            }.flatMapLatest { (isSearch, value) ->
                if (isSearch) {
                    // Arama modu: Tüm dizileri al
                    seriesRepository.getSeries().map { resource ->
                        when (resource) {
                            is Resource.Loading -> {
                                _isSeriesLoading.value = true
                                emptyList<SeriesEntity>()
                            }
                            is Resource.Success -> {
                                _isSeriesLoading.value = false
                                clearError()
                                _previousSeries.value = resource.data
                                resource.data
                            }
                            is Resource.Error -> {
                                _isSeriesLoading.value = false
                                handleResourceError(resource)
                                _previousSeries.value
                            }
                        }
                    }
                } else {
                    // Normal mod: Seçili kategorideki dizileri al
                    @Suppress("UNCHECKED_CAST")
                    val categoryIdString = value as? String
                    if (categoryIdString == null) {
                        kotlinx.coroutines.flow.flowOf(_previousSeries.value)
                    } else {
                        when (categoryIdString) {
                            VIRTUAL_CATEGORY_ID_RECENTLY_ADDED_SERIES ->
                                seriesRepository.getRecentlyAddedSeries(DatabaseConstants.RECENTLY_ADDED_LIMIT)
                                    .onEach { series ->
                                        _previousSeries.value = series
                                    }
                            VIRTUAL_CATEGORY_ID_FAVORITES_SERIES ->
                                defaultViewerIdFlow.flatMapLatest { defaultId ->
                                    if (defaultId != null) {
                                        favoriteRepository.getFavoriteChannelIds(defaultId).flatMapLatest { ids ->
                                            flow {
                                                val series = seriesRepository.getSeriesByIds(ids)
                                                _previousSeries.value = series
                                                emit(series)
                                            }
                                        }
                                    } else {
                                        kotlinx.coroutines.flow.flowOf(_previousSeries.value)
                                    }
                                }
                            else -> {
                                if (categoryIdString.startsWith("viewer_")) {
                                    val viewerId = categoryIdString.removePrefix("viewer_").toIntOrNull()
                                    if (viewerId != null) {
                                        favoriteRepository.getFavoriteChannelIds(viewerId).flatMapLatest { ids ->
                                            flow {
                                                val series = seriesRepository.getSeriesByIds(ids)
                                                _previousSeries.value = series
                                                emit(series)
                                            }
                                        }
                                    } else {
                                        kotlinx.coroutines.flow.flowOf(_previousSeries.value)
                                    }
                                } else {
                                    seriesRepository.getSeriesByCategoryId(categoryIdString).map { resource ->
                                        when (resource) {
                                            is Resource.Loading -> {
                                                _isSeriesLoading.value = true
                                                emptyList<SeriesEntity>()
                                            }
                                            is Resource.Success -> {
                                                _isSeriesLoading.value = false
                                                clearError()
                                                _previousSeries.value = resource.data
                                                resource.data
                                            }
                                            is Resource.Error -> {
                                                _isSeriesLoading.value = false
                                                handleResourceError(resource)
                                                _previousSeries.value
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

        // Kategori ve sort değişiklikleri anında yansısın, sadece search query debounce olsun
        val seriesFlow: Flow<List<ContentItem>> =
            combine(
                seriesWithSearch.onStart { emit(emptyList()) }, // Başlangıç değeri - combine için gerekli
                seriesSortOrderFlow.onStart { emit(null) }, // Başlangıç değeri
                debouncedSeriesSearchQuery.onStart { emit("") }, // Başlangıç değeri
                adultContentPreferenceManager.isAdultContentEnabled().onStart { emit(false) }, // Başlangıç değeri
            ) { items, sortOrder, query, adultContentEnabled ->
                val combineStartTime = System.currentTimeMillis()

                val sortedItems = applySorting(items, sortOrder)
                val searchFilteredItems = applySearchFilter(sortedItems, query)
                val adultFilteredItems = applyAdultContentFilter(searchFilteredItems, adultContentEnabled)
                val combineTime = System.currentTimeMillis() - combineStartTime
                adultFilteredItems
            }.flowOn(Dispatchers.Default) // Heavy computations on background thread

        init {
            // Kategori seçimi fragment'ta yapılacak (ilk kategori otomatik seçilecek)
            _selectedSeriesCategoryId.value = null
            // Verileri ViewModel oluşturulur oluşturulmaz yükle
            loadSeries()
        }

        /**
         * Dizileri yükler.
         * Veriler yalnızca bir kez (ViewModel ilk oluşturulduğunda) yüklenir.
         */
        private fun loadSeries() {
            loadContentIfNeeded()
        }

        override val logTag: String = "📺"

        override val isLoading: MutableStateFlow<Boolean> = _isSeriesLoading

        // Error message artık BaseViewModel'den geliyor, override gerekmiyor
        override val contentLoadErrorStringId: Int = R.string.error_series_load_failed
        override val errorContext: String = "SeriesViewModel"
        override val virtualCategoryIdFavorites: String = VIRTUAL_CATEGORY_ID_FAVORITES_SERIES
        override val virtualCategoryIdRecentlyAdded: String = VIRTUAL_CATEGORY_ID_RECENTLY_ADDED_SERIES

        override suspend fun refreshCategories(): Result<Unit> {
            return seriesRepository.refreshSeriesCategories()
        }

        override suspend fun hasData(): Boolean {
            return seriesRepository.hasSeries()
        }

        override suspend fun refreshContent(): Result<Unit> {
            return seriesRepository.refreshSeries()
        }

        override fun getNormalCategories(): Flow<List<CategoryItem>> {
            return seriesRepository.getSeriesCategories().map { resource ->
                when (resource) {
                    is Resource.Loading -> emptyList()
                    is Resource.Success -> {
                        clearError()
                        resource.data
                    }
                    is Resource.Error -> {
                        handleResourceError(resource)
                        emptyList()
                    }
                }
            }
        }

        override fun getAllContent(): Flow<List<ContentItem>> {
            return seriesRepository.getSeries().map { resource ->
                when (resource) {
                    is Resource.Loading -> emptyList()
                    is Resource.Success -> {
                        clearError()
                        resource.data
                    }
                    is Resource.Error -> {
                        handleResourceError(resource)
                        emptyList()
                    }
                }
            }
        }

        override suspend fun getCategoryCounts(): Map<String, Int> {
            return seriesRepository.getCategoryCounts()
        }

        override fun ContentItem.isAdultContent(): Boolean {
            return when (this) {
                is SeriesEntity -> this.isAdult == true
                else -> false
            }
        }

        override fun ContentItem.getCategoryId(): String? {
            return when (this) {
                is SeriesEntity -> this.categoryId
                else -> null
            }
        }

        override fun createVirtualCategoryEntity(
            categoryId: String,
            categoryName: String,
            parentId: Int,
            sortOrder: Int,
        ): CategoryItem {
            return SeriesCategoryEntity(
                categoryId = categoryId,
                categoryName = categoryName,
                parentId = parentId,
                sortOrder = sortOrder,
            )
        }

        /**
         * Kategori seçer (Diziler için).
         */
        fun selectSeriesCategory(categoryId: Any) {
            _selectedSeriesCategoryId.value = categoryId
        }

        /**
         * Dizileri favorilere ekler.
         */
        fun addSeriesFavorite(
            contentId: Int,
            viewerId: Int,
        ) {
            addFavorite(contentId, viewerId)
        }

        /**
         * Dizileri favorilerden çıkarır.
         */
        fun removeSeriesFavorite(
            contentId: Int,
            viewerId: Int,
        ) {
            removeFavorite(contentId, viewerId)
        }

        /**
         * Dizinin favori olup olmadığını kontrol eder.
         */
        fun isSeriesFavorite(
            contentId: Int,
            viewerId: Int,
        ): Flow<Boolean> = isFavorite(contentId, viewerId)

        /**
         * Dizi arama sorgusunu günceller.
         */
        fun onSeriesSearchQueryChanged(query: String) {
            _seriesSearchQuery.value = query
        }

        /**
         * Dizi sıralama tercihini kaydeder.
         */
        fun saveSeriesSortOrder(sortOrder: SortOrder) {
            viewModelScope.launch {
                sortPreferenceManager.saveSortOrder(ContentType.SERIES, sortOrder)
            }
        }

        /**
         * Mevcut dizi sıralama tercihini döndürür.
         */
        fun getCurrentSeriesSortOrder(): Flow<SortOrder?> = seriesSortOrderFlow
    }
