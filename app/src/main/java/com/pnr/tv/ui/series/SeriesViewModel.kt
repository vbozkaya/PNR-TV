package com.pnr.tv.ui.series

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.pnr.tv.ContentConstants
import com.pnr.tv.R
import com.pnr.tv.db.entity.SeriesCategoryEntity
import com.pnr.tv.db.entity.SeriesEntity
import com.pnr.tv.model.CategoryItem
import com.pnr.tv.model.ContentItem
import com.pnr.tv.model.ContentType
import com.pnr.tv.model.SortOrder
import com.pnr.tv.repository.ContentRepository
import com.pnr.tv.repository.Result
import com.pnr.tv.repository.ViewerRepository
import com.pnr.tv.ui.base.BaseViewModel
import com.pnr.tv.util.SortPreferenceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import timber.log.Timber
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
        private val contentRepository: ContentRepository,
        private val viewerRepository: ViewerRepository,
        private val sortPreferenceManager: SortPreferenceManager,
        @ApplicationContext override val context: Context,
    ) : BaseViewModel() {
        companion object {
            // Sanal kategori ID'leri - Diziler için
            val VIRTUAL_CATEGORY_ID_FAVORITES_SERIES = ContentConstants.VirtualCategoryIds.FAVORITES_STRING
            val VIRTUAL_CATEGORY_ID_RECENTLY_ADDED_SERIES = ContentConstants.VirtualCategoryIds.RECENTLY_ADDED_STRING
            val VIRTUAL_CATEGORY_ID_ALL_SERIES = ContentConstants.VirtualCategoryIds.ALL_STRING
        }

        private val favoriteIds: Flow<List<Int>> = contentRepository.getAllFavoriteChannelIds()

        private fun getViewerCategoryId(viewerId: Int): String = "viewer_$viewerId"

        private val defaultViewerIdFlow: Flow<Int?> =
            viewerRepository.getAllViewers().map { viewers ->
                viewers.find { !it.isDeletable }?.id
            }

        private val seriesCategories: Flow<List<SeriesCategoryEntity>> =
            combine(
                contentRepository.getSeriesCategories(),
                favoriteIds,
                viewerRepository.getViewerIdsWithFavorites(),
                viewerRepository.getAllViewers(),
            ) { normalCategories, _, viewerIdsWithFavorites, allViewers ->
                mutableListOf<SeriesCategoryEntity>().apply {
                    add(
                        SeriesCategoryEntity(
                            VIRTUAL_CATEGORY_ID_RECENTLY_ADDED_SERIES,
                            context.getString(R.string.category_recently_added),
                            ContentConstants.SortOrder.DEFAULT,
                            ContentConstants.SortOrder.FAVORITES - 1,
                        ),
                    )

                    val defaultViewer = allViewers.find { !it.isDeletable }
                    val defaultViewerHasFavorites = defaultViewer?.let { it.id in viewerIdsWithFavorites } ?: false

                    if (defaultViewerHasFavorites && defaultViewer != null) {
                        add(
                            SeriesCategoryEntity(
                                VIRTUAL_CATEGORY_ID_FAVORITES_SERIES,
                                context.getString(R.string.category_favorites),
                                ContentConstants.SortOrder.DEFAULT,
                                ContentConstants.SortOrder.FAVORITES,
                            ),
                        )
                    }

                    viewerIdsWithFavorites.forEach { viewerId ->
                        val viewer = allViewers.find { it.id == viewerId }
                        if (viewer != null && viewer.isDeletable) {
                            add(
                                SeriesCategoryEntity(
                                    getViewerCategoryId(viewerId),
                                    context.getString(R.string.category_viewer_favorites, viewer.name.uppercase()),
                                    ContentConstants.SortOrder.DEFAULT,
                                    ContentConstants.SortOrder.FAVORITES,
                                ),
                            )
                        }
                    }

                    add(
                        SeriesCategoryEntity(
                            VIRTUAL_CATEGORY_ID_ALL_SERIES,
                            context.getString(R.string.category_all_series),
                            ContentConstants.SortOrder.DEFAULT,
                            ContentConstants.SortOrder.ALL,
                        ),
                    )
                    addAll(normalCategories)
                }
            }

        val seriesCategoriesFlow: Flow<List<CategoryItem>> = seriesCategories.map { it }

        private val _selectedSeriesCategoryId = MutableStateFlow<Any?>(null)
        val selectedSeriesCategoryId: StateFlow<Any?> = _selectedSeriesCategoryId.asStateFlow()

        private val _seriesSearchQuery = MutableStateFlow<String>("")
        val seriesSearchQuery: StateFlow<String> = _seriesSearchQuery.asStateFlow()

        private val _isSeriesLoading = MutableStateFlow(false)
        val isSeriesLoading: StateFlow<Boolean> = _isSeriesLoading.asStateFlow()

        private val _seriesErrorMessage = MutableStateFlow<String?>(null)
        val seriesErrorMessage: StateFlow<String?> = _seriesErrorMessage.asStateFlow()

        @OptIn(ExperimentalCoroutinesApi::class)
        private val series: Flow<List<SeriesEntity>> =
            _selectedSeriesCategoryId.asStateFlow().flatMapLatest { categoryId ->
                val categoryIdString = categoryId as? String ?: VIRTUAL_CATEGORY_ID_ALL_SERIES
                Timber.tag("GRID_UPDATE").d("📺 series Flow tetiklendi: categoryId=$categoryIdString")
                when (categoryIdString) {
                    VIRTUAL_CATEGORY_ID_RECENTLY_ADDED_SERIES ->
                        flow {
                            emit(contentRepository.getRecentlyAddedSeries(20))
                        }
                    VIRTUAL_CATEGORY_ID_FAVORITES_SERIES ->
                        defaultViewerIdFlow.flatMapLatest { defaultId ->
                            if (defaultId != null) {
                                contentRepository.getFavoriteChannelIds(defaultId).flatMapLatest { ids ->
                                    flow {
                                        emit(contentRepository.getSeriesByIds(ids))
                                    }
                                }
                            } else {
                                kotlinx.coroutines.flow.flowOf(emptyList())
                            }
                        }
                    VIRTUAL_CATEGORY_ID_ALL_SERIES -> contentRepository.getSeries()
                    else -> {
                        if (categoryIdString.startsWith("viewer_")) {
                            val viewerId = categoryIdString.removePrefix("viewer_").toIntOrNull()
                            if (viewerId != null) {
                                contentRepository.getFavoriteChannelIds(viewerId).flatMapLatest { ids ->
                                    flow {
                                        emit(contentRepository.getSeriesByIds(ids))
                                    }
                                }
                            } else {
                                kotlinx.coroutines.flow.flowOf(emptyList())
                            }
                        } else {
                            contentRepository.getSeriesByCategoryId(categoryIdString)
                        }
                    }
                }
            }

        private val seriesSortOrderFlow: Flow<SortOrder?> = sortPreferenceManager.getSortOrder(ContentType.SERIES)

        // Search query için debounce'lu flow (sadece arama için gecikme)
        @OptIn(FlowPreview::class)
        private val debouncedSeriesSearchQuery: Flow<String> = _seriesSearchQuery.asStateFlow().debounce(500)

        // Kategori ve sort değişiklikleri anında yansısın, sadece search query debounce olsun
        val seriesFlow: Flow<List<ContentItem>> =
            combine(
                series.onStart { emit(emptyList()) }, // Başlangıç değeri - combine için gerekli
                seriesSortOrderFlow.onStart { emit(null) }, // Başlangıç değeri
                debouncedSeriesSearchQuery.onStart { emit("") }, // Başlangıç değeri
            ) { items, sortOrder, query ->
                val combineStartTime = System.currentTimeMillis()
                Timber.tag("GRID_UPDATE").d("🔄 seriesFlow combine: items=${items.size}, sort=$sortOrder, query='$query'")
                val sortedItems = applySorting(items, sortOrder)
                val filteredItems = applySearchFilter(sortedItems, query)
                val combineTime = System.currentTimeMillis() - combineStartTime
                Timber.tag("GRID_UPDATE").d("✅ seriesFlow sonuç: ${filteredItems.size} item (combine süresi: ${combineTime}ms)")
                filteredItems
            }

        init {
            // İlk kategoriyi seç
            _selectedSeriesCategoryId.value = VIRTUAL_CATEGORY_ID_ALL_SERIES
        }

        /**
         * Dizileri yükler (eğer daha önce yüklenmemişse).
         * Ana sayfada zaten yüklenmişse, tekrar yüklemez.
         */
        fun loadSeriesIfNeeded() {
            viewModelScope.launch {
                _seriesErrorMessage.value = null
                
                // Önce veritabanında veri olup olmadığını kontrol et
                val hasData = contentRepository.hasSeries()
                val hasCategories = contentRepository.hasSeriesCategories()
                
                if (hasData && hasCategories) {
                    // Veri zaten var, yükleme yapma
                    Timber.d("📺 Diziler zaten yüklü, tekrar yükleme atlanıyor")
                    _isSeriesLoading.value = false
                    return@launch
                }
                
                // Veri yok, yükleme yap
                _isSeriesLoading.value = true
                Timber.d("📺 Diziler yükleniyor (veri yok veya eksik)")

                try {
                    val categoriesResult = contentRepository.refreshSeriesCategories()
                    if (categoriesResult is Result.Error) {
                        _seriesErrorMessage.value = context.getString(R.string.error_categories_short)
                        _isSeriesLoading.value = false
                        return@launch
                    }
                    val seriesResult = contentRepository.refreshSeries()
                    if (seriesResult is Result.Error) {
                        _seriesErrorMessage.value = context.getString(R.string.error_series_short)
                    }
                } catch (e: Exception) {
                    _seriesErrorMessage.value = context.getString(R.string.error_unknown)
                } finally {
                    _isSeriesLoading.value = false
                }
            }
        }

        /**
         * Kategori seçer (Diziler için).
         */
        fun selectSeriesCategory(categoryId: Any) {
            Timber.tag("GRID_UPDATE").d("📺 selectSeriesCategory: $categoryId")
            _selectedSeriesCategoryId.value = categoryId
        }

        /**
         * Dizileri favorilere ekler.
         */
        fun addSeriesFavorite(
            contentId: Int,
            viewerId: Int,
        ) {
            viewModelScope.launch {
                contentRepository.addFavorite(contentId, viewerId)
                showToast(context.getString(R.string.toast_favorite_added))
            }
        }

        /**
         * Dizileri favorilerden çıkarır.
         */
        fun removeSeriesFavorite(
            contentId: Int,
            viewerId: Int,
        ) {
            viewModelScope.launch {
                contentRepository.removeFavorite(contentId, viewerId)
                showToast(context.getString(R.string.toast_favorite_removed))
            }
        }

        /**
         * Dizinin favori olup olmadığını kontrol eder.
         */
        fun isSeriesFavorite(
            contentId: Int,
            viewerId: Int,
        ): Flow<Boolean> = contentRepository.isFavorite(contentId, viewerId)

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

        // ==================== Helper Functions ====================

        /**
         * İçerik listesine arama filtresi uygular.
         */
        private fun applySearchFilter(
            items: List<ContentItem>,
            query: String,
        ): List<ContentItem> {
            if (query.length < 3) {
                return items
            }

            val lowerQuery = query.lowercase()
            return items.filter { item ->
                item.title.lowercase().contains(lowerQuery)
            }
        }

        /**
         * İçerik listesine sıralama uygular.
         */
        private fun applySorting(
            items: List<ContentItem>,
            sortOrder: SortOrder?,
        ): List<ContentItem> {
            if (sortOrder == null) return items

            return when (sortOrder) {
                SortOrder.A_TO_Z -> items.sortedBy { it.title.lowercase() }
                SortOrder.Z_TO_A -> items.sortedByDescending { it.title.lowercase() }
                SortOrder.RATING_HIGH_TO_LOW ->
                    items.sortedByDescending { item ->
                        when (item) {
                            is SeriesEntity -> item.rating ?: 0.0
                            else -> 0.0
                        }
                    }
                SortOrder.RATING_LOW_TO_HIGH ->
                    items.sortedBy { item ->
                        when (item) {
                            is SeriesEntity -> item.rating ?: Double.MAX_VALUE
                            else -> Double.MAX_VALUE
                        }
                    }
                SortOrder.DATE_NEW_TO_OLD ->
                    items.sortedByDescending { item ->
                        when (item) {
                            is SeriesEntity -> item.added
                            else -> null
                        }?.toLongOrNull() ?: 0L
                    }
                SortOrder.DATE_OLD_TO_NEW ->
                    items.sortedBy { item ->
                        when (item) {
                            is SeriesEntity -> item.added
                            else -> null
                        }?.toLongOrNull() ?: Long.MAX_VALUE
                    }
            }
        }
    }
