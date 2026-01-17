package com.pnr.tv.repository

import android.content.Context
import com.pnr.tv.core.constants.DatabaseConstants
import com.pnr.tv.ui.main.SessionManager
import com.pnr.tv.db.dao.WatchedEpisodeDao
import com.pnr.tv.db.entity.LiveStreamCategoryEntity
import com.pnr.tv.db.entity.LiveStreamEntity
import com.pnr.tv.db.entity.MovieCategoryEntity
import com.pnr.tv.db.entity.MovieEntity
import com.pnr.tv.db.entity.PlaybackPositionEntity
import com.pnr.tv.db.entity.SeriesCategoryEntity
import com.pnr.tv.db.entity.SeriesEntity
import com.pnr.tv.db.entity.WatchedEpisodeEntity
import com.pnr.tv.di.IptvRetrofit
import com.pnr.tv.network.dto.AuthenticationResponseDto
import com.pnr.tv.network.dto.SeriesInfoDto
import com.pnr.tv.util.error.Resource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import retrofit2.Retrofit
import timber.log.Timber
import javax.inject.Inject

/**
 * ContentRepository - Facade Pattern
 *
 * Tüm content repository'lerini tek bir interface altında toplar.
 * Backward compatibility için mevcut API'yi korur.
 *
 * NOT: Bu sınıf artık sadece bir facade'dır. Gerçek işlemler
 * alt repository'lerde (MovieRepository, SeriesRepository, vb.) yapılır.
 */
