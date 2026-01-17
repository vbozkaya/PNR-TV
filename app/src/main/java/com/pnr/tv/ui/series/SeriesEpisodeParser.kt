package com.pnr.tv.ui.series

import com.pnr.tv.network.dto.EpisodeDto
import timber.log.Timber
import javax.inject.Inject

/**
 * Bölüm parse işlemlerini yöneten sınıf.
 * EpisodeDto listesini alıp sezon bazlı gruplanmış ParsedEpisode Map'ine dönüştürür.
 */
class SeriesEpisodeParser
    @Inject
    constructor() {
        private val episodeRegex = "[Ss](\\d+)[._]?[Ee](\\d+)".toRegex()

        /**
         * EpisodeDto listesini parse edip sezon bazlı gruplanmış ParsedEpisode Map'ine dönüştürür.
         *
         * @param episodes Parse edilecek EpisodeDto listesi
         * @return Sezon numarasına göre gruplanmış ParsedEpisode Map'i
         */
        fun parseEpisodes(episodes: List<EpisodeDto>): Map<Int, List<ParsedEpisode>> {
            return episodes.mapNotNull { dto ->
                try {
                    // Null kontrolleri
                    val title = dto.title ?: ""
                    if (title.isBlank()) {
                        Timber.w("Bölüm başlığı boş, atlanıyor: id=${dto.id}")
                        return@mapNotNull null
                    }

                    val matchResult = episodeRegex.find(title)
                    if (matchResult != null && matchResult.groupValues.size == 3) {
                        val season = matchResult.groupValues[1].toIntOrNull()
                        val episode = matchResult.groupValues[2].toIntOrNull()
                        val id = dto.id

                        if (season != null && episode != null && id != null && id.isNotBlank()) {
                            val cleanTitle =
                                title.substringAfter(" - ").takeIf { it.isNotBlank() && it != title }
                                    ?: title.substringAfter(": ").takeIf { it.isNotBlank() && it != title }

                            ParsedEpisode(
                                episodeId = id,
                                seasonNumber = season,
                                episodeNumber = episode,
                                title = title,
                                cleanTitle = cleanTitle,
                                containerExtension = dto.containerExtension,
                            )
                        } else {
                            Timber.w("Bölüm parse edilemedi: title=$title, season=$season, episode=$episode, id=$id")
                            null
                        }
                    } else {
                        Timber.w("Bölüm başlığı regex ile eşleşmedi: title=$title")
                        null
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Bölüm parse edilirken hata: dto.id=${dto.id}, dto.title=${dto.title}")
                    null
                }
            }.groupBy { it.seasonNumber }
        }
    }
