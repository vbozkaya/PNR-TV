package com.pnr.tv.ui.movies

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
 * Film oynatma işlemlerini yöneten handler.
 * Veri şifreleme ve aktivite başlatma gibi düşük seviyeli işleri fragment'tan ayırır.
 */
class MoviePlaybackHandler
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val userRepository: UserRepository,
    ) {
        private var viewModel: MovieDetailViewModel? = null
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
            viewModel: MovieDetailViewModel,
            playerActivityLauncher: ActivityResultLauncher<Intent>,
        ) {
            this.fragment = fragment
            this.viewModel = viewModel
            this.playerActivityLauncher = playerActivityLauncher
        }

        /**
         * Filmi oynatır. Resume dialog kontrolü artık PlayerActivity'de yapılıyor.
         */
        fun playMovie() {
            startMoviePlayback()
        }

        /**
         * Film oynatmayı başlatır. Stream URL'i oluşturur ve PlayerActivity'yi başlatır.
         */
        private fun startMoviePlayback() {
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
                    Timber.e("❌ Film oynatılamadı: Kullanıcı seçili değil")
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
                        Timber.e("❌ Film oynatılamadı: DNS boş")
                        return@launch
                    }

                    val baseUrl = decryptedDns.normalizeBaseUrl()

                    // Stream URL'yi oluştur
                    val streamUrl = viewModel.getStreamUrl(baseUrl, user.username, decryptedPassword)
                    if (streamUrl.isNullOrBlank()) {
                        fragment.requireContext().showCustomToast(
                            fragment.getString(R.string.error_video_url_not_found),
                            android.widget.Toast.LENGTH_LONG,
                        )
                        Timber.e(
                            "❌ Film stream URL oluşturulamadı: " +
                                "baseUrl=$baseUrl, username=${user.username}, movie=${viewModel.movie.value?.streamId}",
                        )
                        return@launch
                    }

                    val movie = viewModel.movie.value
                    if (movie == null) {
                        fragment.requireContext().showCustomToast(
                            fragment.getString(R.string.error_video_url_not_found),
                            android.widget.Toast.LENGTH_LONG,
                        )
                        Timber.e("❌ Film oynatılamadı: Film bilgisi null")
                        return@launch
                    }

                    // Kaldığı yerden devam için film ID'sini gönder
                    val contentId = "movie_${movie.streamId}"

                    val intent =
                        Intent(fragment.requireContext(), PlayerActivity::class.java).apply {
                            putExtra(PlayerIntentHandler.EXTRA_VIDEO_URL, streamUrl)
                            putExtra(PlayerIntentHandler.EXTRA_CONTENT_ID, contentId)
                            putExtra(PlayerIntentHandler.EXTRA_CONTENT_TITLE, movie.name)
                            putExtra(PlayerIntentHandler.EXTRA_CONTENT_RATING, movie.rating ?: -1.0)
                        }
                    playerActivityLauncher.launch(intent)
                } catch (e: Exception) {
                    fragment.requireContext().showCustomToast(
                        fragment.getString(R.string.error_video_url_not_found),
                        android.widget.Toast.LENGTH_LONG,
                    )
                    Timber.e(e, "❌ Film oynatılamadı: Beklenmeyen hata")
                }
            }
        }

    }
