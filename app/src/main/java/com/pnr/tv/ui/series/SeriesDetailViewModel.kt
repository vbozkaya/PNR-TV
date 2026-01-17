package com.pnr.tv.ui.series

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pnr.tv.db.entity.ViewerEntity
import com.pnr.tv.db.entity.WatchedEpisodeEntity
import com.pnr.tv.domain.GetSeasonEpisodesUseCase
import com.pnr.tv.domain.GetSeriesDetailsUseCase
import com.pnr.tv.model.SeriesSeason
import com.pnr.tv.premium.PremiumFeatureGuard
import com.pnr.tv.repository.Result
import com.pnr.tv.ui.main.SessionManager
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

/**
 * Dizi detay sayfası için ViewModel.
 * MVI pattern'ine uygun olarak sadeleştirilmiş, tek sorumluluk prensibine uygun yapı.
 */
class SeriesDetailViewModel
    @AssistedInject
    constructor(
        private val getSeriesDetailsUseCase: GetSeriesDetailsUseCase,
        private val getSeasonEpisodesUseCase: GetSeasonEpisodesUseCase,
        private val metadataProvider: SeriesMetadataProvider,
        private val resumeManager: PlaybackResumeManager,
        private val favoriteHandler: SeriesFavoriteHandler,
        private val sessionManager: SessionManager,
        private val premiumFeatureGuard: PremiumFeatureGuard,
        private val watchedEpisodeDao: com.pnr.tv.db.dao.WatchedEpisodeDao,
    ) : ViewModel() {
        // UI State - MVI pattern
        private val _uiState = MutableStateFlow(SeriesDetailUiState.Empty)
        val uiState: StateFlow<SeriesDetailUiState> = _uiState.asStateFlow()

        // Backward compatibility - mevcut observer'lar için (uiState üzerinden map'leniyor)
        // Lazy initialization ile KSP hatasını önlemek için
        private val _series = MutableStateFlow<com.pnr.tv.db.entity.SeriesEntity?>(null)
        val series: StateFlow<com.pnr.tv.db.entity.SeriesEntity?> = _series.asStateFlow()

        private val _seasons = MutableStateFlow<List<SeriesSeason>>(emptyList())
        val seasons: StateFlow<List<SeriesSeason>> = _seasons.asStateFlow()

        private val _selectedSeasonNumber = MutableStateFlow<Int?>(null)
        val selectedSeasonNumber: StateFlow<Int?> = _selectedSeasonNumber.asStateFlow()

        private val _episodes = MutableStateFlow<List<ParsedEpisode>>(emptyList())
        val episodes: StateFlow<List<ParsedEpisode>> = _episodes.asStateFlow()

        private val _tmdbDetails = MutableStateFlow<com.pnr.tv.network.dto.TmdbTvShowDetailsDto?>(null)
        val tmdbDetails: StateFlow<com.pnr.tv.network.dto.TmdbTvShowDetailsDto?> = _tmdbDetails.asStateFlow()

        private val _isLoading = MutableStateFlow(false)
        val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

        private val _error = MutableStateFlow("")
        val error: StateFlow<String> = _error.asStateFlow()

        // Events
        private val _showViewerSelectionDialog = MutableSharedFlow<List<ViewerEntity>>()
        val showViewerSelectionDialog: SharedFlow<List<ViewerEntity>> = _showViewerSelectionDialog.asSharedFlow()

        private val _showResumePlaybackDialog = MutableSharedFlow<PlaybackResumeManager.ResumePlaybackData>()
        val showResumePlaybackDialog: SharedFlow<PlaybackResumeManager.ResumePlaybackData> = _showResumePlaybackDialog.asSharedFlow()

        // State reducer
        private fun updateState(update: SeriesDetailUiState.() -> SeriesDetailUiState) {
            val newState = _uiState.value.update()
            _uiState.value = newState
            // Backward compatibility - eski StateFlow'ları da güncelle
            _series.value = newState.series
            _seasons.value = newState.seasons
            _selectedSeasonNumber.value = newState.selectedSeasonNumber
            _episodes.value = newState.episodes
            _tmdbDetails.value = newState.tmdbDetails
            _isLoading.value = newState.isLoading
            _error.value = newState.error
        }

        /**
         * Dizi detaylarını yükler.
         */
        fun loadSeries(seriesId: Int) {
            viewModelScope.launch {
                updateState { copy(isLoading = true, error = "") }
                when (val result = getSeriesDetailsUseCase(seriesId)) {
                    is Result.Success -> {
                        val data = result.data
                        updateState {
                            copy(
                                series = data.series,
                                seasons = data.seasons,
                                tmdbDetails = data.tmdbDetails,
                                allParsedEpisodesBySeason = data.episodesBySeason,
                                isLoading = false,
                            )
                        }
                        // İlk sezonu otomatik seç
                        data.seasons.firstOrNull()?.let { selectSeason(it.seasonNumber) }
                    }
                    is Result.PartialSuccess -> {
                        // PartialSuccess durumunda da Success gibi işle
                        val data = result.data
                        updateState {
                            copy(
                                series = data.series,
                                seasons = data.seasons,
                                tmdbDetails = data.tmdbDetails,
                                allParsedEpisodesBySeason = data.episodesBySeason,
                                isLoading = false,
                            )
                        }
                        data.seasons.firstOrNull()?.let { selectSeason(it.seasonNumber) }
                    }
                    is Result.Error -> {
                        updateState {
                            copy(
                                series = null,
                                seasons = emptyList(),
                                episodes = emptyList(),
                                allParsedEpisodesBySeason = emptyMap(),
                                isLoading = false,
                                error = result.message,
                            )
                        }
                    }
                }
            }
        }

        /**
         * Sezon seçer ve bölümleri yükler.
         */
        fun selectSeason(seasonNumber: Int) {
            updateState { copy(selectedSeasonNumber = seasonNumber) }
            viewModelScope.launch {
                try {
                    val episodesInSeason = _uiState.value.allParsedEpisodesBySeason[seasonNumber]
                        ?.sortedBy { it.episodeNumber } ?: emptyList()

                    val episodesWithStatus = getSeasonEpisodesUseCase(episodesInSeason)
                    updateState { copy(episodes = episodesWithStatus) }
                } catch (e: Exception) {
                    updateState { copy(episodes = emptyList()) }
                }
            }
        }

        /**
         * Bir bölümü izlendi olarak işaretle.
         */
        fun markEpisodeAsWatched(episode: ParsedEpisode) {
            viewModelScope.launch {
                val userId = sessionManager.getCurrentUserId().firstOrNull() ?: return@launch
                val watchedEntity =
                    WatchedEpisodeEntity(
                        episodeId = episode.episodeId,
                        userId = userId,
                        seriesId = _uiState.value.series?.streamId ?: 0,
                        seasonNumber = episode.seasonNumber,
                        episodeNumber = episode.episodeNumber,
                        watchedTimestamp = System.currentTimeMillis(),
                        watchProgress = 100,
                    )
                watchedEpisodeDao.markAsWatched(watchedEntity)

                // UI'yı güncelle
                updateState {
                    copy(
                        episodes = _uiState.value.episodes.map {
                            if (it.episodeId == episode.episodeId) {
                                it.copy(watchStatus = WatchStatus.FULLY_WATCHED)
                            } else {
                                it
                            }
                        },
                    )
                }
            }
        }

        /**
         * Bölüm stream URL'ini oluşturur.
         */
        suspend fun getEpisodeStreamUrl(
            baseUrl: String,
            username: String,
            password: String,
            episodeId: Int,
            extension: String?,
        ): String {
            val ext = extension ?: "ts"
            return "$baseUrl/series/$username/$password/$episodeId.$ext"
        }

        // Metadata provider delegasyonları
        suspend fun getCreator(): String? {
            return metadataProvider.getCreator(_uiState.value.series, _uiState.value.tmdbDetails)
        }

        suspend fun getCast(): String? {
            return metadataProvider.getCast(_uiState.value.series, _uiState.value.tmdbDetails)
        }

        suspend fun getOverview(): String? {
            return metadataProvider.getOverview(_uiState.value.series, _uiState.value.tmdbDetails)
        }

        fun getGenre(): String? {
            return metadataProvider.getGenre(_uiState.value.tmdbDetails)
        }

        // Favorite handler delegasyonları
        fun addToFavorites() {
            favoriteHandler.addToFavorites(
                coroutineScope = viewModelScope,
                onShowViewerSelectionDialog = { viewers ->
                    viewModelScope.launch {
                        _showViewerSelectionDialog.emit(viewers)
                    }
                },
            )
        }

        fun saveFavoriteForViewer(viewer: ViewerEntity) {
            _uiState.value.series?.let { series ->
                favoriteHandler.saveFavoriteForViewer(
                    streamId = series.streamId,
                    viewer = viewer,
                    coroutineScope = viewModelScope,
                )
            }
        }

        fun isFavorite(viewerId: Int): Flow<Boolean> {
            val series = _uiState.value.series ?: return flowOf(false)
            return favoriteHandler.isFavorite(series.streamId, viewerId)
        }

        fun removeFavoriteForViewer(viewer: ViewerEntity) {
            _uiState.value.series?.let { series ->
                favoriteHandler.removeFavoriteForViewer(
                    streamId = series.streamId,
                    viewer = viewer,
                    coroutineScope = viewModelScope,
                )
            }
        }

        fun removeFavoriteForAnyViewer() {
            _uiState.value.series?.let { series ->
                favoriteHandler.removeFavoriteForAnyViewer(
                    streamId = series.streamId,
                    coroutineScope = viewModelScope,
                )
            }
        }

        fun isFavoriteInAnyViewer(): Flow<Boolean> {
            val series = _uiState.value.series ?: return flowOf(false)
            return favoriteHandler.isFavoriteInAnyViewer(series.streamId)
        }

        // Premium guard delegasyonları
        fun isPremium(): Flow<Boolean> = premiumFeatureGuard.isPremium()

        suspend fun isPremiumSync(): Boolean = premiumFeatureGuard.isPremiumSync()

        // Resume manager delegasyonları
        suspend fun checkAndShowResumeDialog(episode: ParsedEpisode): Boolean {
            val resumeData = resumeManager.checkAndGetResumeData(episode)
            return if (resumeData != null) {
                _showResumePlaybackDialog.emit(resumeData)
                true
            } else {
                false
            }
        }

        suspend fun deletePlaybackPosition(episode: ParsedEpisode) {
            resumeManager.deletePlaybackPosition(episode)
        }

        @AssistedFactory
        interface Factory {
            fun create(): SeriesDetailViewModel
        }
    }
