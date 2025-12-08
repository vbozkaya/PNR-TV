package com.pnr.tv.ui.browse

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.pnr.tv.Constants
import com.pnr.tv.R
import com.pnr.tv.db.entity.LiveStreamEntity
import com.pnr.tv.db.entity.MovieCategoryEntity
import com.pnr.tv.db.entity.MovieEntity
import com.pnr.tv.db.entity.SeriesCategoryEntity
import com.pnr.tv.db.entity.SeriesEntity
import com.pnr.tv.model.CategoryItem
import com.pnr.tv.model.ContentItem
import com.pnr.tv.model.ContentType
import com.pnr.tv.model.SortOrder
import com.pnr.tv.repository.ContentRepository
import com.pnr.tv.repository.ViewerRepository
import com.pnr.tv.ui.base.BaseViewModel
import com.pnr.tv.util.SortPreferenceManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
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
import kotlinx.coroutines.launch

/**
 * İçerik türüne göre içerikleri yükleyen ViewModel.
 * AssistedInject kullanarak ContentType parametresini alır.
 * Kategori seçimi ve içerik filtreleme desteği sağlar.
 */
class ContentViewModel
    @AssistedInject
    constructor(
        private val contentRepository: ContentRepository,
        private val viewerRepository: ViewerRepository,
        private val sortPreferenceManager: SortPreferenceManager,
        @Assisted private val contentType: ContentType,
        @ApplicationContext override val context: Context,
    ) : BaseViewModel() {
        companion object {
            // Filmler için sanal kategori ID'leri
            val VIRTUAL_CATEGORY_ID_FAVORITES_MOVIES = Constants.VirtualCategoryIds.FAVORITES_STRING
            val VIRTUAL_CATEGORY_ID_RECENTLY_ADDED_MOVIES = Constants.VirtualCategoryIds.RECENTLY_ADDED_STRING
            val VIRTUAL_CATEGORY_ID_ALL_MOVIES = Constants.VirtualCategoryIds.ALL_STRING

            // Diziler için sanal kategori ID'leri
            val VIRTUAL_CATEGORY_ID_FAVORITES_SERIES = Constants.VirtualCategoryIds.FAVORITES_STRING
            val VIRTUAL_CATEGORY_ID_RECENTLY_ADDED_SERIES = Constants.VirtualCategoryIds.RECENTLY_ADDED_STRING
            val VIRTUAL_CATEGORY_ID_ALL_SERIES = Constants.VirtualCategoryIds.ALL_STRING
        }

        private val favoriteIds: Flow<List<Int>> = contentRepository.getAllFavoriteChannelIds()

        // Helper function to create viewer-specific category ID
        private fun getViewerCategoryId(viewerId: Int): String = "viewer_$viewerId"

        // Get default viewer ID
        private val defaultViewerId: Flow<Int?> =
            viewerRepository.getAllViewers().map { viewers ->
                viewers.find { !it.isDeletable }?.id
            }

        // Internal flows for specific types (kept for mapping)
        private val movieCategories: Flow<List<MovieCategoryEntity>> =
            combine(
                contentRepository.getMovieCategories(),
                favoriteIds,
                viewerRepository.getViewerIdsWithFavorites(),
                viewerRepository.getAllViewers(),
            ) { normalCategories, _, viewerIdsWithFavorites, allViewers ->
                mutableListOf<MovieCategoryEntity>().apply {
                    add(
                        MovieCategoryEntity(
                            VIRTUAL_CATEGORY_ID_RECENTLY_ADDED_MOVIES,
                            context.getString(R.string.category_recently_added),
                            Constants.SortOrder.DEFAULT,
                            Constants.SortOrder.FAVORITES - 1,
                        ),
                    )

                    // Find default viewer (isDeletable = false)
                    val defaultViewer = allViewers.find { !it.isDeletable }
                    val defaultViewerHasFavorites = defaultViewer?.let { it.id in viewerIdsWithFavorites } ?: false

                    // Add "FAVORİLER" category for default viewer if it has favorites
                    if (defaultViewerHasFavorites && defaultViewer != null) {
                        add(
                            MovieCategoryEntity(
                                VIRTUAL_CATEGORY_ID_FAVORITES_MOVIES,
                                context.getString(R.string.category_favorites),
                                Constants.SortOrder.DEFAULT,
                                Constants.SortOrder.FAVORITES,
                            ),
                        )
                    }

                    // Add viewer-specific favorite categories for non-default viewers
                    viewerIdsWithFavorites.forEach { viewerId ->
                        val viewer = allViewers.find { it.id == viewerId }
                        if (viewer != null && viewer.isDeletable) {
                            add(
                                MovieCategoryEntity(
                                    getViewerCategoryId(viewerId),
                                    context.getString(R.string.category_viewer_favorites, viewer.name.uppercase()),
                                    Constants.SortOrder.DEFAULT,
                                    Constants.SortOrder.FAVORITES,
                                ),
                            )
                        }
                    }

                    add(
                        MovieCategoryEntity(
                            VIRTUAL_CATEGORY_ID_ALL_MOVIES,
                            context.getString(R.string.category_all_movies),
                            Constants.SortOrder.DEFAULT,
                            Constants.SortOrder.ALL,
                        ),
                    )
                    addAll(normalCategories)
                }
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
                            Constants.SortOrder.DEFAULT,
                            Constants.SortOrder.FAVORITES - 1,
                        ),
                    )

                    // Find default viewer (isDeletable = false)
                    val defaultViewer = allViewers.find { !it.isDeletable }
                    val defaultViewerHasFavorites = defaultViewer?.let { it.id in viewerIdsWithFavorites } ?: false

                    // Add "FAVORİLER" category for default viewer if it has favorites
                    if (defaultViewerHasFavorites && defaultViewer != null) {
                        add(
                            SeriesCategoryEntity(
                                VIRTUAL_CATEGORY_ID_FAVORITES_SERIES,
                                context.getString(R.string.category_favorites),
                                Constants.SortOrder.DEFAULT,
                                Constants.SortOrder.FAVORITES,
                            ),
                        )
                    }

                    // Add viewer-specific favorite categories for non-default viewers
                    viewerIdsWithFavorites.forEach { viewerId ->
                        val viewer = allViewers.find { it.id == viewerId }
                        if (viewer != null && viewer.isDeletable) {
                            add(
                                SeriesCategoryEntity(
                                    getViewerCategoryId(viewerId),
                                    context.getString(R.string.category_viewer_favorites, viewer.name.uppercase()),
                                    Constants.SortOrder.DEFAULT,
                                    Constants.SortOrder.FAVORITES,
                                ),
                            )
                        }
                    }

                    add(
                        SeriesCategoryEntity(
                            VIRTUAL_CATEGORY_ID_ALL_SERIES,
                            context.getString(R.string.category_all_series),
                            Constants.SortOrder.DEFAULT,
                            Constants.SortOrder.ALL,
                        ),
                    )
                    addAll(normalCategories)
                }
            }

        // Generic categories flow - maps to CategoryItem based on contentType
        val categories: Flow<List<CategoryItem>> =
            when (contentType) {
                ContentType.MOVIES -> movieCategories.map { it }
                ContentType.SERIES -> seriesCategories.map { it }
                else -> kotlinx.coroutines.flow.flowOf(emptyList())
            }

        // Seçili kategori ID'si (ContentType'a göre String veya Int)
        private val _selectedCategoryId = MutableStateFlow<Any?>(null)
        val selectedCategoryId: StateFlow<Any?> = _selectedCategoryId.asStateFlow()

        // Arama sorgusu StateFlow
        private val _searchQuery = MutableStateFlow<String>("")
        val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

        // Internal flows for specific types (kept for mapping)
        @OptIn(ExperimentalCoroutinesApi::class)
        private val movies: Flow<List<MovieEntity>> =
            if (contentType == ContentType.MOVIES) {
                _selectedCategoryId.asStateFlow().flatMapLatest { categoryId ->
                    val categoryIdString = categoryId as? String ?: VIRTUAL_CATEGORY_ID_ALL_MOVIES
                    when (categoryIdString) {
                        VIRTUAL_CATEGORY_ID_RECENTLY_ADDED_MOVIES ->
                            flow {
                                emit(contentRepository.getRecentlyAddedMovies(20))
                            }
                        VIRTUAL_CATEGORY_ID_FAVORITES_MOVIES ->
                            defaultViewerId.flatMapLatest { defaultId ->
                                if (defaultId != null) {
                                    contentRepository.getFavoriteChannelIds(defaultId).flatMapLatest { ids ->
                                        flow {
                                            emit(contentRepository.getMoviesByIds(ids))
                                        }
                                    }
                                } else {
                                    kotlinx.coroutines.flow.flowOf(emptyList())
                                }
                            }
                        VIRTUAL_CATEGORY_ID_ALL_MOVIES -> contentRepository.getMovies()
                        else -> {
                            // Check if it's a viewer-specific category
                            if (categoryIdString.startsWith("viewer_")) {
                                val viewerId = categoryIdString.removePrefix("viewer_").toIntOrNull()
                                if (viewerId != null) {
                                    contentRepository.getFavoriteChannelIds(viewerId).flatMapLatest { ids ->
                                        flow {
                                            emit(contentRepository.getMoviesByIds(ids))
                                        }
                                    }
                                } else {
                                    kotlinx.coroutines.flow.flowOf(emptyList())
                                }
                            } else {
                                contentRepository.getMoviesByCategoryId(categoryIdString)
                            }
                        }
                    }
                }
            } else {
                kotlinx.coroutines.flow.flowOf(emptyList())
            }

        @OptIn(ExperimentalCoroutinesApi::class)
        private val series: Flow<List<SeriesEntity>> =
            if (contentType == ContentType.SERIES) {
                _selectedCategoryId.asStateFlow().flatMapLatest { categoryId ->
                    val categoryIdString = categoryId as? String ?: VIRTUAL_CATEGORY_ID_ALL_SERIES
                    when (categoryIdString) {
                        VIRTUAL_CATEGORY_ID_RECENTLY_ADDED_SERIES ->
                            flow {
                                emit(contentRepository.getRecentlyAddedSeries(20))
                            }
                        VIRTUAL_CATEGORY_ID_FAVORITES_SERIES ->
                            defaultViewerId.flatMapLatest { defaultId ->
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
                            // Check if it's a viewer-specific category
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
            } else {
                kotlinx.coroutines.flow.flowOf(emptyList())
            }

        // Sıralama tercihi flow'u
        private val sortOrderFlow: Flow<SortOrder?> = sortPreferenceManager.getSortOrder(contentType)

        // Generic contents flow - maps to ContentItem based on contentType, applies sorting and search filtering
        @OptIn(FlowPreview::class)
        val contents: Flow<List<ContentItem>> =
            when (contentType) {
                ContentType.MOVIES ->
                    combine(
                        movies,
                        sortOrderFlow,
                        _searchQuery.asStateFlow().debounce(500),
                    ) { items, sortOrder, query ->
                        val sortedItems = applySorting(items, sortOrder)
                        applySearchFilter(sortedItems, query)
                    }
                ContentType.SERIES ->
                    combine(
                        series,
                        sortOrderFlow,
                        _searchQuery.asStateFlow().debounce(500),
                    ) { items, sortOrder, query ->
                        val sortedItems = applySorting(items, sortOrder)
                        applySearchFilter(sortedItems, query)
                    }
                else -> kotlinx.coroutines.flow.flowOf(emptyList())
            }

        val liveStreams: Flow<List<LiveStreamEntity>> = contentRepository.getLiveStreams()

        init {
            // İlk kategoriyi seç
            when (contentType) {
                ContentType.MOVIES -> _selectedCategoryId.value = VIRTUAL_CATEGORY_ID_ALL_MOVIES
                ContentType.SERIES -> _selectedCategoryId.value = VIRTUAL_CATEGORY_ID_ALL_SERIES
                ContentType.LIVE_TV -> { /* Live TV için kategori seçimi yok */ }
            }
        }

        /**
         * İçerik türüne göre içerikleri yükler.
         */
        fun loadContent() {
            when (contentType) {
                ContentType.LIVE_TV -> {
                    viewModelScope.launch {
                        contentRepository.refreshLiveStreams()
                    }
                }
                ContentType.MOVIES -> {
                    viewModelScope.launch {
                        try {
                            val categoriesResult = contentRepository.refreshMovieCategories()
                            if (categoriesResult is com.pnr.tv.repository.Result.Error) {
                                showToast(context.getString(R.string.error_categories_load, categoriesResult.message))
                            }
                            val moviesResult = contentRepository.refreshMovies()
                            if (moviesResult is com.pnr.tv.repository.Result.Error) {
                                showToast(context.getString(R.string.error_movies_load, moviesResult.message))
                            }
                        } catch (e: Exception) {
                            showToast(context.getString(R.string.error_unexpected, e.message ?: context.getString(R.string.error_unknown)))
                        }
                    }
                }
                ContentType.SERIES -> {
                    viewModelScope.launch {
                        try {
                            val categoriesResult = contentRepository.refreshSeriesCategories()
                            if (categoriesResult is com.pnr.tv.repository.Result.Error) {
                                showToast(context.getString(R.string.error_categories_load, categoriesResult.message))
                            }
                            val seriesResult = contentRepository.refreshSeries()
                            if (seriesResult is com.pnr.tv.repository.Result.Error) {
                                showToast(context.getString(R.string.error_series_load, seriesResult.message))
                            }
                        } catch (e: Exception) {
                            showToast(context.getString(R.string.error_unexpected, e.message ?: context.getString(R.string.error_unknown)))
                        }
                    }
                }
            }
        }

        /**
         * Kategori seçer.
         */
        fun selectCategory(categoryId: Any) {
            _selectedCategoryId.value = categoryId
        }

        /**
         * İçeriği favorilere ekler (viewerId ile).
         */
        fun addFavorite(
            contentId: Int,
            viewerId: Int,
        ) {
            viewModelScope.launch {
                contentRepository.addFavorite(contentId, viewerId)
                showToast(context.getString(R.string.toast_favorite_added))
            }
        }

        /**
         * İçeriği favorilerden çıkarır (viewerId ile).
         */
        fun removeFavorite(
            contentId: Int,
            viewerId: Int,
        ) {
            viewModelScope.launch {
                contentRepository.removeFavorite(contentId, viewerId)
                showToast(context.getString(R.string.toast_favorite_removed))
            }
        }

        /**
         * İçeriğin favori olup olmadığını kontrol eder (viewerId ile).
         */
        fun isFavorite(
            contentId: Int,
            viewerId: Int,
        ): Flow<Boolean> = contentRepository.isFavorite(contentId, viewerId)

        /**
         * Sıralama tercihini kaydeder.
         */
        fun saveSortOrder(sortOrder: SortOrder) {
            viewModelScope.launch {
                sortPreferenceManager.saveSortOrder(contentType, sortOrder)
            }
        }

        /**
         * Mevcut sıralama tercihini döndürür.
         */
        fun getCurrentSortOrder(): Flow<SortOrder?> = sortOrderFlow

        /**
         * Arama sorgusunu günceller.
         * Fragment'tan çağrılır.
         */
        fun onSearchQueryChanged(query: String) {
            _searchQuery.value = query
        }

        /**
         * İçerik listesine arama filtresi uygular.
         */
        private fun applySearchFilter(
            items: List<ContentItem>,
            query: String,
        ): List<ContentItem> {
            // 3 karakterden az ise filtreleme yapma
            if (query.length < 3) {
                return items
            }

            // Başlıkta arama yap (büyük/küçük harf duyarsız)
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
                            is com.pnr.tv.db.entity.MovieEntity -> item.rating ?: 0.0
                            is com.pnr.tv.db.entity.SeriesEntity -> item.rating ?: 0.0
                            else -> 0.0
                        }
                    }
                SortOrder.RATING_LOW_TO_HIGH ->
                    items.sortedBy { item ->
                        when (item) {
                            is com.pnr.tv.db.entity.MovieEntity -> item.rating ?: Double.MAX_VALUE
                            is com.pnr.tv.db.entity.SeriesEntity -> item.rating ?: Double.MAX_VALUE
                            else -> Double.MAX_VALUE
                        }
                    }
                SortOrder.DATE_NEW_TO_OLD ->
                    items.sortedByDescending { item ->
                        val added =
                            when (item) {
                                is com.pnr.tv.db.entity.MovieEntity -> item.added
                                is com.pnr.tv.db.entity.SeriesEntity -> item.added
                                else -> null
                            }
                        // added string'i timestamp (Long) olarak parse et
                        // null olanlar en başa gelir (0L en küçük değer)
                        added?.toLongOrNull() ?: 0L
                    }
                SortOrder.DATE_OLD_TO_NEW ->
                    items.sortedBy { item ->
                        val added =
                            when (item) {
                                is com.pnr.tv.db.entity.MovieEntity -> item.added
                                is com.pnr.tv.db.entity.SeriesEntity -> item.added
                                else -> null
                            }
                        // added string'i timestamp (Long) olarak parse et
                        // null olanlar en sona gelir (Long.MAX_VALUE en büyük değer)
                        added?.toLongOrNull() ?: Long.MAX_VALUE
                    }
            }
        }

        /**
         * AssistedInject için Factory interface.
         */
        @AssistedFactory
        interface Factory {
            fun create(contentType: ContentType): ContentViewModel
        }
    }
