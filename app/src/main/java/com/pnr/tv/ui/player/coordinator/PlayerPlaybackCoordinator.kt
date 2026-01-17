package com.pnr.tv.ui.player.coordinator

import android.content.Context
import android.content.Intent
import androidx.lifecycle.LifecycleCoroutineScope
import com.pnr.tv.R
import com.pnr.tv.core.constants.TimeConstants
import com.pnr.tv.core.constants.UIConstants
import com.pnr.tv.databinding.ActivityPlayerBinding
import com.pnr.tv.domain.BuildLiveStreamUrlUseCase
import com.pnr.tv.premium.PremiumManager
import com.pnr.tv.repository.ContentRepository
import com.pnr.tv.ui.player.PlayerViewModel
import com.pnr.tv.ui.player.dialog.ResumePlaybackDialog
import com.pnr.tv.ui.player.handler.PlayerIntentHandler
import com.pnr.tv.ui.player.handler.PlayerData
import com.pnr.tv.ui.player.state.PlayerAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * PlayerActivity içindeki oynatma yaşam döngüsü sorumluluklarını yöneten coordinator sınıfı.
 * Activity'yi sadece bir 'host' haline getirmek ve oynatma yaşam döngüsü yönetimini izole etmek için oluşturulmuştur.
 */
class PlayerPlaybackCoordinator(
    private val binding: ActivityPlayerBinding,
    private val viewModel: PlayerViewModel,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val contentRepository: ContentRepository,
    private val buildLiveStreamUrlUseCase: BuildLiveStreamUrlUseCase,
    private val intent: Intent,
    private val context: Context,
    private val premiumManager: PremiumManager,
) {
    // Video bilgilerini saklamak için (onStop'ta release edildikten sonra yeniden yüklemek için)
    private var savedVideoUrl: String? = null
    private var savedContentId: String? = null
    private var savedContentTitle: String? = null
    private var savedContentRating: Double? = null
    private var savedChannelId: Int? = null
    private var savedCategoryId: Int? = null

    // ChannelId ve CategoryId'yi saklamak için (canlı yayın için)
    private var channelId: Int? = null
    private var categoryId: Int? = null

    // Progress güncelleme döngüsü için
    private var progressUpdateJob: Job? = null
    
    // Resume dialog kontrolü için
    private var hasShownResumeDialog: Boolean = false
    private var pendingResumePosition: Long? = null

    /**
     * ChannelId'yi set eder (canlı yayın için).
     */
    fun setChannelId(channelId: Int?) {
        this.channelId = channelId
    }

    /**
     * CategoryId'yi set eder (canlı yayın için).
     */
    fun setCategoryId(categoryId: Int?) {
        this.categoryId = categoryId
    }

    /**
     * Mevcut ChannelId'yi döndürür.
     */
    fun getChannelId(): Int? = channelId

    /**
     * Mevcut CategoryId'yi döndürür.
     */
    fun getCategoryId(): Int? = categoryId

    /**
     * PlayerData'yı alarak oynatmayı başlatır.
     * Canlı yayın playlist oluşturma, film/dizi resume dialog kontrolü gibi tüm başlangıç mantığını yönetir.
     */
    fun initialize(playerData: PlayerData) {
        if (playerData.videoUrl.isNullOrBlank()) {
            // Hata durumunda loading'i ViewModel state'ine göre kapat
            viewModel.handleAction(PlayerAction.SetLoadingMessage(context.getString(R.string.error_video_url_not_found)))
            viewModel.handleAction(PlayerAction.SetIsLoading(false))
            return
        }

        if (playerData.channelId != null && playerData.categoryId != null) {
            // Canlı yayın: Playlist oluştur
            lifecycleScope.launch {
                try {
                    viewModel.handleAction(PlayerAction.SetLoadingMessage(context.getString(R.string.player_loading_fetching_url)))
                    val channels = contentRepository.getLiveStreamsByCategoryIdSync(playerData.categoryId)

                    if (channels.isEmpty()) {
                        // Loading'i ViewModel state'ine göre kapat
                        viewModel.handleAction(PlayerAction.SetLoadingMessage(context.getString(R.string.error_video_url_not_found)))
                        viewModel.handleAction(PlayerAction.SetIsLoading(false))
                        return@launch
                    }

                    val startIndex = channels.indexOfFirst { it.streamId == playerData.channelId }

                    if (startIndex == -1) {
                        viewModel.handleAction(PlayerAction.PlayVideo(playerData.videoUrl, playerData.contentId))
                        viewModel.handleAction(PlayerAction.StartWatching(playerData.channelId))
                        return@launch
                    }

                    val mediaItems = viewModel.createPlaylistFromChannels(channels, buildLiveStreamUrlUseCase)

                    if (mediaItems.isEmpty()) {
                        viewModel.handleAction(PlayerAction.PlayVideo(playerData.videoUrl, playerData.contentId))
                        viewModel.handleAction(PlayerAction.StartWatching(playerData.channelId))
                        return@launch
                    }

                    viewModel.handleAction(PlayerAction.PlayPlaylist(mediaItems, startIndex, null))
                    viewModel.handleAction(PlayerAction.StartWatching(playerData.channelId))
                } catch (e: Exception) {
                    viewModel.handleAction(PlayerAction.PlayVideo(playerData.videoUrl, playerData.contentId))
                    viewModel.handleAction(PlayerAction.StartWatching(playerData.channelId))
                }
            }
        } else {
            // Film/Dizi: Resume dialog kontrolü yap
            if (!playerData.isLiveStream && playerData.contentId != null) {
                lifecycleScope.launch {
                    val dialogShown = checkAndShowResumeDialog(
                        contentId = playerData.contentId,
                        onResumeFromPosition = {
                            // Kaldığı yerden devam et - video başlat, seek otomatik yapılacak
                            viewModel.handleAction(PlayerAction.PlayVideo(playerData.videoUrl, playerData.contentId))
                        },
                        onStartFromBeginning = {
                            // Baştan başla - pozisyon silindi, video başlat
                            viewModel.handleAction(PlayerAction.PlayVideo(playerData.videoUrl, playerData.contentId))
                        },
                    )

                    // Dialog gösterilmediyse direkt oynat
                    if (!dialogShown) {
                        viewModel.handleAction(PlayerAction.PlayVideo(playerData.videoUrl, playerData.contentId))
                    }
                }
            } else {
                // Canlı yayın veya contentId yok - direkt oynat
                viewModel.handleAction(PlayerAction.PlayVideo(playerData.videoUrl, playerData.contentId))
            }
        }
    }

    /**
     * onStart lifecycle metodunda çağrılır.
     * Player null ise yeniden oluşturur ve video'yu yeniden yükler.
     */
    fun onStart() {
        val exoPlayer = viewModel.getPlayer()

        // Player null ise yeniden oluştur ve video'yu yeniden yükle
        if (exoPlayer == null && savedVideoUrl != null) {
            // Loading'i başlat (video yeniden yüklenecek)
            viewModel.handleAction(PlayerAction.SetLoadingMessage(binding.root.context.getString(R.string.player_loading_preparing)))

            // Player'ı yeniden oluştur
            viewModel.handleAction(PlayerAction.ReinitializePlayerIfNeeded)

            // Video bilgileri kaydedilmişse, video'yu yeniden yükle
            val videoUrl = savedVideoUrl!!
            val contentId = savedContentId
            val contentTitle = savedContentTitle
            val contentRating = savedContentRating
            val savedChannel = savedChannelId
            val savedCategory = savedCategoryId

            // ChannelId ve CategoryId'yi geri yükle
            channelId = savedChannel
            categoryId = savedCategory

            // İçerik başlığı ve rating'i geri yükle
            if (contentTitle != null) {
                binding.playerControlView.setContentInfo(contentTitle, contentRating)
                // İzlenen pozisyonu da yükle (canlı yayın değilse)
                if (savedChannel == null && savedCategory == null && contentId != null) {
                    loadWatchedPosition(contentId)
                }
            }

            // Canlı yayın mı kontrol et
            if (savedChannel != null && savedCategory != null) {
                // Canlı yayın: Playlist ile yeniden yükle
                lifecycleScope.launch {
                    try {
                        val channels = contentRepository.getLiveStreamsByCategoryIdSync(savedCategory)
                        if (channels.isNotEmpty()) {
                            val startIndex = channels.indexOfFirst { it.streamId == savedChannel }
                            if (startIndex != -1) {
                                val mediaItems = viewModel.createPlaylistFromChannels(channels, buildLiveStreamUrlUseCase)
                                if (mediaItems.isNotEmpty()) {
                                    viewModel.handleAction(PlayerAction.PlayPlaylist(mediaItems, startIndex, null))
                                    viewModel.handleAction(PlayerAction.StartWatching(savedChannel))
                                } else {
                                    // Fallback: Normal playVideo
                                    viewModel.handleAction(PlayerAction.PlayVideo(videoUrl, contentId))
                                    viewModel.handleAction(PlayerAction.StartWatching(savedChannel))
                                }
                            } else {
                                // Fallback: Normal playVideo
                                viewModel.handleAction(PlayerAction.PlayVideo(videoUrl, contentId))
                                viewModel.handleAction(PlayerAction.StartWatching(savedChannel))
                            }
                        } else {
                            // Fallback: Normal playVideo
                            viewModel.handleAction(PlayerAction.PlayVideo(videoUrl, contentId))
                            viewModel.handleAction(PlayerAction.StartWatching(savedChannel))
                        }
                    } catch (e: Exception) {
                        // Fallback: Normal playVideo
                        viewModel.handleAction(PlayerAction.PlayVideo(videoUrl, contentId))
                        viewModel.handleAction(PlayerAction.StartWatching(savedChannel))
                    }
                }
            } else {
                // Film/Dizi: Normal playVideo (pozisyon otomatik geri yüklenecek)
                viewModel.handleAction(PlayerAction.PlayVideo(videoUrl, contentId))
            }
        }

        // PlayerView'a player'ı ata - Main thread'de
        // UI ayarları (ZOrder, alpha, elevation vb.) PlayerActivity veya PlayerObserverHandler'da yapılmalı
        val currentPlayer = viewModel.getPlayer()
        if (currentPlayer != null) {
            lifecycleScope.launch(Dispatchers.Main) {
                binding.playerView.player = currentPlayer
                binding.playerView.setKeepContentOnPlayerReset(true)
            }
        }
    }

    /**
     * onStop lifecycle metodunda çağrılır.
     * Video bilgilerini kaydeder ve player'ı serbest bırakır.
     */
    fun onStop() {
        // Video bilgilerini kaydet (yeniden yüklemek için)
        savedVideoUrl = intent.getStringExtra(PlayerIntentHandler.EXTRA_VIDEO_URL)
        savedContentId = intent.getStringExtra(PlayerIntentHandler.EXTRA_CONTENT_ID)
        savedContentTitle = intent.getStringExtra(PlayerIntentHandler.EXTRA_CONTENT_TITLE)
        savedContentRating = intent.getDoubleExtra(PlayerIntentHandler.EXTRA_CONTENT_RATING, -1.0).takeIf { it > 0 }
        savedChannelId = channelId
        savedCategoryId = categoryId

        // Progress updater'ı durdur
        stopProgressUpdater()

        // PlayerView'ı null yap - Main thread'de (surface temizleme için)
        lifecycleScope.launch(Dispatchers.Main) {
            binding.playerView.player = null
        }

        // Player kaynaklarını TAMAMEN serbest bırak (KESİN ÇÖZÜM)
        // Bu, decoder, buffer, network thread'lerini tamamen temizler
        viewModel.handleAction(PlayerAction.ReleasePlayer)
    }

    /**
     * onResume lifecycle metodunda çağrılır.
     * Player varsa oynatmaya devam eder.
     */
    fun onResume() {
        // UI ayarları (ZOrder, alpha, elevation vb.) PlayerActivity veya PlayerObserverHandler'da yapılmalı
        val currentPlayer = viewModel.getPlayer()
        if (currentPlayer != null) {
            lifecycleScope.launch(Dispatchers.Main) {
                if (binding.playerView.player != currentPlayer) {
                    binding.playerView.player = currentPlayer
                    binding.playerView.setKeepContentOnPlayerReset(true)
                }
                viewModel.handleAction(PlayerAction.Play)
            }
        }
    }

    /**
     * onDestroy lifecycle metodunda çağrılır.
     * Progress güncelleme döngüsünü durdurur.
     */
    fun onDestroy() {
        // Progress güncelleme döngüsünü durdur
        stopProgressUpdater()
        // PlayerView'ı null yapmamız, memory leak önlemek adına iyi bir pratik olabilir.
        // ViewModel onCleared'da player'ı tamamen serbest bırakacak.
        lifecycleScope.launch(Dispatchers.Main) {
            binding.playerView.player = null
        }
    }

    /**
     * Progress güncelleme döngüsünü başlatır.
     */
    fun startProgressUpdater() {
        // Zaten çalışan bir döngü varsa, tekrar başlatma
        if (progressUpdateJob?.isActive == true) return

        progressUpdateJob =
            lifecycleScope.launch(Dispatchers.Main) {
                while (isActive) {
                    // Sadece player oynuyorsa ve kullanıcı seek yapmıyorsa güncelle
                    if (viewModel.isPlaying.value && !binding.playerControlView.isSeeking()) {
                        // ExoPlayer erişimi Main thread'de olmalı
                        val currentPosition = withContext(Dispatchers.Main) {
                            viewModel.getPlayer()?.currentPosition ?: 0L
                        }
                        binding.playerControlView.updateProgress(currentPosition)
                    }
                    delay(UIConstants.DelayDurations.PLAYER_CONTROL_UPDATE_INTERVAL_MS) // Saniyede iki kez güncelleme yeterli
                }
            }
    }

    /**
     * Progress güncelleme döngüsünü durdurur.
     */
    fun stopProgressUpdater() {
        progressUpdateJob?.cancel()
        progressUpdateJob = null
    }

    /**
     * İzlenen pozisyonu yükler ve PlayerControlView'a set eder.
     */
    fun loadWatchedPosition(contentId: String) {
        lifecycleScope.launch {
            try {
                val savedPosition = contentRepository.getPlaybackPosition(contentId)
                val currentDuration = viewModel.duration.value ?: 0L

                if (savedPosition != null && savedPosition.durationMs > 0 && savedPosition.positionMs > 0) {
                    // Sadece %95'ten az izlenmiş içerikler için göster
                    if (savedPosition.positionMs < savedPosition.durationMs * 0.95) {
                        binding.playerControlView.setWatchedPosition(savedPosition.positionMs)
                    } else {
                        // %95 veya daha fazla izlenmişse, izlenen pozisyonu temizle
                        binding.playerControlView.setWatchedPosition(0L)
                    }
                } else {
                    // Pozisyon yoksa temizle
                    binding.playerControlView.setWatchedPosition(0L)
                }
            } catch (e: Exception) {
                // Hata durumunda sessizce devam et
            }
        }
    }

    /**
     * Resume playback dialog'unu kontrol eder ve gösterir.
     * Video yüklendiğinde, eğer contentId varsa ve kayıtlı pozisyon 10 dakikadan fazlaysa dialog gösterilir.
     * @param contentId İçerik ID'si
     * @param onResumeFromPosition Kullanıcı "Kaldığı Yerden Devam Et" seçtiğinde çağrılacak callback
     * @param onStartFromBeginning Kullanıcı "Baştan Başla" seçtiğinde çağrılacak callback
     * @return true eğer dialog gösterildiyse, false aksi halde
     */
    suspend fun checkAndShowResumeDialog(
        contentId: String?,
        onResumeFromPosition: () -> Unit,
        onStartFromBeginning: () -> Unit,
    ): Boolean {
        if (contentId == null || hasShownResumeDialog) {
            return false
        }

        return try {
            val isPremium = premiumManager.isPremiumSync()
            if (!isPremium) {
                return false
            }

            val savedPosition = contentRepository.getPlaybackPosition(contentId)
            if (savedPosition != null && shouldShowResumeDialog(savedPosition)) {
                hasShownResumeDialog = true
                pendingResumePosition = savedPosition.positionMs

                ResumePlaybackDialog(
                    context = context,
                    playbackPosition = savedPosition,
                    onResumeFromPosition = {
                        pendingResumePosition?.let { position ->
                            viewModel.handleAction(PlayerAction.SeekTo(position))
                        }
                        pendingResumePosition = null
                        onResumeFromPosition()
                    },
                    onStartFromBeginning = {
                        lifecycleScope.launch(Dispatchers.IO) {
                            contentRepository.deletePlaybackPosition(contentId)
                        }
                        pendingResumePosition = null
                        onStartFromBeginning()
                    },
                ).show()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Dialog gösterilip gösterilmeyeceğini belirler.
     * İçeriğin süresinden bağımsız olarak, 10 dakikadan fazla izlenmişse dialog gösterilir.
     * @param position Kayıtlı oynatma pozisyonu
     * @return true eğer dialog gösterilmeli
     */
    private fun shouldShowResumeDialog(position: com.pnr.tv.db.entity.PlaybackPositionEntity): Boolean {
        if (position.durationMs <= 0) return false
        val resumeThresholdMs = TimeConstants.Intervals.TEN_MINUTES_MS // 10 dakika
        return position.positionMs >= resumeThresholdMs
    }

    /**
     * Resume dialog flag'ini sıfırlar (yeni video için).
     */
    fun resetResumeDialogFlag() {
        hasShownResumeDialog = false
        pendingResumePosition = null
    }
}
