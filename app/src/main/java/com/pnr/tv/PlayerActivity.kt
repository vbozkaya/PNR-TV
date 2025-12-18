package com.pnr.tv

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.pnr.tv.databinding.ActivityPlayerBinding
import com.pnr.tv.databinding.PlayerSettingsPanelBinding
import com.pnr.tv.domain.BuildLiveStreamUrlUseCase
import com.pnr.tv.repository.ContentRepository
import com.pnr.tv.ui.player.PlayerViewModel
import com.pnr.tv.ui.player.TrackInfo
import com.pnr.tv.ui.player.TrackSelectionAdapter
import com.pnr.tv.util.IntentValidator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PlayerActivity : BaseActivity() {
    companion object {
        const val EXTRA_VIDEO_URL = "extra_video_url"
        const val EXTRA_CHANNEL_ID = "extra_channel_id"
        const val EXTRA_CATEGORY_ID = "extra_category_id" // Canlı yayın kategorisi ID (kanal değişimi için)
        const val EXTRA_CONTENT_ID = "extra_content_id" // Film/Bölüm ID (kaldığı yerden devam için)
        const val EXTRA_CONTENT_TITLE = "extra_content_title" // Film/Dizi/Bölüm adı
        const val EXTRA_CONTENT_RATING = "extra_content_rating" // IMDB puanı
        const val RESULT_CHANNEL_ID = "result_channel_id"
    }

    private lateinit var binding: ActivityPlayerBinding
    private lateinit var settingsPanelBinding: PlayerSettingsPanelBinding
    private val viewModel: PlayerViewModel by viewModels()

    @Inject
    lateinit var contentRepository: ContentRepository

    @Inject
    lateinit var buildLiveStreamUrlUseCase: BuildLiveStreamUrlUseCase

    private var channelId: Int? = null
    private var categoryId: Int? = null // Canlı yayın kategorisi ID

    // Video bilgilerini saklamak için (onStop'ta release edildikten sonra yeniden yüklemek için)
    private var savedVideoUrl: String? = null
    private var savedContentId: String? = null
    private var savedContentTitle: String? = null
    private var savedContentRating: Double? = null
    private var savedChannelId: Int? = null
    private var savedCategoryId: Int? = null

    // Progress güncelleme döngüsü için
    private var progressUpdateJob: Job? = null

    // Alt yazı paneli için
    private var subtitleAdapter: TrackSelectionAdapter? = null
    private var selectedSubtitleTrack: TrackInfo? = null

    // Ses paneli için
    private var audioAdapter: TrackSelectionAdapter? = null
    private var selectedAudioTrack: TrackInfo? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ekranın açık kalmasını sağla (bekleme moduna girmesini engelle)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Ayarlar paneli binding'i
        settingsPanelBinding = PlayerSettingsPanelBinding.bind(binding.root.findViewById(R.id.settings_panel_container))
        setupSubtitlePanel()

        // Intent validation - güvenlik kontrolü
        val validationResult = IntentValidator.validatePlayerIntent(intent)
        if (!validationResult.isValid) {
            timber.log.Timber.e("❌ Intent validation başarısız: ${validationResult.errorMessage}")
            Toast.makeText(this, validationResult.errorMessage ?: "Geçersiz intent verisi", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Intent'ten verileri al
        val videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL)
        val contentId = intent.getStringExtra(EXTRA_CONTENT_ID)
        val contentTitle = intent.getStringExtra(EXTRA_CONTENT_TITLE)
        val contentRating = intent.getDoubleExtra(EXTRA_CONTENT_RATING, -1.0).takeIf { it > 0 }
        channelId = intent.getIntExtra(EXTRA_CHANNEL_ID, -1).takeIf { it != -1 }
        categoryId = intent.getIntExtra(EXTRA_CATEGORY_ID, -1).takeIf { it != -1 }

        // İçerik başlığı ve IMDB puanını ayarla
        if (binding.playerControlView != null) {
            binding.playerControlView.setContentInfo(contentTitle, contentRating)

            // Canlı yayın modunu ayarla - channelId varsa canlı yayındır
            val isLiveStream = channelId != null
            binding.playerControlView.setLiveStreamMode(isLiveStream)
            timber.log.Timber.d("📺 Canlı yayın modu: $isLiveStream (channelId: $channelId)")

            // Alt yazı paneli durumu callback'i
            binding.playerControlView.setSettingsPanelOpenCallback {
                settingsPanelBinding.playerSettingsPanel.visibility == View.VISIBLE
            }

            // PlayerControlView listener'ını ayarla
            binding.playerControlView.setPlayerControlListener(
                object : PlayerControlListener {
                    override fun onPlayClicked() {
                        viewModel.play()
                    }

                    override fun onPauseClicked() {
                        viewModel.pause()
                    }

                    override fun onSeekTo(newPositionMs: Long) {
                        viewModel.seekTo(newPositionMs)
                    }

                    override fun onSubtitleClicked() {
                        showSubtitlePanel()
                    }

                    override fun onSpeakClicked() {
                        showAudioPanel()
                    }

                    override fun isPlayingState(): Boolean = viewModel.isPlaying.value

                    override fun getDuration(): Long = viewModel.duration.value ?: 0L

                    override fun getCurrentPosition(): Long = viewModel.getPlayer()?.currentPosition ?: 0L
                },
            )
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
        Log.d("PlayerActivity", "🎬 onCreate - Video URL: $videoUrl, Content ID: $contentId")
        timber.log.Timber.d("🎬 Video URL: $videoUrl, Content ID: $contentId")
        if (!videoUrl.isNullOrBlank()) {
            // Canlı yayın ise playlist ile başlat, değilse normal playVideo
            if (channelId != null && categoryId != null) {
                // Canlı yayın: Playlist oluştur ve başlat
                lifecycleScope.launch {
                    try {
                        // Aynı kategorideki tüm kanalları al
                        val channels = contentRepository.getLiveStreamsByCategoryIdSync(categoryId!!)

                        if (channels.isEmpty()) {
                            Toast.makeText(this@PlayerActivity, getString(R.string.error_video_url_not_found), Toast.LENGTH_SHORT).show()
                            finish()
                            return@launch
                        }

                        // Mevcut kanalın pozisyonunu bul
                        val startIndex = channels.indexOfFirst { it.streamId == channelId }

                        if (startIndex == -1) {
                            // Fallback: Normal playVideo kullan
                            viewModel.playVideo(videoUrl, contentId)
                            viewModel.startWatching(channelId!!)
                            return@launch
                        }

                        // Playlist oluştur
                        val mediaItems = viewModel.createPlaylistFromChannels(channels, buildLiveStreamUrlUseCase)

                        if (mediaItems.isEmpty()) {
                            // Fallback: Normal playVideo kullan
                            viewModel.playVideo(videoUrl, contentId)
                            viewModel.startWatching(channelId!!)
                            return@launch
                        }

                        // Playlist ile player'ı başlat
                        viewModel.playPlaylist(mediaItems, startIndex, null)
                        viewModel.startWatching(channelId!!)
                    } catch (e: Exception) {
                        timber.log.Timber.tag("VIDEO_PLAYBACK_ERROR").e(e, "Playlist creation failed: ${e.javaClass.simpleName}")
                        // Fallback: Normal playVideo kullan
                        viewModel.playVideo(videoUrl, contentId)
                        viewModel.startWatching(channelId!!)
                    }
                }
            } else {
                // Film/Dizi: Normal playVideo kullan
                viewModel.playVideo(videoUrl, contentId)
            }
        } else {
            Toast.makeText(this, getString(R.string.error_video_url_not_found), Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onStart() {
        super.onStart()

        val exoPlayer = viewModel.getPlayer()
        timber.log.Timber.d("🔍 onStart: ExoPlayer null mu? ${exoPlayer == null}")

        // Player null ise yeniden oluştur ve video'yu yeniden yükle
        if (exoPlayer == null && savedVideoUrl != null) {
            timber.log.Timber.d("🔄 Player null, yeniden oluşturuluyor ve video yükleniyor...")

            // Player'ı yeniden oluştur
            viewModel.reinitializePlayerIfNeeded()

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
            if (binding.playerControlView != null && contentTitle != null) {
                binding.playerControlView.setContentInfo(contentTitle, contentRating)
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
                                    viewModel.playPlaylist(mediaItems, startIndex, null)
                                    viewModel.startWatching(savedChannel)
                                    timber.log.Timber.d("✅ Canlı yayın playlist ile yeniden yüklendi")
                                } else {
                                    // Fallback: Normal playVideo
                                    viewModel.playVideo(videoUrl, contentId)
                                    viewModel.startWatching(savedChannel)
                                    timber.log.Timber.d("✅ Canlı yayın normal playVideo ile yeniden yüklendi")
                                }
                            } else {
                                // Fallback: Normal playVideo
                                viewModel.playVideo(videoUrl, contentId)
                                viewModel.startWatching(savedChannel)
                                timber.log.Timber.d("✅ Canlı yayın fallback ile yeniden yüklendi")
                            }
                        } else {
                            // Fallback: Normal playVideo
                            viewModel.playVideo(videoUrl, contentId)
                            viewModel.startWatching(savedChannel)
                            timber.log.Timber.d("✅ Canlı yayın fallback (channels empty) ile yeniden yüklendi")
                        }
                    } catch (e: Exception) {
                        timber.log.Timber.e(e, "❌ Playlist yeniden yükleme hatası")
                        // Fallback: Normal playVideo
                        viewModel.playVideo(videoUrl, contentId)
                        if (savedChannel != null) {
                            viewModel.startWatching(savedChannel)
                        }
                    }
                }
            } else {
                // Film/Dizi: Normal playVideo (pozisyon otomatik geri yüklenecek)
                viewModel.playVideo(videoUrl, contentId)
                timber.log.Timber.d("✅ Film/Dizi yeniden yüklendi (pozisyon otomatik geri yüklenecek)")
            }
        }

        // PlayerView'a player'ı ata
        val currentPlayer = viewModel.getPlayer()
        binding.playerView.player = currentPlayer

        if (currentPlayer != null) {
            timber.log.Timber.d("✅ ExoPlayer PlayerView'a atandı")
        } else {
            timber.log.Timber.w("⚠️ ExoPlayer hala NULL! Video yükleniyor olabilir...")
        }
    }

    override fun onPause() {
        super.onPause()
        // Uygulama arka plandayken player'ı pause yap
        // Ekran açık kalma flag'ini kaldır (ekran kapanabilir)
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        viewModel.pause()
        timber.log.Timber.d("⏸️ onPause: Player pause edildi, ekran açık kalma flag'i kaldırıldı")
    }

    override fun onStop() {
        super.onStop()

        // Video bilgilerini kaydet (yeniden yüklemek için)
        savedVideoUrl = intent.getStringExtra(EXTRA_VIDEO_URL)
        savedContentId = intent.getStringExtra(EXTRA_CONTENT_ID)
        savedContentTitle = intent.getStringExtra(EXTRA_CONTENT_TITLE)
        savedContentRating = intent.getDoubleExtra(EXTRA_CONTENT_RATING, -1.0).takeIf { it > 0 }
        savedChannelId = channelId
        savedCategoryId = categoryId

        // Progress updater'ı durdur
        stopProgressUpdater()

        // Player kaynaklarını TAMAMEN serbest bırak (KESİN ÇÖZÜM)
        // Bu, decoder, buffer, network thread'lerini tamamen temizler
        viewModel.releasePlayer()

        // PlayerView'ı null yap
        binding.playerView.player = null

        // Ekran açık kalma flag'ini kaldır
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        timber.log.Timber.d("⏸️ onStop: Player release edildi, tüm kaynaklar serbest bırakıldı")
    }

    override fun onResume() {
        super.onResume()

        // Ekran açık kalma flag'ini ekle
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Player varsa oynatmaya devam et
        viewModel.getPlayer()?.let {
            viewModel.play()
            timber.log.Timber.d("▶️ onResume: Player devam ediyor, ekran açık kalma flag'i eklendi")
        } ?: run {
            timber.log.Timber.d("⏳ onResume: Player henüz hazır değil, onStart'ta yükleniyor...")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Ekran açık kalma flag'ini kaldır
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Progress güncelleme döngüsünü durdur
        stopProgressUpdater()
        // PlayerView'ı null yapmamız, memory leak önlemek adına iyi bir pratik olabilir.
        // ViewModel onCleared'da player'ı tamamen serbest bırakacak.
        binding.playerView.player = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Activity state'ini kaydet - destroy edilirse restore edilebilir
        // Video URL ve diğer önemli bilgileri kaydet
        val videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL)
        val contentId = intent.getStringExtra(EXTRA_CONTENT_ID)
        val contentTitle = intent.getStringExtra(EXTRA_CONTENT_TITLE)
        val contentRating = intent.getDoubleExtra(EXTRA_CONTENT_RATING, -1.0)
        val savedChannelId = channelId

        outState.putString("saved_video_url", videoUrl)
        outState.putString("saved_content_id", contentId)
        outState.putString("saved_content_title", contentTitle)
        outState.putDouble("saved_content_rating", contentRating)
        savedChannelId?.let { outState.putInt("saved_channel_id", it) }

        timber.log.Timber.d("💾 Activity state kaydedildi - Video URL: $videoUrl")
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        // Activity restore edildiğinde state'i geri yükle
        // onCreate'te intent'ten alınan değerler zaten var, burada ek kontrol yapılabilir
        timber.log.Timber.d("🔄 Activity state geri yüklendi")
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
                    // Tek bir mesaj göster (3 saniye)
                    Toast.makeText(this@PlayerActivity, it, Toast.LENGTH_LONG).show()
                    // 3 saniye bekle ve otomatik olarak detay sayfasına geri dön
                    delay(3000)
                    // Activity durumunu kontrol et - destroy edilmişse finish çağırma
                    if (!isFinishing && !isDestroyed) {
                        finishWithResult()
                    }
                }
            }
        }

        // MediaItem değişikliğini dinle (playlist kanal geçişleri için)
        lifecycleScope.launch {
            viewModel.currentMediaItem.collect { mediaItem ->
                mediaItem?.let {
                    val channelName = it.mediaMetadata.title?.toString()
                    // Tag'den kanal ID'sini al (ViewModel'de set edilmiş)
                    val channelIdFromTag = viewModel.getCurrentChannelId()

                    if (channelName != null && channelIdFromTag != null) {
                        // Kanal değişti mi kontrol et (sadece gerçek değişiklikte kontrol bar'ı göster)
                        val isChannelChanged = channelId != null && channelId != channelIdFromTag

                        // Eski kanalın izleme kaydını durdur
                        viewModel.stopWatching()

                        // Yeni kanalın izleme kaydını başlat
                        viewModel.startWatching(channelIdFromTag)

                        // UI'ı güncelle
                        binding.playerControlView.setContentInfo(channelName, null)

                        // ChannelId'yi güncelle
                        channelId = channelIdFromTag

                        // Kanal değiştiyse kontrol bar'ı göster (kullanıcı değişikliği fark etsin)
                        if (isChannelChanged) {
                            binding.playerControlView.showControls()
                        }
                    }
                }
            }
        }
    }

    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent?,
    ): Boolean {
        val isPanelOpen = settingsPanelBinding.playerSettingsPanel.visibility == View.VISIBLE

        // Page Up/Down ve Channel Up/Down - Canlı kanallar arasında geçiş (sadece canlı yayınlarda)
        // Gerçek TV kumandalarında genellikle Channel Up/Down tuşları kullanılır
        if (channelId != null && categoryId != null) {
            when (keyCode) {
                KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_CHANNEL_UP -> {
                    // Panel açıksa önce kapat
                    if (isPanelOpen) {
                        if (audioAdapter != null && settingsPanelBinding.recyclerSubtitleTracks.adapter == audioAdapter) {
                            hideAudioPanel()
                        } else {
                            hideSubtitlePanel()
                        }
                    }
                    // Önceki kanala geç (playlist kullanarak)
                    viewModel.seekToPreviousChannel()
                    return true // Olayı tüket
                }
                KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                    // Panel açıksa önce kapat
                    if (isPanelOpen) {
                        if (audioAdapter != null && settingsPanelBinding.recyclerSubtitleTracks.adapter == audioAdapter) {
                            hideAudioPanel()
                        } else {
                            hideSubtitlePanel()
                        }
                    }
                    // Sonraki kanala geç (playlist kullanarak)
                    viewModel.seekToNextChannel()
                    return true // Olayı tüket
                }
            }
        }

        // Panel açıksa (ses veya alt yazı), focus'un panel içinde kalmasını sağla
        if (isPanelOpen) {
            // BACK tuşu - paneli kapat (tek çıkış yolu)
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                // Hangi panel açıksa onu kapat
                if (audioAdapter != null && settingsPanelBinding.recyclerSubtitleTracks.adapter == audioAdapter) {
                    hideAudioPanel()
                } else {
                    hideSubtitlePanel()
                }
                return true
            }

            // Panel açıkken focus'un panel dışına çıkmasını ENGELLE
            val focusedView = window?.currentFocus
            val isFocusInPanel =
                focusedView?.let { view ->
                    val panel = settingsPanelBinding.playerSettingsPanel
                    // View panel'in içinde mi kontrol et
                    var parent: android.view.ViewParent? = view.parent
                    while (parent != null) {
                        if (parent == panel) {
                            return@let true
                        }
                        parent = parent.parent
                    }
                    false
                } ?: false

            // Focus panel dışındaysa, ilk alt yazı öğesine veya kaydet butonuna geri dön
            if (!isFocusInPanel && focusedView != null && focusedView != settingsPanelBinding.playerSettingsPanel) {
                timber.log.Timber.d("⚠️ Focus panel dışına çıkmış, geri alınıyor")
                settingsPanelBinding.playerSettingsPanel.post {
                    val firstSubtitleTrack = settingsPanelBinding.recyclerSubtitleTracks.findViewHolderForAdapterPosition(0)
                    firstSubtitleTrack?.itemView?.requestFocus() ?: settingsPanelBinding.btnSaveSettings.requestFocus()
                }
                return true
            }

            // DPAD_DOWN/UP tuşları - panel içindeki öğeler arasında gezinme
            // Focus sadece kaydet butonu veya geri tuşu ile çıkabilir
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                // Panel içindeki tuş olaylarını handle et (focus chain çalışsın)
                return super.onKeyDown(keyCode, event)
            }

            // DPAD_LEFT/RIGHT - panel içinde hiçbir şekilde çalışmasın (focus kaybını önlemek için)
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                // Panel içinde sağ/sol yön tuşlarını engelle
                return true
            }

            // Panel içindeki diğer tuş olaylarını handle et
            return super.onKeyDown(keyCode, event)
        }

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
                KeyEvent.KEYCODE_DPAD_CENTER,
                -> {
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

        progressUpdateJob =
            lifecycleScope.launch {
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
        // Activity durumunu kontrol et - destroy edilmişse finish çağırma
        if (isFinishing || isDestroyed) {
            timber.log.Timber.w("⚠️ finishWithResult: Activity zaten kapanıyor veya destroy edilmiş, finish çağrılmıyor")
            return
        }
        val resultIntent =
            Intent().apply {
                channelId?.let { putExtra(RESULT_CHANNEL_ID, it) }
            }
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    private fun setupSubtitlePanel() {
        // RecyclerView'ı focusable yapma (sadece içindeki item'lar focusable olacak)
        settingsPanelBinding.recyclerSubtitleTracks.isFocusable = false

        // Alt yazılar RecyclerView - sadece alt yazılar için
        subtitleAdapter =
            TrackSelectionAdapter(
                tracks = emptyList(),
                selectedTrack = null,
                onTrackSelected = { track ->
                    selectedSubtitleTrack = track
                    timber.log.Timber.d("📝 Alt yazı seçildi: ${track.label}")
                },
                isAudioAdapter = false,
                audioRecyclerView = null, // Ses dilleri yok
                subtitleRecyclerView = null,
                saveButton = settingsPanelBinding.btnSaveSettings,
            )
        val subtitleLayoutManager = LinearLayoutManager(this)
        settingsPanelBinding.recyclerSubtitleTracks.layoutManager = subtitleLayoutManager
        settingsPanelBinding.recyclerSubtitleTracks.adapter = subtitleAdapter

        // Alt yazılar RecyclerView için focus scroll
        setupRecyclerViewFocusScroll(settingsPanelBinding.recyclerSubtitleTracks, subtitleLayoutManager)

        // Kaydet butonu
        settingsPanelBinding.btnSaveSettings.setOnClickListener {
            saveSubtitleSettings()
        }

        // Kaydet butonuna OK tuşu desteği
        settingsPanelBinding.btnSaveSettings.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER && event.action == KeyEvent.ACTION_DOWN) {
                saveSubtitleSettings()
                true
            } else {
                false
            }
        }

        // Panel başlangıçta gizli
        settingsPanelBinding.playerSettingsPanel.visibility = View.GONE
    }

    /**
     * RecyclerView'da focus değiştiğinde otomatik scroll yapar - animasyon YOK
     */
    private fun setupRecyclerViewFocusScroll(
        recyclerView: androidx.recyclerview.widget.RecyclerView,
        layoutManager: LinearLayoutManager,
    ) {
        // Animasyonları devre dışı bırak - sadece direkt scroll
        recyclerView.itemAnimator = null

        // RecyclerView'a focus değişikliğini dinleyecek bir listener ekle
        recyclerView.addOnChildAttachStateChangeListener(
            object : androidx.recyclerview.widget.RecyclerView.OnChildAttachStateChangeListener {
                override fun onChildViewAttachedToWindow(view: View) {
                    // TrackSelectionAdapter'da zaten ekleniyor
                }

                override fun onChildViewDetachedFromWindow(view: View) {
                    // Cleanup
                }
            },
        )

        // RecyclerView'ın kendi scroll listener'ı - direkt scroll, animasyon yok
        recyclerView.addOnScrollListener(
            object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
                override fun onScrolled(
                    recyclerView: androidx.recyclerview.widget.RecyclerView,
                    dx: Int,
                    dy: Int,
                ) {
                    super.onScrolled(recyclerView, dx, dy)
                    // Scroll olduğunda focus'lu item'ın görünürlüğünü kontrol et
                    val focusedView = recyclerView.focusedChild
                    if (focusedView != null) {
                        val position = recyclerView.getChildAdapterPosition(focusedView)
                        if (position != androidx.recyclerview.widget.RecyclerView.NO_POSITION) {
                            val firstVisible = layoutManager.findFirstVisibleItemPosition()
                            val lastVisible = layoutManager.findLastVisibleItemPosition()

                            if (position < firstVisible || position > lastVisible) {
                                // Focus görünür değilse, direkt scroll yap (animasyon yok)
                                recyclerView.post {
                                    layoutManager.scrollToPositionWithOffset(position, 0)
                                }
                            }
                        }
                    }
                }
            },
        )
    }

    private fun showSubtitlePanel() {
        timber.log.Timber.d("📝 Alt yazı paneli açılıyor")

        // Panel zaten görünürse kapat
        if (settingsPanelBinding.playerSettingsPanel.visibility == View.VISIBLE) {
            hideSubtitlePanel()
            return
        }

        // Panel başlığını "Alt Yazılar" olarak ayarla
        settingsPanelBinding.txtSubtitleTitle.text = getString(R.string.player_subtitles_title)

        // Alt yazı track'lerini yükle
        loadSubtitleTracks()

        // Alt yazı adapter'ını RecyclerView'a bağla
        val subtitleLayoutManager = LinearLayoutManager(this)
        settingsPanelBinding.recyclerSubtitleTracks.layoutManager = subtitleLayoutManager
        settingsPanelBinding.recyclerSubtitleTracks.adapter = subtitleAdapter

        // Alt yazı RecyclerView için focus scroll
        setupRecyclerViewFocusScroll(settingsPanelBinding.recyclerSubtitleTracks, subtitleLayoutManager)

        // Kaydet butonunu alt yazı paneli için ayarla
        settingsPanelBinding.btnSaveSettings.setOnClickListener {
            saveSubtitleSettings()
        }
        settingsPanelBinding.btnSaveSettings.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER && event.action == KeyEvent.ACTION_DOWN) {
                saveSubtitleSettings()
                true
            } else {
                false
            }
        }

        // Panel'i göster ve animasyon başlat
        settingsPanelBinding.playerSettingsPanel.visibility = View.VISIBLE
        val slideIn = AnimationUtils.loadAnimation(this, R.anim.slide_in_right)
        settingsPanelBinding.playerSettingsPanel.startAnimation(slideIn)

        // İlk alt yazı öğesine focus ver
        settingsPanelBinding.playerSettingsPanel.post {
            val firstSubtitleTrack = settingsPanelBinding.recyclerSubtitleTracks.findViewHolderForAdapterPosition(0)
            firstSubtitleTrack?.itemView?.requestFocus() ?: settingsPanelBinding.btnSaveSettings.requestFocus()
        }
    }

    private fun hideSubtitlePanel() {
        if (settingsPanelBinding.playerSettingsPanel.visibility != View.VISIBLE) return

        val slideOut = AnimationUtils.loadAnimation(this, R.anim.slide_out_right)
        settingsPanelBinding.playerSettingsPanel.startAnimation(slideOut)

        slideOut.setAnimationListener(
            object : android.view.animation.Animation.AnimationListener {
                override fun onAnimationStart(animation: android.view.animation.Animation?) {}

                override fun onAnimationRepeat(animation: android.view.animation.Animation?) {}

                override fun onAnimationEnd(animation: android.view.animation.Animation?) {
                    settingsPanelBinding.playerSettingsPanel.visibility = View.GONE
                    // Panel kapandıktan sonra play/stop butonuna focus ver
                    binding.playerControlView.post {
                        binding.playerControlView.requestFocusOnPlayStopButton()
                    }
                }
            },
        )
    }

    private fun loadSubtitleTracks() {
        // Alt yazıları yükle - "Kapalı" seçeneği ekle
        val subtitleTracks = viewModel.getSubtitleTracks().toMutableList()
        val currentSubtitleTrack = subtitleTracks.firstOrNull { it.isSelected }

        // Eğer hiç alt yazı seçili değilse, "Kapalı" seçeneğini ekle
        if (currentSubtitleTrack == null) {
            val closedOption =
                TrackInfo(
                    groupIndex = -1,
                    trackIndex = -1,
                    language = null,
                    label = getString(R.string.player_subtitles_off),
                    isSelected = true,
                )
            subtitleTracks.add(0, closedOption)
            selectedSubtitleTrack = closedOption
        } else {
            selectedSubtitleTrack = currentSubtitleTrack
            // "Kapalı" seçeneğini ekle (seçili değil)
            val closedOption =
                TrackInfo(
                    groupIndex = -1,
                    trackIndex = -1,
                    language = null,
                    label = getString(R.string.player_subtitles_off),
                    isSelected = false,
                )
            subtitleTracks.add(0, closedOption)
        }

        subtitleAdapter?.updateTracks(subtitleTracks, selectedSubtitleTrack)

        // Focus chain'i güncelle - adapter yüklendikten sonra
        settingsPanelBinding.playerSettingsPanel.post {
            // Kaydet butonundan yukarı - alt yazıların son item'ına
            val adapter = settingsPanelBinding.recyclerSubtitleTracks.adapter
            if (adapter != null && adapter.itemCount > 0) {
                val lastSubtitleItem = settingsPanelBinding.recyclerSubtitleTracks.findViewHolderForAdapterPosition(adapter.itemCount - 1)
                lastSubtitleItem?.itemView?.let {
                    settingsPanelBinding.btnSaveSettings.nextFocusUpId = it.id
                }
            }
        }

        timber.log.Timber.d("📊 Alt yazı track'leri yüklendi: ${subtitleTracks.size}")
    }

    private fun saveSubtitleSettings() {
        timber.log.Timber.d("💾 Alt yazı ayarları kaydediliyor...")

        // Mevcut pozisyonu kaydet
        val currentPosition = viewModel.getPlayer()?.currentPosition ?: 0L

        // Alt yazıyı uygula (groupIndex -1 ise kapat)
        val subtitleToApply =
            if (selectedSubtitleTrack?.groupIndex == -1) {
                null // "Kapalı" seçildi
            } else {
                selectedSubtitleTrack
            }
        viewModel.selectSubtitleTrack(subtitleToApply)

        // Video pozisyonunu koru (kaldığı yerden devam)
        viewModel.seekTo(currentPosition)

        // Panel'i kapat (hideSubtitlePanel içinde focus yönetimi yapılıyor)
        hideSubtitlePanel()

        timber.log.Timber.d("✅ Alt yazı ayarları kaydedildi ve uygulandı")
    }

    private fun setupAudioPanel() {
        // Ses paneli için RecyclerView'ı focusable yapma (sadece içindeki item'lar focusable olacak)
        // Ses paneli açıldığında adapter ve başlık dinamik olarak değiştirilecek
        // showAudioPanel() içinde yapılıyor
    }

    private fun showAudioPanel() {
        timber.log.Timber.d("🔊 Ses paneli açılıyor")

        // Panel zaten görünürse kapat
        if (settingsPanelBinding.playerSettingsPanel.visibility == View.VISIBLE) {
            hideAudioPanel()
            return
        }

        // Panel başlığını "Ses Dili" olarak değiştir
        settingsPanelBinding.txtSubtitleTitle.text = getString(R.string.player_audio_language_title)

        // Ses track'lerini yükle
        loadAudioTracks()

        // Ses adapter'ını RecyclerView'a bağla
        val audioLayoutManager = LinearLayoutManager(this)
        settingsPanelBinding.recyclerSubtitleTracks.layoutManager = audioLayoutManager
        settingsPanelBinding.recyclerSubtitleTracks.adapter = audioAdapter

        // Ses RecyclerView için focus scroll
        setupRecyclerViewFocusScroll(settingsPanelBinding.recyclerSubtitleTracks, audioLayoutManager)

        // Kaydet butonunu ses paneli için ayarla
        settingsPanelBinding.btnSaveSettings.setOnClickListener {
            saveAudioSettings()
        }
        settingsPanelBinding.btnSaveSettings.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER && event.action == KeyEvent.ACTION_DOWN) {
                saveAudioSettings()
                true
            } else {
                false
            }
        }

        // Panel'i göster ve animasyon başlat
        settingsPanelBinding.playerSettingsPanel.visibility = View.VISIBLE
        val slideIn = AnimationUtils.loadAnimation(this, R.anim.slide_in_right)
        settingsPanelBinding.playerSettingsPanel.startAnimation(slideIn)

        // İlk ses track'ine focus ver
        settingsPanelBinding.playerSettingsPanel.post {
            val firstAudioTrack = settingsPanelBinding.recyclerSubtitleTracks.findViewHolderForAdapterPosition(0)
            firstAudioTrack?.itemView?.requestFocus() ?: settingsPanelBinding.btnSaveSettings.requestFocus()
        }
    }

    private fun hideAudioPanel() {
        if (settingsPanelBinding.playerSettingsPanel.visibility != View.VISIBLE) return

        val slideOut = AnimationUtils.loadAnimation(this, R.anim.slide_out_right)
        settingsPanelBinding.playerSettingsPanel.startAnimation(slideOut)

        slideOut.setAnimationListener(
            object : android.view.animation.Animation.AnimationListener {
                override fun onAnimationStart(animation: android.view.animation.Animation?) {}

                override fun onAnimationRepeat(animation: android.view.animation.Animation?) {}

                override fun onAnimationEnd(animation: android.view.animation.Animation?) {
                    settingsPanelBinding.playerSettingsPanel.visibility = View.GONE
                    // Panel kapandıktan sonra play/stop butonuna focus ver
                    binding.playerControlView.post {
                        binding.playerControlView.requestFocusOnPlayStopButton()
                    }
                }
            },
        )
    }

    private fun loadAudioTracks() {
        // Ses track'lerini yükle
        val audioTracks = viewModel.getAudioTracks()
        val currentAudioTrack = audioTracks.firstOrNull { it.isSelected }
        selectedAudioTrack = currentAudioTrack

        // Ses adapter'ını oluştur
        audioAdapter =
            TrackSelectionAdapter(
                tracks = audioTracks,
                selectedTrack = currentAudioTrack,
                onTrackSelected = { track ->
                    selectedAudioTrack = track
                    timber.log.Timber.d("🔊 Ses dili seçildi: ${track.label}")
                },
                isAudioAdapter = true,
                audioRecyclerView = null,
                subtitleRecyclerView = null,
                saveButton = settingsPanelBinding.btnSaveSettings,
            )

        // Focus chain'i güncelle
        settingsPanelBinding.playerSettingsPanel.post {
            val adapter = settingsPanelBinding.recyclerSubtitleTracks.adapter
            if (adapter != null && adapter.itemCount > 0) {
                val lastAudioItem = settingsPanelBinding.recyclerSubtitleTracks.findViewHolderForAdapterPosition(adapter.itemCount - 1)
                lastAudioItem?.itemView?.let {
                    settingsPanelBinding.btnSaveSettings.nextFocusUpId = it.id
                }
            }
        }

        timber.log.Timber.d("📊 Ses track'leri yüklendi: ${audioTracks.size}")
    }

    private fun saveAudioSettings() {
        timber.log.Timber.d("💾 Ses ayarları kaydediliyor...")

        // Mevcut pozisyonu kaydet
        val currentPosition = viewModel.getPlayer()?.currentPosition ?: 0L

        // Ses dilini uygula
        selectedAudioTrack?.let { track ->
            viewModel.selectAudioTrack(track)
        }

        // Video pozisyonunu koru (kaldığı yerden devam)
        viewModel.seekTo(currentPosition)

        // Panel'i kapat
        hideAudioPanel()

        // Panel kapandıktan sonra play/stop butonuna focus ver
        binding.playerControlView.post {
            binding.playerControlView.requestFocusOnPlayStopButton()
        }

        timber.log.Timber.d("✅ Ses ayarları kaydedildi ve uygulandı")
    }
}
