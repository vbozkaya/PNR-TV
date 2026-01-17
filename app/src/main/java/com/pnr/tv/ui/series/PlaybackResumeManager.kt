package com.pnr.tv.ui.series

import com.pnr.tv.core.constants.TimeConstants
import com.pnr.tv.db.entity.PlaybackPositionEntity
import com.pnr.tv.premium.PremiumFeatureGuard
import com.pnr.tv.repository.PlaybackPositionRepository
import javax.inject.Inject

/**
 * Oynatma pozisyonu ve resume dialog mantığını yöneten manager.
 */
class PlaybackResumeManager
    @Inject
    constructor(
        private val playbackPositionRepository: PlaybackPositionRepository,
        private val premiumFeatureGuard: PremiumFeatureGuard,
    ) {
        data class ResumePlaybackData(
            val playbackPosition: PlaybackPositionEntity,
            val episode: ParsedEpisode,
        )

        /**
         * Bölümün oynatılması gerekip gerekmediğini kontrol eder.
         * Eğer kayıtlı pozisyon varsa ve anlamlıysa, resume dialog gösterilmesi için data döner.
         * Premium özelliği: Sadece Premium kullanıcılar için resume dialog gösterilir.
         *
         * @param episode Bölüm bilgisi
         * @return ResumePlaybackData eğer dialog gösterilmeli, null eğer direkt oynatılmalı
         */
        suspend fun checkAndGetResumeData(episode: ParsedEpisode): ResumePlaybackData? {
            // Premium kontrolü: Premium değilse dialog gösterilmez, her zaman baştan başlar
            if (!premiumFeatureGuard.isPremiumSync()) {
                return null
            }

            val contentId = "episode_${episode.episodeId}"
            val savedPosition = playbackPositionRepository.getPlaybackPosition(contentId)

            if (savedPosition != null && shouldShowResumeDialog(savedPosition)) {
                return ResumePlaybackData(
                    playbackPosition = savedPosition,
                    episode = episode,
                )
            }
            return null
        }

        /**
         * Dialog gösterilip gösterilmeyeceğini belirler.
         * İçeriğin süresinden bağımsız olarak, 10 dakikadan fazla izlenmişse dialog gösterilir.
         */
        private fun shouldShowResumeDialog(position: PlaybackPositionEntity): Boolean {
            if (position.durationMs <= 0) return false
            val resumeThresholdMs = TimeConstants.Intervals.TEN_MINUTES_MS // 10 dakika
            return position.positionMs >= resumeThresholdMs
        }

        /**
         * Oynatma pozisyonunu siler (baştan başlatmak için).
         */
        suspend fun deletePlaybackPosition(episode: ParsedEpisode) {
            val contentId = "episode_${episode.episodeId}"
            playbackPositionRepository.deletePlaybackPosition(contentId)
        }
    }
