package com.pnr.tv.ui.player.state

import android.content.Context
import android.view.View
import com.pnr.tv.R
import com.pnr.tv.databinding.ViewPlayerControlsBinding
import java.util.concurrent.TimeUnit

/**
 * PlayerControlView içindeki veri formatlama ve buton durum yönetimi mantığını yönetir.
 */
class PlayerStateHelper(
    private val binding: ViewPlayerControlsBinding,
    private val context: Context,
) {
    // Buton setup callback'leri - PlayerControlView'dan sağlanacak
    private var setupSeekBarCallback: (() -> Unit)? = null
    private var setupPlayStopButtonsCallback: (() -> Unit)? = null
    private var setupForwardBackwardButtonsCallback: (() -> Unit)? = null
    private var setupSubtitleButtonCallback: (() -> Unit)? = null
    private var setupSpeakButtonCallback: (() -> Unit)? = null
    private var requestFocusCallback: ((View) -> Unit)? = null
    private var findFocusCallback: (() -> View?)? = null
    private var postCallback: ((Runnable) -> Unit)? = null

    /**
     * Buton setup callback'lerini ayarlar.
     */
    fun setButtonSetupCallbacks(
        setupSeekBar: () -> Unit,
        setupPlayStopButtons: () -> Unit,
        setupForwardBackwardButtons: () -> Unit,
        setupSubtitleButton: () -> Unit,
        setupSpeakButton: () -> Unit,
    ) {
        setupSeekBarCallback = setupSeekBar
        setupPlayStopButtonsCallback = setupPlayStopButtons
        setupForwardBackwardButtonsCallback = setupForwardBackwardButtons
        setupSubtitleButtonCallback = setupSubtitleButton
        setupSpeakButtonCallback = setupSpeakButton
    }

    /**
     * Focus yönetimi callback'lerini ayarlar.
     */
    fun setFocusCallbacks(
        requestFocus: (View) -> Unit,
        findFocus: () -> View?,
        post: (Runnable) -> Unit,
    ) {
        requestFocusCallback = requestFocus
        findFocusCallback = findFocus
        postCallback = post
    }


    /**
     * Canlı yayın moduna göre buton durumlarını günceller.
     * @param isLiveStream Canlı yayın modu aktif mi?
     */
    fun updateButtonsState(isLiveStream: Boolean) {
        if (isLiveStream) {
            // Canlı yayında tüm butonları devre dışı bırak - click listener'ları kaldır
            binding.btnPlay.setOnClickListener(null)
            binding.btnStop.setOnClickListener(null)
            binding.btnBackward.setOnClickListener(null)
            binding.btnForward.setOnClickListener(null)
            binding.btnSpeak.setOnClickListener(null)
            binding.btnSubtitle.setOnClickListener(null)
            binding.seekbarProgress.setOnSeekBarChangeListener(null)
            binding.seekbarProgress.isEnabled = false
            binding.seekbarProgress.isFocusable = false
            binding.seekbarProgress.isFocusableInTouchMode = false

            // Butonları görünür yap ama tıklanamaz hale getir
            binding.btnPlay.alpha = 0.5f
            binding.btnStop.alpha = 0.5f
            binding.btnBackward.alpha = 0.5f
            binding.btnForward.alpha = 0.5f
            binding.btnSpeak.alpha = 0.5f
            binding.btnSubtitle.alpha = 0.5f
            binding.seekbarProgress.alpha = 0.5f
        } else {
            // Normal modda butonları tekrar aktif et
            setupSeekBarCallback?.invoke()
            setupPlayStopButtonsCallback?.invoke()
            setupForwardBackwardButtonsCallback?.invoke()
            setupSubtitleButtonCallback?.invoke()
            setupSpeakButtonCallback?.invoke()

            // Alpha değerlerini normale döndür
            binding.btnPlay.alpha = 1.0f
            binding.btnStop.alpha = 1.0f
            binding.btnBackward.alpha = 1.0f
            binding.btnForward.alpha = 1.0f
            binding.btnSpeak.alpha = 1.0f
            binding.btnSubtitle.alpha = 1.0f
            binding.seekbarProgress.alpha = 1.0f
            binding.seekbarProgress.isEnabled = true
            binding.seekbarProgress.isFocusable = true
            binding.seekbarProgress.isFocusableInTouchMode = true
        }
    }

    /**
     * Play/Stop butonlarının görünürlüğünü ve focus durumunu günceller.
     * @param isPlaying Video oynatılıyor mu?
     */
    fun updatePlayStopButtons(isPlaying: Boolean) {
        val currentFocus = findFocusCallback?.invoke()
        val wasFocusOnPlayStop = currentFocus == binding.btnPlay || currentFocus == binding.btnStop

        if (isPlaying) {
            // Video oynatılıyorsa → Stop görünür, Play gizli
            binding.btnStop.visibility = View.VISIBLE
            binding.btnPlay.visibility = View.GONE
            // Eğer focus play/stop butonlarındaysa, yeni görünen stop butonuna ver
            if (wasFocusOnPlayStop) {
                postCallback?.invoke(
                    Runnable {
                        requestFocusCallback?.invoke(binding.btnStop)
                    },
                )
            }
        } else {
            // Video durdurulmuşsa → Play görünür, Stop gizli
            binding.btnPlay.visibility = View.VISIBLE
            binding.btnStop.visibility = View.GONE
            // Eğer focus play/stop butonlarındaysa, yeni görünen play butonuna ver
            if (wasFocusOnPlayStop) {
                postCallback?.invoke(
                    Runnable {
                        requestFocusCallback?.invoke(binding.btnPlay)
                    },
                )
            }
        }
    }

    /**
     * Başlık ve rating bilgisini birleştirerek formatlanmış metni döndürür.
     * @param title İçerik başlığı
     * @param rating İçerik rating'i
     * @return Formatlanmış içerik bilgisi string'i
     */
    fun formatContentInfo(
        title: String?,
        rating: Double?,
    ): String {
        return buildString {
            append(title ?: "")
            rating?.let {
                if (title?.isNotBlank() == true) {
                    append(" ")
                    append(context.getString(R.string.bullet_symbol))
                    append(" ")
                }
                append(String.format("%.1f", it))
                append(" ")
                append(context.getString(R.string.star_symbol))
            }
        }
    }
}
