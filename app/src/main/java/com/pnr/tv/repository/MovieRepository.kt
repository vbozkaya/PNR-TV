package com.pnr.tv.repository

import android.content.Context
import com.pnr.tv.db.dao.MovieCategoryDao
import com.pnr.tv.db.dao.MovieDao
import com.pnr.tv.db.entity.MovieCategoryEntity
import com.pnr.tv.db.entity.MovieEntity
import com.pnr.tv.di.IptvRetrofit
import com.pnr.tv.network.ApiActions
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
 * Filmlerle ilgili tüm işlemleri yöneten repository.
 */
class MovieRepository
    @Inject
    constructor(
        apiServiceManager: ApiServiceManager,
        userRepository: UserRepository,
        private val tmdbRepository: TmdbRepository,
        private val movieDao: MovieDao,
        private val movieCategoryDao: MovieCategoryDao,
        @ApplicationContext context: Context,
    ) : BaseContentRepository(
            apiServiceManager,
            userRepository,
            context,
        ) {
        // ==================== Read Operations ====================

        fun getMovies(): Flow<Resource<List<MovieEntity>>> =
            flow {
                emit(Resource.Loading)
                try {
                    movieDao.getAll().collect { data ->
                        emit(Resource.Success(data))
                    }
                } catch (e: Exception) {
                    val errorMessage = ErrorHelper.createError(e, context, errorContext = "MovieRepository.getMovies").message
                    emit(Resource.Error(errorMessage, e))
                }
            }

        fun getMoviesByCategoryId(categoryId: String): Flow<Resource<List<MovieEntity>>> =
            flow {
                emit(Resource.Loading)
                try {
                    movieDao.getByCategoryId(categoryId).collect { data ->
                        emit(Resource.Success(data))
                    }
                } catch (e: Exception) {
                    val errorMessage = ErrorHelper.createError(e, context, errorContext = "MovieRepository.getMoviesByCategoryId").message
                    emit(Resource.Error(errorMessage, e))
                }
            }

        fun getRecentlyAddedMovies(limit: Int): Flow<List<MovieEntity>> = movieDao.getRecentlyAdded(limit)

        fun getMovieCategories(): Flow<Resource<List<MovieCategoryEntity>>> =
            flow {
                emit(Resource.Loading)
                try {
                    movieCategoryDao.getAll().collect { data ->
                        emit(Resource.Success(data))
                    }
                } catch (e: Exception) {
                    val errorMessage = ErrorHelper.createError(e, context, errorContext = "MovieRepository.getMovieCategories").message
                    emit(Resource.Error(errorMessage, e))
                }
            }

        suspend fun getMoviesByIds(movieIds: List<Int>): List<MovieEntity> {
            if (movieIds.isEmpty()) return emptyList()
            return movieDao.getByIds(movieIds)
        }

        /**
         * Kategori ID'ye göre film sayılarını döndürür (performans optimizasyonu için).
         * @return Kategori ID -> Sayı mapping'i
         */
        suspend fun getCategoryCounts(): Map<String, Int> {
            return movieDao.getCategoryCounts().associate { it.categoryId to it.count }
        }

        /**
         * Veritabanında film verisi olup olmadığını kontrol eder.
         * @return true ise veri var, false ise veri yok
         */
        suspend fun hasMovies(): Boolean {
            return try {
                // DAO'dan direkt veri kontrolü yap - Flow wrapper'ı kullanmadan
                val data = movieDao.getAll().firstOrNull()
                data?.isNotEmpty() ?: false
            } catch (e: Exception) {
                timber.log.Timber.e(e, "Film veri kontrolü hatası")
                false
            }
        }

        /**
         * Veritabanında film kategori verisi olup olmadığını kontrol eder.
         * @return true ise veri var, false ise veri yok
         */
        suspend fun hasMovieCategories(): Boolean {
            return try {
                val resource = getMovieCategories().firstOrNull()
                when (resource) {
                    is Resource.Success -> resource.data.isNotEmpty()
                    else -> false
                }
            } catch (e: Exception) {
                false
            }
        }

        /**
         * Veritabanındaki toplam film sayısını döndürür.
         * Direkt DAO'dan COUNT(*) sorgusu ile okur (performans için).
         * @return Film sayısı
         */
        suspend fun getMoviesCount(): Int {
            return try {
                movieDao.getCount()
            } catch (e: Exception) {
                Timber.e(e, "Film sayısı alınamadı")
                0
            }
        }

        // ==================== Refresh Operations ====================

        /**
         * Filmleri sadece IPTV'den çeker, TMDB senkronizasyonu yapmaz
         * TMDB senkronizasyonu WorkManager tarafından arka planda yapılır
         *
         * @param skipTmdbSync True ise TMDB senkronizasyonu atlanır (WorkManager için)
         * @param forMainScreenUpdate Ana ekran güncelleme için mi? (true ise özel hata mesajları kullanılır)
         */
        suspend fun refreshMovies(
            skipTmdbSync: Boolean = false,
            forMainScreenUpdate: Boolean = false,
            maxRetries: Int = 2,
        ): Result<Unit> {
            // EN BAŞTA kategorileri güncelle (safeApiCall dışında)
            val categoriesResult = refreshMovieCategories()
            if (categoriesResult is Result.Error) {
                Timber.tag("DB_DEBUG").w("⚠️  Film kategorileri güncellemesi başarısız oldu: ${categoriesResult.message}")
            }

            return safeApiCall(
                forMainScreenUpdate = forMainScreenUpdate,
                maxRetries = maxRetries,
                retryDelayMs = 2000L,
                apiCall = { api, user, pass ->
                    // API çağrısı yap
                    val moviesDto = api.getMovies(user, pass, ApiActions.GET_VOD_STREAMS)

                    // Veri doğrulama - eksik field'ları kontrol et
                    val validationReport = DataValidationHelper.validateMovies(moviesDto)
                    validationReport.logReport()

                    // Entity'ye dönüştür
                    val entities = moviesDto.mapNotNull { it.toEntity() }

                    // Kategori adlarına göre yetişkin içerik tespiti
                    val categoryMap =
                        movieCategoryDao.getAll().firstOrNull()
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

                    // Dönüştürülemeyen filmler varsa uyar
                    if (finalEntities.size < moviesDto.size) {
                        val failed = moviesDto.size - finalEntities.size
                        Timber.w("⚠️  $failed film entity'ye dönüştürülemedi!")
                    }

                    // Veritabanına kaydet - AÇIK AÇIK SİL VE EKLE
                    try {
                        // Önce silmeyi dene
                        movieDao.clearAll()

                        // Sonra ekle
                        if (finalEntities.isNotEmpty()) {
                            movieDao.insertAll(finalEntities)
                        }
                    } catch (e: Exception) {
                        Timber.tag("DB_DEBUG").e(e, "!!! FİLM GÜNCELLEME HATASI: Silme veya Ekleme başarısız !!!")
                        // Hatayı yutma, fırlat ki transaction rollback olsun veya üst katman bilsin
                        throw e
                    }

                    if (!skipTmdbSync) {
                        // 1. TMDB ID'si olan filmleri bul
                        val moviesWithTmdbId = finalEntities.filter { it.tmdbId != null }

                        if (moviesWithTmdbId.isNotEmpty()) {
                            // 2. Tüm TMDB ID'lerini topla
                            val allTmdbIds = moviesWithTmdbId.mapNotNull { it.tmdbId }

                            // 3. TEK SORGUDA cache'deki mevcut ID'leri al (N+1 problemi çözüldü!)
                            val existingCaches = tmdbRepository.tmdbCacheDao.getCacheByTmdbIds(allTmdbIds)
                            val existingTmdbIds = existingCaches.map { it.tmdbId }.toSet()

                            // 4. Sadece cache'de olmayan filmleri işle
                            val moviesToFetch =
                                moviesWithTmdbId
                                    .filter { it.tmdbId !in existingTmdbIds }
                                    .map { Pair(it.streamId, it.tmdbId!!) }

                            // 5. Sadece yeni filmler için TMDB verilerini çek
                            if (moviesToFetch.isNotEmpty()) {
                                tmdbRepository.batchFetchMovieDetails(moviesToFetch)
                            }
                        }
                    }
                },
            )
        }

        suspend fun refreshMovieCategories(): Result<Unit> {
            return refreshCategories(
                apiCall = { api, user, pass ->
                    api.getMovieCategories(user, pass)
                },
                entityMapper = { categoriesDto ->
                    categoriesDto.mapIndexedNotNull { index, dto ->
                        val categoryIdString = dto.getCategoryIdAsString()
                        if (categoryIdString == null) {
                            Timber.w("⚠️  Kategori için ID null: ${dto.categoryName}")
                        }
                        categoryIdString?.let {
                            MovieCategoryEntity(
                                categoryId = it,
                                categoryName = dto.categoryName,
                                parentId = dto.parentId ?: 0,
                                sortOrder = index,
                            )
                        }
                    }
                },
                daoClearAll = { movieCategoryDao.clearAll() },
                daoInsertAll = { entities -> movieCategoryDao.insertAll(entities) },
                daoGetAll = { movieCategoryDao.getAll() },
            )
        }
    }
