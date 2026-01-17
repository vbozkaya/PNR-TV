package com.pnr.tv.ui.movies

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.pnr.tv.core.constants.ContentConstants
import com.pnr.tv.core.constants.DatabaseConstants
import com.pnr.tv.R
import com.pnr.tv.core.constants.UIConstants
import com.pnr.tv.db.entity.MovieCategoryEntity
import com.pnr.tv.db.entity.MovieEntity
import com.pnr.tv.model.CategoryItem
import com.pnr.tv.model.ContentItem
import com.pnr.tv.model.ContentType
import com.pnr.tv.model.SortOrder
import com.pnr.tv.premium.PremiumManager
import com.pnr.tv.repository.FavoriteRepository
import com.pnr.tv.repository.MovieRepository
import com.pnr.tv.repository.Result
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
 * Filmler için ViewModel.
 *
 * Film kategorileri, filmler, arama, sıralama ve favoriler işlemlerini yönetir.
 */
@HiltViewModel
class MovieViewModel
    @Inject
    constructor(
        private val movieRepository: MovieRepository,
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
            // Sanal kategori ID'leri - Filmler için
            val VIRTUAL_CATEGORY_ID_FAVORITES_MOVIES = ContentConstants.VirtualCategoryIds.FAVORITES_STRING
            val VIRTUAL_CATEGORY_ID_RECENTLY_ADDED_MOVIES = ContentConstants.VirtualCategoryIds.RECENTLY_ADDED_STRING
        }

        private val defaultViewerIdFlow: Flow<Int?> =
            viewerRepository.getAllViewers().map { viewers ->
                viewers.find { !it.isDeletable }?.id
            }

        private val movieCategories: Flow<List<MovieCategoryEntity>> =
            buildCategories().map { categories ->
                categories.mapNotNull { category ->
                    when (category) {
                        is MovieCategoryEntity -> category
                        else -> null
                    }
                }
            }

        val movieCategoriesFlow: Flow<List<CategoryItem>> = movieCategories.map { it }

        private val _selectedMovieCategoryId = MutableStateFlow<Any?>(null)
        val selectedMovieCategoryId: StateFlow<Any?> = _selectedMovieCategoryId.asStateFlow()

        private val _movieSearchQuery = MutableStateFlow<String>("")
        val movieSearchQuery: StateFlow<String> = _movieSearchQuery.asStateFlow()

        private val _isMoviesLoading = MutableStateFlow(false)
        val isMoviesLoading: StateFlow<Boolean> = _isMoviesLoading.asStateFlow()

        // Error message artık BaseViewModel'den geliyor
        val moviesErrorMessage: StateFlow<String?> = errorMessage

        private val movieSortOrderFlow: Flow<SortOrder?> = sortPreferenceManager.getSortOrder(ContentType.MOVIES)

        // Search query için debounce'lu flow (sadece arama için gecikme)
        @OptIn(FlowPreview::class)
        private val debouncedMovieSearchQuery: Flow<String> =
            _movieSearchQuery.asStateFlow().debounce(
                UIConstants.DelayDurations.SEARCH_DEBOUNCE_MS,
            )

        // Önceki veriyi korumak için StateFlow
        private val _previousMovies = MutableStateFlow<List<MovieEntity>>(emptyList())

        // Arama yapıldığında tüm filmleri, yapılmadığında seçili kategorideki filmleri getir
        @OptIn(ExperimentalCoroutinesApi::class)
        private val moviesWithSearch: Flow<List<MovieEntity>> =
            combine(
                debouncedMovieSearchQuery.onStart { emit("") },
                _selectedMovieCategoryId.asStateFlow(),
            ) { query, categoryId ->
                // Arama yapılıyorsa (3+ karakter), tüm filmleri al
                if (query.length >= 3) {
                    Pair(true, query)
                } else {
                    // Arama yapılmıyorsa, seçili kategorideki filmleri al
                    Pair(false, categoryId as? String)
                }
            }.flatMapLatest { (isSearch, value) ->
                if (isSearch) {
                    // Arama modu: Tüm filmleri al
                    movieRepository.getMovies().map { resource ->
                        when (resource) {
                            is Resource.Loading -> {
                                _isMoviesLoading.value = true
                                emptyList<MovieEntity>()
                            }
                            is Resource.Success -> {
                                _isMoviesLoading.value = false
                                clearError()
                                _previousMovies.value = resource.data
                                resource.data
                            }
                            is Resource.Error -> {
                                _isMoviesLoading.value = false
                                handleResourceError(resource)
                                _previousMovies.value
                            }
                        }
                    }
                } else {
                    // Normal mod: Seçili kategorideki filmleri al
                    @Suppress("UNCHECKED_CAST")
                    val categoryIdString = value as? String
                    if (categoryIdString == null) {
                        kotlinx.coroutines.flow.flowOf(_previousMovies.value)
                    } else {
                        when (categoryIdString) {
                            VIRTUAL_CATEGORY_ID_RECENTLY_ADDED_MOVIES ->
                                movieRepository.getRecentlyAddedMovies(DatabaseConstants.RECENTLY_ADDED_LIMIT)
                                    .onEach { movies ->
                                        _previousMovies.value = movies
                                    }
                            VIRTUAL_CATEGORY_ID_FAVORITES_MOVIES ->
                                defaultViewerIdFlow.flatMapLatest { defaultId ->
                                    if (defaultId != null) {
                                        favoriteRepository.getFavoriteChannelIds(defaultId).flatMapLatest { ids ->
                                            flow {
                                                val movies = movieRepository.getMoviesByIds(ids)
                                                _previousMovies.value = movies
                                                emit(movies)
                                            }
                                        }
                                    } else {
                                        kotlinx.coroutines.flow.flowOf(_previousMovies.value)
                                    }
                                }
                            else -> {
                                if (categoryIdString.startsWith("viewer_")) {
                                    val viewerId = categoryIdString.removePrefix("viewer_").toIntOrNull()
                                    if (viewerId != null) {
                                        favoriteRepository.getFavoriteChannelIds(viewerId).flatMapLatest { ids ->
                                            flow {
                                                val movies = movieRepository.getMoviesByIds(ids)
                                                _previousMovies.value = movies
                                                emit(movies)
                                            }
                                        }
                                    } else {
                                        kotlinx.coroutines.flow.flowOf(_previousMovies.value)
                                    }
                                } else {
                                    movieRepository.getMoviesByCategoryId(categoryIdString).map { resource ->
                                        when (resource) {
                                            is Resource.Loading -> {
                                                _isMoviesLoading.value = true
                                                emptyList<MovieEntity>()
                                            }
                                            is Resource.Success -> {
                                                _isMoviesLoading.value = false
                                                clearError()
                                                _previousMovies.value = resource.data
                                                resource.data
                                            }
                                            is Resource.Error -> {
                                                _isMoviesLoading.value = false
                                                handleResourceError(resource)
                                                _previousMovies.value
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
        val moviesFlow: Flow<List<ContentItem>> =
            combine(
                moviesWithSearch.onStart { emit(emptyList()) }, // Başlangıç değeri - combine için gerekli
                movieSortOrderFlow.onStart { emit(null) }, // Başlangıç değeri
                debouncedMovieSearchQuery.onStart { emit("") }, // Başlangıç değeri
                adultContentPreferenceManager.isAdultContentEnabled().onStart { emit(false) }, // Başlangıç değeri
            ) { items, sortOrder, query, adultContentEnabled ->
                val combineStartTime = System.currentTimeMillis()

                val sortedItems = applySorting(items, sortOrder)
                val searchFilteredItems = applySearchFilter(sortedItems, query)
                val adultFilteredItems = applyAdultContentFilter(searchFilteredItems, adultContentEnabled)
                val combineTime = System.currentTimeMillis() - combineStartTime
                adultFilteredItems
            }.flowOn(Dispatchers.Default)

        init {
            // Kategori seçimi fragment'ta yapılacak (ilk kategori otomatik seçilecek)
            _selectedMovieCategoryId.value = null
            // Verileri ViewModel oluşturulur oluşturulmaz yükle
            loadMovies()
        }

        /**
         * Filmleri yükler.
         * Veriler yalnızca bir kez (ViewModel ilk oluşturulduğunda) yüklenir.
         */
        private fun loadMovies() {
            loadContentIfNeeded()
        }

        override val logTag: String = "🎬"

        override val isLoading: MutableStateFlow<Boolean> = _isMoviesLoading

        // Error message artık BaseViewModel'den geliyor, override gerekmiyor
        override val contentLoadErrorStringId: Int = R.string.error_movies_load_failed
        override val errorContext: String = "MovieViewModel"
        override val virtualCategoryIdFavorites: String = VIRTUAL_CATEGORY_ID_FAVORITES_MOVIES
        override val virtualCategoryIdRecentlyAdded: String = VIRTUAL_CATEGORY_ID_RECENTLY_ADDED_MOVIES

        override suspend fun refreshCategories(): Result<Unit> {
            return movieRepository.refreshMovieCategories()
        }

        override suspend fun hasData(): Boolean {
            return movieRepository.hasMovies()
        }

        override suspend fun refreshContent(): Result<Unit> {
            return movieRepository.refreshMovies()
        }

        override fun getNormalCategories(): Flow<List<CategoryItem>> {
            return movieRepository.getMovieCategories().map { resource ->
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
            return movieRepository.getMovies().map { resource ->
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
            return movieRepository.getCategoryCounts()
        }

        override fun ContentItem.isAdultContent(): Boolean {
            return when (this) {
                is MovieEntity -> this.isAdult == true
                else -> false
            }
        }

        override fun ContentItem.getCategoryId(): String? {
            return when (this) {
                is MovieEntity -> this.categoryId
                else -> null
            }
        }

        override fun createVirtualCategoryEntity(
            categoryId: String,
            categoryName: String,
            parentId: Int,
            sortOrder: Int,
        ): CategoryItem {
            return MovieCategoryEntity(
                categoryId = categoryId,
                categoryName = categoryName,
                parentId = parentId,
                sortOrder = sortOrder,
            )
        }

        /**
         * Kategori seçer (Filmler için).
         */
        fun selectMovieCategory(categoryId: Any) {
            _selectedMovieCategoryId.value = categoryId
        }

        /**
         * Filmleri favorilere ekler.
         */
        fun addMovieFavorite(
            contentId: Int,
            viewerId: Int,
        ) {
            addFavorite(contentId, viewerId)
        }

        /**
         * Filmleri favorilerden çıkarır.
         */
        fun removeMovieFavorite(
            contentId: Int,
            viewerId: Int,
        ) {
            removeFavorite(contentId, viewerId)
        }

        /**
         * Filmin favori olup olmadığını kontrol eder.
         */
        fun isMovieFavorite(
            contentId: Int,
            viewerId: Int,
        ): Flow<Boolean> = isFavorite(contentId, viewerId)

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
    }
