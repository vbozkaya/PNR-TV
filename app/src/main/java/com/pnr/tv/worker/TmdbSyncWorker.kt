package com.pnr.tv.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pnr.tv.core.constants.ContentConstants
import com.pnr.tv.db.dao.MovieCategoryDao
import com.pnr.tv.db.dao.MovieDao
import com.pnr.tv.db.dao.SeriesCategoryDao
import com.pnr.tv.db.dao.SeriesDao
import com.pnr.tv.repository.TmdbRepository
import com.pnr.tv.repository.TmdbTvRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.firstOrNull
import timber.log.Timber

/**
 * TMDB verilerini arka planda akıllı bir şekilde senkronize eden Worker
 *
 * Özellikler:
 * - Kategori sırasına göre öncelikli güncelleme
 * - "Tümü" kategorisini atlama
 * - Delta senkronizasyon (sadece yeni içerikler)
 * - Kullanıcı deneyimini engellemeyen arka plan işlemi
 */
@HiltWorker
class TmdbSyncWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val tmdbRepository: TmdbRepository,
        private val tmdbTvRepository: TmdbTvRepository,
        private val movieDao: MovieDao,
        private val movieCategoryDao: MovieCategoryDao,
        private val seriesDao: SeriesDao,
        private val seriesCategoryDao: SeriesCategoryDao,
    ) : CoroutineWorker(context, params) {
        companion object {
            const val WORK_NAME = "tmdb_sync_work"
            const val INPUT_CONTENT_TYPE = "content_type"
            const val CONTENT_TYPE_MOVIES = "movies"
            const val CONTENT_TYPE_SERIES = "series"
            const val CONTENT_TYPE_ALL = "all"
        }

        override suspend fun doWork(): Result {
            return try {
                val contentType = inputData.getString(INPUT_CONTENT_TYPE) ?: CONTENT_TYPE_ALL

                when (contentType) {
                    CONTENT_TYPE_MOVIES -> syncMovies()
                    CONTENT_TYPE_SERIES -> syncSeries()
                    CONTENT_TYPE_ALL -> {
                        syncMovies()
                        syncSeries()
                    }
                }

                Result.success()
            } catch (e: Exception) {
                Timber.e(e, "❌ TMDB Worker hatası: ${e.message}")

                // Retry edilsin mi kontrolü
                if (runAttemptCount < 3) {
                    Result.retry()
                } else {
                    Result.failure()
                }
            }
        }

        /**
         * Filmleri akıllı bir şekilde senkronize eder
         * Kategori önceliğine göre işler
         */
        private suspend fun syncMovies() {
            // 1. Tüm kategorileri al (sıralı)
            val categories = movieCategoryDao.getAll().firstOrNull() ?: emptyList()

            // 2. "Tümü" ve sanal kategorileri filtrele
            val categoriesToProcess =
                categories.filter { category ->
                    val categoryId = category.categoryId
                    // "0", "-1", "-2" gibi sanal kategorileri atla
                    categoryId != ContentConstants.VirtualCategoryIds.ALL_STRING &&
                        categoryId != ContentConstants.VirtualCategoryIds.FAVORITES_STRING &&
                        categoryId != ContentConstants.VirtualCategoryIds.RECENTLY_ADDED_STRING
                }.sortedBy { it.sortOrder }

            // 3. Her kategoriyi sırayla işle
            categoriesToProcess.forEachIndexed { index, category ->
                try {
                    // Kategorideki filmleri al
                    val moviesInCategory = movieDao.getByCategoryId(category.categoryId).firstOrNull() ?: emptyList()

                    // TMDB ID'si olan ancak cache'de olmayan filmleri bul
                    val moviesToFetch =
                        moviesInCategory
                            .filter { it.tmdbId != null }
                            .mapNotNull { movie ->
                                val tmdbId = movie.tmdbId!!
                                val cached = tmdbRepository.tmdbCacheDao.getCacheByTmdbId(tmdbId)
                                if (cached == null) {
                                    Pair(movie.streamId, tmdbId)
                                } else {
                                    null
                                }
                            }

                    if (moviesToFetch.isNotEmpty()) {
                        val fetchedCount = tmdbRepository.batchFetchMovieDetails(moviesToFetch)
                    } else {
                    }
                } catch (e: Exception) {
                    Timber.e(e, "   ❌ Kategori hatası: ${category.categoryName}")
                }
            }
        }

        /**
         * Dizileri akıllı bir şekilde senkronize eder
         * Kategori önceliğine göre işler
         */
        private suspend fun syncSeries() {
            // 1. Tüm kategorileri al (sıralı)
            val categories = seriesCategoryDao.getAll().firstOrNull() ?: emptyList()

            // 2. "Tümü" ve sanal kategorileri filtrele
            val categoriesToProcess =
                categories.filter { category ->
                    val categoryId = category.categoryId
                    // Sanal kategorileri atla
                    categoryId != ContentConstants.VirtualCategoryIds.ALL_STRING &&
                        categoryId != ContentConstants.VirtualCategoryIds.FAVORITES_STRING &&
                        categoryId != ContentConstants.VirtualCategoryIds.RECENTLY_ADDED_STRING
                }.sortedBy { it.sortOrder }

            // 3. Her kategoriyi sırayla işle
            categoriesToProcess.forEachIndexed { index, category ->
                try {
                    // Kategorideki dizileri al
                    val seriesInCategory = seriesDao.getByCategoryId(category.categoryId).firstOrNull() ?: emptyList()

                    // TMDB ID'si olan ancak cache'de olmayan dizileri bul
                    val seriesToFetch =
                        seriesInCategory
                            .filter { it.tmdbId != null }
                            .mapNotNull { series ->
                                val tmdbId = series.tmdbId!!
                                val cached = tmdbTvRepository.tmdbCacheDao.getCacheByTmdbId(tmdbId)
                                if (cached == null) {
                                    Pair(series.streamId, tmdbId)
                                } else {
                                    null
                                }
                            }

                    if (seriesToFetch.isNotEmpty()) {
                        val fetchedCount = tmdbTvRepository.batchFetchTvShowDetails(seriesToFetch)
                    } else {
                    }
                } catch (e: Exception) {
                    Timber.e(e, "   ❌ Kategori hatası: ${category.categoryName}")
                }
            }
        }
    }
