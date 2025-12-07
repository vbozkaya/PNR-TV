package com.pnr.tv.repository

import com.pnr.tv.Constants
import com.pnr.tv.db.dao.MovieCategoryDao
import com.pnr.tv.db.dao.MovieDao
import com.pnr.tv.db.entity.MovieCategoryEntity
import com.pnr.tv.db.entity.MovieEntity
import com.pnr.tv.di.IptvRetrofit
import com.pnr.tv.network.ApiActions
import com.pnr.tv.network.dto.toEntity
import com.pnr.tv.repository.Result.Success
import com.pnr.tv.repository.UserRepository
import com.pnr.tv.repository.TmdbRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import retrofit2.Retrofit
import timber.log.Timber
import javax.inject.Inject
import android.content.Context

/**
 * Filmlerle ilgili tüm işlemleri yöneten repository.
 */
class MovieRepository
    @Inject
    constructor(
        @IptvRetrofit retrofitBuilder: Retrofit.Builder,
        userRepository: UserRepository,
        private val tmdbRepository: TmdbRepository,
        private val movieDao: MovieDao,
        private val movieCategoryDao: MovieCategoryDao,
        @ApplicationContext context: Context,
    ) : BaseContentRepository(
        retrofitBuilder,
        userRepository,
        context,
    ) {

        // ==================== Read Operations ====================

        fun getMovies(): Flow<List<MovieEntity>> = movieDao.getAll()

        fun getMoviesByCategoryId(categoryId: String): Flow<List<MovieEntity>> = movieDao.getByCategoryId(categoryId)

        suspend fun getRecentlyAddedMovies(limit: Int): List<MovieEntity> = movieDao.getRecentlyAdded(limit)

        fun getMovieCategories(): Flow<List<MovieCategoryEntity>> = movieCategoryDao.getAll()

        suspend fun getMoviesByIds(movieIds: List<Int>): List<MovieEntity> {
            if (movieIds.isEmpty()) return emptyList()
            return movieDao.getByIds(movieIds)
        }

        // ==================== Refresh Operations ====================

        /**
         * Filmleri sadece IPTV'den çeker, TMDB senkronizasyonu yapmaz
         * TMDB senkronizasyonu WorkManager tarafından arka planda yapılır
         * 
         * @param skipTmdbSync True ise TMDB senkronizasyonu atlanır (WorkManager için)
         */
        suspend fun refreshMovies(skipTmdbSync: Boolean = false): Result<Unit> =
            safeApiCall { api, user, pass ->
                Timber.d("═══════════════════════════════════════")
                Timber.d("📥 FİLM VERİLERİ GÜNCELENİYOR...")
                Timber.d("═══════════════════════════════════════")
                
                // API çağrısı yap
                val moviesDto = api.getMovies(user, pass, ApiActions.GET_VOD_STREAMS)
                Timber.d("✅ API'den ${moviesDto.size} film alındı")
                
                // İlk 3 filmin örnek verisini göster
                if (moviesDto.isNotEmpty()) {
                    Timber.d("───────────────────────────────────────")
                    Timber.d("📋 İLK 3 FİLM ÖRNEĞİ (HAM VERİ):")
                    moviesDto.take(3).forEachIndexed { index, movie ->
                        Timber.d("${index + 1}. Film:")
                        Timber.d("   • ID: ${movie.streamId}")
                        Timber.d("   • İsim: ${movie.name}")
                        Timber.d("   • Rating: ${movie.rating}")
                        Timber.d("   • Plot: ${movie.plot?.take(50)}...")
                        Timber.d("   • CategoryId: ${movie.categoryId}")
                        Timber.d("   • Ekleme Tarihi: ${movie.added}")
                        Timber.d("   • Icon URL: ${movie.streamIconUrl?.take(50)}...")
                    }
                    Timber.d("───────────────────────────────────────")
                }
                
                // Entity'ye dönüştür
                val entities = moviesDto.mapNotNull { it.toEntity() }
                Timber.d("🔄 ${entities.size} film entity'ye dönüştürüldü")
                
                // Dönüştürülemeyen filmler varsa uyar
                if (entities.size < moviesDto.size) {
                    val failed = moviesDto.size - entities.size
                    Timber.w("⚠️  ${failed} film entity'ye dönüştürülemedi!")
                }
                
                // Veritabanına kaydet
                movieDao.replaceAll(entities)
                Timber.d("💾 ${entities.size} film veritabanına kaydedildi")
                
                // ═══════════════════════════════════════
                // DELTA SENKRONIZASYONU - Arka planda WorkManager ile yapılacak
                // ═══════════════════════════════════════
                
                if (!skipTmdbSync) {
                    Timber.d("🔄 DELTA SENKRONIZASYONU BAŞLIYOR...")
                    
                    // 1. TMDB ID'si olan filmleri bul
                    val moviesWithTmdbId = entities.filter { it.tmdbId != null }
                    Timber.d("   • TMDB ID'li film sayısı: ${moviesWithTmdbId.size}")
                    
                    if (moviesWithTmdbId.isNotEmpty()) {
                        // 2. Tüm TMDB ID'lerini topla
                        val allTmdbIds = moviesWithTmdbId.mapNotNull { it.tmdbId }
                        
                        // 3. TEK SORGUDA cache'deki mevcut ID'leri al (N+1 problemi çözüldü!)
                        val existingCaches = tmdbRepository.tmdbCacheDao.getCacheByTmdbIds(allTmdbIds)
                        val existingTmdbIds = existingCaches.map { it.tmdbId }.toSet()
                        
                        // 4. Sadece cache'de olmayan filmleri işle
                        val moviesToFetch = moviesWithTmdbId
                            .filter { it.tmdbId !in existingTmdbIds }
                            .map { Pair(it.streamId, it.tmdbId!!) }
                        
                        val newMovieCount = moviesToFetch.size
                        Timber.d("   • Yeni film (cache'de yok): $newMovieCount")
                        Timber.d("   • Mevcut film (cache'de var): ${moviesWithTmdbId.size - newMovieCount}")
                        
                        // 5. Sadece yeni filmler için TMDB verilerini çek
                        if (moviesToFetch.isNotEmpty()) {
                            Timber.d("🎬 $newMovieCount yeni film için TMDB verileri çekiliyor...")
                            val fetchedCount = tmdbRepository.batchFetchMovieDetails(moviesToFetch)
                            Timber.d("✅ $fetchedCount/$newMovieCount film başarıyla işlendi")
                        } else {
                            Timber.d("✅ Yeni film yok, TMDB isteği atılmadı")
                        }
                    } else {
                        Timber.d("⚠️  TMDB ID'li film yok, delta senkronizasyonu atlanıyor")
                    }
                } else {
                    Timber.d("⏭️  TMDB senkronizasyonu atlandı (WorkManager'da yapılacak)")
                }
                
                Timber.d("═══════════════════════════════════════")
                
                // Rating istatistikleri
                val withRating = entities.count { it.rating != null && it.rating > 0 }
                val withPlot = entities.count { !it.plot.isNullOrBlank() }
                val withTmdb = entities.count { it.tmdbId != null }
                Timber.d("📊 İSTATİSTİKLER:")
                Timber.d("   • Rating olan: $withRating / ${entities.size}")
                Timber.d("   • Açıklama olan: $withPlot / ${entities.size}")
                Timber.d("   • TMDB ID olan: $withTmdb / ${entities.size}")
                Timber.d("═══════════════════════════════════════")
            }

        suspend fun refreshMovieCategories(): Result<Unit> =
            safeApiCall { api, user, pass ->
                Timber.d("═══════════════════════════════════════")
                Timber.d("📂 FİLM KATEGORİLERİ GÜNCELENİYOR...")
                Timber.d("═══════════════════════════════════════")
                
                val categoriesDto = api.getMovieCategories(user, pass)
                Timber.d("✅ API'den ${categoriesDto.size} kategori alındı")
                
                // İlk 5 kategoriyi göster
                if (categoriesDto.isNotEmpty()) {
                    Timber.d("───────────────────────────────────────")
                    Timber.d("📋 İLK 5 KATEGORİ ÖRNEĞİ:")
                    categoriesDto.take(5).forEachIndexed { index, cat ->
                        Timber.d("${index + 1}. ${cat.categoryName} (ID: ${cat.categoryId}, Parent: ${cat.parentId})")
                    }
                    Timber.d("───────────────────────────────────────")
                }
                
                val entities =
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
                
                Timber.d("🔄 ${entities.size} kategori entity'ye dönüştürüldü")
                movieCategoryDao.replaceAll(entities)
                Timber.d("💾 ${entities.size} kategori veritabanına kaydedildi")
                Timber.d("═══════════════════════════════════════")
            }
    }

