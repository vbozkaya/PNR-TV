package com.pnr.tv.ui.movies

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.pnr.tv.ContentConstants
import com.pnr.tv.R
import com.pnr.tv.db.entity.MovieCategoryEntity
import com.pnr.tv.db.entity.MovieEntity
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
 * Filmler için ViewModel.
 *
 * Film kategorileri, filmler, arama, sıralama ve favoriler işlemlerini yönetir.
 */
@HiltViewModel
class MovieViewModel
    @Inject
    constructor(
        private val contentRepository: ContentRepository,
        private val viewerRepository: ViewerRepository,
        private val sortPreferenceManager: SortPreferenceManager,
        @ApplicationContext override val context: Context,
    ) : BaseViewModel() {
        companion object {
            // Sanal kategori ID'leri - Filmler için
            val VIRTUAL_CATEGORY_ID_FAVORITES_MOVIES = ContentConstants.VirtualCategoryIds.FAVORITES_STRING
            val VIRTUAL_CATEGORY_ID_RECENTLY_ADDED_MOVIES = ContentConstants.VirtualCategoryIds.RECENTLY_ADDED_STRING
            val VIRTUAL_CATEGORY_ID_ALL_MOVIES = ContentConstants.VirtualCategoryIds.ALL_STRING
        }

        private val favoriteIds: Flow<List<Int>> = contentRepository.getAllFavoriteChannelIds()

        private fun getViewerCategoryId(viewerId: Int): String = "viewer_$viewerId"

        private val defaultViewerIdFlow: Flow<Int?> =
            viewerRepository.getAllViewers().map { viewers ->
                viewers.find { !it.isDeletable }?.id
            }

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
                            ContentConstants.SortOrder.DEFAULT,
                            ContentConstants.SortOrder.FAVORITES - 1,
                        ),
                    )

                    val defaultViewer = allViewers.find { !it.isDeletable }
                    val defaultViewerHasFavorites = defaultViewer?.let { it.id in viewerIdsWithFavorites } ?: false

                    if (defaultViewerHasFavorites && defaultViewer != null) {
                        add(
                            MovieCategoryEntity(
                                VIRTUAL_CATEGORY_ID_FAVORITES_MOVIES,
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
                                MovieCategoryEntity(
                                    getViewerCategoryId(viewerId),
                                    context.getString(R.string.category_viewer_favorites, viewer.name.uppercase()),
                                    ContentConstants.SortOrder.DEFAULT,
                                    ContentConstants.SortOrder.FAVORITES,
                                ),
                            )
                        }
                    }

                    add(
                        MovieCategoryEntity(
                            VIRTUAL_CATEGORY_ID_ALL_MOVIES,
                            context.getString(R.string.category_all_movies),
                            ContentConstants.SortOrder.DEFAULT,
                            ContentConstants.SortOrder.ALL,
                        ),
                    )
                    addAll(normalCategories)
                }
            }

        val movieCategoriesFlow: Flow<List<CategoryItem>> = movieCategories.map { it }

        private val _selectedMovieCategoryId = MutableStateFlow<Any?>(null)
        val selectedMovieCategoryId: StateFlow<Any?> = _selectedMovieCategoryId.asStateFlow()

        private val _movieSearchQuery = MutableStateFlow<String>("")
        val movieSearchQuery: StateFlow<String> = _movieSearchQuery.asStateFlow()

        private val _isMoviesLoading = MutableStateFlow(false)
        val isMoviesLoading: StateFlow<Boolean> = _isMoviesLoading.asStateFlow()

        private val _moviesErrorMessage = MutableStateFlow<String?>(null)
        val moviesErrorMessage: StateFlow<String?> = _moviesErrorMessage.asStateFlow()

        @OptIn(ExperimentalCoroutinesApi::class)
        private val movies: Flow<List<MovieEntity>> =
            _selectedMovieCategoryId.asStateFlow().flatMapLatest { categoryId ->
                val categoryIdString = categoryId as? String ?: VIRTUAL_CATEGORY_ID_ALL_MOVIES
                Timber.tag("GRID_UPDATE").d("🎬 movies Flow tetiklendi: categoryId=$categoryIdString")
                when (categoryIdString) {
                    VIRTUAL_CATEGORY_ID_RECENTLY_ADDED_MOVIES ->
                        flow {
                            emit(contentRepository.getRecentlyAddedMovies(20))
                        }
                    VIRTUAL_CATEGORY_ID_FAVORITES_MOVIES ->
                        defaultViewerIdFlow.flatMapLatest { defaultId ->
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

        private val movieSortOrderFlow: Flow<SortOrder?> = sortPreferenceManager.getSortOrder(ContentType.MOVIES)

        // Search query için debounce'lu flow (sadece arama için gecikme)
        @OptIn(FlowPreview::class)
        private val debouncedMovieSearchQuery: Flow<String> = _movieSearchQuery.asStateFlow().debounce(500)

        // Kategori ve sort değişiklikleri anında yansısın, sadece search query debounce olsun
        val moviesFlow: Flow<List<ContentItem>> =
            combine(
                movies.onStart { emit(emptyList()) }, // Başlangıç değeri - combine için gerekli
                movieSortOrderFlow.onStart { emit(null) }, // Başlangıç değeri
                debouncedMovieSearchQuery.onStart { emit("") }, // Başlangıç değeri
            ) { items, sortOrder, query ->
                val combineStartTime = System.currentTimeMillis()
                Timber.tag("GRID_UPDATE").d("🔄 moviesFlow combine: items=${items.size}, sort=$sortOrder, query='$query'")
                val sortedItems = applySorting(items, sortOrder)
                val filteredItems = applySearchFilter(sortedItems, query)
                val combineTime = System.currentTimeMillis() - combineStartTime
                Timber.tag("GRID_UPDATE").d("✅ moviesFlow sonuç: ${filteredItems.size} item (combine süresi: ${combineTime}ms)")
                filteredItems
            }

        init {
            // İlk kategoriyi seç
            _selectedMovieCategoryId.value = VIRTUAL_CATEGORY_ID_ALL_MOVIES
        }

        /**
         * Filmleri yükler (eğer daha önce yüklenmemişse).
         * Ana sayfada zaten yüklenmişse, tekrar yüklemez.
         */
        fun loadMoviesIfNeeded() {
            viewModelScope.launch {
                _moviesErrorMessage.value = null
                
                // Önce veritabanında veri olup olmadığını kontrol et
                val hasData = contentRepository.hasMovies()
                val hasCategories = contentRepository.hasMovieCategories()
                
                if (hasData && hasCategories) {
                    // Veri zaten var, yükleme yapma
                    Timber.d("🎬 Filmler zaten yüklü, tekrar yükleme atlanıyor")
                    _isMoviesLoading.value = false
                    return@launch
                }
                
                // Veri yok, yükleme yap
                _isMoviesLoading.value = true
                Timber.d("🎬 Filmler yükleniyor (veri yok veya eksik)")

                try {
                    val categoriesResult = contentRepository.refreshMovieCategories()
                    if (categoriesResult is Result.Error) {
                        _moviesErrorMessage.value = context.getString(R.string.error_categories_short)
                        _isMoviesLoading.value = false
                        return@launch
                    }
                    val moviesResult = contentRepository.refreshMovies()
                    if (moviesResult is Result.Error) {
                        _moviesErrorMessage.value = context.getString(R.string.error_movies_short)
                    }
                } catch (e: Exception) {
                    _moviesErrorMessage.value = context.getString(R.string.error_unknown)
                } finally {
                    _isMoviesLoading.value = false
                }
            }
        }

        /**
         * Kategori seçer (Filmler için).
         */
        fun selectMovieCategory(categoryId: Any) {
            Timber.tag("GRID_UPDATE").d("🎬 selectMovieCategory: $categoryId")
            _selectedMovieCategoryId.value = categoryId
        }

        /**
         * Filmleri favorilere ekler.
         */
        fun addMovieFavorite(
            contentId: Int,
            viewerId: Int,
        ) {
            viewModelScope.launch {
                contentRepository.addFavorite(contentId, viewerId)
                showToast(context.getString(R.string.toast_favorite_added))
            }
        }

        /**
         * Filmleri favorilerden çıkarır.
         */
        fun removeMovieFavorite(
            contentId: Int,
            viewerId: Int,
        ) {
            viewModelScope.launch {
                contentRepository.removeFavorite(contentId, viewerId)
                showToast(context.getString(R.string.toast_favorite_removed))
            }
        }

        /**
         * Filmin favori olup olmadığını kontrol eder.
         */
        fun isMovieFavorite(
            contentId: Int,
            viewerId: Int,
        ): Flow<Boolean> = contentRepository.isFavorite(contentId, viewerId)

        /**
         * Film arama sorgusunu günceller.
         */
        fun onMovieSearchQueryChanged(query: String) {
            _movieSearchQuery.value = query
        }

        /**
         * Film sıralama tercihini kaydeder.
         */
        fun saveMovieSortOrder(sortOrder: SortOrder) {
            viewModelScope.launch {
                sortPreferenceManager.saveSortOrder(ContentType.MOVIES, sortOrder)
            }
        }

        /**
         * Mevcut film sıralama tercihini döndürür.
         */
        fun getCurrentMovieSortOrder(): Flow<SortOrder?> = movieSortOrderFlow

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
                            is MovieEntity -> item.rating ?: 0.0
                            else -> 0.0
                        }
                    }
                SortOrder.RATING_LOW_TO_HIGH ->
                    items.sortedBy { item ->
                        when (item) {
                            is MovieEntity -> item.rating ?: Double.MAX_VALUE
                            else -> Double.MAX_VALUE
                        }
                    }
                SortOrder.DATE_NEW_TO_OLD ->
                    items.sortedByDescending { item ->
                        when (item) {
                            is MovieEntity -> item.added
                            else -> null
                        }?.toLongOrNull() ?: 0L
                    }
                SortOrder.DATE_OLD_TO_NEW ->
                    items.sortedBy { item ->
                        when (item) {
                            is MovieEntity -> item.added
                            else -> null
                        }?.toLongOrNull() ?: Long.MAX_VALUE
                    }
            }
        }
    }
