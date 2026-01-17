package com.pnr.tv.ui.series

import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.pnr.tv.R
import com.pnr.tv.extensions.normalizeBaseUrl
import com.pnr.tv.extensions.showCustomToast
import com.pnr.tv.repository.UserRepository
import com.pnr.tv.security.DataEncryption
import com.pnr.tv.ui.player.PlayerActivity
import com.pnr.tv.ui.player.handler.PlayerIntentHandler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Dizi bölümlerini oynatma işlemlerini yöneten handler.
 * Veri şifreleme ve aktivite başlatma gibi düşük seviyeli işleri fragment'tan ayırır.
 */
class SeriesPlaybackHandler
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val userRepository: UserRepository,
    ) {
        private var viewModel: SeriesDetailViewModel? = null
        private var fragment: Fragment? = null
        private var playerActivityLauncher: ActivityResultLauncher<Intent>? = null

        /**
         * Handler'ı kurar. Fragment ve ViewModel referanslarını bağlar.
         *
         * @param fragment Fragment referansı (lifecycle için)
         * @param viewModel ViewModel referansı (veri işlemleri için)
         * @param playerActivityLauncher Activity başlatmak için launcher
         */
        fun setup(
            fragment: Fragment,
            viewModel: SeriesDetailViewModel,
            playerActivityLauncher: ActivityResultLauncher<Intent>,
        ) {
            this.fragment = fragment
            this.viewModel = viewModel
            this.playerActivityLauncher = playerActivityLauncher
        }

        /**
         * Bölümü oynatır. Resume dialog kontrolü artık PlayerActivity'de yapılıyor.
         */
        fun playEpisode(episode: ParsedEpisode) {
            startEpisodePlayback(episode)
        }

        /**
         * Bölüm oynatmayı başlatır. Stream URL'i oluşturur ve PlayerActivity'yi başlatır.
         */
        private fun startEpisodePlayback(episode: ParsedEpisode) {
            val fragment = this.fragment ?: return
            val viewModel = this.viewModel ?: return
            val playerActivityLauncher = this.playerActivityLauncher ?: return

            fragment.viewLifecycleOwner.lifecycleScope.launch {
                val user = userRepository.currentUser.firstOrNull()
                if (user == null) {
                    fragment.requireContext().showCustomToast(
                        fragment.getString(R.string.error_user_not_selected),
                        android.widget.Toast.LENGTH_LONG,
                    )
                    Timber.e("❌ Bölüm oynatılamadı: Kullanıcı seçili değil")
                    return@launch
                }

                try {
                    // DNS ve password'ü şifre çöz
                    val decryptedDns = DataEncryption.decryptSensitiveData(user.dns, fragment.requireContext())
                    val decryptedPassword = DataEncryption.decryptSensitiveData(user.password, fragment.requireContext())

                    if (decryptedDns.isBlank()) {
                        fragment.requireContext().showCustomToast(
                            fragment.getString(R.string.error_video_url_not_found),
                            android.widget.Toast.LENGTH_LONG,
                        )
                        Timber.e("❌ Bölüm oynatılamadı: DNS boş")
                        return@launch
                    }

                    val baseUrl = decryptedDns.normalizeBaseUrl()
                    val episodeIdInt = episode.episodeId.toIntOrNull()
                    if (episodeIdInt == null) {
                        fragment.requireContext().showCustomToast(
                            fragment.getString(R.string.error_video_url_not_found),
                            android.widget.Toast.LENGTH_LONG,
                        )
                        Timber.e("❌ Episode ID geçersiz: ${episode.episodeId}")
                        return@launch
                    }

                    val streamUrl =
                        viewModel.getEpisodeStreamUrl(
                            baseUrl,
                            user.username,
                            decryptedPassword,
                            episodeIdInt,
                            episode.containerExtension,
                        )

                    if (streamUrl.isBlank()) {
                        fragment.requireContext().showCustomToast(
                            fragment.getString(R.string.error_video_url_not_found),
                            android.widget.Toast.LENGTH_LONG,
                        )
                        Timber.e(
                            "❌ Bölüm stream URL oluşturulamadı: baseUrl=$baseUrl, username=${user.username}, episodeId=$episodeIdInt",
                        )
                        return@launch
                    }

                    // Kaldığı yerden devam için bölüm ID'sini gönder
                    val contentId = "episode_${episode.episodeId}"

                    // Bölüm başlığı ve dizi IMDB puanı
                    val seriesInfo = viewModel.series.value
                    val episodeTitle = "${seriesInfo?.name ?: ""} - ${episode.title}"
                    val seriesRating = seriesInfo?.rating

                    // NOT: markEpisodeAsWatched artık çağrılmıyor çünkü watchProgress
                    // otomatik olarak PlayerViewModel tarafından güncelleniyor.

                    val intent =
                        Intent(fragment.requireContext(), PlayerActivity::class.java).apply {
                            putExtra(PlayerIntentHandler.EXTRA_VIDEO_URL, streamUrl)
                            putExtra(PlayerIntentHandler.EXTRA_CONTENT_ID, contentId)
                            putExtra(PlayerIntentHandler.EXTRA_CONTENT_TITLE, episodeTitle)
                            putExtra(PlayerIntentHandler.EXTRA_CONTENT_RATING, seriesRating ?: -1.0)
                            // Episode bilgileri (watchProgress güncellemesi için)
                            putExtra(PlayerIntentHandler.EXTRA_EPISODE_ID, episode.episodeId)
                            seriesInfo?.streamId?.let { putExtra(PlayerIntentHandler.EXTRA_SERIES_ID, it) }
                            putExtra(PlayerIntentHandler.EXTRA_SEASON_NUMBER, episode.seasonNumber)
                            putExtra(PlayerIntentHandler.EXTRA_EPISODE_NUMBER, episode.episodeNumber)
                        }
                    playerActivityLauncher.launch(intent)
                } catch (e: Exception) {
                    fragment.requireContext().showCustomToast(
                        fragment.getString(R.string.error_video_url_not_found),
                        android.widget.Toast.LENGTH_LONG,
                    )
                    Timber.e(e, "❌ Bölüm oynatılamadı: Beklenmeyen hata")
                }
            }
        }

    }
