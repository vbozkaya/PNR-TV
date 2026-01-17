package com.pnr.tv.ui.player.manager

import android.view.KeyEvent
import com.pnr.tv.core.constants.PlayerConstants
import com.pnr.tv.databinding.ViewPlayerControlsBinding
import com.pnr.tv.ui.player.component.PlayerControlListener
import com.pnr.tv.ui.player.component.PlayerTimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * PlayerControlView için seek işlemlerini yöneten sınıf.
 * Seek hesaplamaları, zamanlayıcıları ve UI güncellemelerini bu sınıf üzerinden yapar.
 */
class PlayerSeekManager(
    private val binding: ViewPlayerControlsBinding,
    private var listener: PlayerControlListener?,
    private var viewScope: CoroutineScope?,
) {
    // Seek ile ilgili state değişkenleri
    private var lastSeekTime = 0L // Son seek işleminin zamanı (throttle için)
    private var seekCommitJob: Job? = null
    private var isUserSeekingCallback: (() -> Boolean)? = null // isUserSeeking state'ini okumak için callback
    private var setIsUserSeekingCallback: ((Boolean) -> Unit)? = null // isUserSeeking state'ini güncellemek için callback
    private var cancelAutoHideTimerCallback: (() -> Unit)? = null // Auto-hide timer'ı iptal etmek için callback
    private var resetAutoHideTimerCallback: (() -> Unit)? = null // Auto-hide timer'ı sıfırlamak için callback
    private var getWatchedPositionCallback: (() -> Long)? = null // watchedPosition'ı okumak için callback
    private var setWatchedPositionCallback: ((Long) -> Unit)? = null // watchedPosition'ı güncellemek için callback
    private var updateWatchedProgressCallback: (() -> Unit)? = null // watchedProgress'i güncellemek için callback
    private var getCachedDurationCallback: (() -> Long)? = null // Cached duration'ı okumak için callback

    companion object {
        private const val SEEK_AUTO_COMMIT_DELAY = 1000L // 1 saniye
        private const val SEEK_INCREMENT = PlayerConstants.SEEK_INCREMENT_30_SECONDS_MS // 30 saniye (varsayılan)
        private const val BUTTON_SEEK_AMOUNT = PlayerConstants.SEEK_INCREMENT_30_SECONDS_MS // 30 saniye (butonlar için)

        // Kademeli seek sabitleri
        private const val SEEK_AMOUNT_SHORT_HOLD = 60000L // 1 dakika (500ms-5s arası)
        private const val SEEK_AMOUNT_LONG_HOLD = 300000L // 5 dakika (5s+)
        private const val HOLD_THRESHOLD_SHORT = 500L // 500ms (kısa basılı tutma başlangıcı)
        private const val HOLD_THRESHOLD_LONG = 5000L // 5 saniye (uzun basılı tutma başlangıcı)
        private const val THROTTLE_DELAY = 300L // 300ms (5 dakikalık atlamalar için throttle)
    }

    /**
     * Listener'ı günceller.
     */
    fun setListener(listener: PlayerControlListener?) {
        this.listener = listener
    }

    /**
     * ViewScope'u günceller.
     */
    fun setViewScope(viewScope: CoroutineScope?) {
        this.viewScope = viewScope
    }

    /**
     * isUserSeeking state'ini okumak için callback ayarlar.
     */
    fun setIsUserSeekingCallbacks(
        get: () -> Boolean,
        set: (Boolean) -> Unit,
    ) {
        this.isUserSeekingCallback = get
        this.setIsUserSeekingCallback = set
    }

    /**
     * Auto-hide timer'ı iptal etmek için callback ayarlar.
     */
    fun setCancelAutoHideTimerCallback(callback: () -> Unit) {
        this.cancelAutoHideTimerCallback = callback
    }

    /**
     * Auto-hide timer'ı sıfırlamak için callback ayarlar.
     */
    fun setResetAutoHideTimerCallback(callback: () -> Unit) {
        this.resetAutoHideTimerCallback = callback
    }

    /**
     * watchedPosition ile ilgili callback'leri ayarlar.
     */
    fun setWatchedPositionCallbacks(
        get: () -> Long,
        set: (Long) -> Unit,
        updateProgress: () -> Unit,
    ) {
        this.getWatchedPositionCallback = get
        this.setWatchedPositionCallback = set
        this.updateWatchedProgressCallback = updateProgress
    }

    /**
     * Cached duration callback'ini ayarlar.
     */
    fun setGetCachedDurationCallback(callback: () -> Long) {
        this.getCachedDurationCallback = callback
    }

    /**
     * Kademeli seek miktarını hesaplar.
     * Basılı tutma süresine göre farklı seek miktarları döndürür.
     *
     * @param event KeyEvent - Tuş event'i
     * @param isForward İleri mi geri mi (true = ileri, false = geri)
     * @return Seek miktarı (milisaniye cinsinden, negatif veya pozitif)
     */
    fun calculateSeekAmount(
        event: KeyEvent,
        isForward: Boolean,
    ): Long {
        val currentTime = System.currentTimeMillis()

        // İlk basış mı kontrol et (repeatCount == 0)
        if (event.repeatCount == 0) {
            // İlk basış: Her zaman 30 saniye
            lastSeekTime = currentTime // İlk basışta zaman damgasını güncelle
            return if (isForward) SEEK_INCREMENT else -SEEK_INCREMENT
        }

        // Key repeat: Basılı tutma süresine göre hesapla
        val holdDuration = event.eventTime - event.downTime

        val seekAmount =
            when {
                holdDuration > HOLD_THRESHOLD_LONG -> SEEK_AMOUNT_LONG_HOLD // 5 Dakika
                holdDuration > HOLD_THRESHOLD_SHORT -> SEEK_AMOUNT_SHORT_HOLD // 1 Dakika
                else -> SEEK_INCREMENT // 30 Saniye
            }

        // 5 dakikalık atlamalarda throttle uygula
        if (seekAmount == SEEK_AMOUNT_LONG_HOLD) {
            val timeSinceLastSeek = currentTime - lastSeekTime

            if (timeSinceLastSeek < THROTTLE_DELAY) {
                // Throttle aktif: Önceki seek miktarını kullan (daha küçük)
                val throttledAmount = if (isForward) SEEK_AMOUNT_SHORT_HOLD else -SEEK_AMOUNT_SHORT_HOLD
                lastSeekTime = currentTime
                return throttledAmount
            }

            lastSeekTime = currentTime
        } else {
            // Diğer seek miktarları için de zaman damgasını güncelle
            lastSeekTime = currentTime
        }

        return if (isForward) seekAmount else -seekAmount
    }

    /**
     * Preview zamanını günceller (txtCurrentTime).
     */
    fun updatePreviewTime(positionMs: Long) {
        binding.txtCurrentTime.text = PlayerTimeFormatter.format(positionMs)
    }

    /**
     * Seek commit zamanlayıcısını başlatır.
     */
    fun startSeekCommitTimer(newPositionMs: Long) {
        // Önceki timer'ı iptal et
        seekCommitJob?.cancel()

        // 1 saniye sonra otomatik commit
        seekCommitJob =
            viewScope?.launch {
                delay(SEEK_AUTO_COMMIT_DELAY)
                commitSeekPosition(newPositionMs)
            }
    }

    /**
     * Seek pozisyonunu commit eder (gerçek seek işlemini yapar).
     */
    fun commitSeekPosition(newPositionMs: Long) {
        if (isUserSeekingCallback?.invoke() != true) {
            return
        }

        // Video pozisyonunu değiştir
        listener?.onSeekTo(newPositionMs)

        // İzlenen pozisyonu yeni pozisyona güncelle
        // Geri sarıldığında da izlenen pozisyon geri gider (izlenmemiş sayılır)
        setWatchedPositionCallback?.invoke(newPositionMs)
        updateWatchedProgressCallback?.invoke()

        // Flag'leri sıfırla
        setIsUserSeekingCallback?.invoke(false)
        seekCommitJob?.cancel()

        // Auto-hide sayacını yeniden başlat
        resetAutoHideTimerCallback?.invoke()
    }

    /**
     * Seek buton tıklamasını işler (Forward/Backward butonları için).
     */
    fun handleSeekButtonClick(amount: Long) {
        // Duration'ı önce listener'dan al, 0 ise cache'den kullan
        var duration = listener?.getDuration() ?: 0L
        val cachedDuration = getCachedDurationCallback?.invoke() ?: 0L
        
        // Eğer duration 0 ise cache'den kullan
        if (duration <= 0 && cachedDuration > 0) {
            duration = cachedDuration
        }
        
        val currentPosition = listener?.getCurrentPosition() ?: 0L
        val newPosition = if (duration > 0) {
            (currentPosition + amount).coerceIn(0, duration)
        } else {
            (currentPosition + amount).coerceAtLeast(0L)
        }

        // 1. Adım: UI'ı ANINDA güncelle (görsel geri bildirim)
        // SeekBar yüzde bazlı çalışıyor (max=100)
        // Float precision kullanarak hassas hesaplama yap
        val progressPercent = if (duration > 0) {
            // Float üzerinden hesapla, sonra yuvarla
            ((newPosition.toFloat() / duration.toFloat()) * 100f).toInt().coerceIn(0, 100)
        } else {
            // Duration yoksa mevcut progress'i koru veya 0 bırak
            binding.seekbarProgress.progress
        }
        
        // SeekBar'ı anında güncelle ve görsel olarak refresh et
        binding.seekbarProgress.progress = progressPercent
        
        // Animasyon beklemeden noktayı oraya taşı
        binding.seekbarProgress.jumpDrawablesToCurrentState()
        // Arayüzün o milisaniyede tazelenmesini zorla
        binding.seekbarProgress.postInvalidateOnAnimation()
        binding.seekbarProgress.invalidate()
        binding.seekbarProgress.requestLayout()
        
        updatePreviewTime(newPosition)

        // İzlenen pozisyonu anlık olarak güncelle (watchedPosition güncellemesi önce yapılmalı)
        setWatchedPositionCallback?.invoke(newPosition)
        // Secondary progress'i güncelle (duration ile birlikte)
        updateWatchedProgressCallback?.invoke()

        // 2. Adım: Panel açık kalsın (isUserSeeking flag'ini set etmiyoruz, sadece buton seek'i)
        cancelAutoHideTimerCallback?.invoke()

        // 3. Adım: Doğrudan seek yap (anında tepki için)
        listener?.onSeekTo(newPosition)

        // 4. Adım: Timer'ı yeniden başlat
        resetAutoHideTimerCallback?.invoke()
    }


    /**
     * Cleanup: Tüm job'ları iptal eder.
     */
    fun cleanup() {
        seekCommitJob?.cancel()
        seekCommitJob = null
    }
}
