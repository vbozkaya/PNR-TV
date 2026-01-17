package com.pnr.tv.domain

import android.content.Context
import com.pnr.tv.R
import com.pnr.tv.db.entity.SeriesEntity
import com.pnr.tv.model.SeriesSeason
import com.pnr.tv.network.dto.toEntity
import com.pnr.tv.repository.ContentRepository
import com.pnr.tv.repository.Result
import com.pnr.tv.repository.TmdbTvRepository
import com.pnr.tv.ui.series.ParsedEpisode
import com.pnr.tv.ui.series.SeriesEpisodeParser
import com.pnr.tv.util.error.ErrorHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Dizi detaylarını yükleyen use case.
 * Series ve TMDB metadata yükleme mantığını içerir.
 */
class GetSeriesDetailsUseCase
    @Inject
    constructor(
        private val contentRepository: ContentRepository,
        private val tmdbTvRepository: TmdbTvRepository,
        private val episodeParser: SeriesEpisodeParser,
        @ApplicationContext private val context: Context,
    ) {
        data class SeriesDetailsResult(
            val series: SeriesEntity?,
            val seasons: List<SeriesSeason>,
            val episodesBySeason: Map<Int, List<ParsedEpisode>>,
            val tmdbDetails: com.pnr.tv.network.dto.TmdbTvShowDetailsDto?,
        )

        suspend operator fun invoke(seriesId: Int): Result<SeriesDetailsResult> {
            return try {
                val result = contentRepository.getSeriesInfo(seriesId)

                if (result is Result.Success) {
                    val seriesInfo = result.data
                    val series = seriesInfo.info?.toEntity(seriesId)

                    val allEpisodes = seriesInfo.episodes?.values?.flatten() ?: emptyList()
                    val episodesBySeason = episodeParser.parseEpisodes(allEpisodes)

                    val seasonTabs =
                        episodesBySeason.keys.sorted().map { seasonNum ->
                            val episodeCount = episodesBySeason[seasonNum]?.size ?: 0
                            SeriesSeason(
                                seasonNumber = seasonNum,
                                name = context.getString(R.string.season_format_with_episodes, seasonNum, episodeCount),
                            )
                        }

                    // TMDB details yükle
                    val tmdbDetails =
                        series?.let {
                            if (it.tmdbId != null) {
                                tmdbTvRepository.getTvShowDetailsById(it.tmdbId)
                            } else {
                                it.name?.let { title -> tmdbTvRepository.getTvShowDetailsByTitle(title) }
                            }
                        }

                    Result.Success(
                        SeriesDetailsResult(
                            series = series,
                            seasons = seasonTabs,
                            episodesBySeason = episodesBySeason,
                            tmdbDetails = tmdbDetails,
                        ),
                    )
                } else {
                    val errorMessage =
                        if (result is Result.Error) {
                            result.message.ifEmpty { context.getString(R.string.error_loading_series) }
                        } else {
                            context.getString(R.string.error_loading_series)
                        }
                    Timber.e("Dizi bilgisi alınamadı: $errorMessage")
                    Result.Error(
                        message = errorMessage,
                    )
                }
            } catch (e: Exception) {
                val errorResult =
                    ErrorHelper.createError(
                        exception = e,
                        context = context,
                        errorContext = "GetSeriesDetailsUseCase",
                    )
                Timber.e(e, "Dizi yüklenirken hata oluştu: ${errorResult.message}")
                Result.Error(
                    message = errorResult.message.ifEmpty { context.getString(R.string.error_loading_series) },
                    exception = e,
                )
            }
        }
    }
