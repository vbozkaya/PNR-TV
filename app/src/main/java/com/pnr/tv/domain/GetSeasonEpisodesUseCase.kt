package com.pnr.tv.domain

import com.pnr.tv.db.dao.WatchedEpisodeDao
import com.pnr.tv.ui.main.SessionManager
import com.pnr.tv.ui.series.ParsedEpisode
import com.pnr.tv.ui.series.WatchStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Sezon bölümlerini ve izlenme durumlarını getiren use case.
 */
class GetSeasonEpisodesUseCase
    @Inject
    constructor(
        private val watchedEpisodeDao: WatchedEpisodeDao,
        private val sessionManager: SessionManager,
    ) {
        suspend operator fun invoke(
            episodesInSeason: List<ParsedEpisode>,
        ): List<ParsedEpisode> {
            return try {
                val userId = sessionManager.getCurrentUserId().firstOrNull()
                if (userId == null) {
                    episodesInSeason.map { it.copy(watchStatus = WatchStatus.NOT_WATCHED) }
                } else {
                    // Batch database query: Tüm episode ID'leri tek sorguda çek
                    val episodeIds = episodesInSeason.map { it.episodeId }
                    val watchedEntitiesMap =
                        withContext(Dispatchers.IO) {
                            watchedEpisodeDao.getWatchedStatusForEpisodes(episodeIds, userId)
                                .associateBy { it.episodeId }
                        }

                    // Map results back to episodes
                    episodesInSeason.map { episode ->
                        val watchedEntity = watchedEntitiesMap[episode.episodeId]
                        val newStatus =
                            when {
                                watchedEntity == null -> WatchStatus.NOT_WATCHED
                                watchedEntity.watchProgress >= 90 -> WatchStatus.FULLY_WATCHED
                                watchedEntity.watchProgress > 10 -> WatchStatus.IN_PROGRESS
                                else -> WatchStatus.NOT_WATCHED
                            }
                        episode.copy(watchStatus = newStatus)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Bölüm izlenme durumları yüklenirken hata oluştu, varsayılan durum kullanılıyor")
                episodesInSeason.map { it.copy(watchStatus = WatchStatus.NOT_WATCHED) }
            }
        }
    }
