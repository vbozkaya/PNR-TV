package com.pnr.tv.ui.player.coordinator

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import com.pnr.tv.R
import com.pnr.tv.databinding.ViewPlayerControlsBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * PlayerControlView içindeki panel görünürlük ve animasyon mantığını yönetir.
 */
class PlayerVisibilityCoordinator(
    private val binding: ViewPlayerControlsBinding,
    private val viewScope: CoroutineScope,
    private val context: Context,
    private val rootView: View,
) {
    companion object {
        private const val CONTROLS_AUTO_HIDE_DELAY = 8000L // 8 saniye
    }

    // State değişkenleri
    private var hideControlsJob: Job? = null
    private var isFirstPanelOpen = true

    // Callback'ler
    private var isSettingsPanelOpenCallback: (() -> Boolean)? = null
    private var onControlsShown: (() -> Unit)? = null
    private var isPlayingStateCallback: (() -> Boolean)? = null
    private var isLiveStreamCallback: (() -> Boolean)? = null
    private var updateButtonsStateCallback: (() -> Unit)? = null
    private var onFirstPanelOpenChanged: ((Boolean) -> Unit)? = null
    private var onControlsShownManually: (() -> Unit)? = null

    /**
     * Ayarlar panelinin açık olup olmadığını kontrol eden callback'i ayarlar.
     */
    fun setSettingsPanelOpenCallback(callback: () -> Boolean) {
        isSettingsPanelOpenCallback = callback
    }

    /**
     * Kontroller gösterildiğinde çağrılacak callback'i ayarlar (örn. focus yönetimi için).
     */
    fun setOnControlsShownCallback(callback: () -> Unit) {
        onControlsShown = callback
    }

    /**
     * Oynatma durumunu kontrol eden callback'i ayarlar.
     */
    fun setIsPlayingStateCallback(callback: () -> Boolean) {
        isPlayingStateCallback = callback
    }

    /**
     * Canlı yayın modunu kontrol eden callback'i ayarlar.
     */
    fun setIsLiveStreamCallback(callback: () -> Boolean) {
        isLiveStreamCallback = callback
    }

    /**
     * Buton durumlarını güncellemek için callback'i ayarlar.
     */
    fun setUpdateButtonsStateCallback(callback: () -> Unit) {
        updateButtonsStateCallback = callback
    }

    /**
     * İlk panel açılış durumunu güncellemek için callback'i ayarlar.
     */
    fun setOnFirstPanelOpenChangedCallback(callback: (Boolean) -> Unit) {
        onFirstPanelOpenChanged = callback
    }

    /**
     * Kullanıcı manuel olarak panel açtığında çağrılacak callback'i ayarlar.
     */
    fun setOnControlsShownManuallyCallback(callback: () -> Unit) {
        onControlsShownManually = callback
    }

    /**
     * İlk panel açılış durumunu ayarlar.
     */
    fun setIsFirstPanelOpen(value: Boolean) {
        isFirstPanelOpen = value
    }

    /**
     * İlk panel açılış durumunu döndürür.
     */
    fun isFirstPanelOpen(): Boolean {
        return isFirstPanelOpen
    }

    /**
     * Kontrol panelini gösterir ve animasyonu başlatır.
     */
    fun showControls() {
        val visibility = rootView.visibility
        val wasFirstPanelOpen = isFirstPanelOpen

        val isFirstOpen = wasFirstPanelOpen && visibility != View.VISIBLE

        // Panel zaten görünürse, sadece sayacı sıfırla
        if (visibility == View.VISIBLE) {
            resetAutoHideTimer()
            // Kullanıcı manuel olarak panel açtıysa callback'i çağır
            onControlsShownManually?.invoke()
            return
        }

        // Paneli göster ve animasyonu başlat
        rootView.visibility = View.VISIBLE

        // Parent view'ı üste getir
        (rootView.parent as? ViewGroup)?.bringChildToFront(rootView)
        rootView.bringToFront()

        // Elevation'ı artır (z-order için)
        rootView.elevation = 20f

        // Tüm child view'ları da görünür yap
        binding.panelTopSection.visibility = View.VISIBLE
        binding.panelBottomSection.visibility = View.VISIBLE
        binding.panelDivider.visibility = View.VISIBLE
        binding.txtContentTitle.visibility = View.VISIBLE
        binding.seekbarProgress.visibility = View.VISIBLE
        binding.txtCurrentTime.visibility = View.VISIBLE
        binding.txtTotalTime.visibility = View.VISIBLE

        // Butonları da görünür yap - canlı yayında bile görünür ama devre dışı
        val isPlaying = isPlayingStateCallback?.invoke() ?: false
        binding.btnPlay.visibility = if (!isPlaying) View.VISIBLE else View.GONE
        binding.btnStop.visibility = if (isPlaying) View.VISIBLE else View.GONE
        binding.btnBackward.visibility = View.VISIBLE
        binding.btnForward.visibility = View.VISIBLE
        binding.btnSpeak.visibility = View.VISIBLE
        binding.btnSubtitle.visibility = View.VISIBLE

        // Canlı yayında buton durumlarını güncelle
        if (isLiveStreamCallback?.invoke() == true) {
            updateButtonsStateCallback?.invoke()
        }
        
        // Butonların visibility'si ayarlandıktan sonra, animasyon başlamadan önce focus ver
        // Böylece doğru buton (play/stop) seçili olur
        rootView.post {
            onControlsShown?.invoke()
        }

        // Child view'ların background'larını da şeffaf yap
        binding.panelTopSection.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        binding.panelBottomSection.setBackgroundColor(android.graphics.Color.TRANSPARENT)

        rootView.requestLayout()
        rootView.invalidate()
        rootView.post {
            rootView.requestLayout()
            rootView.invalidate()
            rootView.bringToFront()
        }
        val slideIn = AnimationUtils.loadAnimation(context, R.anim.slide_in_bottom)
        rootView.startAnimation(slideIn)

        // İlk açılış flag'ini güncelle
        if (isFirstOpen) {
            isFirstPanelOpen = false
            onFirstPanelOpenChanged?.invoke(false)
        }

        // Animasyon listener'ı kaldırıldı - focus yönetimi post ile yapılıyor
        // (Butonların visibility'si ayarlandıktan hemen sonra)

        // Otomatik gizlenme sayacını başlat
        resetAutoHideTimer()
        
        // Kullanıcı manuel olarak panel açtıysa callback'i çağır
        // (İlk otomatik açılışta çağrılmaz, sadece manuel açılışta)
        if (!isFirstOpen) {
            onControlsShownManually?.invoke()
        }
    }

    /**
     * Kontrol panelini gizler ve animasyonu başlatır.
     */
    fun hideControls() {
        if (rootView.visibility != View.VISIBLE) return

        // Ayarlar paneli açıksa kontrol bar'ı kapatma
        if (isSettingsPanelOpenCallback?.invoke() == true) {
            return
        }

        // Paneli gizle ve animasyonu başlat
        val slideOut = AnimationUtils.loadAnimation(context, R.anim.slide_out_bottom)
        rootView.startAnimation(slideOut)

        // Animasyon bitince visibility'yi GONE yap
        slideOut.setAnimationListener(
            object : android.view.animation.Animation.AnimationListener {
                override fun onAnimationStart(animation: android.view.animation.Animation?) {}

                override fun onAnimationRepeat(animation: android.view.animation.Animation?) {}

                override fun onAnimationEnd(animation: android.view.animation.Animation?) {
                    rootView.visibility = View.GONE
                    rootView.requestLayout()
                    rootView.invalidate()
                }
            },
        )
    }

    /**
     * Otomatik gizlenme sayacını sıfırlar.
     */
    fun resetAutoHideTimer() {
        // Önceki sayacı iptal et
        hideControlsJob?.cancel()

        // Yeni sayaç başlat
        hideControlsJob =
            viewScope.launch {
                delay(CONTROLS_AUTO_HIDE_DELAY)
                hideControls()
            }
    }

    /**
     * Otomatik gizlenme sayacını iptal eder.
     */
    fun cancelAutoHideTimer() {
        hideControlsJob?.cancel()
    }

    /**
     * Coordinator'ı temizler.
     */
    fun cleanup() {
        hideControlsJob?.cancel()
        hideControlsJob = null
    }
}
