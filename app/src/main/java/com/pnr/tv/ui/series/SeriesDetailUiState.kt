package com.pnr.tv.ui.series

import com.pnr.tv.db.entity.SeriesEntity
import com.pnr.tv.model.SeriesSeason
import com.pnr.tv.network.dto.TmdbTvShowDetailsDto

/**
 * Series Detail ekranının tüm UI state'ini tutan data class.
 * MVI pattern'ine uygun olarak tüm state'leri tek bir yerde toplar.
 */
data class SeriesDetailUiState(
    val series: SeriesEntity? = null,
    val seasons: List<SeriesSeason> = emptyList(),
    val selectedSeasonNumber: Int? = null,
    val episodes: List<ParsedEpisode> = emptyList(),
    val tmdbDetails: TmdbTvShowDetailsDto? = null,
    val isLoading: Boolean = false,
    val error: String = "",
    val allParsedEpisodesBySeason: Map<Int, List<ParsedEpisode>> = emptyMap(),
) {
    companion object {
        val Empty = SeriesDetailUiState()
    }
}
