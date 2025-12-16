package com.pnr.tv

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.pnr.tv.db.entity.LiveStreamCategoryEntity
import com.pnr.tv.db.entity.LiveStreamEntity
import com.pnr.tv.db.entity.MovieCategoryEntity
import com.pnr.tv.db.entity.MovieEntity
import com.pnr.tv.db.entity.SeriesCategoryEntity
import com.pnr.tv.db.entity.SeriesEntity
import com.pnr.tv.domain.BuildLiveStreamUrlUseCase
import com.pnr.tv.model.CategoryItem
import com.pnr.tv.model.ContentItem
import com.pnr.tv.model.ContentType
import com.pnr.tv.model.SortOrder
import com.pnr.tv.network.dto.AuthenticationResponseDto
import com.pnr.tv.repository.ContentRepository
import com.pnr.tv.repository.Result
import com.pnr.tv.repository.UserRepository
import com.pnr.tv.repository.ViewerRepository
import com.pnr.tv.ui.base.BaseViewModel
import com.pnr.tv.util.SortPreferenceManager
import com.pnr.tv.worker.TmdbSyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class MainViewModel
    @Inject
    constructor(
        private val userRepository: UserRepository,
        private val contentRepository: ContentRepository,
        private val viewerRepository: ViewerRepository,
        private val sortPreferenceManager: SortPreferenceManager,
        private val buildLiveStreamUrlUseCase: BuildLiveStreamUrlUseCase,
        @ApplicationContext override val context: Context,
    ) : BaseViewModel() {
        companion object {
            // Sanal kategori ID'leri - Canlı Yayınlar için
            val VIRTUAL_CATEGORY_ID_FAVORITES = Constants.VirtualCategoryIdsInt.FAVORITES
            val VIRTUAL_CATEGORY_ID_RECENTLY_WATCHED = Constants.VirtualCategoryIdsInt.RECENTLY_WATCHED

            // Sanal kategori ID'leri - Filmler için
            val VIRTUAL_CATEGORY_ID_FAVORITES_MOVIES = Constants.VirtualCategoryIds.FAVORITES_STRING
            val VIRTUAL_CATEGORY_ID_RECENTLY_ADDED_MOVIES = Constants.VirtualCategoryIds.RECENTLY_ADDED_STRING
            val VIRTUAL_CATEGORY_ID_ALL_MOVIES = Constants.VirtualCategoryIds.ALL_STRING

            // Sanal kategori ID'leri - Diziler için
            val VIRTUAL_CATEGORY_ID_FAVORITES_SERIES = Constants.VirtualCategoryIds.FAVORITES_STRING
            val VIRTUAL_CATEGORY_ID_RECENTLY_ADDED_SERIES = Constants.VirtualCategoryIds.RECENTLY_ADDED_STRING
            val VIRTUAL_CATEGORY_ID_ALL_SERIES = Constants.VirtualCategoryIds.ALL_STRING
        }

        val currentUser = userRepository.currentUser.asLiveData()
        val userInfo = MutableLiveData<AuthenticationResponseDto?>()

        // Güncelleme durumu için enum
        enum class UpdateState {
            IDLE,
            LOADING,
            COMPLETED,
            ERROR,
        }

        // Güncelleme durumu için StateFlow
        private val _updateState = MutableStateFlow(UpdateState.IDLE)
        val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

        // Hata mesajı için StateFlow (ana güncelleme için)
        private val _errorMessage = MutableStateFlow<String?>(null)
        val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

        // ==================== Live Streams State ====================

        // For now, use default viewer (id = 1)
        private val defaultViewerId = 1
        private val favoriteChannelIds: Flow<List<Int>> = contentRepository.getFavoriteChannelIds(defaultViewerId)
        private val recentlyWatchedChannelIds: Flow<List<Int>> = contentRepository.getRecentlyWatchedChannelIds()

        // Canlı yayın kategorileri
        val liveStreamCategories: Flow<List<CategoryItem>> =
            combine(
                contentRepository.getLiveStreamCategories(),
                favoriteChannelIds,
                recentlyWatchedChannelIds,
            ) { normalCategories, _, _ ->
                val categoriesWithVirtual = mutableListOf<LiveStreamCategoryEntity>()

                // Favoriler kategorisini her zaman en başa ekle
                categoriesWithVirtual.add(
                    LiveStreamCategoryEntity(
                        categoryIdInt = VIRTUAL_CATEGORY_ID_FAVORITES,
                        categoryName = context.getString(R.string.category_favorites),
                        sortOrder = Constants.SortOrder.FAVORITES,
                    ),
                )

                // Son İzlenenler kategorisini her zaman ekle
                categoriesWithVirtual.add(
                    LiveStreamCategoryEntity(
                        categoryIdInt = VIRTUAL_CATEGORY_ID_RECENTLY_WATCHED,
                        categoryName = context.getString(R.string.category_recently_watched),
                        sortOrder = Constants.SortOrder.ALL,
                    ),
                )

                // Normal kategorileri ekle
                categoriesWithVirtual.addAll(normalCategories)

                categoriesWithVirtual
            }

        // Seçili kategori ID (String format - CategoryAdapter için)
        private val _selectedLiveStreamCategoryId = MutableStateFlow<String?>(null)
        val selectedLiveStreamCategoryId: StateFlow<String?> = _selectedLiveStreamCategoryId.asStateFlow()

        // Canlı yayınlar için loading state
        private val _isLiveStreamsLoading = MutableStateFlow(false)
        val isLiveStreamsLoading: StateFlow<Boolean> = _isLiveStreamsLoading.asStateFlow()

        // Canlı yayınlar için error state
        private val _liveStreamsErrorMessage = MutableStateFlow<String?>(null)
        val liveStreamsErrorMessage: StateFlow<String?> = _liveStreamsErrorMessage.asStateFlow()

        // Seçili kategoriye ait kanalları döndürür
        @OptIn(ExperimentalCoroutinesApi::class)
        val liveStreams: Flow<List<LiveStreamEntity>> =
            _selectedLiveStreamCategoryId
                .asStateFlow()
                .flatMapLatest { categoryIdString ->
                    val categoryId = categoryIdString?.toIntOrNull()
                    if (categoryId != null) {
                        when (categoryId) {
                            VIRTUAL_CATEGORY_ID_FAVORITES -> {
                                favoriteChannelIds.flatMapLatest { favoriteIds ->
                                    if (favoriteIds.isNotEmpty()) {
                                        flow {
                                            val channels = contentRepository.getLiveStreamsByIds(favoriteIds)
                                            emit(channels)
                                        }
                                    } else {
                                        kotlinx.coroutines.flow.flowOf(emptyList())
                                    }
                                }
                            }
                            VIRTUAL_CATEGORY_ID_RECENTLY_WATCHED -> {
                                recentlyWatchedChannelIds.flatMapLatest { recentlyWatchedIds ->
                                    if (recentlyWatchedIds.isNotEmpty()) {
                                        flow {
                                            val channels = contentRepository.getLiveStreamsByIds(recentlyWatchedIds)
                                            emit(channels)
                                        }
                                    } else {
                                        kotlinx.coroutines.flow.flowOf(emptyList())
                                    }
                                }
                            }
                            else -> {
                                contentRepository.getLiveStreamsByCategoryId(categoryId)
                            }
                        }
                    } else {
                        kotlinx.coroutines.flow.flowOf(emptyList())
                    }
                }

        // Channel selection event - URL building için
        private val _openPlayerEvent = MutableSharedFlow<Triple<String, Int, Int?>>()
        val openPlayerEvent: SharedFlow<Triple<String, Int, Int?>> = _openPlayerEvent.asSharedFlow()

        // ==================== Movies State ====================

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
                            Constants.SortOrder.DEFAULT,
                            Constants.SortOrder.FAVORITES - 1,
                        ),
                    )

                    val defaultViewer = allViewers.find { !it.isDeletable }
                    val defaultViewerHasFavorites = defaultViewer?.let { it.id in viewerIdsWithFavorites } ?: false

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
                movies, // Kategori değişikliği anında yansır
                movieSortOrderFlow, // Sort değişikliği anında yansır
                debouncedMovieSearchQuery, // Sadece search query debounce olur
            ) { items, sortOrder, query ->
                val sortedItems = applySorting(items, sortOrder)
                applySearchFilter(sortedItems, query)
            }

        // ==================== Series State ====================

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

                    val defaultViewer = allViewers.find { !it.isDeletable }
                    val defaultViewerHasFavorites = defaultViewer?.let { it.id in viewerIdsWithFavorites } ?: false

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
                series, // Kategori değişikliği anında yansır
                seriesSortOrderFlow, // Sort değişikliği anında yansır
                debouncedSeriesSearchQuery, // Sadece search query debounce olur
            ) { items, sortOrder, query ->
                val sortedItems = applySorting(items, sortOrder)
                applySearchFilter(sortedItems, query)
            }

        init {
            // İlk kategorileri seç
            _selectedMovieCategoryId.value = VIRTUAL_CATEGORY_ID_ALL_MOVIES
            _selectedSeriesCategoryId.value = VIRTUAL_CATEGORY_ID_ALL_SERIES
        }

        // ==================== Live Streams Functions ====================

        /**
         * Canlı yayınları yükler (eğer daha önce yüklenmemişse).
         */
        fun loadLiveStreamsIfNeeded() {
            viewModelScope.launch {
                _liveStreamsErrorMessage.value = null
                _isLiveStreamsLoading.value = true

                try {
                    val result = contentRepository.refreshLiveStreams()
                    if (result is Result.Error) {
                        _liveStreamsErrorMessage.value = context.getString(R.string.error_live_streams_short)
                    }
                } catch (e: Exception) {
                    _liveStreamsErrorMessage.value = context.getString(R.string.error_unknown)
                } finally {
                    _isLiveStreamsLoading.value = false
                }
            }
        }

        /**
         * Kategori seçer (Canlı Yayınlar için).
         */
        fun selectLiveStreamCategory(categoryId: String?) {
            _selectedLiveStreamCategoryId.value = categoryId
        }

        /**
         * Kanalı favorilere ekler.
         */
        fun addLiveStreamFavorite(channelId: Int) {
            viewModelScope.launch {
                contentRepository.addFavorite(channelId, defaultViewerId)
                showToast(context.getString(R.string.toast_favorite_added))
            }
        }

        /**
         * Kanalı favorilerden çıkarır.
         */
        fun removeLiveStreamFavorite(channelId: Int) {
            viewModelScope.launch {
                contentRepository.removeFavorite(channelId, defaultViewerId)
                showToast(context.getString(R.string.toast_favorite_removed))
            }
        }

        /**
         * Kanalın favori olup olmadığını kontrol eder.
         */
        fun isLiveStreamFavorite(channelId: Int): Flow<Boolean> = contentRepository.isFavorite(channelId, defaultViewerId)

        /**
         * Kanal seçildiğinde çağrılır.
         */
        fun onChannelSelected(channel: LiveStreamEntity) {
            viewModelScope.launch {
                val url = buildLiveStreamUrlUseCase(channel)
                if (url != null) {
                    Timber.d("📡 CANLI YAYIN URL: $url")
                    _openPlayerEvent.emit(Triple(url, channel.streamId, channel.categoryId))
                } else {
                    Timber.e("❌ Canlı yayın stream URL oluşturulamadı!")
                }
            }
        }

        // ==================== Movies Functions ====================

        /**
         * Filmleri yükler (eğer daha önce yüklenmemişse).
         */
        fun loadMoviesIfNeeded() {
            viewModelScope.launch {
                _moviesErrorMessage.value = null
                _isMoviesLoading.value = true

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

        // ==================== Series Functions ====================

        /**
         * Dizileri yükler (eğer daha önce yüklenmemişse).
         */
        fun loadSeriesIfNeeded() {
            viewModelScope.launch {
                _seriesErrorMessage.value = null
                _isSeriesLoading.value = true

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
                            is MovieEntity -> item.rating ?: 0.0
                            is SeriesEntity -> item.rating ?: 0.0
                            else -> 0.0
                        }
                    }
                SortOrder.RATING_LOW_TO_HIGH ->
                    items.sortedBy { item ->
                        when (item) {
                            is MovieEntity -> item.rating ?: Double.MAX_VALUE
                            is SeriesEntity -> item.rating ?: Double.MAX_VALUE
                            else -> Double.MAX_VALUE
                        }
                    }
                SortOrder.DATE_NEW_TO_OLD ->
                    items.sortedByDescending { item ->
                        val added =
                            when (item) {
                                is MovieEntity -> item.added
                                is SeriesEntity -> item.added
                                else -> null
                            }
                        added?.toLongOrNull() ?: 0L
                    }
                SortOrder.DATE_OLD_TO_NEW ->
                    items.sortedBy { item ->
                        val added =
                            when (item) {
                                is MovieEntity -> item.added
                                is SeriesEntity -> item.added
                                else -> null
                            }
                        added?.toLongOrNull() ?: Long.MAX_VALUE
                    }
            }
        }

        // ==================== Main Content Refresh ====================

        /**
         * Tüm içeriği günceller
         *
         * Yeni akış:
         * 1. IPTV senkronizasyonu (hızlı, ön planda)
         * 2. WorkManager ile TMDB senkronizasyonu (yavaş, arka planda)
         *
         * Not: Rate limiting artık ağ katmanında (RateLimiterInterceptor) yapılıyor.
         * UI mantığı (delay, durum güncellemeleri) Fragment/Activity'de yönetilmeli.
         */
        fun refreshAllContent() {
            viewModelScope.launch {
                try {
                    // Yüklenme başladı
                    _updateState.value = UpdateState.LOADING
                    _errorMessage.value = null

                    Timber.d("🚀 HIZLI GÜNCELLEME BAŞLADI (Sadece IPTV)")
                    Timber.d("⚠️ Rate limiting ağ katmanında yapılıyor...")

                    // IPTV içeriklerini yenile (rate limiting interceptor tarafından yönetiliyor)
                    val refreshResult =
                        try {
                            refreshIptvContent()
                        } catch (e: Exception) {
                            // refreshIptvContent içindeki hataları yakala
                            Timber.e(e, "❌ REFRESH IPTV CONTENT HATASI")
                            context.getString(R.string.error_server_error)
                        }

                    // Sonuçları işle
                    handleRefreshResult(refreshResult)
                } catch (e: Exception) {
                    // Beklenmeyen hata durumu - uygulamanın kapanmasını önle
                    Timber.e(e, "❌ GÜNCELLEME HATASI")
                    _errorMessage.value = context.getString(R.string.error_server_error)
                    _updateState.value = UpdateState.ERROR
                }
            }
        }

        /**
         * IPTV içeriklerini yeniler (filmler, diziler, canlı yayınlar).
         * Rate limiting ağ katmanında (RateLimiterInterceptor) yapılıyor.
         *
         * @return Hata mesajı, başarılı ise null
         */
        private suspend fun refreshIptvContent(): String? {
            return try {
                // Önce kullanıcı kontrolü yap
                val allUsers =
                    try {
                        userRepository.allUsers.firstOrNull() ?: emptyList()
                    } catch (e: Exception) {
                        Timber.e(e, "Kullanıcı listesi alınamadı")
                        emptyList()
                    }

                if (allUsers.isEmpty()) {
                    return context.getString(R.string.error_user_not_exists)
                }

                val currentUser =
                    try {
                        userRepository.currentUser.firstOrNull()
                    } catch (e: Exception) {
                        Timber.e(e, "Mevcut kullanıcı alınamadı")
                        null
                    }

                if (currentUser == null) {
                    return context.getString(R.string.error_user_not_selected)
                }

                // Filmleri yenile
                val moviesResult =
                    try {
                        contentRepository.refreshMovies(skipTmdbSync = true, forMainScreenUpdate = true)
                    } catch (e: Exception) {
                        Timber.e(e, "Filmler yenilenirken hata")
                        Result.Error(message = context.getString(R.string.error_server_error), exception = e)
                    }
                if (moviesResult is Result.Error) {
                    return moviesResult.message
                }

                // Dizileri yenile (rate limiting interceptor tarafından yönetiliyor)
                val seriesResult =
                    try {
                        contentRepository.refreshSeries(skipTmdbSync = true, forMainScreenUpdate = true)
                    } catch (e: Exception) {
                        Timber.e(e, "Diziler yenilenirken hata")
                        Result.Error(message = context.getString(R.string.error_server_error), exception = e)
                    }
                if (seriesResult is Result.Error) {
                    return seriesResult.message
                }

                // Canlı yayınları yenile (rate limiting interceptor tarafından yönetiliyor)
                val liveStreamsResult =
                    try {
                        contentRepository.refreshLiveStreams(forMainScreenUpdate = true)
                    } catch (e: Exception) {
                        Timber.e(e, "Canlı yayınlar yenilenirken hata")
                        Result.Error(message = context.getString(R.string.error_server_error), exception = e)
                    }
                if (liveStreamsResult is Result.Error) {
                    return liveStreamsResult.message
                }

                null
            } catch (e: Exception) {
                // Beklenmeyen hata - uygulamanın kapanmasını önle
                Timber.e(e, "refreshIptvContent genel hata")
                context.getString(R.string.error_server_error)
            }
        }

        /**
         * Yenileme sonuçlarını işler ve durumu günceller.
         *
         * @param errorMessage Hata mesajı, null ise başarılı
         */
        private suspend fun handleRefreshResult(errorMessage: String?) {
            if (errorMessage != null) {
                _errorMessage.value = errorMessage
                _updateState.value = UpdateState.ERROR
            } else {
                // IPTV güncelleme tamamlandı - kullanıcı artık uygulamayı kullanabilir
                // Not: Kanal ikonları lazy loading ile otomatik olarak yüklenecek (Coil)
                _updateState.value = UpdateState.COMPLETED

                Timber.d("✅ HIZLI GÜNCELLEME TAMAMLANDI")
                Timber.d("🔄 ARKA PLAN TMDB SENKRONIZASYONU BAŞLATILIYOR...")

                // TMDB senkronizasyonunu arka planda başlat
                scheduleTmdbSync()
            }
        }

        /**
         * TMDB arka plan senkronizasyonunu planlar.
         * WorkManager ile akıllı kategori önceliklendirmesi yapar.
         */
        private fun scheduleTmdbSync() {
            startTmdbBackgroundSync()
        }

        /**
         * Güncelleme durumunu IDLE'a sıfırlar.
         * UI tarafından COMPLETED durumunu gösterdikten sonra çağrılmalıdır.
         */
        fun resetUpdateState() {
            _updateState.value = UpdateState.IDLE
            _errorMessage.value = null
        }

        /**
         * TMDB arka plan senkronizasyonunu başlatır
         * WorkManager ile akıllı kategori önceliklendirmesi yapar
         */
        private fun startTmdbBackgroundSync() {
            val constraints =
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED) // İnternet gerekli
                    .build()

            val workRequest =
                OneTimeWorkRequestBuilder<TmdbSyncWorker>()
                    .setConstraints(constraints)
                    .setInputData(
                        workDataOf(
                            TmdbSyncWorker.INPUT_CONTENT_TYPE to TmdbSyncWorker.CONTENT_TYPE_ALL,
                        ),
                    )
                    .build()

            // Mevcut TMDB sync işini iptal et ve yenisini başlat
            WorkManager.getInstance(context).enqueueUniqueWork(
                TmdbSyncWorker.WORK_NAME,
                ExistingWorkPolicy.REPLACE, // Eski işi iptal et, yenisini başlat
                workRequest,
            )

            Timber.d("📋 WorkManager görevi oluşturuldu ve kuyruğa eklendi")
        }

        fun fetchUserInfo() {
            viewModelScope.launch {
                try {
                    val result = contentRepository.fetchUserInfo()
                    when (result) {
                        is Result.Success -> {
                            userInfo.value = result.data
                        }
                        is Result.Error -> {
                            Timber.e(result.exception, "Kullanıcı bilgileri alınamadı: ${result.message}")
                            userInfo.value = null
                            _errorMessage.value = result.message
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "fetchUserInfo() coroutine içinde beklenmeyen hata: ${e.javaClass.simpleName}")
                    userInfo.value = null
                }
            }
        }
    }
