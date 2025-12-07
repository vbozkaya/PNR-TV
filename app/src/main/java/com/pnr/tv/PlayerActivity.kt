package com.pnr.tv

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.pnr.tv.databinding.ActivityPlayerBinding
import com.pnr.tv.ui.player.PlayerViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PlayerActivity : BaseActivity() {
    companion object {
        const val EXTRA_VIDEO_URL = "extra_video_url"
        const val EXTRA_CHANNEL_ID = "extra_channel_id"
        const val EXTRA_CONTENT_ID = "extra_content_id" // Film/Bölüm ID (kaldığı yerden devam için)
        const val EXTRA_CONTENT_TITLE = "extra_content_title" // Film/Dizi/Bölüm adı
        const val EXTRA_CONTENT_RATING = "extra_content_rating" // IMDB puanı
        const val RESULT_CHANNEL_ID = "result_channel_id"
    }

    private lateinit var binding: ActivityPlayerBinding
    private val viewModel: PlayerViewModel by viewModels()
    private var channelId: Int? = null
    
    // Progress güncelleme döngüsü için
    private val activityScope = CoroutineScope(Dispatchers.Main)
    private var progressUpdateJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Intent'ten verileri al
        val videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL)
        val contentId = intent.getStringExtra(EXTRA_CONTENT_ID)
        val contentTitle = intent.getStringExtra(EXTRA_CONTENT_TITLE)
        val contentRating = intent.getDoubleExtra(EXTRA_CONTENT_RATING, -1.0).takeIf { it > 0 }
        channelId = intent.getIntExtra(EXTRA_CHANNEL_ID, -1).takeIf { it != -1 }
        
        // İçerik başlığı ve IMDB puanını ayarla
        timber.log.Timber.d("🔍 onCreate: playerControlView null mu? ${binding.playerControlView == null}")
        if (binding.playerControlView != null) {
            binding.playerControlView.setContentInfo(contentTitle, contentRating)

            // PlayerControlView listener'ını ayarla
            binding.playerControlView.setPlayerControlListener(object : PlayerControlListener {
            override fun onPlayClicked() {
                viewModel.play()
            }
            override fun onPauseClicked() {
                viewModel.pause()
            }
            override fun onSeekTo(newPositionMs: Long) {
                viewModel.seekTo(newPositionMs)
            }
            override fun isPlayingState(): Boolean = viewModel.isPlaying.value
            override fun getDuration(): Long = viewModel.duration.value ?: 0L
            override fun getCurrentPosition(): Long = viewModel.getPlayer()?.currentPosition ?: 0L
            })
        } else {
            timber.log.Timber.e("❌ onCreate: playerControlView NULL!")
        }

        // ViewModel state flows'ları gözlemle
        observeViewModelState()
        observePlaybackState()

        // Back button callback
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    finishWithResult()
                }
            },
        )

        // Video URL'i ViewModel'e gönder
        timber.log.Timber.d("🎬 Video URL: $videoUrl, Content ID: $contentId")
        if (!videoUrl.isNullOrBlank()) {
            viewModel.playVideo(videoUrl, contentId)
            channelId?.let {
                viewModel.startWatching(it)
            }
        } else {
            timber.log.Timber.e("❌ Video URL bulunamadı!")
            Toast.makeText(this, "Video URL bulunamadı", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onStart() {
        super.onStart()
        val exoPlayer = viewModel.getPlayer()
        timber.log.Timber.d("🔍 onStart: ExoPlayer null mu? ${exoPlayer == null}")
        binding.playerView.player = exoPlayer
        if (exoPlayer != null) {
            timber.log.Timber.d("✅ ExoPlayer PlayerView'a atandı")
        } else {
            timber.log.Timber.e("❌ ExoPlayer NULL! Video oynatılamayacak!")
        }
    }

    override fun onStop() {
        super.onStop()
        // onStop'ta player'ı null yapmak yerine, activity görünür olmadığında oynatmayı durdur
        viewModel.pause()
    }

    override fun onResume() {
        super.onResume()
        // Activity tekrar görünür olduğunda oynatmaya devam et
        viewModel.play()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Progress güncelleme döngüsünü durdur
        stopProgressUpdater()
        // Coroutine scope'u temizle
        activityScope.cancel()
        // PlayerView'ı null yapmamız, memory leak önlemek adına iyi bir pratik olabilir.
        // ViewModel onCleared'da player'ı tamamen serbest bırakacak.
        binding.playerView.player = null
    }

    private fun observeViewModelState() {
        lifecycleScope.launch {
            viewModel.isPlaying.collect { isPlaying ->
                // Play/Stop butonlarının görünürlüğünü güncelle
                binding.playerControlView.updatePlayStopButtons(isPlaying)
                if (isPlaying) {
                    startProgressUpdater() // Oynatma başlayınca döngüyü başlat
                } else {
                    stopProgressUpdater() // Duraklayınca döngüyü durdur
                }
            }
        }

        lifecycleScope.launch {
            viewModel.isBuffering.collect { isBuffering ->
                // UI güncellemeleri
            }
        }

        lifecycleScope.launch {
            viewModel.errorMessage.collect { errorMessage ->
                errorMessage?.let {
                    Toast.makeText(this@PlayerActivity, it, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Geri tuşu - Panel kapalıyken aktiviteyi kapat
        if (keyCode == KeyEvent.KEYCODE_BACK && !binding.playerControlView.isVisible()) {
            finishWithResult()
            return true
        }
        
        // Panel kapalıyken D-Pad tuşlarına basıldığında paneli aç
        // Panel açıldıktan sonra tüm yönetim PlayerControlView'a bırakılır
        if (!binding.playerControlView.isVisible()) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_DPAD_CENTER -> {
                    timber.log.Timber.d("📱 Kontrol paneli kapalıydı, gösteriliyor...")
                    binding.playerControlView.showControls()
                    // Olayı tüketmiyoruz, PlayerControlView ilk açılış odağını ayarlayabilsin
                    return super.onKeyDown(keyCode, event)
                }
            }
        }
        
        // Panel açıksa veya diğer tuşlar için - PlayerControlView'a bırak
        return super.onKeyDown(keyCode, event)
    }

    private fun observePlaybackState() {
        // Current position değişikliklerini dinle
        // NOT: Artık progress güncellemesi startProgressUpdater() tarafından yapılıyor
        // Bu observer'ı kaldırmadık çünkü başka yerlerde kullanılıyor olabilir
        // Ancak updateProgress çağrısını kaldırdık
    }
    
    private fun startProgressUpdater() {
        // Zaten çalışan bir döngü varsa, tekrar başlatma
        if (progressUpdateJob?.isActive == true) return

        progressUpdateJob = activityScope.launch {
            while (isActive) {
                // Sadece player oynuyorsa ve kullanıcı seek yapmıyorsa güncelle
                if (viewModel.isPlaying.value && !binding.playerControlView.isSeeking()) {
                    val currentPosition = viewModel.getPlayer()?.currentPosition ?: 0L
                    binding.playerControlView.updateProgress(currentPosition)
                }
                delay(500) // Saniyede iki kez güncelleme yeterli
            }
        }
    }

    private fun stopProgressUpdater() {
        progressUpdateJob?.cancel()
        progressUpdateJob = null
    }

    private fun finishWithResult() {
        val resultIntent = Intent().apply {
            channelId?.let { putExtra(RESULT_CHANNEL_ID, it) }
        }
        setResult(RESULT_OK, resultIntent)
        finish()
    }
}
