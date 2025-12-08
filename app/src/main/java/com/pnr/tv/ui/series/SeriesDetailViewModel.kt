package com.pnr.tv.ui.series

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pnr.tv.R
import com.pnr.tv.db.dao.WatchedEpisodeDao
import com.pnr.tv.db.entity.SeriesEntity
import com.pnr.tv.db.entity.ViewerEntity
import com.pnr.tv.db.entity.WatchedEpisodeEntity
import com.pnr.tv.network.dto.TmdbTvShowDetailsDto
import com.pnr.tv.network.dto.toEntity
import com.pnr.tv.repository.ContentRepository
import com.pnr.tv.repository.TmdbRepository
import com.pnr.tv.repository.ViewerRepository
import com.pnr.tv.ui.series.model.SeriesSeason
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * İzlenme durumu enum'u
 */
enum class WatchStatus {
    NOT_WATCHED,      // İzlenmedi (Beyaz çerçeve)
    IN_PROGRESS,      // Yarım kaldı (Kırmızı çerçeve)
    FULLY_WATCHED     // Tamamlandı (Yeşil çerçeve)
}

/**
 * Ayrıştırılmış bölüm verisi.
 */
data class ParsedEpisode(
    val episodeId: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String, // Orijinal başlık
    val cleanTitle: String?, // Sadece bölüm adı (nullable)
    val watchStatus: WatchStatus = WatchStatus.NOT_WATCHED, // İzlenme durumu
    val containerExtension: String? = null // Container format (ts, mp4, mkv, etc.)
)

/**
 * Dizi detay sayfası için ViewModel.
 * Seçilen dizinin tüm bilgilerini (sezonlar, bölümler vb.) sağlar.
 */
