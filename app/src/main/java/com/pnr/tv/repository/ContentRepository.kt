package com.pnr.tv.repository

import android.content.Context
import com.pnr.tv.Constants
import com.pnr.tv.db.entity.LiveStreamCategoryEntity
import com.pnr.tv.db.entity.LiveStreamEntity
import com.pnr.tv.db.entity.MovieCategoryEntity
import com.pnr.tv.db.entity.MovieEntity
import com.pnr.tv.db.entity.PlaybackPositionEntity
import com.pnr.tv.db.entity.SeriesCategoryEntity
import com.pnr.tv.db.entity.SeriesEntity
import com.pnr.tv.di.IptvRetrofit
import com.pnr.tv.network.dto.AuthenticationResponseDto
import com.pnr.tv.network.dto.SeriesInfoDto
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
        @IptvRetrofit private val retrofitBuilder: Retrofit.Builder,
        private val userRepository: UserRepository,
        @ApplicationContext private val context: Context,
    ) {
        // BaseContentRepository'yi composition olarak kullan
        // fetchUserInfo için safeApiCall metoduna ihtiyacımız var
        private val baseRepository = BaseContentRepository(retrofitBuilder, userRepository, context)

        // ==================== Movie Operations ====================

        fun getMovies(): Flow<List<MovieEntity>> = movieRepository.getMovies()

        fun getMoviesByCategoryId(categoryId: String): Flow<List<MovieEntity>> = movieRepository.getMoviesByCategoryId(categoryId)

        suspend fun getRecentlyAddedMovies(limit: Int): List<MovieEntity> = movieRepository.getRecentlyAddedMovies(limit)

        fun getMovieCategories(): Flow<List<MovieCategoryEntity>> = movieRepository.getMovieCategories()

        suspend fun refreshMovies(
            skipTmdbSync: Boolean = false,
            forMainScreenUpdate: Boolean = false,
        ): Result<Unit> = movieRepository.refreshMovies(skipTmdbSync, forMainScreenUpdate)

        suspend fun refreshMovieCategories(): Result<Unit> = movieRepository.refreshMovieCategories()

        suspend fun getMoviesByIds(movieIds: List<Int>): List<MovieEntity> = movieRepository.getMoviesByIds(movieIds)

        // ==================== Series Operations ====================

        fun getSeries(): Flow<List<SeriesEntity>> = seriesRepository.getSeries()

        fun getSeriesByCategoryId(categoryId: String): Flow<List<SeriesEntity>> = seriesRepository.getSeriesByCategoryId(categoryId)

        suspend fun getRecentlyAddedSeries(limit: Int): List<SeriesEntity> = seriesRepository.getRecentlyAddedSeries(limit)

        fun getSeriesCategories(): Flow<List<SeriesCategoryEntity>> = seriesRepository.getSeriesCategories()

        suspend fun refreshSeries(
            skipTmdbSync: Boolean = false,
            forMainScreenUpdate: Boolean = false,
        ): Result<Unit> = seriesRepository.refreshSeries(skipTmdbSync, forMainScreenUpdate)

        suspend fun refreshSeriesCategories(): Result<Unit> = seriesRepository.refreshSeriesCategories()

        suspend fun getSeriesInfo(seriesId: Int): Result<SeriesInfoDto> = seriesRepository.getSeriesInfo(seriesId)

        suspend fun getSeriesByIds(seriesIds: List<Int>): List<SeriesEntity> = seriesRepository.getSeriesByIds(seriesIds)

        // ==================== LiveStream Operations ====================

        fun getLiveStreams(): Flow<List<LiveStreamEntity>> = liveStreamRepository.getLiveStreams()

        fun getLiveStreamsByCategoryId(categoryId: Int): Flow<List<LiveStreamEntity>> =
            liveStreamRepository.getLiveStreamsByCategoryId(
                categoryId,
            )

        suspend fun getLiveStreamsByCategoryIdSync(categoryId: Int): List<LiveStreamEntity> =
            liveStreamRepository.getLiveStreamsByCategoryId(categoryId).firstOrNull() ?: emptyList()

        fun getLiveStreamCategories(): Flow<List<LiveStreamCategoryEntity>> = liveStreamRepository.getLiveStreamCategories()

        suspend fun refreshLiveStreams(forMainScreenUpdate: Boolean = false): Result<Unit> =
            liveStreamRepository.refreshLiveStreams(forMainScreenUpdate)

        suspend fun refreshLiveStreamCategories(): Result<Unit> = liveStreamRepository.refreshLiveStreamCategories()

        suspend fun getLiveStreamsByIds(channelIds: List<Int>): List<LiveStreamEntity> =
            liveStreamRepository.getLiveStreamsByIds(
                channelIds,
            )

        // ==================== Favorite Operations ====================

        suspend fun addFavorite(
            channelId: Int,
            viewerId: Int,
        ) = favoriteRepository.addFavorite(channelId, viewerId)

        suspend fun removeFavorite(
            channelId: Int,
            viewerId: Int,
        ) = favoriteRepository.removeFavorite(channelId, viewerId)

        fun isFavorite(
            channelId: Int,
            viewerId: Int,
        ): Flow<Boolean> = favoriteRepository.isFavorite(channelId, viewerId)

        fun getFavoriteChannelIds(viewerId: Int): Flow<List<Int>> = favoriteRepository.getFavoriteChannelIds(viewerId)

        fun getAllFavoriteChannelIds(): Flow<List<Int>> = favoriteRepository.getAllFavoriteChannelIds()

        fun getViewerIdsWithFavorites(): Flow<List<Int>> = favoriteRepository.getViewerIdsWithFavorites()

        // ==================== Recently Watched Operations ====================

        suspend fun saveRecentlyWatched(channelId: Int) = recentlyWatchedRepository.saveRecentlyWatched(channelId)

        fun getRecentlyWatchedChannelIds(limit: Int = Constants.RECENTLY_WATCHED_DEFAULT_LIMIT): Flow<List<Int>> =
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
