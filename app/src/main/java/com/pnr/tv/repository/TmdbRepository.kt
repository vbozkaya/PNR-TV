package com.pnr.tv.repository

import android.content.Context
import com.pnr.tv.Constants
import com.pnr.tv.db.dao.TmdbCacheDao
import com.pnr.tv.db.entity.TmdbCacheEntity
import com.pnr.tv.network.TmdbApiService
import com.pnr.tv.network.dto.TmdbMovieDetailsDto
import com.pnr.tv.network.dto.TmdbTvShowDetailsDto
import com.pnr.tv.util.LocaleHelper
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
        private val tmdbApiService: TmdbApiService,
        val tmdbCacheDao: TmdbCacheDao, // Internal kullanım için public
        @ApplicationContext private val context: Context,
    ) {
    companion object {
        /**
         * Cache geçerlilik süresi (24 saat = 86400000 milisaniye)
         * Bu süreden eski cache'ler yeniden API'den çekilir
         */
        private const val CACHE_VALIDITY_DURATION = 24 * 60 * 60 * 1000L // 24 saat

        /**
         * Batch işlemi sırasında istekler arası bekleme süresi (milisaniye)
         * TMDB API rate limiting için
         */
        private const val BATCH_REQUEST_DELAY = 250L // 250ms = saniyede 4 istek
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
    suspend fun getMovieDetailsById(tmdbId: Int, forceRefresh: Boolean = false): TmdbMovieDetailsDto? {
        return try {
            Timber.d("TMDB: Film detayları isteniyor - ID: $tmdbId")
            
            // 1. Cache'i kontrol et
            if (!forceRefresh) {
                val cachedData = tmdbCacheDao.getCacheByTmdbId(tmdbId)
                if (cachedData != null) {
                    val cacheAge = System.currentTimeMillis() - cachedData.cacheTime
                    val isValid = cacheAge < CACHE_VALIDITY_DURATION
                    
                    if (isValid) {
                        Timber.d("TMDB: Cache'den döndürülüyor (${cacheAge / 60000} dk eski)")
                        return cacheToDto(cachedData)
                    }
                }
            }
            
            // 2. Çok dilli fallback zinciri ile API'den çek
            val details = fetchMovieWithFallback(tmdbId)
            
            if (details != null) {
                // 3. Cache'e kaydet
                val director = getDirector(details)
                val castForCache = getCastForCache(details)
                val overview = getOverview(details)
                
                val cacheEntity = TmdbCacheEntity(
                    tmdbId = tmdbId,
                    title = details.title,
                    director = director,
                    cast = castForCache,
                    overview = overview,
                    cacheTime = System.currentTimeMillis()
                )
                tmdbCacheDao.insertCache(cacheEntity)
                Timber.d("TMDB: Cache'e kaydedildi")
            }
            
            details
        } catch (e: Exception) {
            Timber.e(e, "TMDB API hatası (ID: $tmdbId): ${e.message}")
            
            // Hata durumunda cache'den dön
            val cachedData = tmdbCacheDao.getCacheByTmdbId(tmdbId)
            if (cachedData != null) {
                Timber.w("TMDB: API hatası, eski cache döndürülüyor")
                return cacheToDto(cachedData)
            }
            
            null
        }
    }

    /**
     * Çok dilli fallback zinciri ile film detaylarını çeker
     * Her adımda eksik alanları doldurur
     */
    private suspend fun fetchMovieWithFallback(tmdbId: Int): TmdbMovieDetailsDto? {
        Timber.d("TMDB Fallback: Film detayları çekiliyor - ID: $tmdbId")
        
        // Dil zincirini hazırla
        val languageChain = LocaleHelper.getLanguageFallbackChain(context)
        Timber.d("TMDB Fallback: Dil zinciri: $languageChain")
        
        var mergedDetails: TmdbMovieDetailsDto? = null
        var originalLanguage: String? = null
        
        // Fallback zincirini sırayla dene
        for ((index, language) in languageChain.withIndex()) {
            try {
                Timber.d("TMDB Fallback [${index + 1}/${languageChain.size}]: Dil: $language")
                
                val details = tmdbApiService.getMovieDetails(
                    movieId = tmdbId,
                    apiKey = Constants.Tmdb.API_KEY,
                    language = language
                )
                
                // İlk istekten original_language'i al
                if (originalLanguage == null) {
                    originalLanguage = details.originalLanguage
                }
                
                // Verileri birleştir (merge)
                mergedDetails = mergeMovieDetails(mergedDetails, details)
                
                val hasOverview = !mergedDetails.overview.isNullOrBlank()
                Timber.d("TMDB Fallback: Overview: ${if (hasOverview) "Var ✅" else "Yok ❌"}")
                
                // Overview varsa zinciri kır
                if (hasOverview) {
                    Timber.d("TMDB Fallback: Tamamlandı ($language dilinde)")
                    break
                }
            } catch (e: Exception) {
                Timber.w("TMDB Fallback: $language dili başarısız - ${e.message}")
            }
        }
        
        // Son çare: Orijinal dil
        if (mergedDetails?.overview.isNullOrBlank() && originalLanguage != null && !languageChain.contains(originalLanguage)) {
            try {
                Timber.d("TMDB Fallback [Son Çare]: Orijinal dil: $originalLanguage")
                
                val details = tmdbApiService.getMovieDetails(
                    movieId = tmdbId,
                    apiKey = Constants.Tmdb.API_KEY,
                    language = originalLanguage
                )
                
                mergedDetails = mergeMovieDetails(mergedDetails, details)
                Timber.d("TMDB Fallback: Orijinal dilden alındı")
            } catch (e: Exception) {
                Timber.w("TMDB Fallback: Orijinal dil başarısız")
            }
        }
        
        return mergedDetails
    }

    /**
     * İki film detay DTO'sunu birleştirir
     * İlk DTO'daki boş alanları ikinci DTO'dan doldurur
     */
    private fun mergeMovieDetails(
        primary: TmdbMovieDetailsDto?,
        fallback: TmdbMovieDetailsDto
    ): TmdbMovieDetailsDto {
        if (primary == null) return fallback
        
        return TmdbMovieDetailsDto(
            id = primary.id ?: fallback.id,
            title = primary.title ?: fallback.title,
            overview = primary.overview.takeIf { !it.isNullOrBlank() } ?: fallback.overview,
            originalLanguage = primary.originalLanguage ?: fallback.originalLanguage,
            genres = primary.genres ?: fallback.genres,
            credits = primary.credits ?: fallback.credits
        )
    }

    /**
     * Film türlerini formatlı string olarak döndürür
     * @return Virgülle ayrılmış tür listesi (örn: "Action, Sci-Fi, Thriller")
     */
    fun getGenres(movieDetails: TmdbMovieDetailsDto?): String? {
        val genres = movieDetails?.genres
            ?.mapNotNull { it.name }
            ?.joinToString(", ")
        return if (genres.isNullOrBlank()) null else genres
    }

    /**
     * Cache entity'sini DTO'ya çevirir
     */
    private fun cacheToDto(cache: TmdbCacheEntity): TmdbMovieDetailsDto {
        return TmdbMovieDetailsDto(
            id = cache.tmdbId,
            title = cache.title,
            overview = cache.overview,
            originalLanguage = null, // Cache'de saklanmıyor
            genres = null, // Cache'de saklanmıyor
            credits = null
        )
    }
    /**
     * Film adını TMDB araması için temizler
     * - Parantez içindeki yıl bilgisini çıkarır: "Film Adı (2025)" -> "Film Adı"
     * - Türkçe karakterleri İngilizce karşılıklarına çevirir
     * - Gereksiz boşlukları temizler
     */
    private fun cleanMovieTitle(title: String): String {
        return title
            // Parantez içindeki her şeyi çıkar (genellikle yıl bilgisi)
            .replace(Regex("\\s*\\([^)]*\\)\\s*"), " ")
            // Türkçe karakterleri İngilizce'ye çevir (bazı durumlarda yardımcı olabilir)
            .replace("ı", "i")
            .replace("İ", "I")
            .replace("ğ", "g")
            .replace("Ğ", "G")
            .replace("ü", "u")
            .replace("Ü", "U")
            .replace("ş", "s")
            .replace("Ş", "S")
            .replace("ö", "o")
            .replace("Ö", "O")
            .replace("ç", "c")
            .replace("Ç", "C")
            // Birden fazla boşluğu tek boşluğa indir
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Film adının ana kısmını alır (iki nokta üst üsteden önceki kısım)
     * Örnek: "Predator: Vahşi Topraklar" -> "Predator"
     */
    private fun getMainTitle(title: String): String {
        return title.split(":").firstOrNull()?.trim() ?: title
    }

    /**
     * Film adından yıl bilgisini çıkarır
     */
    private fun extractYear(title: String): Int? {
        val yearRegex = Regex("\\((\\d{4})\\)")
        return yearRegex.find(title)?.groupValues?.get(1)?.toIntOrNull()
    }

    /**
     * Film adına göre TMDB'de arama yapar ve ilk sonucun detaylarını getirir
     * @param movieTitle Film adı
     * @return Film detayları veya null
     */
    suspend fun getMovieDetailsByTitle(movieTitle: String): TmdbMovieDetailsDto? {
        return try {
            Timber.d("TMDB: Orijinal film adı: $movieTitle")
            
            // Film adını temizle
            val cleanedTitle = cleanMovieTitle(movieTitle)
            val year = extractYear(movieTitle)
            
            Timber.d("TMDB: Temizlenmiş film adı: $cleanedTitle${if (year != null) " (Yıl: $year)" else ""}")
            
            // Strateji 1: Türkçe dil + Türkiye region ile tam temizlenmiş başlık
            var searchResult = tmdbApiService.searchMovie(
                apiKey = Constants.Tmdb.API_KEY,
                query = cleanedTitle,
                language = "tr-TR",
                region = "TR",
                year = year
            )

            Timber.d("TMDB: [TR+Region] Tam başlık arama sonucu: ${searchResult.results?.size ?: 0}")
            
            // Strateji 2: Türkçe dil, region yok (daha geniş arama)
            if (searchResult.results.isNullOrEmpty()) {
                Timber.d("TMDB: [TR-NoRegion] Tam başlık ile deneniyor: $cleanedTitle")
                
                searchResult = tmdbApiService.searchMovie(
                    apiKey = Constants.Tmdb.API_KEY,
                    query = cleanedTitle,
                    language = "tr-TR",
                    region = null,
                    year = year
                )
                
                Timber.d("TMDB: [TR-NoRegion] Tam başlık arama sonucu: ${searchResult.results?.size ?: 0}")
            }
            
            // Strateji 3: İngilizce dil ile tam başlık (Türkçe film isimleri için)
            if (searchResult.results.isNullOrEmpty()) {
                Timber.d("TMDB: [EN] Tam başlık ile deneniyor: $cleanedTitle")
                
                searchResult = tmdbApiService.searchMovie(
                    apiKey = Constants.Tmdb.API_KEY,
                    query = cleanedTitle,
                    language = "en-US",
                    region = null,
                    year = year
                )
                
                Timber.d("TMDB: [EN] Tam başlık arama sonucu: ${searchResult.results?.size ?: 0}")
            }
            
            // Strateji 4: Ana başlık (iki nokta öncesi) - Türkçe + Region
            if (searchResult.results.isNullOrEmpty() && cleanedTitle.contains(":")) {
                val mainTitle = getMainTitle(cleanedTitle)
                Timber.d("TMDB: [TR+Region] Ana başlık ile deneniyor: $mainTitle")
                
                searchResult = tmdbApiService.searchMovie(
                    apiKey = Constants.Tmdb.API_KEY,
                    query = mainTitle,
                    language = "tr-TR",
                    region = "TR",
                    year = year
                )
                
                Timber.d("TMDB: [TR+Region] Ana başlık arama sonucu: ${searchResult.results?.size ?: 0}")
            }
            
            // Strateji 5: Ana başlık - Türkçe, region yok
            if (searchResult.results.isNullOrEmpty() && cleanedTitle.contains(":")) {
                val mainTitle = getMainTitle(cleanedTitle)
                Timber.d("TMDB: [TR-NoRegion] Ana başlık ile deneniyor: $mainTitle")
                
                searchResult = tmdbApiService.searchMovie(
                    apiKey = Constants.Tmdb.API_KEY,
                    query = mainTitle,
                    language = "tr-TR",
                    region = null,
                    year = year
                )
                
                Timber.d("TMDB: [TR-NoRegion] Ana başlık arama sonucu: ${searchResult.results?.size ?: 0}")
            }
            
            // Strateji 6: Ana başlık - İngilizce
            if (searchResult.results.isNullOrEmpty() && cleanedTitle.contains(":")) {
                val mainTitle = getMainTitle(cleanedTitle)
                Timber.d("TMDB: [EN] Ana başlık ile deneniyor: $mainTitle")
                
                searchResult = tmdbApiService.searchMovie(
                    apiKey = Constants.Tmdb.API_KEY,
                    query = mainTitle,
                    language = "en-US",
                    region = null,
                    year = year
                )
                
                Timber.d("TMDB: [EN] Ana başlık arama sonucu: ${searchResult.results?.size ?: 0}")
            }
            
            // Yıl bilgisi varsa, önce yıla göre filtrele
            val results = searchResult.results
            val movieId = if (year != null && !results.isNullOrEmpty()) {
                // Yıl eşleşmesi olan filmi bul
                val matchingMovie = results.firstOrNull { movie ->
                    movie.title?.contains(year.toString()) == true ||
                    movie.originalTitle?.contains(year.toString()) == true
                }
                matchingMovie?.id ?: results.firstOrNull()?.id
            } else {
                results?.firstOrNull()?.id
            }

            if (movieId != null) {
                Timber.d("TMDB: Film bulundu, ID: $movieId - Detaylar getiriliyor...")
                
                // Film detaylarını getir (oyuncular ve ekip dahil)
                val details = tmdbApiService.getMovieDetails(
                    movieId = movieId,
                    apiKey = Constants.Tmdb.API_KEY
                )
                
                Timber.d("TMDB: Detaylar alındı - Film: ${details.title}, Yönetmen: ${getDirector(details)}, Oyuncu sayısı: ${details.credits?.cast?.size ?: 0}")
                details
            } else {
                Timber.w("TMDB: Film bulunamadı - Orijinal: '$movieTitle', Temizlenmiş: '$cleanedTitle'")
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "TMDB API hatası: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    /**
     * Film detaylarından yönetmen bilgisini çıkarır
     * Cache'den geliyorsa doğrudan cache'deki değeri kullan
     */
    suspend fun getDirector(tmdbId: Int, movieDetails: TmdbMovieDetailsDto?): String? {
        // Önce cache'e bak (çünkü cache'de director ayrı saklanıyor)
        val cache = tmdbCacheDao.getCacheByTmdbId(tmdbId)
        if (cache?.director != null) {
            return cache.director
        }
        
        // Cache'de yoksa DTO'dan çıkar
        return movieDetails?.credits?.crew?.firstOrNull { it.job == "Director" }?.name
    }

    /**
     * Film detaylarından oyuncu listesini çıkarır (ilk 5 oyuncu)
     * 
     * Cache'den veya DTO'dan ham veri olarak List<String> döner.
     * UI formatlaması (virgülle birleştirme vb.) ViewModel'de yapılır.
     * 
     * @return Oyuncu isimleri listesi veya null
     */
    suspend fun getCast(tmdbId: Int, movieDetails: TmdbMovieDetailsDto?): List<String>? {
        // Önce cache'e bak
        val cache = tmdbCacheDao.getCacheByTmdbId(tmdbId)
        if (cache?.cast != null) {
            // Cache'den gelen string'i listeye çevir
            return cache.cast.split(", ").filter { it.isNotBlank() }
        }
        
        // Cache'de yoksa DTO'dan çıkar - HAM VERİ
        val castList = movieDetails?.credits?.cast
            ?.sortedBy { it.order }
            ?.take(5)
            ?.mapNotNull { it.name }

        return if (castList.isNullOrEmpty()) null else castList
    }

    /**
     * Film detaylarından açıklamayı çıkarır
     * Cache'den geliyorsa doğrudan cache'deki değeri kullan
     */
    suspend fun getOverview(tmdbId: Int, movieDetails: TmdbMovieDetailsDto?): String? {
        // Önce cache'e bak
        val cache = tmdbCacheDao.getCacheByTmdbId(tmdbId)
        if (cache?.overview != null) {
            return cache.overview
        }
        
        // Cache'de yoksa DTO'dan çıkar
        return movieDetails?.overview?.takeIf { it.isNotBlank() }
    }

    // Private metodlar (cache kaydetme için)
    private fun getDirector(movieDetails: TmdbMovieDetailsDto?): String? {
        return movieDetails?.credits?.crew?.firstOrNull { it.job == "Director" }?.name
    }

    /**
     * Cache'e kaydetmek için oyuncu listesini string'e çevirir
     */
    private fun getCastForCache(movieDetails: TmdbMovieDetailsDto?): String? {
        val cast = movieDetails?.credits?.cast
            ?.sortedBy { it.order }
            ?.take(5)
            ?.mapNotNull { it.name }
            ?.joinToString(", ")

        return if (cast.isNullOrEmpty()) null else cast
    }

    private fun getOverview(movieDetails: TmdbMovieDetailsDto?): String? {
        return movieDetails?.overview?.takeIf { it.isNotBlank() }
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
        
        Timber.d("TMDB Batch (Film): ${movieIdsWithTmdb.size} film için detay çekilecek")
        
        movieIdsWithTmdb.forEachIndexed { index, (streamId, tmdbId) ->
            try {
                // Cache'de var mı kontrol et
                val existingCache = tmdbCacheDao.getCacheByTmdbId(tmdbId)
                
                if (existingCache == null) {
                    Timber.d("TMDB Batch Film [$index/${movieIdsWithTmdb.size}]: Stream ID $streamId, TMDB ID $tmdbId - Çekiliyor...")
                    
                    // API'den çek ve cache'e kaydet
                    getMovieDetailsById(tmdbId)
                    successCount++
                    
                    // Rate limiting için bekle
                    if (index < movieIdsWithTmdb.size - 1) {
                        kotlinx.coroutines.delay(BATCH_REQUEST_DELAY)
                    }
                } else {
                    Timber.d("TMDB Batch Film [$index/${movieIdsWithTmdb.size}]: TMDB ID $tmdbId zaten cache'de, atlanıyor")
                }
            } catch (e: Exception) {
                Timber.e(e, "TMDB Batch Film hatası - Stream ID: $streamId, TMDB ID: $tmdbId")
            }
        }
        
        Timber.d("TMDB Batch Film tamamlandı: $successCount/${movieIdsWithTmdb.size} başarılı")
        return successCount
    }

    // ==================== TV SHOWS (DİZİLER) ====================

    /**
     * TMDB ID ile doğrudan dizi detaylarını getirir
     * Çok dilli fallback zinciri kullanarak en iyi çeviriyi bulur
     * 
     * @param tmdbId TMDB dizi ID'si
     * @param forceRefresh True ise cache'i yoksay ve API'den çek
     * @return Dizi detayları veya null
     */
    suspend fun getTvShowDetailsById(tmdbId: Int, forceRefresh: Boolean = false): TmdbTvShowDetailsDto? {
        return try {
            Timber.d("TMDB TV: Dizi detayları isteniyor - ID: $tmdbId")
            
            // 1. Cache'i kontrol et
            if (!forceRefresh) {
                val cachedData = tmdbCacheDao.getCacheByTmdbId(tmdbId)
                if (cachedData != null) {
                    val cacheAge = System.currentTimeMillis() - cachedData.cacheTime
                    val isValid = cacheAge < CACHE_VALIDITY_DURATION
                    
                    if (isValid) {
                        Timber.d("TMDB TV: Cache'den döndürülüyor (${cacheAge / 60000} dk eski)")
                        return cacheToDtoTv(cachedData)
                    }
                }
            }
            
            // 2. Çok dilli fallback zinciri ile API'den çek
            val details = fetchTvShowWithFallback(tmdbId)
            
            if (details != null) {
                // 3. Cache'e kaydet
                val creator = getCreator(details)
                val castForCache = getCastForCacheTv(details)
                val overview = getOverviewFromTv(details)
                
                val cacheEntity = TmdbCacheEntity(
                    tmdbId = tmdbId,
                    title = details.name,
                    director = creator,
                    cast = castForCache,
                    overview = overview,
                    cacheTime = System.currentTimeMillis()
                )
                tmdbCacheDao.insertCache(cacheEntity)
                Timber.d("TMDB TV: Cache'e kaydedildi")
            }
            
            details
        } catch (e: Exception) {
            Timber.e(e, "TMDB TV API hatası (ID: $tmdbId): ${e.message}")
            
            // Hata durumunda cache'den dön
            val cachedData = tmdbCacheDao.getCacheByTmdbId(tmdbId)
            if (cachedData != null) {
                Timber.w("TMDB TV: API hatası, eski cache döndürülüyor")
                return cacheToDtoTv(cachedData)
            }
            
            null
        }
    }

    /**
     * Çok dilli fallback zinciri ile dizi detaylarını çeker
     */
    private suspend fun fetchTvShowWithFallback(tmdbId: Int): TmdbTvShowDetailsDto? {
        Timber.d("TMDB TV Fallback: Dizi detayları çekiliyor - ID: $tmdbId")
        
        val languageChain = LocaleHelper.getLanguageFallbackChain(context)
        Timber.d("TMDB TV Fallback: Dil zinciri: $languageChain")
        
        var mergedDetails: TmdbTvShowDetailsDto? = null
        var originalLanguage: String? = null
        
        for ((index, language) in languageChain.withIndex()) {
            try {
                Timber.d("TMDB TV Fallback [${index + 1}/${languageChain.size}]: Dil: $language")
                
                val details = tmdbApiService.getTvShowDetails(
                    tvId = tmdbId,
                    apiKey = Constants.Tmdb.API_KEY,
                    language = language
                )
                
                if (originalLanguage == null) {
                    originalLanguage = details.originalLanguage
                }
                
                mergedDetails = mergeTvShowDetails(mergedDetails, details)
                
                val hasOverview = !mergedDetails.overview.isNullOrBlank()
                Timber.d("TMDB TV Fallback: Overview: ${if (hasOverview) "Var ✅" else "Yok ❌"}")
                
                if (hasOverview) {
                    Timber.d("TMDB TV Fallback: Tamamlandı ($language dilinde)")
                    break
                }
            } catch (e: Exception) {
                Timber.w("TMDB TV Fallback: $language dili başarısız - ${e.message}")
            }
        }
        
        // Son çare: Orijinal dil
        if (mergedDetails?.overview.isNullOrBlank() && originalLanguage != null && !languageChain.contains(originalLanguage)) {
            try {
                Timber.d("TMDB TV Fallback [Son Çare]: Orijinal dil: $originalLanguage")
                
                val details = tmdbApiService.getTvShowDetails(
                    tvId = tmdbId,
                    apiKey = Constants.Tmdb.API_KEY,
                    language = originalLanguage
                )
                
                mergedDetails = mergeTvShowDetails(mergedDetails, details)
                Timber.d("TMDB TV Fallback: Orijinal dilden alındı")
            } catch (e: Exception) {
                Timber.w("TMDB TV Fallback: Orijinal dil başarısız")
            }
        }
        
        return mergedDetails
    }

    /**
     * İki dizi detay DTO'sunu birleştirir
     */
    private fun mergeTvShowDetails(
        primary: TmdbTvShowDetailsDto?,
        fallback: TmdbTvShowDetailsDto
    ): TmdbTvShowDetailsDto {
        if (primary == null) return fallback
        
        return TmdbTvShowDetailsDto(
            id = primary.id ?: fallback.id,
            name = primary.name ?: fallback.name,
            overview = primary.overview.takeIf { !it.isNullOrBlank() } ?: fallback.overview,
            originalLanguage = primary.originalLanguage ?: fallback.originalLanguage,
            genres = primary.genres ?: fallback.genres,
            createdBy = primary.createdBy ?: fallback.createdBy,
            credits = primary.credits ?: fallback.credits
        )
    }

    /**
     * Dizi türlerini formatlı string olarak döndürür
     */
    fun getGenresFromTv(tvDetails: TmdbTvShowDetailsDto?): String? {
        val genres = tvDetails?.genres
            ?.mapNotNull { it.name }
            ?.joinToString(", ")
        return if (genres.isNullOrBlank()) null else genres
    }

    /**
     * Cache entity'sini TV DTO'ya çevirir
     */
    private fun cacheToDtoTv(cache: TmdbCacheEntity): TmdbTvShowDetailsDto {
        return TmdbTvShowDetailsDto(
            id = cache.tmdbId,
            name = cache.title,
            overview = cache.overview,
            originalLanguage = null, // Cache'de saklanmıyor
            genres = null, // Cache'de saklanmıyor
            createdBy = null,
            credits = null
        )
    }

    /**
     * Dizi adına göre TMDB'de arama yapar ve ilk sonucun detaylarını getirir
     * Film arama mantığıyla aynı stratejiyi kullanır
     */
    suspend fun getTvShowDetailsByTitle(seriesTitle: String): TmdbTvShowDetailsDto? {
        return try {
            Timber.d("TMDB TV: Orijinal dizi adı: $seriesTitle")
            
            val cleanedTitle = cleanMovieTitle(seriesTitle)
            val year = extractYear(seriesTitle)
            
            Timber.d("TMDB TV: Temizlenmiş dizi adı: $cleanedTitle${if (year != null) " (Yıl: $year)" else ""}")
            
            // 6 katmanlı arama stratejisi (filmlerle aynı)
            var searchResult = tmdbApiService.searchTvShow(
                apiKey = Constants.Tmdb.API_KEY,
                query = cleanedTitle,
                language = "tr-TR",
                region = "TR",
                year = year
            )

            if (searchResult.results.isNullOrEmpty()) {
                searchResult = tmdbApiService.searchTvShow(
                    apiKey = Constants.Tmdb.API_KEY,
                    query = cleanedTitle,
                    language = "tr-TR",
                    year = year
                )
            }

            if (searchResult.results.isNullOrEmpty()) {
                searchResult = tmdbApiService.searchTvShow(
                    apiKey = Constants.Tmdb.API_KEY,
                    query = cleanedTitle,
                    language = "en-US",
                    year = year
                )
            }

            if (searchResult.results.isNullOrEmpty() && cleanedTitle.contains(":")) {
                val mainTitle = getMainTitle(cleanedTitle)
                searchResult = tmdbApiService.searchTvShow(
                    apiKey = Constants.Tmdb.API_KEY,
                    query = mainTitle,
                    language = "tr-TR",
                    region = "TR",
                    year = year
                )
            }

            val results = searchResult.results
            val tvId = results?.firstOrNull()?.id

            if (tvId != null) {
                Timber.d("TMDB TV: Dizi bulundu, ID: $tvId - Detaylar getiriliyor...")
                getTvShowDetailsById(tvId)
            } else {
                Timber.w("TMDB TV: Dizi bulunamadı - '$seriesTitle'")
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
    suspend fun getCreator(tmdbId: Int, tvDetails: TmdbTvShowDetailsDto?): String? {
        val cache = tmdbCacheDao.getCacheByTmdbId(tmdbId)
        if (cache?.director != null) {
            return cache.director
        }
        return getCreator(tvDetails)
    }

    private fun getCreator(tvDetails: TmdbTvShowDetailsDto?): String? {
        return tvDetails?.createdBy?.firstOrNull()?.name
    }

    /**
     * Dizi oyuncularını döndürür
     */
    suspend fun getCastFromTv(tmdbId: Int, tvDetails: TmdbTvShowDetailsDto?): List<String>? {
        val cache = tmdbCacheDao.getCacheByTmdbId(tmdbId)
        if (cache?.cast != null) {
            return cache.cast.split(", ").filter { it.isNotBlank() }
        }
        return getCastFromTv(tvDetails)
    }

    private fun getCastFromTv(tvDetails: TmdbTvShowDetailsDto?): List<String>? {
        val castList = tvDetails?.credits?.cast
            ?.sortedBy { it.order }
            ?.take(5)
            ?.mapNotNull { it.name }
        return if (castList.isNullOrEmpty()) null else castList
    }
    
    /**
     * Cache'e kaydetmek için dizi oyuncu listesini string'e çevirir
     */
    private fun getCastForCacheTv(tvDetails: TmdbTvShowDetailsDto?): String? {
        val cast = getCastFromTv(tvDetails)
        return cast?.joinToString(", ")
    }

    /**
     * Dizi açıklamasını döndürür
     */
    suspend fun getOverviewFromTv(tmdbId: Int, tvDetails: TmdbTvShowDetailsDto?): String? {
        val cache = tmdbCacheDao.getCacheByTmdbId(tmdbId)
        if (cache?.overview != null) {
            return cache.overview
        }
        return getOverviewFromTv(tvDetails)
    }

    private fun getOverviewFromTv(tvDetails: TmdbTvShowDetailsDto?): String? {
        return tvDetails?.overview?.takeIf { it.isNotBlank() }
    }

    /**
     * Birden fazla dizi için TMDB verilerini toplu olarak çeker
     * Delta senkronizasyonu için kullanılır
     */
    suspend fun batchFetchTvShowDetails(seriesIdsWithTmdb: List<Pair<Int, Int>>): Int {
        var successCount = 0
        
        Timber.d("TMDB Batch (Dizi): ${seriesIdsWithTmdb.size} dizi için detay çekilecek")
        
        seriesIdsWithTmdb.forEachIndexed { index, (streamId, tmdbId) ->
            try {
                val existingCache = tmdbCacheDao.getCacheByTmdbId(tmdbId)
                
                if (existingCache == null) {
                    Timber.d("TMDB Batch Dizi [$index/${seriesIdsWithTmdb.size}]: Stream ID $streamId, TMDB ID $tmdbId - Çekiliyor...")
                    
                    getTvShowDetailsById(tmdbId)
                    successCount++
                    
                    if (index < seriesIdsWithTmdb.size - 1) {
                        kotlinx.coroutines.delay(BATCH_REQUEST_DELAY)
                    }
                } else {
                    Timber.d("TMDB Batch Dizi [$index/${seriesIdsWithTmdb.size}]: TMDB ID $tmdbId zaten cache'de, atlanıyor")
                }
            } catch (e: Exception) {
                Timber.e(e, "TMDB Batch Dizi hatası - Stream ID: $streamId, TMDB ID: $tmdbId")
            }
        }
        
        Timber.d("TMDB Batch Dizi tamamlandı: $successCount/${seriesIdsWithTmdb.size} başarılı")
        return successCount
    }
}

