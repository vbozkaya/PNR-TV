package com.pnr.tv.ui.movies

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pnr.tv.R
import com.pnr.tv.core.constants.TimeConstants
import com.pnr.tv.db.entity.MovieEntity
import com.pnr.tv.db.entity.PlaybackPositionEntity
import com.pnr.tv.db.entity.ViewerEntity
import com.pnr.tv.network.dto.TmdbMovieDetailsDto
import com.pnr.tv.premium.PremiumFeatureGuard
import com.pnr.tv.repository.ContentRepository
import com.pnr.tv.repository.PlaybackPositionRepository
import com.pnr.tv.repository.TmdbRepository
import com.pnr.tv.repository.ViewerRepository
import com.pnr.tv.util.error.ErrorHelper
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

/**
 * Film detay sayfası için ViewModel.
 * Seçilen filmin tüm bilgilerini sağlar.
 */
class MovieDetailViewModel
    @AssistedInject
    constructor(
        private val contentRepository: ContentRepository,
        private val playbackPositionRepository: PlaybackPositionRepository,
        private val viewerRepository: ViewerRepository,
        private val tmdbRepository: TmdbRepository,
        private val premiumFeatureGuard: PremiumFeatureGuard,
        @ApplicationContext private val context: Context,
    ) : ViewModel() {
        // UI State - Tüm ekran durumlarını tek noktadan yönetir
        private val _uiState = MutableStateFlow<MovieDetailUiState>(MovieDetailUiState.Initial)
        val uiState: StateFlow<MovieDetailUiState> = _uiState.asStateFlow()

        // Eski yapı - geriye dönük uyumluluk için (ihtiyaç duyulursa)
        private val _movie = MutableStateFlow<MovieEntity?>(null)
        val movie: StateFlow<MovieEntity?> = _movie.asStateFlow()

        private val _tmdbDetails = MutableStateFlow<TmdbMovieDetailsDto?>(null)
        val tmdbDetails: StateFlow<TmdbMovieDetailsDto?> = _tmdbDetails.asStateFlow()

        // Event to show viewer selection dialog
        private val _showViewerSelectionDialog = MutableSharedFlow<List<ViewerEntity>>()
        val showViewerSelectionDialog: SharedFlow<List<ViewerEntity>> = _showViewerSelectionDialog.asSharedFlow()

        // Event to show resume playback dialog
        private val _showResumePlaybackDialog = MutableSharedFlow<PlaybackPositionEntity>()
        val showResumePlaybackDialog: SharedFlow<PlaybackPositionEntity> = _showResumePlaybackDialog.asSharedFlow()

        /**
         * Belirli bir film ID'sine göre film bilgisini yükler.
         * Ayrıca TMDB'den film detaylarını da getirir.
         *
         * UI State pattern kullanarak tüm yükleme sürecini yönetir:
         * - Loading: Veri yüklenirken
         * - Success: Veri başarıyla yüklendi
         * - Error: Hata oluştu
         */
        fun loadMovie(movieId: Int) {
            viewModelScope.launch {
                try {
                    // 1. Loading durumuna geç
                    _uiState.value = MovieDetailUiState.Loading

                    // 2. Film bilgilerini getir
                    // ✅ Direkt veritabanından senkron okuma yap (Flow yerine)
                    // Bu, diziler gibi çalışır ve daha güvenilirdir
                    val foundMovie =
                        try {
                            val movies = contentRepository.getMoviesByIds(listOf(movieId))
                            movies.firstOrNull()
                        } catch (e: Exception) {
                            // Veritabanı hatası
                            _uiState.value =
                                MovieDetailUiState.Error(
                                    message = context.getString(R.string.error_movie_not_found),
                                    exception = e,
                                )
                            return@launch
                        }

                    if (foundMovie == null) {
                        // Film bulunamadı - veritabanında film yok veya bu ID'ye sahip film yok
                        _uiState.value =
                            MovieDetailUiState.Error(
                                message = context.getString(R.string.error_movie_not_found),
                            )
                        return@launch
                    }

                    _movie.value = foundMovie

                    // 3. TMDB detaylarını getir
                    val tmdbMovie =
                        if (foundMovie.tmdbId != null) {
                            // Strateji 1: TMDB ID varsa doğrudan kullan
                            tmdbRepository.getMovieDetailsById(foundMovie.tmdbId)
                        } else {
                            // Strateji 2: Film adıyla ara (fallback)
                            foundMovie.name?.let { movieTitle ->
                                tmdbRepository.getMovieDetailsByTitle(movieTitle)
                            }
                        }

                    _tmdbDetails.value = tmdbMovie

                    // 4. TMDB verilerinden bilgileri çıkar
                    val director = getDirector(foundMovie.tmdbId, tmdbMovie)
                    val genre = tmdbRepository.getGenres(tmdbMovie)
                    val cast = getCast(foundMovie.tmdbId, tmdbMovie)
                    val overview = getOverview(foundMovie.tmdbId, tmdbMovie)
                    val posterUrl = tmdbRepository.getPosterUrl(tmdbMovie)
                    val tmdbRating = tmdbRepository.getRating(tmdbMovie)

                    // 5. Success durumuna geç
                    _uiState.value =
                        MovieDetailUiState.Success(
                            movie = foundMovie,
                            tmdbDetails = tmdbMovie,
                            director = director,
                            genre = genre,
                            cast = cast,
                            overview = overview,
                            posterUrl = posterUrl,
                            rating = tmdbRating,
                        )
                } catch (e: Exception) {
                    // 6. Hata durumuna geç - ErrorHelper kullanarak kullanıcı dostu mesaj oluştur
                    val errorResult =
                        ErrorHelper.createError(
                            exception = e,
                            context = context,
                            errorContext = "MovieDetailViewModel",
                        )
                    _uiState.value =
                        MovieDetailUiState.Error(
                            message = errorResult.message,
                            exception = e,
                        )
                }
            }
        }

        /**
         * TMDB'den yönetmen bilgisini döndürür
         * Cache'den veya DTO'dan alır
         */
        suspend fun getDirector(
            tmdbId: Int?,
            tmdbDetails: TmdbMovieDetailsDto?,
        ): String? {
            return if (tmdbId != null) {
                tmdbRepository.getDirector(tmdbId, tmdbDetails)
            } else {
                null
            }
        }

        /**
         * TMDB'den oyuncu listesini döndürür ve UI için formatlı string'e çevirir
         *
         * Repository'den ham veri (List<String>) alır,
         * ViewModel'de UI formatlaması yapar (virgülle birleştirme)
         *
         * @return Virgülle ayrılmış oyuncu listesi veya null
         */
        suspend fun getCast(
            tmdbId: Int?,
            tmdbDetails: TmdbMovieDetailsDto?,
        ): String? {
            if (tmdbId == null) return null

            // Repository'den ham veri al
            val castList = tmdbRepository.getCast(tmdbId, tmdbDetails)

            // UI formatlaması - virgülle birleştir
            return formatCastForDisplay(castList)
        }

        /**
         * Oyuncu listesini UI için formatlı string'e çevirir
         *
         * Gelecekte format değiştirilmek istenirse (örneğin alt alta liste)
         * sadece bu metod değiştirilir, Repository değişmez
         *
         * @param castList Ham oyuncu isimleri listesi
         * @return Formatlı string (örn: "Actor1, Actor2, Actor3")
         */
        private fun formatCastForDisplay(castList: List<String>?): String? {
            if (castList.isNullOrEmpty()) return null

            // Şu an: Virgülle birleştir
            // Gelecekte: Bullet point'li liste yapılabilir
            return castList.joinToString(", ")
        }

        /**
         * TMDB'den açıklamayı döndürür, yoksa mevcut plot'u kullanır
         * Cache'den veya DTO'dan alır
         */
        suspend fun getOverview(
            tmdbId: Int?,
            tmdbDetails: TmdbMovieDetailsDto?,
        ): String? {
            val tmdbOverview =
                if (tmdbId != null) {
                    tmdbRepository.getOverview(tmdbId, tmdbDetails)
                } else {
                    null
                }
            return tmdbOverview ?: _movie.value?.plot
        }

        /**
         * Filmin oynatma URL'sini oluşturur.
         */
        suspend fun getStreamUrl(
            baseUrl: String,
            username: String,
            password: String,
        ): String? {
            val movie = _movie.value ?: return null
            // API'den gelen container extension'ı kullan, yoksa varsayılan ts
            val extension = movie.containerExtension ?: "ts"
            // IPTV VOD stream URL formatı: {baseUrl}/movie/{username}/{password}/{streamId}.{extension}
            return "$baseUrl/movie/$username/$password/${movie.streamId}.$extension"
        }

        /**
         * Tüm izleyicileri getirir.
         */
        fun getAllViewers(): Flow<List<ViewerEntity>> = viewerRepository.getAllViewers()

        /**
         * Favori ekleme işlemini başlatır.
         * İzleyici listesini çeker ve UI'a dialog göstermesi için bildirir.
         */
        fun addToFavorites() {
            viewModelScope.launch {
                val viewers = viewerRepository.getAllViewers().firstOrNull() ?: emptyList()
                if (viewers.isNotEmpty()) {
                    _showViewerSelectionDialog.emit(viewers)
                } else {
                    // İzleyici yoksa hata log'u (UI'da gösterilmez, sadece log)
                    timber.log.Timber.w("Favori eklenemedi: İzleyici bulunamadı")
                }
            }
        }

        /**
         * Premium durumunu kontrol eder.
         */
        fun isPremium(): Flow<Boolean> = premiumFeatureGuard.isPremium()

        /**
         * Premium durumunu senkron olarak kontrol eder.
         */
        suspend fun isPremiumSync(): Boolean = premiumFeatureGuard.isPremiumSync()

        /**
         * Filmi belirli bir izleyici için favorilere ekler.
         */
        fun saveFavoriteForViewer(viewer: ViewerEntity) {
            val movie = _movie.value ?: return
            viewModelScope.launch {
                contentRepository.addFavorite(movie.streamId, viewer.id)
            }
        }

        /**
         * Filmin belirli bir izleyici için favori olup olmadığını kontrol eder.
         */
        fun isFavorite(viewerId: Int): Flow<Boolean> {
            val movie = _movie.value ?: return kotlinx.coroutines.flow.flowOf(false)
            return contentRepository.isFavorite(movie.streamId, viewerId)
        }

        /**
         * Filmi belirli bir izleyici için favorilerden çıkarır.
         */
        fun removeFavoriteForViewer(viewer: ViewerEntity) {
            val movie = _movie.value ?: return
            viewModelScope.launch {
                contentRepository.removeFavorite(movie.streamId, viewer.id)
            }
        }

        /**
         * Filmi tüm izleyicilerden favorilerden çıkarır.
         * Toggle favori işlemi için kullanılır - tüm izleyicilerden favoriyi kaldırır.
         */
        fun removeFavoriteForAnyViewer() {
            val movie = _movie.value ?: return
            viewModelScope.launch {
                contentRepository.removeFavoriteForAllViewers(movie.streamId)
            }
        }

        /**
         * Filmin herhangi bir izleyici için favori olup olmadığını kontrol eder.
         * Tüm izleyiciler için favori durumunu kontrol eder ve en az birinde favori ise true döner.
         */
        fun isFavoriteInAnyViewer(): Flow<Boolean> {
            val movie = _movie.value ?: return kotlinx.coroutines.flow.flowOf(false)

            return viewerRepository.getAllViewers().flatMapLatest { viewers ->
                if (viewers.isEmpty()) {
                    kotlinx.coroutines.flow.flowOf(false)
                } else {
                    // Her izleyici için favori durumunu kontrol et ve birleştir
                    combine(
                        viewers.map { viewer ->
                            contentRepository.isFavorite(movie.streamId, viewer.id)
                        },
                    ) { favoriteStates ->
                        favoriteStates.any { it }
                    }
                }
            }
        }

        /**
         * Filmin oynatılması gerekip gerekmediğini kontrol eder.
         * Eğer kayıtlı pozisyon varsa ve anlamlıysa, dialog gösterilmesi için event emit eder.
         * Premium özelliği: Sadece Premium kullanıcılar için resume dialog gösterilir.
         *
         * @return true eğer dialog gösterilmeli, false eğer direkt oynatılmalı
         */
        suspend fun checkAndShowResumeDialog(): Boolean {
            // Premium kontrolü: Premium değilse dialog gösterilmez, her zaman baştan başlar
            if (!premiumFeatureGuard.isPremiumSync()) {
                return false
            }

            val movie = _movie.value ?: return false
            val contentId = "movie_${movie.streamId}"
            val savedPosition = playbackPositionRepository.getPlaybackPosition(contentId)

            if (savedPosition != null && shouldShowResumeDialog(savedPosition)) {
                _showResumePlaybackDialog.emit(savedPosition)
                return true
            }
            return false
        }

        /**
         * Dialog gösterilip gösterilmeyeceğini belirler.
         * İçeriğin süresinden bağımsız olarak, 10 dakikadan fazla izlenmişse dialog gösterilir.
         *
         * @param position Kayıtlı oynatma pozisyonu
         * @return true eğer dialog gösterilmeli
         */
        private fun shouldShowResumeDialog(position: PlaybackPositionEntity): Boolean {
            if (position.durationMs <= 0) return false

            val resumeThresholdMs = TimeConstants.Intervals.TEN_MINUTES_MS // 10 dakika

            return position.positionMs >= resumeThresholdMs
        }

        /**
         * Oynatma pozisyonunu siler (baştan başlatmak için).
         */
        suspend fun deletePlaybackPosition() {
            val movie = _movie.value ?: return
            val contentId = "movie_${movie.streamId}"
            playbackPositionRepository.deletePlaybackPosition(contentId)
        }

        /**
         * AssistedInject için Factory interface.
         */
        @AssistedFactory
        interface Factory {
            fun create(): MovieDetailViewModel
        }
    }
