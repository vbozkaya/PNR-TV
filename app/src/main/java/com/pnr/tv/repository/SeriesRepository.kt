package com.pnr.tv.repository

import android.content.Context
import com.pnr.tv.core.constants.NetworkConstants
import com.pnr.tv.db.dao.SeriesCategoryDao
import com.pnr.tv.db.dao.SeriesDao
import com.pnr.tv.db.entity.SeriesCategoryEntity
import com.pnr.tv.db.entity.SeriesEntity
import com.pnr.tv.di.IptvRetrofit
import com.pnr.tv.network.ApiActions
import com.pnr.tv.network.dto.SeriesInfoDto
import com.pnr.tv.network.dto.toEntity
import com.pnr.tv.util.error.ErrorHelper
import com.pnr.tv.util.error.Resource
import com.pnr.tv.util.validation.AdultContentDetector
import com.pnr.tv.util.validation.DataValidationHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import retrofit2.Retrofit
import timber.log.Timber
import javax.inject.Inject

/**
 * Dizilerle ilgili tüm işlemleri yöneten repository.
 */
class SeriesRepository
    @Inject
    constructor(
        apiServiceManager: ApiServiceManager,
        userRepository: UserRepository,
        private val tmdbTvRepository: TmdbTvRepository,
        private val seriesDao: SeriesDao,
        private val seriesCategoryDao: SeriesCategoryDao,
        @ApplicationContext context: Context,
    ) : BaseContentRepository(
            apiServiceManager,
            userRepository,
            context,
        ) {
        // ==================== Read Operations ====================

        fun getSeries(): Flow<Resource<List<SeriesEntity>>> =
            flow {
                emit(Resource.Loading)
                try {
                    seriesDao.getAll().collect { data ->
                        emit(Resource.Success(data))
                    }
                } catch (e: Exception) {
                    val errorMessage = ErrorHelper.createError(e, context, errorContext = "SeriesRepository.getSeries").message
                    emit(Resource.Error(errorMessage, e))
                }
            }

        fun getSeriesByCategoryId(categoryId: String): Flow<Resource<List<SeriesEntity>>> =
            flow {
                emit(Resource.Loading)
                try {
                    seriesDao.getByCategoryId(categoryId).collect { data ->
                        emit(Resource.Success(data))
                    }
                } catch (e: Exception) {
                    val errorMessage = ErrorHelper.createError(e, context, errorContext = "SeriesRepository.getSeriesByCategoryId").message
                    emit(Resource.Error(errorMessage, e))
                }
            }

        fun getRecentlyAddedSeries(limit: Int): Flow<List<SeriesEntity>> = seriesDao.getRecentlyAdded(limit)

        fun getSeriesCategories(): Flow<Resource<List<SeriesCategoryEntity>>> =
            flow {
                emit(Resource.Loading)
                try {
                    seriesCategoryDao.getAll().collect { data ->
                        emit(Resource.Success(data))
                    }
                } catch (e: Exception) {
                    val errorMessage = ErrorHelper.createError(e, context, errorContext = "SeriesRepository.getSeriesCategories").message
                    emit(Resource.Error(errorMessage, e))
                }
            }

        suspend fun getSeriesByIds(seriesIds: List<Int>): List<SeriesEntity> {
            if (seriesIds.isEmpty()) return emptyList()
            return seriesDao.getByIds(seriesIds)
        }

        /**
         * Kategori ID'ye göre dizi sayılarını döndürür (performans optimizasyonu için).
         * @return Kategori ID -> Sayı mapping'i
         */
        suspend fun getCategoryCounts(): Map<String, Int> {
            return seriesDao.getCategoryCounts().associate { it.categoryId to it.count }
        }

        /**
         * Veritabanında dizi verisi olup olmadığını kontrol eder.
         * @return true ise veri var, false ise veri yok
         */
        suspend fun hasSeries(): Boolean {
            return try {
                // DAO'dan direkt veri kontrolü yap - Flow wrapper'ı kullanmadan
                val data = seriesDao.getAll().firstOrNull()
                data?.isNotEmpty() ?: false
            } catch (e: Exception) {
                timber.log.Timber.e(e, "Dizi veri kontrolü hatası")
                false
            }
        }

        /**
         * Veritabanında dizi kategori verisi olup olmadığını kontrol eder.
         * @return true ise veri var, false ise veri yok
         */
        suspend fun hasSeriesCategories(): Boolean {
            return try {
                val resource = getSeriesCategories().firstOrNull()
                when (resource) {
                    is Resource.Success -> resource.data.isNotEmpty()
                    else -> false
                }
            } catch (e: Exception) {
                false
            }
        }

        /**
         * Veritabanındaki toplam dizi sayısını döndürür.
         * Direkt DAO'dan COUNT(*) sorgusu ile okur (performans için).
         * @return Dizi sayısı
         */
        suspend fun getSeriesCount(): Int {
            return try {
                seriesDao.getCount()
            } catch (e: Exception) {
                Timber.e(e, "Dizi sayısı alınamadı")
                0
            }
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
        ): Result<Unit> {
            // EN BAŞTA kategorileri güncelle (safeApiCall dışında)
            val categoriesResult = refreshSeriesCategories()
            if (categoriesResult is Result.Error) {
                Timber.tag("DB_DEBUG").w("⚠️  Dizi kategorileri güncellemesi başarısız oldu: ${categoriesResult.message}")
            }

            return safeApiCall(
                forMainScreenUpdate = forMainScreenUpdate,
                maxRetries = maxRetries,
                retryDelayMs = NetworkConstants.Network.LONG_RETRY_DELAY_MILLIS,
                apiCall = { api, user, pass ->
                    val seriesDto = api.getSeries(user, pass, ApiActions.GET_SERIES)

                    // Veri doğrulama - eksik field'ları kontrol et
                    val validationReport = DataValidationHelper.validateSeries(seriesDto)
                    validationReport.logReport()

                    val entities = seriesDto.mapNotNull { it.toEntity() }

                    // Kategori adlarına göre yetişkin içerik tespiti
                    val categoryMap =
                        seriesCategoryDao.getAll().firstOrNull()
                            ?.associateBy { it.categoryId } ?: emptyMap()

                    var categoryBasedAdultCount = 0
                    val entitiesWithAdultCheck =
                        entities.map { entity ->
                            // Kategori adını kontrol et (her zaman kontrol et, API'den gelen değeri doğrula)
                            val category = entity.categoryId?.let { categoryMap[it] }
                            val isAdultFromCategory = AdultContentDetector.isAdultCategory(category?.categoryName)

                            // Eğer kategori kontrolü adult içerik değil diyorsa (null dönerse), API'den gelen isAdult=true değerini override et
                            if (isAdultFromCategory == null && entity.isAdult == true) {
                                // Kategori kontrolü adult içerik değil diyor, API'den gelen true değerini null yap
                                entity.copy(isAdult = null)
                            } else if (isAdultFromCategory == true) {
                                // Kategori kontrolü adult içerik diyor
                                categoryBasedAdultCount++
                                entity.copy(isAdult = true)
                            } else {
                                // Kategori kontrolü belirsiz (null) ve API'den gelen değer de null/false, mevcut değeri koru
                                entity
                            }
                        }

                    val finalEntities = entitiesWithAdultCheck

                    // Veritabanına kaydet - AÇIK AÇIK SİL VE EKLE
                    try {
                        // Önce silmeyi dene
                        seriesDao.clearAll()

                        // Sonra ekle
                        if (finalEntities.isNotEmpty()) {
                            seriesDao.insertAll(finalEntities)
                        }
                    } catch (e: Exception) {
                        Timber.tag("DB_DEBUG").e(e, "!!! DİZİ GÜNCELLEME HATASI: Silme veya Ekleme başarısız !!!")
                        // Hatayı yutma, fırlat ki transaction rollback olsun veya üst katman bilsin
                        throw e
                    }

                    if (!skipTmdbSync) {
                        // 1. TMDB ID'si olan dizileri bul
                        val seriesWithTmdbId = entities.filter { it.tmdbId != null }

                        if (seriesWithTmdbId.isNotEmpty()) {
                            // 2. Tüm TMDB ID'lerini topla
                            val allTmdbIds = seriesWithTmdbId.mapNotNull { it.tmdbId }

                            // 3. TEK SORGUDA cache'deki mevcut ID'leri al (N+1 problemi çözüldü!)
                            val existingCaches = tmdbTvRepository.tmdbCacheDao.getCacheByTmdbIds(allTmdbIds)
                            val existingTmdbIds = existingCaches.map { it.tmdbId }.toSet()

                            // 4. Sadece cache'de olmayan dizileri işle
                            val seriesToFetch =
                                seriesWithTmdbId
                                    .filter { it.tmdbId !in existingTmdbIds }
                                    .map { Pair(it.streamId, it.tmdbId!!) }

                            // 5. Sadece yeni diziler için TMDB verilerini çek
                            if (seriesToFetch.isNotEmpty()) {
                                tmdbTvRepository.batchFetchTvShowDetails(seriesToFetch)
                            }
                        }
                    }
                },
            )
        }

        suspend fun refreshSeriesCategories(): Result<Unit> {
            return refreshCategories(
                apiCall = { api, user, pass ->
                    api.getSeriesCategories(user, pass)
                },
                entityMapper = { categoriesDto ->
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
                },
                daoClearAll = { seriesCategoryDao.clearAll() },
                daoInsertAll = { entities -> seriesCategoryDao.insertAll(entities) },
                daoGetAll = { seriesCategoryDao.getAll() },
            )
        }

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
