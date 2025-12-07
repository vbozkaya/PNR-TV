package com.pnr.tv.domain

import com.pnr.tv.repository.UserRepository
import com.pnr.tv.db.entity.LiveStreamEntity
import com.pnr.tv.extensions.normalizeBaseUrl
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

/**
 * Canlı yayın stream URL'si oluşturan use case.
 * Kullanıcı bilgilerini (dns, username, password) ve stream ID'sini kullanarak
 * IPTV stream URL'sini oluşturur.
 */
class BuildLiveStreamUrlUseCase
    @Inject
    constructor(
        private val userRepository: UserRepository,
    ) {
        /**
         * Canlı yayın için stream URL'si oluşturur.
         *
         * @param channel Stream URL'si oluşturulacak kanal
         * @return Stream URL'si, kullanıcı bilgisi yoksa null
         */
        suspend operator fun invoke(channel: LiveStreamEntity): String? {
            val user = userRepository.currentUser.firstOrNull() ?: return null

            val baseUrl = user.dns.normalizeBaseUrl()

            // IPTV stream URL formatı: {baseUrl}/live/{username}/{password}/{streamId}.ts
            return "$baseUrl/live/${user.username}/${user.password}/${channel.streamId}.ts"
        }
    }

