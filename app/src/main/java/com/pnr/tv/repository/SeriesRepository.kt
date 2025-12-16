package com.pnr.tv.repository

import android.content.Context
import com.pnr.tv.db.dao.SeriesCategoryDao
import com.pnr.tv.db.dao.SeriesDao
import com.pnr.tv.db.entity.SeriesCategoryEntity
import com.pnr.tv.db.entity.SeriesEntity
import com.pnr.tv.di.IptvRetrofit
import com.pnr.tv.network.ApiActions
import com.pnr.tv.network.dto.SeriesInfoDto
import com.pnr.tv.network.dto.toEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import retrofit2.Retrofit
import timber.log.Timber
import javax.inject.Inject

/**
 * Dizilerle ilgili tüm işlemleri yöneten repository.
 */
class SeriesRepository
    @Inject
    constructor(
        @IptvRetrofit retrofitBuilder: Retrofit.Builder,
        userRepository: UserRepository,
        private val tmdbRepository: TmdbRepository,
        private val seriesDao: SeriesDao,
        private val seriesCategoryDao: SeriesCategoryDao,
        @ApplicationContext context: Context,
    ) : BaseContentRepository(
            retrofitBuilder,
            userRepository,
            context,
        ) {
        // ==================== Read Operations ====================

        fun getSeries(): Flow<List<SeriesEntity>> = seriesDao.getAll()

        fun getSeriesByCategoryId(categoryId: String): Flow<List<SeriesEntity>> = seriesDao.getByCategoryId(categoryId)

        suspend fun getRecentlyAddedSeries(limit: Int): List<SeriesEntity> = seriesDao.getRecentlyAdded(limit)

        fun getSeriesCategories(): Flow<List<SeriesCategoryEntity>> = seriesCategoryDao.getAll()

        suspend fun getSeriesByIds(seriesIds: List<Int>): List<SeriesEntity> {
            if (seriesIds.isEmpty()) return emptyList()
            return seriesDao.getByIds(seriesIds)
        }

        // ==================== Refresh Operations ====================

        /**
         * Dizileri sadece IPTV'den çeker, TMDB senkronizasyonu yapmaz
         * TMDB senkronizasyonu WorkManager tarafından arka planda yapılır
         *
         * @param skipTmdbSync True ise TMDB senkronizasyonu atlanır (WorkManager için)
         * @param forMainScreenUpdate Ana ekran güncelleme için mi? (true ise özel hata mesajları kullanılır)
         */
        suspend fun refreshSeries(
            skipTmdbSync: Boolean = false,
            forMainScreenUpdate: Boolean = false,
            maxRetries: Int = 2,
        ): Result<Unit> =
            safeApiCall(
                forMainScreenUpdate = forMainScreenUpdate,
                maxRetries = maxRetries,
                retryDelayMs = 2000L,
                apiCall = { api, user, pass ->
                    Timber.d("═══════════════════════════════════════")
                    Timber.d("📺 DİZİ VERİLERİ GÜNCELENİYOR...")
                    Timber.d("═══════════════════════════════════════")

                    val seriesDto = api.getSeries(user, pass, ApiActions.GET_SERIES)
                    Timber.d("✅ API'den ${seriesDto.size} dizi alındı")

                    // Veri doğrulama - eksik field'ları kontrol et
                    val validationReport = com.pnr.tv.util.DataValidationHelper.validateSeries(seriesDto)
                    validationReport.logReport()

                    // İlk 3 dizinin örnek verisini göster
                    if (seriesDto.isNotEmpty()) {
                        Timber.d("───────────────────────────────────────")
                        Timber.d("📋 İLK 3 DİZİ ÖRNEĞİ:")
                        seriesDto.take(3).forEachIndexed { index, series ->
                            Timber.d("${index + 1}. Dizi:")
                            Timber.d("   • ID: ${series.seriesId}")
                            Timber.d("   • İsim: ${series.name}")
                            Timber.d("   • Rating: ${series.rating}")
                            Timber.d("   • Plot: ${series.plot?.take(50)}...")
                            Timber.d("   • CategoryId: ${series.categoryId}")
                        }
                        Timber.d("───────────────────────────────────────")
                    }

                    val entities = seriesDto.mapNotNull { it.toEntity() }
                    Timber.d("🔄 ${entities.size} dizi entity'ye dönüştürüldü")

                    seriesDao.replaceAll(entities)
                    Timber.d("💾 ${entities.size} dizi veritabanına kaydedildi")

                    // ═══════════════════════════════════════
                    // DELTA SENKRONIZASYONU - Arka planda WorkManager ile yapılacak
                    // ═══════════════════════════════════════

                    if (!skipTmdbSync) {
                        Timber.d("🔄 DELTA SENKRONIZASYONU (DİZİ) BAŞLIYOR...")

                        // 1. TMDB ID'si olan dizileri bul
                        val seriesWithTmdbId = entities.filter { it.tmdbId != null }
                        Timber.d("   • TMDB ID'li dizi sayısı: ${seriesWithTmdbId.size}")

                        if (seriesWithTmdbId.isNotEmpty()) {
                            // 2. Tüm TMDB ID'lerini topla
                            val allTmdbIds = seriesWithTmdbId.mapNotNull { it.tmdbId }

                            // 3. TEK SORGUDA cache'deki mevcut ID'leri al (N+1 problemi çözüldü!)
                            val existingCaches = tmdbRepository.tmdbCacheDao.getCacheByTmdbIds(allTmdbIds)
                            val existingTmdbIds = existingCaches.map { it.tmdbId }.toSet()

                            // 4. Sadece cache'de olmayan dizileri işle
                            val seriesToFetch =
                                seriesWithTmdbId
                                    .filter { it.tmdbId !in existingTmdbIds }
                                    .map { Pair(it.streamId, it.tmdbId!!) }

                            val newSeriesCount = seriesToFetch.size
                            Timber.d("   • Yeni dizi (cache'de yok): $newSeriesCount")
                            Timber.d("   • Mevcut dizi (cache'de var): ${seriesWithTmdbId.size - newSeriesCount}")

                            // 5. Sadece yeni diziler için TMDB verilerini çek
                            if (seriesToFetch.isNotEmpty()) {
                                Timber.d("📺 $newSeriesCount yeni dizi için TMDB verileri çekiliyor...")
                                val fetchedCount = tmdbRepository.batchFetchTvShowDetails(seriesToFetch)
                                Timber.d("✅ $fetchedCount/$newSeriesCount dizi başarıyla işlendi")
                            } else {
                                Timber.d("✅ Yeni dizi yok, TMDB isteği atılmadı")
                            }
                        } else {
                            Timber.d("⚠️  TMDB ID'li dizi yok, delta senkronizasyonu atlanıyor")
                        }

                        Timber.d("═══════════════════════════════════════")
                    } else {
                        Timber.d("⏭️  TMDB senkronizasyonu atlandı (WorkManager'da yapılacak)")
                    }

                    val withRating = entities.count { it.rating != null && it.rating > 0 }
                    val withPlot = entities.count { !it.plot.isNullOrBlank() }
                    val withTmdb = entities.count { it.tmdbId != null }
                    Timber.d("📊 İSTATİSTİKLER:")
                    Timber.d("   • Rating olan: $withRating / ${entities.size}")
                    Timber.d("   • Açıklama olan: $withPlot / ${entities.size}")
                    Timber.d("   • TMDB ID olan: $withTmdb / ${entities.size}")
                    Timber.d("═══════════════════════════════════════")
                },
            )

        suspend fun refreshSeriesCategories(): Result<Unit> =
            safeApiCall(
                apiCall = { api, user, pass ->
                    Timber.d("═══════════════════════════════════════")
                    Timber.d("📂 DİZİ KATEGORİLERİ GÜNCELENİYOR...")
                    Timber.d("═══════════════════════════════════════")

                    val categoriesDto = api.getSeriesCategories(user, pass)
                    Timber.d("✅ API'den ${categoriesDto.size} kategori alındı")

                    if (categoriesDto.isNotEmpty()) {
                        Timber.d("───────────────────────────────────────")
                        Timber.d("📋 İLK 5 KATEGORİ:")
                        categoriesDto.take(5).forEachIndexed { index, cat ->
                            Timber.d("${index + 1}. ${cat.categoryName} (ID: ${cat.categoryId})")
                        }
                        Timber.d("───────────────────────────────────────")
                    }

                    val entities =
                        categoriesDto.mapIndexedNotNull { index, dto ->
                            dto.categoryId?.let {
                                SeriesCategoryEntity(
                                    categoryId = it,
                                    categoryName = dto.categoryName,
                                    parentId = dto.parentId ?: 0,
                                    sortOrder = index,
                                )
                            }
                        }

                    Timber.d("💾 ${entities.size} kategori veritabanına kaydedildi")
                    seriesCategoryDao.replaceAll(entities)
                    Timber.d("═══════════════════════════════════════")
                },
            )

        /**
         * Belirli bir dizi için detaylı bilgi (sezonlar ve bölümler) getirir.
         */
        suspend fun getSeriesInfo(seriesId: Int): Result<SeriesInfoDto> =
            safeApiCall(
                apiCall = { api, user, pass ->
                    api.getSeriesInfo(user, pass, seriesId = seriesId)
                },
            )
    }
