package com.pnr.tv.ui.player

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.pnr.tv.core.base.BaseActivity
import com.pnr.tv.R
import com.pnr.tv.databinding.ActivityPlayerBinding
import com.pnr.tv.domain.BuildLiveStreamUrlUseCase
import com.pnr.tv.extensions.showCustomToast
import com.pnr.tv.repository.ContentRepository
import com.pnr.tv.util.validation.IntentValidator
import com.pnr.tv.ui.player.component.PlayerControlListener
import com.pnr.tv.ui.player.coordinator.PlayerPanelCoordinator
import com.pnr.tv.ui.player.coordinator.PlayerPlaybackCoordinator
import com.pnr.tv.ui.player.handler.PlayerIntentHandler
import com.pnr.tv.ui.player.handler.PlayerKeyHandler
import com.pnr.tv.ui.player.handler.PlayerObserverHandler
import com.pnr.tv.ui.player.handler.PlayerData
import com.pnr.tv.ui.player.state.PlayerAction
import com.pnr.tv.premium.PremiumManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PlayerActivity : BaseActivity() {
    private lateinit var binding: ActivityPlayerBinding
    private val viewModel: PlayerViewModel by viewModels()

    private var panelCoordinator: PlayerPanelCoordinator? = null
    private var playbackCoordinator: PlayerPlaybackCoordinator? = null
    private var observerHandler: PlayerObserverHandler? = null

    private var progressUpdateJob: Job? = null

    @Inject
    lateinit var contentRepository: ContentRepository

    @Inject
    lateinit var buildLiveStreamUrlUseCase: BuildLiveStreamUrlUseCase

    @Inject
    lateinit var premiumManager: PremiumManager

    private var keyHandler: PlayerKeyHandler? = null

    override fun shouldShowAds(): Boolean = false

    override fun shouldLoadBackground(): Boolean = false

    override fun shouldSetupNavbar(): Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Loading container'ı en üste getir ve görünür yap
        binding.loadingContainer.bringToFront()
        binding.loadingContainer.visibility = android.view.View.VISIBLE
        binding.loadingContainer.elevation = 50f
        // Child view'ların görünür olduğundan emin ol
        binding.loadingProgress.visibility = android.view.View.VISIBLE
        binding.loadingMessage.visibility = android.view.View.VISIBLE

        panelCoordinator =
            PlayerPanelCoordinator(
                binding = binding,
                viewModel = viewModel,
                lifecycleScope = lifecycleScope,
                contentRepository = contentRepository,
                context = this,
            )
        panelCoordinator?.setupPanels()

        playbackCoordinator =
            PlayerPlaybackCoordinator(
                binding = binding,
                viewModel = viewModel,
                lifecycleScope = lifecycleScope,
                contentRepository = contentRepository,
                buildLiveStreamUrlUseCase = buildLiveStreamUrlUseCase,
                intent = intent,
                context = this,
                premiumManager = premiumManager,
            )

        val validationResult = IntentValidator.validatePlayerIntent(intent)
        if (!validationResult.isValid) {
            showCustomToast(validationResult.errorMessage ?: getString(R.string.error_invalid_intent_data), Toast.LENGTH_LONG)
            finish()
            return
        }

        val playerData = PlayerIntentHandler.extractPlayerData(intent)

        playbackCoordinator?.setChannelId(playerData.channelId)
        playbackCoordinator?.setCategoryId(playerData.categoryId)

        if (playerData.episodeId != null) {
            viewModel.handleAction(
                PlayerAction.SetEpisodeInfo(
                    playerData.episodeId,
                    playerData.seriesId,
                    playerData.seasonNumber,
                    playerData.episodeNumber,
                ),
            )
        }

        binding.playerControlView.setContentInfo(playerData.contentTitle, playerData.contentRating)
        binding.playerControlView.setLiveStreamMode(playerData.isLiveStream)

        binding.playerControlView.setSettingsPanelOpenCallback {
            panelCoordinator?.isSettingsPanelVisible() == true
        }

        if (!playerData.isLiveStream && playerData.contentId != null) {
            playbackCoordinator?.loadWatchedPosition(playerData.contentId)
        }

        binding.playerControlView.setPlayerControlListener(
            object : PlayerControlListener {
                override fun onPlayClicked() {
                    viewModel.handleAction(PlayerAction.Play)
                }
                override fun onPauseClicked() {
                    viewModel.handleAction(PlayerAction.Pause)
                }
                override fun onSeekTo(newPositionMs: Long) {
                    viewModel.handleAction(PlayerAction.SeekTo(newPositionMs))
                }
                override fun onSubtitleClicked() {
                    panelCoordinator?.showSettingsPanel()
                }
                override fun onSpeakClicked() {
                    panelCoordinator?.showAudioPanel()
                }
                override fun onChannelListRequested() {
                    panelCoordinator?.showChannelListPanel(
                        playbackCoordinator?.getCategoryId(),
                        playbackCoordinator?.getChannelId(),
                    )
                }
                override fun isPlayingState(): Boolean = viewModel.isPlaying.value
                override fun getDuration(): Long = viewModel.duration.value ?: 0L
                override fun getCurrentPosition(): Long = viewModel.getPlayer()?.currentPosition ?: 0L
            },
        )

        observerHandler =
            PlayerObserverHandler(
                binding = binding,
                viewModel = viewModel,
                lifecycleScope = lifecycleScope,
                panelCoordinator = panelCoordinator,
                playbackCoordinator = playbackCoordinator,
                intent = intent,
                activity = this,
                finishWithResult = ::finishWithResult,
            )
        observerHandler?.observeViewModelState()
        
        // onRenderedFirstFrame callback'ini set et - video başladığında kontrol barını göster
        viewModel.onRenderedFirstFrameCallback = {
            // Garanti önlem olarak loading container'ı gizle
            binding.loadingContainer.visibility = android.view.View.GONE
            observerHandler?.showControlsOnPlaybackStart()
        }

        // isPlaying değişimlerinde progress loop'u başlat/durdur
        viewModel.onIsPlayingChangedCallback = { isPlaying ->
            if (isPlaying) {
                startProgressLoop()
            } else {
                stopProgressLoop()
            }
        }
        
        // Kullanıcı manuel olarak panel açtığında timer'ı iptal et
        binding.playerControlView.setOnControlsShownManuallyCallback {
            observerHandler?.cancelInitialControlsTimer()
        }

        keyHandler =
            PlayerKeyHandler(
                settingsPanel = panelCoordinator?.getSettingsPanel(),
                channelListPanel = panelCoordinator?.getChannelListPanel(),
                playerControlView = binding.playerControlView,
                viewModel = viewModel,
                getChannelId = { playbackCoordinator?.getChannelId() },
                getCategoryId = { playbackCoordinator?.getCategoryId() },
                window = window,
                settingsPanelBinding = panelCoordinator?.getSettingsPanelBinding()!!,
                finishWithResult = ::finishWithResult,
                showChannelListPanel = {
                    panelCoordinator?.showChannelListPanel(
                        playbackCoordinator?.getCategoryId(),
                        playbackCoordinator?.getChannelId(),
                    )
                },
            )

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    // Önce panelleri kontrol et - eğer herhangi bir panel açıksa kapat
                    val isSettingsPanelOpen = panelCoordinator?.isSettingsPanelVisible() == true
                    val isChannelListPanelOpen = panelCoordinator?.isChannelListPanelVisible() == true
                    
                    if (isSettingsPanelOpen) {
                        // Ayarlar paneli açıksa kapat (ses veya altyazı paneli)
                        val settingsPanel = panelCoordinator?.getSettingsPanel()
                        if (settingsPanel?.isAudioPanelOpen() == true) {
                            settingsPanel.hideAudioPanel()
                        } else {
                            settingsPanel?.hideSubtitlePanel()
                        }
                        // Panel kapatıldı, Activity'den çıkma
                        return
                    }
                    
                    if (isChannelListPanelOpen) {
                        // Kanal listesi paneli açıksa kapat
                        panelCoordinator?.hideChannelListPanel()
                        // Panel kapatıldı, Activity'den çıkma
                        return
                    }
                    
                    // Hiçbir panel açık değilse Activity'den çık
                    finishWithResult()
                }
            },
        )

        // PlayerView'a player'ı hemen ata (eğer player zaten initialize edilmişse) - Main thread'de
        val initialPlayer = viewModel.getPlayer()
        if (initialPlayer != null) {
            lifecycleScope.launch(Dispatchers.Main) {
                binding.playerView.visibility = android.view.View.VISIBLE
                binding.playerView.alpha = 1.0f
                binding.playerView.elevation = 0f
                
                try {
                    val videoSurfaceView = binding.playerView.videoSurfaceView
                    if (videoSurfaceView is android.view.SurfaceView) {
                        videoSurfaceView.setZOrderOnTop(false)
                        videoSurfaceView.setZOrderMediaOverlay(false)
                    }
                } catch (e: Exception) {
                    // SurfaceView z-order ayarlanamadı
                }
                
                binding.playerView.player = initialPlayer
                binding.playerView.setKeepContentOnPlayerReset(true)
                
                // Altyazı görünürlüğü kontrolü - SubtitleView ayarlarını resetle ve görünür yap
                try {
                    binding.playerView.subtitleView?.let { subtitleView ->
                        subtitleView.visibility = android.view.View.VISIBLE
                        subtitleView.setUserDefaultStyle()
                        subtitleView.setUserDefaultTextSize()
                        // Görünürlüğü garanti et
                        subtitleView.alpha = 1.0f
                    }
                } catch (e: Exception) {
                    // SubtitleView ayarlanamadı
                }
                
                binding.playerView.requestLayout()
                binding.playerView.invalidate()
            }
        }

        playbackCoordinator?.initialize(playerData)
    }

    override fun onStart() {
        super.onStart()
        playbackCoordinator?.onStart()
    }

    override fun onPause() {
        super.onPause()
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        viewModel.handleAction(PlayerAction.Pause)
        stopProgressLoop()
    }

    override fun onStop() {
        super.onStop()
        playbackCoordinator?.onStop()
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onResume() {
        super.onResume()
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        playbackCoordinator?.onResume()
        if (viewModel.isPlaying.value) {
            startProgressLoop()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            lifecycleScope.launch(Dispatchers.Main) {
                val player = viewModel.getPlayer()
                if (player != null) {
                    binding.playerView.visibility = android.view.View.VISIBLE
                    binding.playerView.alpha = 1.0f
                    binding.playerView.elevation = 0f
                    
                    try {
                        val videoSurfaceView = binding.playerView.videoSurfaceView
                        if (videoSurfaceView is android.view.SurfaceView) {
                            videoSurfaceView.setZOrderOnTop(false)
                            videoSurfaceView.setZOrderMediaOverlay(false)
                        }
                    } catch (e: Exception) {
                        // SurfaceView z-order ayarlanamadı
                    }
                    
                    if (binding.playerView.player != player) {
                        binding.playerView.player = player
                        binding.playerView.setKeepContentOnPlayerReset(true)
                        
                        // Altyazı görünürlüğü kontrolü - SubtitleView ayarlarını resetle ve görünür yap
                        try {
                            binding.playerView.subtitleView?.let { subtitleView ->
                                subtitleView.visibility = android.view.View.VISIBLE
                                subtitleView.setUserDefaultStyle()
                                subtitleView.setUserDefaultTextSize()
                                // Görünürlüğü garanti et
                                subtitleView.alpha = 1.0f
                            }
                        } catch (e: Exception) {
                            // SubtitleView ayarlanamadı
                        }
                    }
                    binding.playerView.requestLayout()
                    binding.playerView.invalidate()
                    if (player.playWhenReady && !player.isPlaying) {
                        player.play()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        playbackCoordinator?.onDestroy()
        stopProgressLoop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val playerData = PlayerIntentHandler.extractPlayerData(intent)
        val savedChannelId = playbackCoordinator?.getChannelId()

        outState.putString("saved_video_url", playerData.videoUrl)
        outState.putString("saved_content_id", playerData.contentId)
        outState.putString("saved_content_title", playerData.contentTitle)
        playerData.contentRating?.let { outState.putDouble("saved_content_rating", it) }
        savedChannelId?.let { outState.putInt("saved_channel_id", it) }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
    }

    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent?,
    ): Boolean {
        return keyHandler?.handleKeyDown(keyCode, event) ?: super.onKeyDown(keyCode, event)
    }

    private fun finishWithResult() {
        if (isFinishing || isDestroyed) {
            return
        }
        val resultIntent =
            Intent().apply {
                playbackCoordinator?.getChannelId()?.let { putExtra(PlayerIntentHandler.RESULT_CHANNEL_ID, it) }
            }
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    private fun startProgressLoop() {
        if (progressUpdateJob?.isActive == true) return
        progressUpdateJob =
            lifecycleScope.launch(Dispatchers.Main) {
                while (isActive) {
                    val player = viewModel.getPlayer()
                    if (player != null && player.duration > 0) {
                        binding.playerControlView.updateProgressWithDuration(
                            position = player.currentPosition,
                            duration = player.duration,
                        )
                    }
                    kotlinx.coroutines.delay(com.pnr.tv.core.constants.UIConstants.DelayDurations.PLAYER_CONTROL_UPDATE_INTERVAL_MS)
                }
            }
    }

    private fun stopProgressLoop() {
        progressUpdateJob?.cancel()
        progressUpdateJob = null
    }
}
