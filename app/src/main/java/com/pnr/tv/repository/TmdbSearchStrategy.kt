package com.pnr.tv.repository

import android.content.Context
import com.pnr.tv.network.TmdbApiService
import com.pnr.tv.network.dto.TmdbMovieDetailsDto
import com.pnr.tv.util.ui.LocaleHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named

/**
 * TMDB arama stratejisi sınıfı
 * Film adına göre TMDB'de arama yapmak için çoklu strateji yaklaşımı kullanır
 */
class TmdbSearchStrategy
    @Inject
    constructor(
        private val tmdbApiService: TmdbApiService,
        @ApplicationContext private val context: Context,
        @Named("tmdb_api_key") private val tmdbApiKey: String,
        private val tmdbSearchHelper: TmdbSearchHelper,
    ) {
        /**
         * Film adına göre TMDB'de arama yapar ve ilk sonucun detaylarını getirir
         * @param movieTitle Film adı
         * @return Film detayları veya null
         */
        suspend fun searchMovieByTitle(movieTitle: String): TmdbMovieDetailsDto? {
            return try {
                // Film adını temizle
                val cleanedTitle = tmdbSearchHelper.cleanMovieTitle(movieTitle)
                val year = tmdbSearchHelper.extractYear(movieTitle)

                // Cihaz dilini al
                val deviceLanguage = LocaleHelper.getDeviceLanguageWithRegion(context)
                val deviceLanguageCode = LocaleHelper.getDeviceLanguage(context)
                // Country bilgisini locale'den al
                val locale =
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                        context.resources.configuration.locales[0]
                    } else {
                        @Suppress("DEPRECATION")
                        context.resources.configuration.locale
                    }
                val deviceCountry = locale.country

                // Strateji 1: Cihaz dili + bölge ile tam temizlenmiş başlık
                var searchResult =
                    tmdbApiService.searchMovie(
                        apiKey = tmdbApiKey,
                        query = cleanedTitle,
                        language = deviceLanguage,
                        region = if (deviceCountry.isNotEmpty()) deviceCountry else null,
                        year = year,
                    )

                // Strateji 2: Cihaz dili, region yok (daha geniş arama)
                if (searchResult.results.isNullOrEmpty()) {
                    searchResult =
                        tmdbApiService.searchMovie(
                            apiKey = tmdbApiKey,
                            query = cleanedTitle,
                            language = deviceLanguage,
                            region = null,
                            year = year,
                        )
                }

                // Strateji 3: İngilizce dil ile tam başlık (fallback)
                if (searchResult.results.isNullOrEmpty() && deviceLanguageCode != "en") {
                    searchResult =
                        tmdbApiService.searchMovie(
                            apiKey = tmdbApiKey,
                            query = cleanedTitle,
                            language = "en-US",
                            region = null,
                            year = year,
                        )
                }

                // Strateji 4: Ana başlık (iki nokta öncesi) - Cihaz dili + Region
                if (searchResult.results.isNullOrEmpty() && cleanedTitle.contains(":")) {
                    val mainTitle = tmdbSearchHelper.getMainTitle(cleanedTitle)

                    searchResult =
                        tmdbApiService.searchMovie(
                            apiKey = tmdbApiKey,
                            query = mainTitle,
                            language = deviceLanguage,
                            region = if (deviceCountry.isNotEmpty()) deviceCountry else null,
                            year = year,
                        )
                }

                // Strateji 5: Ana başlık - Cihaz dili, region yok
                if (searchResult.results.isNullOrEmpty() && cleanedTitle.contains(":")) {
                    val mainTitle = tmdbSearchHelper.getMainTitle(cleanedTitle)

                    searchResult =
                        tmdbApiService.searchMovie(
                            apiKey = tmdbApiKey,
                            query = mainTitle,
                            language = deviceLanguage,
                            region = null,
                            year = year,
                        )
                }

                // Strateji 6: Ana başlık - İngilizce (fallback)
                if (searchResult.results.isNullOrEmpty() && cleanedTitle.contains(":") && deviceLanguageCode != "en") {
                    val mainTitle = tmdbSearchHelper.getMainTitle(cleanedTitle)

                    searchResult =
                        tmdbApiService.searchMovie(
                            apiKey = tmdbApiKey,
                            query = mainTitle,
                            language = "en-US",
                            region = null,
                            year = year,
                        )
                }

                // Yıl bilgisi varsa, önce yıla göre filtrele
                val results = searchResult.results
                val movieId =
                    if (year != null && !results.isNullOrEmpty()) {
                        // Yıl eşleşmesi olan filmi bul
                        val matchingMovie =
                            results.firstOrNull { movie ->
                                movie.title?.contains(year.toString()) == true ||
                                    movie.originalTitle?.contains(year.toString()) == true
                            }
                        matchingMovie?.id ?: results.firstOrNull()?.id
                    } else {
                        results?.firstOrNull()?.id
                    }

                if (movieId != null) {
                    // Film detaylarını getir (oyuncular ve ekip dahil)
                    val details =
                        tmdbApiService.getMovieDetails(
                            movieId = movieId,
                            apiKey = tmdbApiKey,
                        )

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
    }
