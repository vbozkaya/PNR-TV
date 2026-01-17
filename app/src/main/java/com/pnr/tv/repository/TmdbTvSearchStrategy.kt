package com.pnr.tv.repository

import android.content.Context
import com.pnr.tv.network.TmdbApiService
import com.pnr.tv.network.dto.TmdbTvShowDetailsDto
import com.pnr.tv.util.ui.LocaleHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named

/**
 * TMDB TV arama stratejisi sınıfı
 * Dizi adına göre TMDB'de arama yapmak için çoklu strateji yaklaşımı kullanır
 */
class TmdbTvSearchStrategy
    @Inject
    constructor(
        private val tmdbApiService: TmdbApiService,
        @ApplicationContext private val context: Context,
        @Named("tmdb_api_key") private val tmdbApiKey: String,
        private val tmdbSearchHelper: TmdbSearchHelper,
    ) {
        /**
         * Dizi adına göre TMDB'de arama yapar ve ilk sonucun ID'sini döndürür
         * @param seriesTitle Dizi adı
         * @return TMDB dizi ID'si veya null
         */
        suspend fun searchTvShowByTitle(seriesTitle: String): Int? {
            return try {
                val cleanedTitle = tmdbSearchHelper.cleanMovieTitle(seriesTitle)
                val year = tmdbSearchHelper.extractYear(seriesTitle)

                val deviceLanguage = LocaleHelper.getDeviceLanguageWithRegion(context)
                val deviceLanguageCode = LocaleHelper.getDeviceLanguage(context)
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
                    tmdbApiService.searchTvShow(
                        apiKey = tmdbApiKey,
                        query = cleanedTitle,
                        language = deviceLanguage,
                        region = if (deviceCountry.isNotEmpty()) deviceCountry else null,
                        year = year,
                    )

                // Strateji 2: Cihaz dili, region yok (daha geniş arama)
                if (searchResult.results.isNullOrEmpty()) {
                    searchResult =
                        tmdbApiService.searchTvShow(
                            apiKey = tmdbApiKey,
                            query = cleanedTitle,
                            language = deviceLanguage,
                            year = year,
                        )
                }

                // Strateji 3: İngilizce dil ile tam başlık (fallback)
                if (searchResult.results.isNullOrEmpty() && deviceLanguageCode != "en") {
                    searchResult =
                        tmdbApiService.searchTvShow(
                            apiKey = tmdbApiKey,
                            query = cleanedTitle,
                            language = "en-US",
                            year = year,
                        )
                }

                // Strateji 4: Ana başlık (iki nokta öncesi) - Cihaz dili + Region
                if (searchResult.results.isNullOrEmpty() && cleanedTitle.contains(":")) {
                    val mainTitle = tmdbSearchHelper.getMainTitle(cleanedTitle)
                    searchResult =
                        tmdbApiService.searchTvShow(
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
                        tmdbApiService.searchTvShow(
                            apiKey = tmdbApiKey,
                            query = mainTitle,
                            language = deviceLanguage,
                            year = year,
                        )
                }

                // Strateji 6: Ana başlık - İngilizce (fallback)
                if (searchResult.results.isNullOrEmpty() && cleanedTitle.contains(":") && deviceLanguageCode != "en") {
                    val mainTitle = tmdbSearchHelper.getMainTitle(cleanedTitle)
                    searchResult =
                        tmdbApiService.searchTvShow(
                            apiKey = tmdbApiKey,
                            query = mainTitle,
                            language = "en-US",
                            year = year,
                        )
                }

                val results = searchResult.results
                val tvId = results?.firstOrNull()?.id

                if (tvId == null) {
                    Timber.w("TMDB TV: Dizi bulunamadı - '$seriesTitle'")
                }

                tvId
            } catch (e: Exception) {
                Timber.e(e, "TMDB TV API hatası: ${e.message}")
                null
            }
        }
    }
