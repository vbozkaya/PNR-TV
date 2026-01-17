package com.pnr.tv.ui.player.handler

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.media3.common.Player
import com.pnr.tv.R
import com.pnr.tv.core.constants.UIConstants
import com.pnr.tv.databinding.ActivityPlayerBinding
import com.pnr.tv.extensions.showCustomToast
import com.pnr.tv.ui.player.PlayerViewModel
import com.pnr.tv.ui.player.coordinator.PlayerPanelCoordinator
import com.pnr.tv.ui.player.coordinator.PlayerPlaybackCoordinator
import com.pnr.tv.ui.player.state.PlayerAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * ViewModel state observer'larını yöneten handler sınıfı
 * Tüm collect bloklarını bu sınıf üstlenir
 */
class PlayerObserverHandler(
    private val binding: ActivityPlayerBinding,
    private val viewModel: PlayerViewModel,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val panelCoordinator: PlayerPanelCoordinator?,
    private val playbackCoordinator: PlayerPlaybackCoordinator?,
    private val intent: Intent,
    private val activity: Activity,
    private val finishWithResult: () -> Unit,
) {
    companion object {
        private const val INITIAL_CONTROLS_AUTO_HIDE_DELAY = 5000L // 5 saniye
    }
    
    // İlk oynatma başladığında kontrol barını göstermek için flag
    private var hasShownInitialControls = false
    private var initialControlsHideJob: Job? = null
    
    /**
     * Kullanıcı manuel olarak panel açtığında timer'ı iptal et
     */
    fun cancelInitialControlsTimer() {
        initialControlsHideJob?.cancel()
        initialControlsHideJob = null
    }
    
    /**
     * Video oynamaya başladığında kontrol barını göster (onRenderedFirstFrame veya onIsPlayingChanged için)
     */
    fun showControlsOnPlaybackStart() {
        if (!hasShownInitialControls) {
            hasShownInitialControls = true
            binding.playerControlView.showControls()
            
            // Video başladığında playback status mesajını gizle
            binding.tvPlaybackStatus.visibility = android.view.View.GONE
            
            // 5 saniye sonra otomatik kapat
            initialControlsHideJob?.cancel()
            initialControlsHideJob = lifecycleScope.launch(Dispatchers.Main) {
                delay(INITIAL_CONTROLS_AUTO_HIDE_DELAY)
                // Eğer kullanıcı manuel olarak panel açmışsa kapatma
                if (binding.playerControlView.isVisible()) {
                    binding.playerControlView.hideControls()
                }
            }
        }
    }
    
    /**
     * Tüm ViewModel state observer'larını başlatır
     */
    fun observeViewModelState() {
        observeIsPlaying()
        observeIsBuffering()
        observeErrorMessage()
        observeIsLoading()
        observeLoadingMessage()
        observeLoadingState()
        observeDuration()
        observeCurrentMediaItem()
        observePlayerReady()
    }

    private fun observeIsPlaying() {
        lifecycleScope.launch {
            viewModel.isPlaying.collect { isPlaying ->
                withContext(Dispatchers.Main) {
                    binding.playerControlView.updatePlayStopButtons(isPlaying)
                    if (isPlaying) {
                        // İlk oynatma başladığında kontrol barını göster ve 5 saniye sonra kapat
                        showControlsOnPlaybackStart()
                        
                        // Video başladığında playback status mesajını gizle
                        binding.tvPlaybackStatus.visibility = android.view.View.GONE
                    } else {
                    }
                }
            }
        }
    }

    private fun observeIsBuffering() {
        lifecycleScope.launch {
            viewModel.isBuffering.collect { isBuffering ->
                withContext(Dispatchers.Main) {
                    // STATE_BUFFERING olduğunda playback status mesajını göster
                    if (isBuffering) {
                        binding.tvPlaybackStatus.visibility = android.view.View.VISIBLE
                        binding.tvPlaybackStatus.text = activity.getString(R.string.player_loading_buffering)
                    } else {
                        // STATE_READY olduğunda veya video başladığında mesajı gizle
                        // Ancak video gerçekten oynuyorsa gizle (TV donanımı yavaşsa mesaj kalabilir)
                        val player = viewModel.getPlayer()
                        val isPlaying = player?.isPlaying == true
                        val playbackState = player?.playbackState
                        
                        // Video oynuyorsa veya READY durumundaysa gizle
                        if (isPlaying || playbackState == Player.STATE_READY) {
                            binding.tvPlaybackStatus.visibility = android.view.View.GONE
                        }
                    }
                }
            }
        }
    }

    private fun observeErrorMessage() {
        lifecycleScope.launch {
            viewModel.errorMessage.collect { errorMessage ->
                errorMessage?.let {
                    binding.loadingContainer.visibility = android.view.View.GONE
                    activity.showCustomToast(it, Toast.LENGTH_LONG)
                    delay(UIConstants.DelayDurations.PLAYER_ERROR_DISPLAY_DURATION_MS)
                    if (!activity.isFinishing && !activity.isDestroyed) {
                        finishWithResult()
                    }
                }
            }
        }
    }

    private fun observeIsLoading() {
        lifecycleScope.launch {
            viewModel.isLoading.collect { isLoading ->
                // Tüm UI işlemlerini Main thread'de yap
                withContext(Dispatchers.Main) {
                    val currentMessage = binding.loadingMessage.text.toString()
                    val viewModelMessage = viewModel.loadingMessage.value
                    Timber.tag("LoadingDebug").d("observeIsLoading: isLoading=$isLoading, container.visibility=${binding.loadingContainer.visibility}, currentMessage='$currentMessage', viewModelMessage='$viewModelMessage'")
                    if (isLoading) {
                        // Loading başladığında container'ı göster ve elevation'ı yükselt
                        binding.loadingContainer.bringToFront()
                        binding.loadingContainer.visibility = android.view.View.VISIBLE
                        binding.loadingContainer.elevation = 50f
                        // Child view'ların görünür olduğundan emin ol
                        binding.loadingProgress.visibility = android.view.View.VISIBLE
                        binding.loadingMessage.visibility = android.view.View.VISIBLE
                        // Mesajı ViewModel'den al ve set et (eğer boşsa veya güncel değilse)
                        if (viewModelMessage.isNotEmpty() && currentMessage != viewModelMessage) {
                            binding.loadingMessage.text = viewModelMessage
                            Timber.tag("LoadingDebug").d("observeIsLoading: Updated message from ViewModel: '$viewModelMessage'")
                        }
                        Timber.tag("LoadingDebug").d("Loading SHOWN: container=${binding.loadingContainer.visibility}, progress=${binding.loadingProgress.visibility}, message=${binding.loadingMessage.visibility}, finalMessage='${binding.loadingMessage.text}'")
                    } else {
                        // Loading kapandığında container'ı tamamen kaldır - KRİTİK
                        binding.loadingContainer.visibility = android.view.View.GONE
                        binding.loadingContainer.elevation = 0f // KRİTİK: Elevation'ı sıfırla
                        // Parent ViewGroup'dan remove edip tekrar en alta ekle (z-order için)
                        val parent = binding.loadingContainer.parent as? android.view.ViewGroup
                        parent?.let {
                            val index = it.indexOfChild(binding.loadingContainer)
                            if (index >= 0) {
                                it.removeViewAt(index)
                                it.addView(binding.loadingContainer, 0) // En alta ekle (index 0 = en altta)
                            }
                        }
                    }
                    
                    // Loading kapandığında PlayerView'ın görünür olduğundan emin ol
                    if (!isLoading) {
                        // PlayerView visibility ve alpha kontrolü
                        if (binding.playerView.visibility != android.view.View.VISIBLE) {
                            binding.playerView.visibility = android.view.View.VISIBLE
                        }
                        if (binding.playerView.alpha != 1.0f) {
                            binding.playerView.alpha = 1.0f
                        }
                        
                        // PlayerView'ı öne getir ve elevation'ı ayarla
                        binding.playerView.elevation = 0f
                        
                        // Z-order ayarı - SurfaceView için
                        try {
                            val videoSurfaceView = binding.playerView.videoSurfaceView
                            if (videoSurfaceView is android.view.SurfaceView) {
                                videoSurfaceView.setZOrderOnTop(false)
                                videoSurfaceView.setZOrderMediaOverlay(false)
                            }
                        } catch (e: Exception) {
                            // SurfaceView z-order ayarlanamadı
                        }
                        
                        binding.playerView.requestLayout()
                        binding.playerView.invalidate()
                        
                        // PlayerView'a player'ı tekrar set et (eğer set edilmemişse) - Main thread'de
                        val player = viewModel.getPlayer()
                        if (player != null) {
                            if (binding.playerView.player != player) {
                                binding.playerView.player = player
                                binding.playerView.setKeepContentOnPlayerReset(true)
                            }
                            // Player'ın playWhenReady durumunu kontrol et
                            if (!player.isPlaying && player.playWhenReady) {
                                player.play()
                            }
                            
                            // Loading kapandığında ve video hazırsa playback status'u gizle
                            val playbackState = player.playbackState
                            if (playbackState == Player.STATE_READY || player.isPlaying) {
                                binding.tvPlaybackStatus.visibility = android.view.View.GONE
                            }
                        }
                    }
                }
            }
        }
    }

    private fun observeLoadingMessage() {
        lifecycleScope.launch {
            viewModel.loadingMessage.collect { message ->
                Timber.tag("LoadingDebug").d("observeLoadingMessage: message='$message', container.visibility=${binding.loadingContainer.visibility}")
                binding.loadingMessage.text = message
                // Eğer loading açıksa mesajı göster
                if (viewModel.isLoading.value && binding.loadingContainer.visibility != android.view.View.VISIBLE) {
                    binding.loadingContainer.visibility = android.view.View.VISIBLE
                    binding.loadingMessage.visibility = android.view.View.VISIBLE
                    Timber.tag("LoadingDebug").d("observeLoadingMessage: Made container visible because loading=true")
                }
            }
        }
    }

    private fun observeLoadingState() {
        lifecycleScope.launch {
            viewModel.isLoading.collect { isLoading ->
                withContext(Dispatchers.Main) {
                    binding.loadingContainer.visibility = if (isLoading) android.view.View.VISIBLE else android.view.View.GONE
                    Timber.tag("LoadingDebug").d("UI Updated: visibility=${binding.loadingContainer.visibility}")
                }
            }
        }
    }

    private fun observeDuration() {
        lifecycleScope.launch {
            viewModel.duration.collect { duration ->
                if (duration != null && duration > 0) {
                    val contentId = intent.getStringExtra(PlayerIntentHandler.EXTRA_CONTENT_ID)
                    val channelId = playbackCoordinator?.getChannelId()
                    if (contentId != null && channelId == null) {
                        playbackCoordinator?.loadWatchedPosition(contentId)
                    }
                }
            }
        }
    }

    private fun observeCurrentMediaItem() {
        lifecycleScope.launch {
            viewModel.currentMediaItem.collect { mediaItem ->
                mediaItem?.let {
                    val channelName = it.mediaMetadata.title?.toString()
                    val channelIdFromTag = viewModel.getCurrentChannelId()

                    if (channelName != null && channelIdFromTag != null) {
                        val currentChannelId = playbackCoordinator?.getChannelId()
                        val isChannelChanged = currentChannelId != null && currentChannelId != channelIdFromTag

                        viewModel.handleAction(PlayerAction.StopWatching)
                        viewModel.handleAction(PlayerAction.StartWatching(channelIdFromTag))

                        binding.playerControlView.setContentInfo(channelName, null)
                        playbackCoordinator?.setChannelId(channelIdFromTag)
                        panelCoordinator?.updateChannelListPanelCurrentChannelId(channelIdFromTag)

                        if (isChannelChanged) {
                            // Yeni kanal başladığında flag'i sıfırla
                            hasShownInitialControls = false
                            initialControlsHideJob?.cancel()
                            binding.playerControlView.showControls()
                        }
                    }
                }
            }
        }
    }

    /**
     * Player hazır olduğunda PlayerView'a player'ı set eder
     * Tüm surface atamaları Main thread'de yapılır
     */
    private fun observePlayerReady() {
        lifecycleScope.launch {
            viewModel.isBuffering.collect { isBuffering ->
                val player = viewModel.getPlayer()
                if (player != null && !isBuffering) {
                    // Player hazır ve buffering yok, PlayerView'a set et - Main thread'de
                    withContext(Dispatchers.Main) {
                        if (binding.playerView.player != player) {
                            binding.playerView.player = player
                            binding.playerView.setKeepContentOnPlayerReset(true)
                            binding.playerView.visibility = android.view.View.VISIBLE
                            binding.playerView.alpha = 1.0f
                            binding.playerView.requestLayout()
                            binding.playerView.invalidate()
                        }
                    }
                }
            }
        }
        
        // Ayrıca isPlaying state'ini de kontrol et
        lifecycleScope.launch {
            viewModel.isPlaying.collect { isPlaying ->
                val player = viewModel.getPlayer()
                if (player != null && isPlaying) {
                    // Player oynuyor, PlayerView'a set et - Main thread'de
                    withContext(Dispatchers.Main) {
                        if (binding.playerView.player != player) {
                            binding.playerView.player = player
                            binding.playerView.setKeepContentOnPlayerReset(true)
                            binding.playerView.visibility = android.view.View.VISIBLE
                            binding.playerView.alpha = 1.0f
                            binding.playerView.requestLayout()
                            binding.playerView.invalidate()
                        }
                    }
                }
            }
        }
    }
}
