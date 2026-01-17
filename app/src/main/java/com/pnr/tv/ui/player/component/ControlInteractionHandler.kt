package com.pnr.tv.ui.player.component

import com.pnr.tv.core.constants.PlayerConstants
import com.pnr.tv.databinding.ViewPlayerControlsBinding
import com.pnr.tv.ui.player.manager.PlayerSeekManager

/**
 * Buton setup ve click listener'larını yöneten sınıf.
 * PlayerControlListener çağrılarını buradan yönetir.
 */
class ControlInteractionHandler(
    private val binding: ViewPlayerControlsBinding,
    private var listener: PlayerControlListener?,
    private val seekManager: PlayerSeekManager,
    private val updatePlayStopButtons: (Boolean) -> Unit,
    private val showControls: () -> Unit,
) {
    /**
     * Play/Stop butonlarını setup eder.
     */
    fun setupPlayStopButtons() {
        // Play butonuna tıklanınca video oynat
        binding.btnPlay.setOnClickListener {
            // UI'ı anında güncelle, sonra player aksiyonunu tetikle
            updatePlayStopButtons(true)
            // Focus'u updatePlayStopButtons() içinde vereceğiz (buton görünür hale geldikten sonra)
            listener?.onPlayClicked()
            showControls() // Panel göster
        }

        // Stop butonuna tıklanınca video durdur
        binding.btnStop.setOnClickListener {
            // UI'ı anında güncelle, sonra player aksiyonunu tetikle
            updatePlayStopButtons(false)
            // Focus'u updatePlayStopButtons() içinde vereceğiz (buton görünür hale geldikten sonra)
            listener?.onPauseClicked()
            showControls() // Panel göster
        }

        // setOnKeyListener kaldırıldı - dispatchKeyEvent içinde performClick() kullanılıyor
    }

    /**
     * Forward/Backward butonlarını setup eder.
     */
    fun setupForwardBackwardButtons() {
        binding.btnBackward.setOnClickListener {
            seekManager.handleSeekButtonClick(-PlayerConstants.SEEK_INCREMENT_30_SECONDS_MS)
        }

        binding.btnForward.setOnClickListener {
            seekManager.handleSeekButtonClick(PlayerConstants.SEEK_INCREMENT_30_SECONDS_MS)
        }
    }

    /**
     * Subtitle butonunu setup eder.
     */
    fun setupSubtitleButton() {
        binding.btnSubtitle.setOnClickListener {
            listener?.onSubtitleClicked()
        }
    }

    /**
     * Speak butonunu setup eder.
     */
    fun setupSpeakButton() {
        binding.btnSpeak.setOnClickListener {
            listener?.onSpeakClicked()
        }
    }

    /**
     * Listener'ı günceller.
     */
    fun setListener(listener: PlayerControlListener?) {
        this.listener = listener
        // Listener değiştiğinde butonları yeniden setup et
        setupPlayStopButtons()
        setupForwardBackwardButtons()
        setupSubtitleButton()
        setupSpeakButton()
    }
}
