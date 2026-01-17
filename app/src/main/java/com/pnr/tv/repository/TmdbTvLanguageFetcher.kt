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
 * TMDB çok dilli dizi detayları getirme sınıfı
 * Dil fallback zinciri yönetimi ve veri birleştirme işlemlerini yönetir
 */
class TmdbTvLanguageFetcher
    @Inject
    constructor(
        private val tmdbApiService: TmdbApiService,
        @ApplicationContext private val context: Context,
        @Named("tmdb_api_key") private val tmdbApiKey: String,
        private val tmdbTvDataMapper: TmdbTvDataMapper,
    ) {
        /**
         * Çok dilli fallback zinciri ile dizi detaylarını çeker
         * Her adımda eksik alanları doldurur
         *
         * @param tmdbId TMDB dizi ID'si
         * @return Dizi detayları veya null
         */
        suspend fun fetchTvShowWithFallback(tmdbId: Int): TmdbTvShowDetailsDto? {
            val languageChain = LocaleHelper.getLanguageFallbackChain(context)

            var mergedDetails: TmdbTvShowDetailsDto? = null
            var originalLanguage: String? = null

            for (language in languageChain) {
                try {
                    val details =
                        tmdbApiService.getTvShowDetails(
                            tvId = tmdbId,
                            apiKey = tmdbApiKey,
                            language = language,
                        )

                    if (originalLanguage == null) {
                        originalLanguage = details.originalLanguage
                    }

                    mergedDetails = tmdbTvDataMapper.mergeTvShowDetails(mergedDetails, details)

                    val hasOverview = !mergedDetails.overview.isNullOrBlank()

                    if (hasOverview) {
                        break
                    }
                } catch (e: Exception) {
                    Timber.w("TMDB TV Fallback: $language dili başarısız - ${e.message}")
                }
            }

            // Son çare: Orijinal dil
            if (mergedDetails?.overview.isNullOrBlank() && originalLanguage != null && !languageChain.contains(originalLanguage)) {
                try {
                    val details =
                        tmdbApiService.getTvShowDetails(
                            tvId = tmdbId,
                            apiKey = tmdbApiKey,
                            language = originalLanguage,
                        )

                    mergedDetails = tmdbTvDataMapper.mergeTvShowDetails(mergedDetails, details)
                } catch (e: Exception) {
                    Timber.w("TMDB TV Fallback: Orijinal dil başarısız")
                }
            }

            return mergedDetails
        }
    }
