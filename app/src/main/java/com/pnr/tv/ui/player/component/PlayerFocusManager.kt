package com.pnr.tv.ui.player.component

import android.view.KeyEvent
import android.view.View
import com.pnr.tv.databinding.ViewPlayerControlsBinding
import com.pnr.tv.ui.player.handler.PlayerFocusHandler

/**
 * Focus yönetimi ve dispatchKeyEvent işlemlerini yöneten sınıf.
 * PlayerFocusHandler ile iletişimi bu sınıf kurar.
 */
class PlayerFocusManager(
    private val binding: ViewPlayerControlsBinding,
    private val focusHandler: PlayerFocusHandler,
    private val getIsUserSeeking: () -> Boolean,
    private val getIsLiveStream: () -> Boolean,
    private val findFocus: () -> View?,
    private val post: (Runnable) -> Unit,
) {
    /**
     * Tüm kontrol view'larının focusable olduğundan emin olur.
     * Focus chain XML'de tanımlı, burada sadece focusable özelliklerini garanti ediyoruz.
     */
    fun ensureAllViewsFocusable() {
        binding.seekbarProgress.isFocusable = true
        binding.seekbarProgress.isFocusableInTouchMode = true

        binding.btnPlay.isFocusable = true
        binding.btnPlay.isFocusableInTouchMode = true
        binding.btnStop.isFocusable = true
        binding.btnStop.isFocusableInTouchMode = true
        binding.btnBackward.isFocusable = true
        binding.btnBackward.isFocusableInTouchMode = true
        binding.btnForward.isFocusable = true
        binding.btnForward.isFocusableInTouchMode = true
        binding.btnSubtitle.isFocusable = true
        binding.btnSubtitle.isFocusableInTouchMode = true
        binding.btnSpeak.isFocusable = true
        binding.btnSpeak.isFocusableInTouchMode = true
    }

    /**
     * Play/Stop butonlarından hangisi görünürse ona focus verir.
     */
    fun requestFocusOnPlayStopButton() {
        focusHandler.requestFocusOnPlayStopButton()
    }

    /**
     * KeyEvent'i handle eder ve doğru sonucu döndürür.
     * @param event KeyEvent - İşlenecek tuş olayı
     * @param superDispatchKeyEvent Super sınıfın dispatchKeyEvent metodunu çağıran lambda
     * @return Boolean - true ise olay işlendi, false ise işlenmedi, super'e devret
     */
    fun dispatchKeyEvent(
        event: KeyEvent?,
        superDispatchKeyEvent: (KeyEvent?) -> Boolean,
    ): Boolean {
        if (event == null) return superDispatchKeyEvent(event)

        // Focus handler'a devret - tuş yönetimi coordinator'lar üzerinden yapılıyor
        focusHandler.setIsUserSeeking(getIsUserSeeking())

        val handled = focusHandler.handleKeyEvent(event)
        if (handled != null) {
            return handled
        }

        // Handler işlemediyse super'e devret
        return superDispatchKeyEvent(event)
    }

    /**
     * Listener'ı günceller.
     */
    fun setListener(listener: PlayerControlListener?) {
        focusHandler.setListener(listener)
    }

    /**
     * Canlı yayın modunu günceller.
     */
    fun setIsLiveStream(isLiveStream: Boolean) {
        focusHandler.setIsLiveStream(isLiveStream)
    }

    /**
     * Ayarlar paneli açık olup olmadığını kontrol eden callback'i ayarlar.
     */
    fun setSettingsPanelOpenCallback(callback: () -> Boolean) {
        focusHandler.setSettingsPanelOpenCallback(callback)
    }
}