class ContentRepository
    @Inject
    constructor(
        private val movieRepository: MovieRepository,
        private val seriesRepository: SeriesRepository,
        private val liveStreamRepository: LiveStreamRepository,
        private val favoriteRepository: FavoriteRepository,
        private val recentlyWatchedRepository: RecentlyWatchedRepository,
        private val playbackPositionRepository: PlaybackPositionRepository,
        private val watchedEpisodeDao: WatchedEpisodeDao,
        private val sessionManager: SessionManager,
        private val apiServiceManager: ApiServiceManager,
        private val userRepository: UserRepository,
        @ApplicationContext private val context: Context,
    ) {
        // BaseContentRepository'yi composition olarak kullan
        // fetchUserInfo için safeApiCall metoduna ihtiyacımız var
        private val baseRepository = BaseContentRepository(
            apiServiceManager = apiServiceManager,
            userRepository = userRepository,
            context = context,
        )

        // ==================== Movie Operations ====================

        fun getMovies(): Flow<Resource<List<MovieEntity>>> = movieRepository.getMovies()

        fun getMoviesByCategoryId(categoryId: String): Flow<Resource<List<MovieEntity>>> = movieRepository.getMoviesByCategoryId(categoryId)

        fun getRecentlyAddedMovies(limit: Int): Flow<List<MovieEntity>> = movieRepository.getRecentlyAddedMovies(limit)

        fun getMovieCategories(): Flow<Resource<List<MovieCategoryEntity>>> = movieRepository.getMovieCategories()

        suspend fun refreshMovies(
            skipTmdbSync: Boolean = false,
            forMainScreenUpdate: Boolean = false,
        ): Result<Unit> = movieRepository.refreshMovies(skipTmdbSync, forMainScreenUpdate)

        suspend fun refreshMovieCategories(): Result<Unit> = movieRepository.refreshMovieCategories()

        suspend fun getMoviesByIds(movieIds: List<Int>): List<MovieEntity> = movieRepository.getMoviesByIds(movieIds)

        suspend fun getMovieCategoryCounts(): Map<String, Int> = movieRepository.getCategoryCounts()

        suspend fun hasMovies(): Boolean = movieRepository.hasMovies()

        suspend fun hasMovieCategories(): Boolean = movieRepository.hasMovieCategories()

        // ==================== Series Operations ====================

        fun getSeries(): Flow<Resource<List<SeriesEntity>>> = seriesRepository.getSeries()

        fun getSeriesByCategoryId(categoryId: String): Flow<Resource<List<SeriesEntity>>> =
            seriesRepository.getSeriesByCategoryId(
                categoryId,
            )

        fun getRecentlyAddedSeries(limit: Int): Flow<List<SeriesEntity>> = seriesRepository.getRecentlyAddedSeries(limit)

        fun getSeriesCategories(): Flow<Resource<List<SeriesCategoryEntity>>> = seriesRepository.getSeriesCategories()

        suspend fun refreshSeries(
            skipTmdbSync: Boolean = false,
            forMainScreenUpdate: Boolean = false,
        ): Result<Unit> = seriesRepository.refreshSeries(skipTmdbSync, forMainScreenUpdate)

        suspend fun refreshSeriesCategories(): Result<Unit> = seriesRepository.refreshSeriesCategories()

        suspend fun getSeriesInfo(seriesId: Int): Result<SeriesInfoDto> = seriesRepository.getSeriesInfo(seriesId)

        suspend fun getSeriesByIds(seriesIds: List<Int>): List<SeriesEntity> = seriesRepository.getSeriesByIds(seriesIds)

        suspend fun getSeriesCategoryCounts(): Map<String, Int> = seriesRepository.getCategoryCounts()

        suspend fun hasSeries(): Boolean = seriesRepository.hasSeries()

        suspend fun hasSeriesCategories(): Boolean = seriesRepository.hasSeriesCategories()

        // ==================== LiveStream Operations ====================

        fun getLiveStreams(): Flow<Resource<List<LiveStreamEntity>>> = liveStreamRepository.getLiveStreams()

        fun getLiveStreamsByCategoryId(categoryId: Int): Flow<Resource<List<LiveStreamEntity>>> =
            liveStreamRepository.getLiveStreamsByCategoryId(
                categoryId,
            )

        suspend fun getLiveStreamsByCategoryIdSync(categoryId: Int): List<LiveStreamEntity> {
            return liveStreamRepository.getLiveStreamsByCategoryIdSync(categoryId)
        }

        fun getLiveStreamCategories(): Flow<Resource<List<LiveStreamCategoryEntity>>> = liveStreamRepository.getLiveStreamCategories()

        suspend fun refreshLiveStreams(forMainScreenUpdate: Boolean = false): Result<Unit> =
            liveStreamRepository.refreshLiveStreams(forMainScreenUpdate)

        suspend fun refreshLiveStreamCategories(): Result<Unit> = liveStreamRepository.refreshLiveStreamCategories()

        suspend fun getLiveStreamsByIds(channelIds: List<Int>): List<LiveStreamEntity> =
            liveStreamRepository.getLiveStreamsByIds(
                channelIds,
            )

        suspend fun hasLiveStreams(): Boolean = liveStreamRepository.hasLiveStreams()

        suspend fun hasLiveStreamCategories(): Boolean = liveStreamRepository.hasLiveStreamCategories()

        // ==================== Favorite Operations ====================

        suspend fun addFavorite(
            channelId: Int,
            viewerId: Int,
        ) = favoriteRepository.addFavorite(channelId, viewerId)

        suspend fun removeFavorite(
            channelId: Int,
            viewerId: Int,
        ) = favoriteRepository.removeFavorite(channelId, viewerId)

        /**
         * Belirli bir içeriği (channelId) tüm izleyicilerden favorilerden çıkarır.
         * Toggle favori işlemi için kullanılır.
         */
        suspend fun removeFavoriteForAllViewers(channelId: Int) = favoriteRepository.removeFavoriteForAllViewers(channelId)

        fun isFavorite(
            channelId: Int,
            viewerId: Int,
        ): Flow<Boolean> = favoriteRepository.isFavorite(channelId, viewerId)

        fun getFavoriteChannelIds(viewerId: Int): Flow<List<Int>> = favoriteRepository.getFavoriteChannelIds(viewerId)

        fun getAllFavoriteChannelIds(): Flow<List<Int>> = favoriteRepository.getAllFavoriteChannelIds()

        fun getViewerIdsWithFavorites(): Flow<List<Int>> = favoriteRepository.getViewerIdsWithFavorites()

        // ==================== Recently Watched Operations ====================

        suspend fun saveRecentlyWatched(channelId: Int) = recentlyWatchedRepository.saveRecentlyWatched(channelId)

        fun getRecentlyWatchedChannelIds(limit: Int = DatabaseConstants.RECENTLY_WATCHED_DEFAULT_LIMIT): Flow<List<Int>> =
            recentlyWatchedRepository.getRecentlyWatchedChannelIds(limit)

        // ==================== Playback Position Operations ====================

        suspend fun savePlaybackPosition(
            contentId: String,
            positionMs: Long,
            durationMs: Long,
        ) = playbackPositionRepository.savePlaybackPosition(contentId, positionMs, durationMs)

        suspend fun getPlaybackPosition(contentId: String): PlaybackPositionEntity? =
            playbackPositionRepository.getPlaybackPosition(contentId)

        suspend fun deletePlaybackPosition(contentId: String) = playbackPositionRepository.deletePlaybackPosition(contentId)

        suspend fun cleanupOldPlaybackPositions() = playbackPositionRepository.cleanupOldPlaybackPositions()

        // ==================== Watch Progress Operations ====================

        /**
         * Bölüm izleme ilerlemesini günceller.
         * Hem PlaybackPosition hem de WatchedEpisodeEntity'yi günceller.
         *
         * @param contentId İçerik ID'si (episode_ prefix'li olabilir)
         * @param positionMs Mevcut pozisyon (milisaniye)
         * @param durationMs Toplam süre (milisaniye)
         * @param episodeId Bölüm ID'si (contentId'den parse edilir veya direkt verilir)
         * @param seriesId Dizi ID'si (opsiyonel, mevcut kayıt varsa kullanılır)
         * @param seasonNumber Sezon numarası (opsiyonel, mevcut kayıt varsa kullanılır)
         * @param episodeNumber Bölüm numarası (opsiyonel, mevcut kayıt varsa kullanılır)
         */
        suspend fun updateWatchProgress(
            contentId: String,
            positionMs: Long,
            durationMs: Long,
            episodeId: String? = null,
            seriesId: Int? = null,
            seasonNumber: Int? = null,
            episodeNumber: Int? = null,
        ) {
            // Playback position'ı her zaman kaydet
            savePlaybackPosition(contentId, positionMs, durationMs)

            // Episode bilgisi yoksa veya duration 0 ise watchProgress güncelleme
            if (durationMs <= 0) return

            val userId = sessionManager.getCurrentUserId().firstOrNull() ?: return

            // Episode ID'yi parse et (contentId'den "episode_" prefix'ini kaldır)
            val parsedEpisodeId = episodeId ?: contentId.removePrefix("episode_")

            // Progress yüzdesini hesapla
            val progressPercentage = ((positionMs.toFloat() / durationMs.toFloat()) * 100).toInt().coerceIn(0, 100)

            try {
                // Mevcut kaydı kontrol et
                val existingEntity = watchedEpisodeDao.getWatchedEpisode(parsedEpisodeId, userId)

                if (existingEntity != null) {
                    // Mevcut kayıt varsa, sadece watchProgress ve timestamp'i güncelle
                    val updatedEntity =
                        existingEntity.copy(
                            watchProgress = progressPercentage,
                            watchedTimestamp = System.currentTimeMillis(),
                        )
                    watchedEpisodeDao.markAsWatched(updatedEntity)
                } else if (seriesId != null && seasonNumber != null && episodeNumber != null) {
                    // Yeni kayıt oluştur (tüm bilgiler mevcut)
                    val newEntity =
                        WatchedEpisodeEntity(
                            episodeId = parsedEpisodeId,
                            userId = userId,
                            seriesId = seriesId,
                            seasonNumber = seasonNumber,
                            episodeNumber = episodeNumber,
                            watchedTimestamp = System.currentTimeMillis(),
                            watchProgress = progressPercentage,
                        )
                    watchedEpisodeDao.markAsWatched(newEntity)
                }
                // Eğer mevcut kayıt yoksa ve episode bilgileri eksikse, sadece playback position kaydedilir
            } catch (e: Exception) {
                Timber.e(e, "Watch progress güncellenirken hata: contentId=$contentId, episodeId=$parsedEpisodeId")
            }
        }

        // ==================== User Info Operations ====================

        /**
         * Kullanıcı bilgilerini getirir.
         * Bu metod BaseContentRepository'den safeApiCall kullanır.
         */
        suspend fun fetchUserInfo(): Result<AuthenticationResponseDto> {
            return baseRepository.safeApiCall(apiCall = { api, user, pass ->
                try {
                    api.getUserInfo(user, pass)
                } catch (e: com.squareup.moshi.JsonDataException) {
                    Timber.e(e, "JSON Parse Hatası - API response formatı beklenenle uyuşmuyor: ${e.message}")
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "API çağrısı sırasında beklenmeyen hata: ${e.javaClass.simpleName} - ${e.message}")
                    throw e
                }
            })
        }
    }
