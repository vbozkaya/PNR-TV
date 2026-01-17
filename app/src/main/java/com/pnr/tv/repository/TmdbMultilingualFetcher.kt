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
 * TMDB çok dilli film detayları getirme sınıfı
 * Dil fallback zinciri yönetimi ve veri birleştirme işlemlerini yönetir
 */
class TmdbMultilingualFetcher
    @Inject
    constructor(
        private val tmdbApiService: TmdbApiService,
        @ApplicationContext private val context: Context,
        @Named("tmdb_api_key") private val tmdbApiKey: String,
        private val tmdbDataMapper: TmdbDataMapper,
    ) {
        /**
         * Çok dilli fallback zinciri ile film detaylarını çeker
         * Her adımda eksik alanları doldurur
         *
         * Fallback sırası:
         * 1. Cihaz dili + bölge (örn: pt-BR)
         * 2. Sadece cihaz dili (örn: pt)
         * 3. İngilizce (en-US)
         * 4. Orijinal dil (original_language)
         *
         * @param tmdbId TMDB film ID'si
         * @return Film detayları veya null
         */
        suspend fun fetchMovieWithFallback(tmdbId: Int): TmdbMovieDetailsDto? {
            // Dil zincirini hazırla
            val languageChain = LocaleHelper.getLanguageFallbackChain(context)

            var mergedDetails: TmdbMovieDetailsDto? = null
            var originalLanguage: String? = null

            // Fallback zincirini sırayla dene
            for (language in languageChain) {
                try {
                    val details =
                        tmdbApiService.getMovieDetails(
                            movieId = tmdbId,
                            apiKey = tmdbApiKey,
                            language = language,
                        )

                    // İlk istekten original_language'i al
                    if (originalLanguage == null) {
                        originalLanguage = details.originalLanguage
                    }

                    // Verileri birleştir (merge)
                    mergedDetails = tmdbDataMapper.mergeMovieDetails(mergedDetails, details)

                    val hasOverview = !mergedDetails.overview.isNullOrBlank()

                    // Overview varsa zinciri kır
                    if (hasOverview) {
                        break
                    }
                } catch (e: Exception) {
                    Timber.w("TMDB Fallback: $language dili başarısız - ${e.message}")
                }
            }

            // Son çare: Orijinal dil
            if (mergedDetails?.overview.isNullOrBlank() && originalLanguage != null && !languageChain.contains(originalLanguage)) {
                try {
                    val details =
                        tmdbApiService.getMovieDetails(
                            movieId = tmdbId,
                            apiKey = tmdbApiKey,
                            language = originalLanguage,
                        )

                    mergedDetails = tmdbDataMapper.mergeMovieDetails(mergedDetails, details)
                } catch (e: Exception) {
                    Timber.w("TMDB Fallback: Orijinal dil başarısız")
                }
            }

            return mergedDetails
        }
    }
