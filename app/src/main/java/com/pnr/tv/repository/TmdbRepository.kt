package com.pnr.tv.repository

import android.content.Context
import com.pnr.tv.core.constants.NetworkConstants
import com.pnr.tv.core.constants.TimeConstants
import com.pnr.tv.db.dao.TmdbCacheDao
import com.pnr.tv.db.entity.TmdbCacheEntity
import com.pnr.tv.network.dto.TmdbMovieDetailsDto
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TMDB API ile film detaylarını getiren repository
 *
 * Cache stratejisi:
 * 1. Önce yerel cache'e bak
 * 2. Cache varsa ve yeterince yeniyse (24 saat) onu kullan
 * 3. Cache yoksa veya eskiyse API'den çek ve cache'le
 *
 * Delta senkronizasyonu:
 * Toplu güncelleme sırasında sadece yeni filmlerin TMDB verilerini çeker
 */
@Singleton
class TmdbRepository
    @Inject
    constructor(
        // Internal kullanım için public
        val tmdbCacheDao: TmdbCacheDao,
        @ApplicationContext private val context: Context,
        private val tmdbSearchStrategy: TmdbSearchStrategy,
        private val tmdbDataMapper: TmdbDataMapper,
        private val tmdbMultilingualFetcher: TmdbMultilingualFetcher,
    ) {
        companion object {
            /**
             * Cache geçerlilik süresi (24 saat = 86400000 milisaniye)
             * Bu süreden eski cache'ler yeniden API'den çekilir
             */
            private const val CACHE_VALIDITY_DURATION = TimeConstants.Intervals.TWENTY_FOUR_HOURS_MS // 24 saat

            /**
             * Batch işlemi sırasında istekler arası bekleme süresi (milisaniye)
             * TMDB API rate limiting için
             */
            private const val BATCH_REQUEST_DELAY = NetworkConstants.TmdbApi.BATCH_REQUEST_DELAY_MS
        }

        /**
         * TMDB ID ile doğrudan film detaylarını getirir
         * Çok dilli fallback zinciri kullanarak en iyi çeviriyi bulur
         *
         * Fallback sırası:
         * 1. Cihaz dili + bölge (örn: pt-BR)
         * 2. Sadece cihaz dili (örn: pt)
         * 3. İngilizce (en-US)
         * 4. Orijinal dil (original_language)
         *
         * @param tmdbId TMDB film ID'si
         * @param forceRefresh True ise cache'i yoksay ve API'den çek
         * @return Film detayları veya null
         */
        suspend fun getMovieDetailsById(
            tmdbId: Int,
            forceRefresh: Boolean = false,
        ): TmdbMovieDetailsDto? {
            return try {
                // 1. Cache'i kontrol et
                if (!forceRefresh) {
                    val cachedData = tmdbCacheDao.getCacheByTmdbId(tmdbId)
                    if (cachedData != null) {
                        val cacheAge = System.currentTimeMillis() - cachedData.cacheTime
                        val isValid = cacheAge < CACHE_VALIDITY_DURATION

                        if (isValid) {
                            return tmdbDataMapper.cacheToDto(cachedData)
                        }
                    }
                }

                // 2. Çok dilli fallback zinciri ile API'den çek
                val details = tmdbMultilingualFetcher.fetchMovieWithFallback(tmdbId)

                if (details != null) {
                    // 3. Cache'e kaydet
                    val director = tmdbDataMapper.getDirectorFromDto(details)
                    val castForCache = tmdbDataMapper.getCastForCache(details)
                    val overview = tmdbDataMapper.getOverviewFromDto(details)

                    val cacheEntity =
                        TmdbCacheEntity(
                            tmdbId = tmdbId,
                            title = details.title,
                            director = director,
                            cast = castForCache,
                            overview = overview,
                            cacheTime = System.currentTimeMillis(),
                        )
                    tmdbCacheDao.insertCache(cacheEntity)
                }

                details
            } catch (e: Exception) {
                Timber.e(e, "TMDB API hatası (ID: $tmdbId): ${e.message}")

                // Hata durumunda cache'den dön
                val cachedData = tmdbCacheDao.getCacheByTmdbId(tmdbId)
                if (cachedData != null) {
                    Timber.w("TMDB: API hatası, eski cache döndürülüyor")
                    return tmdbDataMapper.cacheToDto(cachedData)
                }

                null
            }
        }

        /**
         * Film türlerini formatlı string olarak döndürür
         * @return Virgülle ayrılmış tür listesi (örn: "Action, Sci-Fi, Thriller")
         */
        fun getGenres(movieDetails: TmdbMovieDetailsDto?): String? {
            return tmdbDataMapper.getGenres(movieDetails)
        }

        /**
         * Film adına göre TMDB'de arama yapar ve ilk sonucun detaylarını getirir
         * @param movieTitle Film adı
         * @return Film detayları veya null
         */
        suspend fun getMovieDetailsByTitle(movieTitle: String): TmdbMovieDetailsDto? {
            return tmdbSearchStrategy.searchMovieByTitle(movieTitle)
        }

        /**
         * Film detaylarından yönetmen bilgisini çıkarır
         * Cache'den geliyorsa doğrudan cache'deki değeri kullan
         */
        suspend fun getDirector(
            tmdbId: Int,
            movieDetails: TmdbMovieDetailsDto?,
        ): String? {
            // Önce cache'e bak (çünkü cache'de director ayrı saklanıyor)
            val cache = tmdbCacheDao.getCacheByTmdbId(tmdbId)
            if (cache?.director != null) {
                return cache.director
            }

            // Cache'de yoksa DTO'dan çıkar
            return tmdbDataMapper.getDirectorFromDto(movieDetails)
        }

        /**
         * Film detaylarından oyuncu listesini çıkarır (ilk 5 oyuncu)
         *
         * Cache'den veya DTO'dan ham veri olarak List<String> döner.
         * UI formatlaması (virgülle birleştirme vb.) ViewModel'de yapılır.
         *
         * @return Oyuncu isimleri listesi veya null
         */
        suspend fun getCast(
            tmdbId: Int,
            movieDetails: TmdbMovieDetailsDto?,
        ): List<String>? {
            // Önce cache'e bak
            val cache = tmdbCacheDao.getCacheByTmdbId(tmdbId)
            if (cache?.cast != null) {
                // Cache'den gelen string'i listeye çevir
                return cache.cast.split(", ").filter { it.isNotBlank() }
            }

            // Cache'de yoksa DTO'dan çıkar - HAM VERİ
            val castList =
                movieDetails?.credits?.cast
                    ?.sortedBy { it.order }
                    ?.take(5)
                    ?.mapNotNull { it.name }

            return if (castList.isNullOrEmpty()) null else castList
        }

        /**
         * Film detaylarından açıklamayı çıkarır
         * Cache'den geliyorsa doğrudan cache'deki değeri kullan
         */
        suspend fun getOverview(
            tmdbId: Int,
            movieDetails: TmdbMovieDetailsDto?,
        ): String? {
            // Önce cache'e bak
            val cache = tmdbCacheDao.getCacheByTmdbId(tmdbId)
            if (cache?.overview != null) {
                return cache.overview
            }

            // Cache'de yoksa DTO'dan çıkar
            return tmdbDataMapper.getOverviewFromDto(movieDetails)
        }

        /**
         * TMDB'den gelen poster görsel URL'ini döndürür
         * @return Tam poster URL'i veya null
         */
        fun getPosterUrl(movieDetails: TmdbMovieDetailsDto?): String? {
            return tmdbDataMapper.getPosterUrl(movieDetails)
        }

        /**
         * TMDB'den gelen backdrop görsel URL'ini döndürür
         * @return Tam backdrop URL'i veya null
         */
        fun getBackdropUrl(movieDetails: TmdbMovieDetailsDto?): String? {
            return tmdbDataMapper.getBackdropUrl(movieDetails)
        }

        /**
         * TMDB'den gelen rating'i döndürür
         * @return Rating (0-10 arası) veya null
         */
        fun getRating(movieDetails: TmdbMovieDetailsDto?): Double? {
            return tmdbDataMapper.getRating(movieDetails)
        }

        /**
         * Birden fazla film için TMDB verilerini toplu olarak çeker
         * Delta senkronizasyonu için kullanılır
         *
         * @param movieIdsWithTmdb TMDB ID'si olan film listesi (ID, TMDB ID çifti)
         * @return Başarıyla işlenen film sayısı
         */
        suspend fun batchFetchMovieDetails(movieIdsWithTmdb: List<Pair<Int, Int>>): Int {
            var successCount = 0

            movieIdsWithTmdb.forEachIndexed { index, (streamId, tmdbId) ->
                try {
                    // Cache'de var mı kontrol et
                    val existingCache = tmdbCacheDao.getCacheByTmdbId(tmdbId)

                    if (existingCache == null) {
                        // API'den çek ve cache'e kaydet
                        getMovieDetailsById(tmdbId)
                        successCount++

                        // Rate limiting için bekle
                        if (index < movieIdsWithTmdb.size - 1) {
                            kotlinx.coroutines.delay(NetworkConstants.TmdbApi.BATCH_REQUEST_DELAY_MS)
                        }
                    } else {
                        // TMDB ID zaten cache'de, atlanıyor
                    }
                } catch (e: Exception) {
                    Timber.e(e, "TMDB Batch Film hatası - Stream ID: $streamId, TMDB ID: $tmdbId")
                }
            }

            return successCount
        }
    }
