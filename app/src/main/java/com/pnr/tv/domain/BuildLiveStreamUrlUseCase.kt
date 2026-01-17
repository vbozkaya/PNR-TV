package com.pnr.tv.domain

import android.content.Context
import com.pnr.tv.db.entity.LiveStreamEntity
import com.pnr.tv.extensions.normalizeBaseUrl
import com.pnr.tv.repository.UserRepository
import com.pnr.tv.security.DataEncryption
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.firstOrNull
import timber.log.Timber
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
        @ApplicationContext private val context: Context,
    ) {
        /**
         * Canlı yayın için stream URL'si oluşturur.
         *
         * @param channel Stream URL'si oluşturulacak kanal
         * @return Stream URL'si, kullanıcı bilgisi yoksa null
         */
        suspend operator fun invoke(channel: LiveStreamEntity): String? {
            val user = userRepository.currentUser.firstOrNull()
            if (user == null) {
                Timber.w("❌ BuildLiveStreamUrlUseCase: Kullanıcı seçili değil - channelId=${channel.streamId}")
                return null
            }

            try {
                // DNS ve password'ü şifre çöz
                val decryptedDns = DataEncryption.decryptSensitiveData(user.dns, context)
                val decryptedPassword = DataEncryption.decryptSensitiveData(user.password, context)

                if (decryptedDns.isBlank()) {
                    Timber.w("❌ BuildLiveStreamUrlUseCase: DNS boş - channelId=${channel.streamId}, username=${user.username}")
                    return null
                }

                val baseUrl = decryptedDns.normalizeBaseUrl()

                // IPTV stream URL formatı: {baseUrl}/live/{username}/{password}/{streamId}.ts
                val url = "$baseUrl/live/${user.username}/$decryptedPassword/${channel.streamId}.ts"
                Timber.d("✅ BuildLiveStreamUrlUseCase: URL oluşturuldu - channelId=${channel.streamId}, url=$url")
                return url
            } catch (e: Exception) {
                Timber.e(
                    e,
                    "❌ BuildLiveStreamUrlUseCase: URL oluşturulurken hata - channelId=${channel.streamId}, username=${user.username}",
                )
                return null
            }
        }
    }