class SeriesDetailViewModel
    @AssistedInject
    constructor(
        private val contentRepository: ContentRepository,
        private val tmdbRepository: TmdbRepository,
        private val watchedEpisodeDao: WatchedEpisodeDao,
        private val viewerRepository: ViewerRepository,
        @ApplicationContext private val context: Context,
    ) : ViewModel() {
        private val _series = MutableStateFlow<SeriesEntity?>(null)
        val series: StateFlow<SeriesEntity?> = _series.asStateFlow()

        private val _seasons = MutableStateFlow<List<SeriesSeason>>(emptyList())
        val seasons: StateFlow<List<SeriesSeason>> = _seasons.asStateFlow()

        private val _selectedSeasonNumber = MutableStateFlow<Int?>(null)
        val selectedSeasonNumber: StateFlow<Int?> = _selectedSeasonNumber.asStateFlow()

        private val _episodes = MutableStateFlow<List<ParsedEpisode>>(emptyList())
        val episodes: StateFlow<List<ParsedEpisode>> = _episodes.asStateFlow()

        private val _tmdbDetails = MutableStateFlow<TmdbTvShowDetailsDto?>(null)
        val tmdbDetails: StateFlow<TmdbTvShowDetailsDto?> = _tmdbDetails.asStateFlow()

        private val _isLoading = MutableStateFlow(false)
        val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

        private val _error = MutableSharedFlow<String>()
        val error: SharedFlow<String> = _error.asSharedFlow()

        private val _allParsedEpisodesBySeason = MutableStateFlow<Map<Int, List<ParsedEpisode>>>(emptyMap())

        private val _showViewerSelectionDialog = MutableSharedFlow<List<ViewerEntity>>()
        val showViewerSelectionDialog: SharedFlow<List<ViewerEntity>> = _showViewerSelectionDialog.asSharedFlow()

        private val episodeRegex = "[Ss](\\d+)[._]?[Ee](\\d+)".toRegex()

        fun loadSeries(seriesId: Int) {
            viewModelScope.launch {
                _isLoading.value = true
                try {
                    val result = contentRepository.getSeriesInfo(seriesId)

                    if (result is com.pnr.tv.repository.Result.Success) {
                        val seriesInfo = result.data
                        _series.value = seriesInfo.info?.toEntity(seriesId)

                        val allEpisodes = seriesInfo.episodes?.values?.flatten() ?: emptyList()

                        val episodesBySeason = allEpisodes.mapNotNull { dto ->
                            val matchResult = episodeRegex.find(dto.title ?: "")
                            if (matchResult != null && matchResult.groupValues.size == 3) {
                                val season = matchResult.groupValues[1].toIntOrNull()
                                val episode = matchResult.groupValues[2].toIntOrNull()
                                val id = dto.id
                                if (season != null && episode != null && id != null) {
                                    val rawTitle = dto.title ?: ""
                                    val cleanTitle = rawTitle.substringAfter(" - ").takeIf { it.isNotBlank() && it != rawTitle } 
                                        ?: rawTitle.substringAfter(": ").takeIf { it.isNotBlank() && it != rawTitle }

                                    timber.log.Timber.d("📺 API'den bölüm: $rawTitle, containerExtension: ${dto.containerExtension ?: "null (varsayılan ts kullanılacak)"}")

                                    ParsedEpisode(
                                        episodeId = id,
                                        seasonNumber = season,
                                        episodeNumber = episode,
                                        title = rawTitle,
                                        cleanTitle = cleanTitle,
                                        containerExtension = dto.containerExtension
                                    )
                                } else { null }
                            } else { null }
                        }.groupBy { it.seasonNumber }

                        _allParsedEpisodesBySeason.value = episodesBySeason

                        val seasonTabs = episodesBySeason.keys.sorted().map { seasonNum ->
                            val episodeCount = episodesBySeason[seasonNum]?.size ?: 0
                            SeriesSeason(
                                seasonNumber = seasonNum, 
                                name = context.getString(R.string.season_format_with_episodes, seasonNum, episodeCount)
                            )
                        }
                        _seasons.value = seasonTabs

                        seasonTabs.firstOrNull()?.let { selectSeason(it.seasonNumber) }

                        _series.value?.let {
                            val tmdbDetailsResult = if (it.tmdbId != null) {
                                tmdbRepository.getTvShowDetailsById(it.tmdbId)
                            } else {
                                it.name?.let { title -> tmdbRepository.getTvShowDetailsByTitle(title) }
                            }
                            _tmdbDetails.value = tmdbDetailsResult
                        }
                    } else {
                        val errorMessage = if (result is com.pnr.tv.repository.Result.Error) result.message else "Bilinmeyen hata"
                        Timber.e("Dizi bilgisi alınamadı: $errorMessage")
                        _error.emit("Dizi bilgileri yüklenemedi: $errorMessage")
                        _series.value = null
                        _seasons.value = emptyList()
                        _episodes.value = emptyList()
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Dizi yüklenirken hata oluştu")
                    _error.emit("Dizi yüklenirken beklenmeyen bir hata oluştu. Lütfen tekrar deneyin.")
                    _series.value = null
                    _seasons.value = emptyList()
                    _episodes.value = emptyList()
                } finally {
                    _isLoading.value = false
                }
            }
        }

        fun selectSeason(seasonNumber: Int) {
            _selectedSeasonNumber.value = seasonNumber
            
            viewModelScope.launch {
                val episodesInSeason = _allParsedEpisodesBySeason.value[seasonNumber]?.sortedBy { it.episodeNumber } ?: emptyList()
                
                // İzlenme durumlarını kontrol et
                val episodesWithWatchedStatus = episodesInSeason.map { episode ->
                    val watchedEntity = watchedEpisodeDao.getWatchedEpisode(episode.episodeId)
                    val newStatus = when {
                        watchedEntity == null -> WatchStatus.NOT_WATCHED
                        watchedEntity.watchProgress >= 90 -> WatchStatus.FULLY_WATCHED
                        watchedEntity.watchProgress > 10 -> WatchStatus.IN_PROGRESS // %10'dan fazla ve %90'dan az
                        else -> WatchStatus.NOT_WATCHED // %10 veya daha az
                    }
                    episode.copy(watchStatus = newStatus)
                }
                
                _episodes.value = episodesWithWatchedStatus
            }
        }
        
        /**
         * Bir bölümü izlendi olarak işaretle.
         */
        fun markEpisodeAsWatched(episode: ParsedEpisode) {
            viewModelScope.launch {
                val watchedEntity = WatchedEpisodeEntity(
                    episodeId = episode.episodeId,
                    seriesId = _series.value?.streamId ?: 0,
                    seasonNumber = episode.seasonNumber,
                    episodeNumber = episode.episodeNumber,
                    watchedTimestamp = System.currentTimeMillis(),
                    watchProgress = 100
                )
                watchedEpisodeDao.markAsWatched(watchedEntity)
                
                // UI'yı güncelle
                _episodes.value = _episodes.value.map {
                    if (it.episodeId == episode.episodeId) {
                        it.copy(watchStatus = WatchStatus.FULLY_WATCHED)
                    } else {
                        it
                    }
                }
            }
        }

        suspend fun getEpisodeStreamUrl(baseUrl: String, username: String, password: String, episodeId: Int, extension: String?): String {
            // API'den gelen container extension'ı kullan, yoksa varsayılan ts
            val ext = extension ?: "ts"
            // Diziler için /series/ path'i kullan (filmler /movie/ kullanıyor)
            return "$baseUrl/series/$username/$password/$episodeId.$ext"
        }

        suspend fun getCreator(tmdbId: Int?, tvDetails: TmdbTvShowDetailsDto?): String? {
            return tmdbId?.let { tmdbRepository.getCreator(it, tvDetails) }
        }

        suspend fun getCast(tmdbId: Int?, tvDetails: TmdbTvShowDetailsDto?): String? {
            return tmdbId?.let { tmdbRepository.getCastFromTv(it, tvDetails)?.joinToString(", ") }
        }

        suspend fun getOverview(tmdbId: Int?, tvDetails: TmdbTvShowDetailsDto?): String? {
            val tmdbOverview = tmdbId?.let { tmdbRepository.getOverviewFromTv(it, tvDetails) }
            return tmdbOverview ?: _series.value?.plot
        }

        fun getGenre(tvDetails: TmdbTvShowDetailsDto?): String? {
            return tmdbRepository.getGenresFromTv(tvDetails)
        }

        /**
         * Favorilere ekleme - izleyici seçim dialog'unu göster
         */
        fun addToFavorites() {
            viewModelScope.launch {
                val viewers = viewerRepository.getAllViewers().firstOrNull() ?: emptyList()
                if (viewers.isNotEmpty()) {
                    _showViewerSelectionDialog.emit(viewers)
                }
            }
        }

        /**
         * Seçilen izleyici için diziyi favorilere ekle
         */
        fun saveFavoriteForViewer(viewer: ViewerEntity) {
            _series.value?.let { series ->
                viewModelScope.launch {
                    contentRepository.addFavorite(series.streamId, viewer.id)
                }
            }
        }

        @AssistedFactory
        interface Factory {
            fun create(): SeriesDetailViewModel
        }
    }
