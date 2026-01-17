package com.pnr.tv.repository

import android.content.Context
import com.pnr.tv.core.constants.TimeConstants
import com.pnr.tv.core.constants.NetworkConstants
import com.pnr.tv.db.dao.TmdbCacheDao
import com.pnr.tv.db.entity.TmdbCacheEntity
import com.pnr.tv.network.dto.TmdbTvShowDetailsDto
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TMDB API ile dizi (TV Show) detaylarını getiren repository
 *
 * Cache stratejisi:
 * 1. Önce yerel cache'e bak
 * 2. Cache varsa ve yeterince yeniyse (24 saat) onu kullan
 * 3. Cache yoksa veya eskiyse API'den çek ve cache'le
 *
 * Delta senkronizasyonu:
 * Toplu güncelleme sırasında sadece yeni dizilerin TMDB verilerini çeker
 */
@Singleton
class TmdbTvRepository
    @Inject
    constructor(
        val tmdbCacheDao: TmdbCacheDao,
        @ApplicationContext private val context: Context,
        private val tmdbTvSearchStrategy: TmdbTvSearchStrategy,
        private val tmdbTvDataMapper: TmdbTvDataMapper,
        private val tmdbTvLanguageFetcher: TmdbTvLanguageFetcher,
    ) {
        companion object {
            private const val CACHE_VALIDITY_DURATION = TimeConstants.Intervals.TWENTY_FOUR_HOURS_MS
        }

        /**
         * TMDB ID ile doğrudan dizi detaylarını getirir
         * Çok dilli fallback zinciri kullanarak en iyi çeviriyi bulur
         *
         * @param tmdbId TMDB dizi ID'si
         * @param forceRefresh True ise cache'i yoksay ve API'den çek
         * @return Dizi detayları veya null
         */
        suspend fun getTvShowDetailsById(
            tmdbId: Int,
            forceRefresh: Boolean = false,
        ): TmdbTvShowDetailsDto? {
            return try {
                if (!forceRefresh) {
                    val cachedData = tmdbCacheDao.getCacheByTmdbId(tmdbId)
                    if (cachedData != null) {
                        val cacheAge = System.currentTimeMillis() - cachedData.cacheTime
                        if (cacheAge < CACHE_VALIDITY_DURATION) {
                            return tmdbTvDataMapper.cacheToDtoTv(cachedData)
                        }
                    }
                }

                val details = tmdbTvLanguageFetcher.fetchTvShowWithFallback(tmdbId)

                if (details != null) {
                    val cacheEntity =
                        TmdbCacheEntity(
                            tmdbId = tmdbId,
                            title = details.name,
                            director = tmdbTvDataMapper.getCreator(details),
                            cast = tmdbTvDataMapper.getCastForCacheTv(details),
                            overview = tmdbTvDataMapper.getOverviewFromTv(details),
                            cacheTime = System.currentTimeMillis(),
                        )
                    tmdbCacheDao.insertCache(cacheEntity)
                }

                details
            } catch (e: Exception) {
                Timber.e(e, "TMDB TV API hatası (ID: $tmdbId): ${e.message}")

                val cachedData = tmdbCacheDao.getCacheByTmdbId(tmdbId)
                if (cachedData != null) {
                    Timber.w("TMDB TV: API hatası, eski cache döndürülüyor")
                    return tmdbTvDataMapper.cacheToDtoTv(cachedData)
                }

                null
            }
        }

        /**
         * Dizi adına göre TMDB'de arama yapar ve ilk sonucun detaylarını getirir
         */
        suspend fun getTvShowDetailsByTitle(seriesTitle: String): TmdbTvShowDetailsDto? {
            return try {
                val tvId = tmdbTvSearchStrategy.searchTvShowByTitle(seriesTitle)
                if (tvId != null) {
                    getTvShowDetailsById(tvId)
                } else {
                    null
                }
            } catch (e: Exception) {
                Timber.e(e, "TMDB TV API hatası: ${e.message}")
                null
            }
        }

        /**
         * Dizi yaratıcısını döndürür (director yerine)
         */
        suspend fun getCreator(
            tmdbId: Int,
            tvDetails: TmdbTvShowDetailsDto?,
        ): String? {
            val cache = tmdbCacheDao.getCacheByTmdbId(tmdbId)
            if (cache?.director != null) {
                return cache.director
            }
            return tmdbTvDataMapper.getCreator(tvDetails)
        }

        /**
         * Dizi oyuncularını döndürür
         */
        suspend fun getCastFromTv(
            tmdbId: Int,
            tvDetails: TmdbTvShowDetailsDto?,
        ): List<String>? {
            val cache = tmdbCacheDao.getCacheByTmdbId(tmdbId)
            if (cache?.cast != null) {
                return cache.cast.split(", ").filter { it.isNotBlank() }
            }
            return tmdbTvDataMapper.getCastFromTv(tvDetails)
        }

        /**
         * Dizi açıklamasını döndürür
         */
        suspend fun getOverviewFromTv(
            tmdbId: Int,
            tvDetails: TmdbTvShowDetailsDto?,
        ): String? {
            val cache = tmdbCacheDao.getCacheByTmdbId(tmdbId)
            if (cache?.overview != null) {
                return cache.overview
            }
            return tmdbTvDataMapper.getOverviewFromTv(tvDetails)
        }

        /**
         * Dizi türlerini formatlı string olarak döndürür
         */
        fun getGenresFromTv(tvDetails: TmdbTvShowDetailsDto?): String? {
            return tmdbTvDataMapper.getGenresFromTv(tvDetails)
        }

        /**
         * TMDB'den gelen dizi poster görsel URL'ini döndürür
         */
        fun getPosterUrlFromTv(tvDetails: TmdbTvShowDetailsDto?): String? {
            return tmdbTvDataMapper.getPosterUrlFromTv(tvDetails)
        }

        /**
         * TMDB'den gelen dizi backdrop görsel URL'ini döndürür
         */
        fun getBackdropUrlFromTv(tvDetails: TmdbTvShowDetailsDto?): String? {
            return tmdbTvDataMapper.getBackdropUrlFromTv(tvDetails)
        }

        /**
         * TMDB'den gelen dizi rating'ini döndürür
         */
        fun getRatingFromTv(tvDetails: TmdbTvShowDetailsDto?): Double? {
            return tmdbTvDataMapper.getRatingFromTv(tvDetails)
        }

        /**
         * Birden fazla dizi için TMDB verilerini toplu olarak çeker
         * Delta senkronizasyonu için kullanılır
         */
        suspend fun batchFetchTvShowDetails(seriesIdsWithTmdb: List<Pair<Int, Int>>): Int {
            var successCount = 0

            seriesIdsWithTmdb.forEachIndexed { index, (streamId, tmdbId) ->
                try {
                    val existingCache = tmdbCacheDao.getCacheByTmdbId(tmdbId)

                    if (existingCache == null) {
                        getTvShowDetailsById(tmdbId)
                        successCount++

                        if (index < seriesIdsWithTmdb.size - 1) {
                            kotlinx.coroutines.delay(NetworkConstants.TmdbApi.BATCH_REQUEST_DELAY_MS)
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "TMDB Batch Dizi hatası - Stream ID: $streamId, TMDB ID: $tmdbId")
                }
            }

            return successCount
        }
    }
