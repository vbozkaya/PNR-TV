package com.pnr.tv.ui.player.panel

import android.content.Context
import android.view.View
import android.view.animation.AnimationUtils
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.pnr.tv.R
import com.pnr.tv.databinding.PlayerSettingsPanelBinding
import com.pnr.tv.ui.player.PlayerViewModel
import com.pnr.tv.ui.player.adapter.TrackSelectionAdapter
import com.pnr.tv.ui.player.manager.PlayerTrackManager
import com.pnr.tv.ui.player.state.TrackInfo

/**
 * Ayarlar paneli (Alt Yazı ve Ses Dili) yönetimi için helper sınıf.
 * PlayerActivity'den ayarlar paneli mantığını ayırmak için oluşturulmuştur.
 */
class PlayerSettingsPanel(
    private val binding: PlayerSettingsPanelBinding,
    private val context: Context,
    private val viewModel: PlayerViewModel,
    private val onSubtitleButtonFocus: () -> Unit, // Panel kapandıktan sonra alt yazı butonuna focus vermek için
    private val onAudioButtonFocus: () -> Unit, // Panel kapandıktan sonra ses butonuna focus vermek için
) {
    // Helper sınıflar
    private val trackDataHelper = TrackDataHelper(context)
    private val tvFocusManager = TvFocusManager()

    // Alt yazı paneli için
    private var subtitleAdapter: TrackSelectionAdapter? = null
    private var selectedSubtitleTrack: TrackInfo? = null

    // Ses paneli için
    private var audioAdapter: TrackSelectionAdapter? = null
    private var selectedAudioTrack: TrackInfo? = null

    init {
        setupSubtitlePanel()
    }

    /**
     * Panel'in görünür olup olmadığını döndürür.
     */
    fun isVisible(): Boolean {
        return binding.playerSettingsPanel.visibility == View.VISIBLE
    }

    /**
     * Şu anda ses paneli mi açık kontrol eder.
     */
    fun isAudioPanelOpen(): Boolean {
        return isVisible() && audioAdapter != null && binding.recyclerSubtitleTracks.adapter == audioAdapter
    }

    /**
     * Alt yazı panelini setup eder.
     */
    private fun setupSubtitlePanel() {
        // RecyclerView'ı focusable yapma (sadece içindeki item'lar focusable olacak)
        binding.recyclerSubtitleTracks.isFocusable = false

        // Alt yazılar RecyclerView - sadece alt yazılar için
        subtitleAdapter =
            TrackSelectionAdapter(
                tracksList = emptyList(),
                selectedTrack = null,
                onTrackSelected = { track ->
                    selectedSubtitleTrack = track
                    // Seçim yapıldığında anında kaydet ve paneli kapat
                    saveSubtitleSettings(closePanel = true)
                },
                isAudioAdapter = false,
                audioRecyclerView = null, // Ses dilleri yok
                subtitleRecyclerView = null,
                saveButton = null, // Kaydet butonu kaldırıldı
            )
        val subtitleLayoutManager = LinearLayoutManager(context)
        binding.recyclerSubtitleTracks.layoutManager = subtitleLayoutManager
        binding.recyclerSubtitleTracks.adapter = subtitleAdapter

        // TvFocusManager ile UI ve focus ayarlarını yapılandır
        tvFocusManager.setupRecyclerViewFocusScroll(binding.recyclerSubtitleTracks, subtitleLayoutManager)
        tvFocusManager.setupRecyclerViewFocusTrap(
            recyclerView = binding.recyclerSubtitleTracks,
            layoutManager = subtitleLayoutManager,
            onBackPressed = { hideSubtitlePanel() },
        )
        tvFocusManager.setupPanelFocusProtection(binding.playerSettingsPanel)
        
        // Yazılımsal odak kilidi - RecyclerView için setOnKeyListener
        // Sadece tuşu yutarak (consume) sistemin işlem yapmasını engelle
        binding.recyclerSubtitleTracks.setOnKeyListener { _, keyCode, event ->
            if (event?.action != android.view.KeyEvent.ACTION_DOWN) {
                return@setOnKeyListener false
            }
            
            val focusedView = binding.recyclerSubtitleTracks.focusedChild ?: return@setOnKeyListener false
            val focusedPosition = binding.recyclerSubtitleTracks.getChildAdapterPosition(focusedView)
            
            if (focusedPosition == RecyclerView.NO_POSITION) {
                return@setOnKeyListener false
            }
            
            val itemCount = binding.recyclerSubtitleTracks.adapter?.itemCount ?: 0
            
            // Son öğede aşağı basıldığında - sadece tuşu yut
            if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN && focusedPosition == itemCount - 1) {
                return@setOnKeyListener true
            }
            
            // İlk öğede yukarı basıldığında - sadece tuşu yut
            if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP && focusedPosition == 0) {
                return@setOnKeyListener true
            }
            
            false
        }

        // Kaydet butonu kaldırıldı - seçim anında uygulanıyor
        binding.btnSaveSettings.visibility = View.GONE

        // Panel başlangıçta gizli
        binding.playerSettingsPanel.visibility = View.GONE
    }

    /**
     * Ses panelini setup eder.
     * Not: Ses paneli açıldığında adapter ve başlık dinamik olarak değiştirilecek.
     */
    private fun setupAudioPanel() {
        // Ses paneli için RecyclerView'ı focusable yapma (sadece içindeki item'lar focusable olacak)
        // Ses paneli açıldığında adapter ve başlık dinamik olarak değiştirilecek
        // showAudioPanel() içinde yapılıyor
    }

    /**
     * Alt yazı panelini gösterir.
     */
    fun showSubtitlePanel() {
        if (binding.playerSettingsPanel.visibility == View.VISIBLE) {
            hideSubtitlePanel()
            return
        }

        binding.txtSubtitleTitle.text = context.getString(R.string.player_subtitles_title)

        // TrackDataHelper ile alt yazı track'lerini yükle
        loadSubtitleTracks()

        // Alt yazı adapter'ını RecyclerView'a bağla
        val subtitleLayoutManager = LinearLayoutManager(context)
        binding.recyclerSubtitleTracks.layoutManager = subtitleLayoutManager
        binding.recyclerSubtitleTracks.adapter = subtitleAdapter

        // TvFocusManager ile UI ve focus ayarlarını yapılandır
        tvFocusManager.setupRecyclerViewFocusScroll(binding.recyclerSubtitleTracks, subtitleLayoutManager)
        tvFocusManager.setupRecyclerViewFocusTrap(
            recyclerView = binding.recyclerSubtitleTracks,
            layoutManager = subtitleLayoutManager,
            onBackPressed = { hideSubtitlePanel() },
        )
        tvFocusManager.setupPanelFocusProtection(binding.playerSettingsPanel)

        // Kaydet butonu kaldırıldı - seçim anında uygulanıyor
        binding.btnSaveSettings.visibility = View.GONE

        // Panel'i önce göster ve animasyon başlat
        binding.playerSettingsPanel.visibility = View.VISIBLE
        val slideIn = AnimationUtils.loadAnimation(context, R.anim.slide_in_right)
        binding.playerSettingsPanel.startAnimation(slideIn)

        // Otomatik focus: Seçili track'e odak ver, yoksa ilk öğeye
        val tracks = subtitleAdapter?.tracks ?: emptyList()
        val targetPos = if (selectedSubtitleTrack != null) {
            tracks.indexOf(selectedSubtitleTrack).coerceAtLeast(0)
        } else {
            0
        }
        
        // RecyclerView'ın render edilmesini bekle, sonra öğeyi görünür kıl ve odağı ver
        binding.recyclerSubtitleTracks.post {
            subtitleLayoutManager.scrollToPosition(targetPos)
            // ViewHolder'ın oluşturulması için kısa bir gecikme
            binding.recyclerSubtitleTracks.post {
                val viewHolder = binding.recyclerSubtitleTracks.findViewHolderForAdapterPosition(targetPos)
                if (viewHolder != null) {
                    viewHolder.itemView.isFocusable = true
                    viewHolder.itemView.isFocusableInTouchMode = true
                    viewHolder.itemView.requestFocus()
                } else {
                    // ViewHolder henüz oluşturulmamışsa, birkaç kez dene
                    var retryCount = 0
                    val maxRetries = 5
                    val retryRunnable = object : Runnable {
                        override fun run() {
                            val holder = binding.recyclerSubtitleTracks.findViewHolderForAdapterPosition(targetPos)
                            if (holder != null) {
                                holder.itemView.isFocusable = true
                                holder.itemView.isFocusableInTouchMode = true
                                holder.itemView.requestFocus()
                            } else if (retryCount < maxRetries) {
                                retryCount++
                                binding.recyclerSubtitleTracks.postDelayed(this, 50)
                            }
                        }
                    }
                    binding.recyclerSubtitleTracks.postDelayed(retryRunnable, 50)
                }
            }
        }
    }

    /**
     * Alt yazı panelini gizler.
     */
    fun hideSubtitlePanel() {
        if (binding.playerSettingsPanel.visibility != View.VISIBLE) return

        val slideOut = AnimationUtils.loadAnimation(context, R.anim.slide_out_right)
        binding.playerSettingsPanel.startAnimation(slideOut)

        slideOut.setAnimationListener(
            object : android.view.animation.Animation.AnimationListener {
                override fun onAnimationStart(animation: android.view.animation.Animation?) {}

                override fun onAnimationRepeat(animation: android.view.animation.Animation?) {}

                override fun onAnimationEnd(animation: android.view.animation.Animation?) {
                    binding.playerSettingsPanel.visibility = View.GONE
                    // Panel kapandıktan sonra alt yazı butonuna focus ver
                    onSubtitleButtonFocus()
                }
            },
        )
    }

    /**
     * Alt yazı track'lerini yükler.
     * TrackDataHelper kullanarak veri işleme mantığını yürütür.
     */
    private fun loadSubtitleTracks() {
        // ViewModel'den alt yazı track'lerini al
        val subtitleTracks = viewModel.getSubtitleTracks()
        
        // TrackDataHelper ile işle: "Kapalı" seçeneği ekle ve seçili track'i belirle
        val result = trackDataHelper.processSubtitleTracks(subtitleTracks, selectedSubtitleTrack)
        selectedSubtitleTrack = result.selectedTrack
        
        // Adapter'ı güncelle
        subtitleAdapter?.updateTracks(result.tracks, selectedSubtitleTrack)
    }

    /**
     * Alt yazı ayarlarını kaydeder.
     * @param closePanel Panel kapatılacak mı? (varsayılan: false - panel açık kalır)
     */
    private fun saveSubtitleSettings(closePanel: Boolean = false) {
        val subtitleToApply =
            if (selectedSubtitleTrack?.groupIndex == -1) {
                null
            } else {
                selectedSubtitleTrack
            }

        subtitleAdapter?.updateSelectedTrack(selectedSubtitleTrack)
        viewModel.selectSubtitleTrack(subtitleToApply)
        
        // Seçimi yaptıktan hemen sonra paneli derhal kapat
        hideSubtitlePanel()
    }

    /**
     * Ses panelini gösterir.
     */
    fun showAudioPanel() {
        if (binding.playerSettingsPanel.visibility == View.VISIBLE) {
            hideAudioPanel()
            return
        }

        binding.txtSubtitleTitle.text = context.getString(R.string.player_audio_language_title)

        // TrackDataHelper ile ses track'lerini yükle
        loadAudioTracks()

        // Ses adapter'ını RecyclerView'a bağla
        val audioLayoutManager = LinearLayoutManager(context)
        binding.recyclerSubtitleTracks.layoutManager = audioLayoutManager
        binding.recyclerSubtitleTracks.adapter = audioAdapter

        // TvFocusManager ile UI ve focus ayarlarını yapılandır
        tvFocusManager.setupRecyclerViewFocusScroll(binding.recyclerSubtitleTracks, audioLayoutManager)
        tvFocusManager.setupRecyclerViewFocusTrap(
            recyclerView = binding.recyclerSubtitleTracks,
            layoutManager = audioLayoutManager,
            onBackPressed = { hideAudioPanel() },
        )
        tvFocusManager.setupPanelFocusProtection(binding.playerSettingsPanel)
        
        // Yazılımsal odak kilidi - RecyclerView için setOnKeyListener
        // Sadece tuşu yutarak (consume) sistemin işlem yapmasını engelle
        binding.recyclerSubtitleTracks.setOnKeyListener { _, keyCode, event ->
            if (event?.action != android.view.KeyEvent.ACTION_DOWN) {
                return@setOnKeyListener false
            }
            
            val focusedView = binding.recyclerSubtitleTracks.focusedChild ?: return@setOnKeyListener false
            val focusedPosition = binding.recyclerSubtitleTracks.getChildAdapterPosition(focusedView)
            
            if (focusedPosition == RecyclerView.NO_POSITION) {
                return@setOnKeyListener false
            }
            
            val itemCount = binding.recyclerSubtitleTracks.adapter?.itemCount ?: 0
            
            // Son öğede aşağı basıldığında - sadece tuşu yut
            if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN && focusedPosition == itemCount - 1) {
                return@setOnKeyListener true
            }
            
            // İlk öğede yukarı basıldığında - sadece tuşu yut
            if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP && focusedPosition == 0) {
                return@setOnKeyListener true
            }
            
            false
        }

        // Kaydet butonu kaldırıldı - seçim anında uygulanıyor
        binding.btnSaveSettings.visibility = View.GONE

        // Panel'i önce göster ve animasyon başlat
        binding.playerSettingsPanel.visibility = View.VISIBLE
        val slideIn = AnimationUtils.loadAnimation(context, R.anim.slide_in_right)
        binding.playerSettingsPanel.startAnimation(slideIn)

        // Otomatik focus: Seçili track'e odak ver, yoksa ilk öğeye
        val tracks = audioAdapter?.tracks ?: emptyList()
        val targetPos = if (selectedAudioTrack != null) {
            tracks.indexOf(selectedAudioTrack).coerceAtLeast(0)
        } else {
            0
        }
        
        // RecyclerView'ın render edilmesini bekle, sonra öğeyi görünür kıl ve odağı ver
        binding.recyclerSubtitleTracks.post {
            audioLayoutManager.scrollToPosition(targetPos)
            // ViewHolder'ın oluşturulması için kısa bir gecikme
            binding.recyclerSubtitleTracks.post {
                val viewHolder = binding.recyclerSubtitleTracks.findViewHolderForAdapterPosition(targetPos)
                if (viewHolder != null) {
                    viewHolder.itemView.isFocusable = true
                    viewHolder.itemView.isFocusableInTouchMode = true
                    viewHolder.itemView.requestFocus()
                } else {
                    // ViewHolder henüz oluşturulmamışsa, birkaç kez dene
                    var retryCount = 0
                    val maxRetries = 5
                    val retryRunnable = object : Runnable {
                        override fun run() {
                            val holder = binding.recyclerSubtitleTracks.findViewHolderForAdapterPosition(targetPos)
                            if (holder != null) {
                                holder.itemView.isFocusable = true
                                holder.itemView.isFocusableInTouchMode = true
                                holder.itemView.requestFocus()
                            } else if (retryCount < maxRetries) {
                                retryCount++
                                binding.recyclerSubtitleTracks.postDelayed(this, 50)
                            }
                        }
                    }
                    binding.recyclerSubtitleTracks.postDelayed(retryRunnable, 50)
                }
            }
        }
    }

    /**
     * Ses panelini gizler.
     */
    fun hideAudioPanel() {
        if (binding.playerSettingsPanel.visibility != View.VISIBLE) return

        val slideOut = AnimationUtils.loadAnimation(context, R.anim.slide_out_right)
        binding.playerSettingsPanel.startAnimation(slideOut)

        slideOut.setAnimationListener(
            object : android.view.animation.Animation.AnimationListener {
                override fun onAnimationStart(animation: android.view.animation.Animation?) {}

                override fun onAnimationRepeat(animation: android.view.animation.Animation?) {}

                override fun onAnimationEnd(animation: android.view.animation.Animation?) {
                    binding.playerSettingsPanel.visibility = View.GONE
                    // Panel kapandıktan sonra ses butonuna focus ver
                    onAudioButtonFocus()
                }
            },
        )
    }

    /**
     * Ses track'lerini yükler.
     * Panel güncellemesi için tracks değiştiğinde çağrılabilir.
     * TrackDataHelper kullanarak veri işleme mantığını yürütür.
     */
    private fun loadAudioTracks() {
        // ViewModel'den ses track'lerini al
        val audioTracks = viewModel.getAudioTracks()
        
        // TrackDataHelper ile işle: seçili track'i belirle
        val result = trackDataHelper.processAudioTracks(audioTracks, selectedAudioTrack)
        selectedAudioTrack = result.selectedTrack

        if (audioAdapter != null) {
            // Mevcut adapter'ı güncelle
            audioAdapter?.updateTracks(result.tracks, result.selectedTrack)
        } else {
            // Ses adapter'ını oluştur
            audioAdapter =
                TrackSelectionAdapter(
                    tracksList = result.tracks,
                    selectedTrack = result.selectedTrack,
                    onTrackSelected = { track ->
                        selectedAudioTrack = track
                        // Seçim yapıldığında anında kaydet ve paneli kapat
                        saveAudioSettings(closePanel = true)
                    },
                    isAudioAdapter = true,
                    audioRecyclerView = null,
                    subtitleRecyclerView = null,
                    saveButton = null, // Kaydet butonu kaldırıldı
                )
        }
    }

    /**
     * Ses ayarlarını kaydeder.
     * @param closePanel Panel kapatılacak mı? (varsayılan: false - panel açık kalır)
     */
    private fun saveAudioSettings(closePanel: Boolean = false) {
        audioAdapter?.updateSelectedTrack(selectedAudioTrack)
        selectedAudioTrack?.let { track ->
            viewModel.selectAudioTrack(track)
        }
        
        // Seçimi yaptıktan hemen sonra paneli derhal kapat
        hideAudioPanel()
    }

    /**
     * Panel açıkken tuş olaylarını handle eder.
     * @param keyCode Tuş kodu
     * @param event Tuş olayı
     * @return Olay işlendi ise true, aksi halde false
     */
    fun handleKeyEvent(
        keyCode: Int,
        event: android.view.KeyEvent?,
    ): Boolean {
        if (!isVisible()) {
            return false
        }

        // BACK tuşu - paneli kapat
        if (keyCode == android.view.KeyEvent.KEYCODE_BACK) {
            if (isAudioPanelOpen()) {
                hideAudioPanel()
            } else {
                hideSubtitlePanel()
            }
            return true
        }

        return false
    }

}
