package com.pnr.tv.ui.series

import com.pnr.tv.db.entity.SeriesEntity
import com.pnr.tv.network.dto.TmdbTvShowDetailsDto
import com.pnr.tv.repository.TmdbTvRepository
import javax.inject.Inject

/**
 * Dizi metadata formatlama işlemlerini yöneten provider.
 * Creator, cast, overview, genre gibi formatlanmış verileri sağlar.
 */
class SeriesMetadataProvider
    @Inject
    constructor(
        private val tmdbTvRepository: TmdbTvRepository,
    ) {
        suspend fun getCreator(
            series: SeriesEntity?,
            tmdbDetails: TmdbTvShowDetailsDto?,
        ): String? {
            return series?.tmdbId?.let { tmdbTvRepository.getCreator(it, tmdbDetails) }
        }

        suspend fun getCast(
            series: SeriesEntity?,
            tmdbDetails: TmdbTvShowDetailsDto?,
        ): String? {
            return series?.tmdbId?.let { tmdbTvRepository.getCastFromTv(it, tmdbDetails)?.joinToString(", ") }
        }

        suspend fun getOverview(
            series: SeriesEntity?,
            tmdbDetails: TmdbTvShowDetailsDto?,
        ): String? {
            val tmdbOverview = series?.tmdbId?.let { tmdbTvRepository.getOverviewFromTv(it, tmdbDetails) }
            return tmdbOverview ?: series?.plot
        }

        fun getGenre(tmdbDetails: TmdbTvShowDetailsDto?): String? {
            return tmdbTvRepository.getGenresFromTv(tmdbDetails)
        }
    }
