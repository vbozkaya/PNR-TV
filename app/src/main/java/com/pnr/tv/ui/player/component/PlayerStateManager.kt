package com.pnr.tv.ui.player.component

import android.content.Context
import android.view.View
import com.pnr.tv.databinding.ViewPlayerControlsBinding
import com.pnr.tv.ui.player.state.PlayerStateHelper

/**
 * PlayerStateHelper ve PlayerControlListener ile olan etkileşimi yönetir.
 * updatePlayStopButtons, updateProgressInternal, formatContentInfo ve setLiveStreamMode
 * gibi fonksiyonların mantığını bu sınıf yönetir.
 */
class PlayerStateManager(
    private val binding: ViewPlayerControlsBinding,
    private val context: Context,
    private val stateHelper: PlayerStateHelper,
    private val listener: PlayerControlListener?,
) {
    // State değişkenleri
    private var isUserSeeking = false
    private var isLiveStream = false
    private var watchedPosition = 0L
    private var cachedDuration: Long = 0L

    /**
     * Kullanıcının şu anda seek yapıp yapmadığını ayarlar.
     */
    fun setIsUserSeeking(seeking: Boolean) {
        isUserSeeking = seeking
    }

    /**
     * Kullanıcının şu anda seek yapıp yapmadığını döndürür.
     */
    fun isUserSeeking(): Boolean = isUserSeeking

    /**
     * Canlı yayın modunu ayarlar.
     */
    fun setIsLiveStream(liveStream: Boolean) {
        isLiveStream = liveStream
    }

    /**
     * Canlı yayın modunu döndürür.
     */
    fun isLiveStream(): Boolean = isLiveStream

    /**
     * İzlenen pozisyonu ayarlar.
     */
    fun setWatchedPosition(positionMs: Long) {
        watchedPosition = positionMs
    }

    /**
     * İzlenen pozisyonu döndürür.
     */
    fun getWatchedPosition(): Long = watchedPosition

    /**
     * Cache'lenmiş duration'ı ayarlar.
     */
    fun setCachedDuration(duration: Long) {
        if (duration > 0) {
            cachedDuration = duration
        }
    }

    /**
     * Cache'lenmiş duration'ı döndürür.
     */
    fun getCachedDuration(): Long = cachedDuration

    /**
     * Play/Stop butonlarının görünürlüğünü ve focus durumunu günceller.
     * @param isPlaying Video oynatılıyor mu?
     */
    fun updatePlayStopButtons(isPlaying: Boolean) {
        stateHelper.updatePlayStopButtons(isPlaying)
    }

    /**
     * Canlı yayın modunu ayarlar - canlı yayında tüm kontrol butonları devre dışı olur.
     */
    fun setLiveStreamMode(enabled: Boolean) {
        setIsLiveStream(enabled)
        stateHelper.updateButtonsState(isLiveStream)
    }

    /**
     * Başlık ve rating bilgisini formatlar ve UI'a uygular.
     */
    fun setContentInfo(
        title: String?,
        rating: Double?,
    ) {
        val displayText = stateHelper.formatContentInfo(title, rating)
        binding.txtContentTitle.text = displayText
    }

    /**
     * Progress'i günceller (internal metod).
     * @param position Mevcut pozisyon (ms)
     * @param duration Toplam süre (ms)
     */
    fun updateProgressInternal(
        position: Long,
        duration: Long,
    ) {
        // Duration'ı cache'le (seek sırasında kullanmak için)
        if (duration > 0) {
            cachedDuration = duration
        }

        if (!isUserSeeking) {
            if (duration > 0) {
                // SeekBar yüzde bazlı çalışıyor (max=100)
                // Float precision kullanarak hassas hesaplama yap
                val progressPercent = ((position.toFloat() / duration.toFloat()) * 100f).toInt().coerceIn(0, 100)

                binding.seekbarProgress.progress = progressPercent

                // Animasyon beklemeden noktayı oraya taşı
                binding.seekbarProgress.jumpDrawablesToCurrentState()
                // Arayüzün o milisaniyede tazelenmesini zorla
                binding.seekbarProgress.postInvalidateOnAnimation()
                binding.seekbarProgress.invalidate()
                binding.seekbarProgress.requestLayout()

                binding.txtCurrentTime.text = PlayerTimeFormatter.format(position)

                // İzlenen pozisyonu güncelle:
                // - İleri sarıldığında: izlenen pozisyon artar
                // - Geri sarıldığında: izlenen pozisyon da geri gider (izlenmemiş sayılır)
                watchedPosition = position
            }
        }
        binding.txtTotalTime.text = PlayerTimeFormatter.format(duration)
        // İzlenen progress'i de güncelle
        updateWatchedProgress(duration)
    }

    /**
     * Progress'i günceller (listener'dan duration alır).
     */
    fun updateProgress(position: Long) {
        val duration = listener?.getDuration() ?: 0L
        updateProgressInternal(position, duration)
    }

    /**
     * Progress'i günceller (duration parametre olarak verilir).
     */
    fun updateProgressWithDuration(
        position: Long,
        duration: Long,
    ) {
        updateProgressInternal(position, duration)
    }

    /**
     * SeekBar'da izlenen kısmı (kırmızı) günceller.
     */
    fun updateWatchedProgress(durationOverride: Long? = null) {
        // Duration'ı önce parametreden al, yoksa listener'dan, yoksa cache'den
        var duration = durationOverride ?: (listener?.getDuration() ?: 0L)
        if (duration <= 0) {
            duration = cachedDuration
        }

        if (duration > 0 && watchedPosition > 0) {
            // SeekBar yüzde bazlı çalışıyor (max=100)
            // Float precision kullanarak hassas hesaplama yap
            val watchedPercent = ((watchedPosition.toFloat() / duration.toFloat()) * 100f).toInt().coerceIn(0, 100)
            binding.seekbarProgress.secondaryProgress = watchedPercent
        } else {
            binding.seekbarProgress.secondaryProgress = 0
        }
        // Görsel güncelleme için invalidate, requestLayout ve animasyon metodları
        binding.seekbarProgress.jumpDrawablesToCurrentState()
        binding.seekbarProgress.postInvalidateOnAnimation()
        binding.seekbarProgress.invalidate()
        binding.seekbarProgress.requestLayout()
    }
}
