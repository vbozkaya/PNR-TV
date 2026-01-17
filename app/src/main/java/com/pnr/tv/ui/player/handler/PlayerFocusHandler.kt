package com.pnr.tv.ui.player.handler

import android.content.Context
import android.view.KeyEvent
import android.view.View
import com.pnr.tv.databinding.ViewPlayerControlsBinding
import com.pnr.tv.ui.player.component.PlayerControlListener
import javax.inject.Inject

/**
 * PlayerControlView için tuş olaylarını ve focus yönetimini işleyen sınıf.
 * DPAD navigasyon mantığını PlayerControlView'dan ayırarak kodu daha modüler hale getirir.
 */
class PlayerFocusHandler
    @Inject
    constructor(
        private val context: Context,
    ) {
        // Referanslar - setter metodlarıyla ayarlanacak
        private var binding: ViewPlayerControlsBinding? = null
        private var listener: PlayerControlListener? = null
        private var isUserSeeking: Boolean = false
        private var isLiveStream: Boolean = false
        private var isSettingsPanelOpenCallback: (() -> Boolean)? = null

        // Callback'ler - PlayerControlView'dan alınacak
        private var calculateSeekAmountCallback: ((KeyEvent, Boolean) -> Long)? = null
        private var handleSeekButtonClickCallback: ((Long) -> Unit)? = null
        private var commitSeekPositionCallback: ((Long) -> Unit)? = null
        private var showControlsCallback: (() -> Unit)? = null
        private var hideControlsCallback: (() -> Unit)? = null
        private var resetAutoHideTimerCallback: (() -> Unit)? = null
        private var isVisibleCallback: (() -> Boolean)? = null
        private var findFocusCallback: (() -> View?)? = null
        private var postCallback: ((Runnable) -> Unit)? = null

        /**
         * Binding nesnesini ayarlar.
         */
        fun setBinding(binding: ViewPlayerControlsBinding) {
            this.binding = binding
        }

        /**
         * PlayerControlListener'ı ayarlar.
         */
        fun setListener(listener: PlayerControlListener?) {
            this.listener = listener
        }

        /**
         * Kullanıcının seek yapıp yapmadığını ayarlar.
         */
        fun setIsUserSeeking(isSeeking: Boolean) {
            this.isUserSeeking = isSeeking
        }

        /**
         * Canlı yayın modunu ayarlar.
         */
        fun setIsLiveStream(isLive: Boolean) {
            this.isLiveStream = isLive
        }

        /**
         * Ayarlar paneli açık olup olmadığını kontrol eden callback'i ayarlar.
         */
        fun setSettingsPanelOpenCallback(callback: (() -> Boolean)?) {
            this.isSettingsPanelOpenCallback = callback
        }

        /**
         * Callback'leri ayarlar.
         */
        fun setCallbacks(
            calculateSeekAmount: (KeyEvent, Boolean) -> Long,
            handleSeekButtonClick: (Long) -> Unit,
            commitSeekPosition: (Long) -> Unit,
            showControls: () -> Unit,
            hideControls: () -> Unit,
            resetAutoHideTimer: () -> Unit,
            isVisible: () -> Boolean,
            findFocus: () -> View?,
            post: (Runnable) -> Unit,
        ) {
            this.calculateSeekAmountCallback = calculateSeekAmount
            this.handleSeekButtonClickCallback = handleSeekButtonClick
            this.commitSeekPositionCallback = commitSeekPosition
            this.showControlsCallback = showControls
            this.hideControlsCallback = hideControls
            this.resetAutoHideTimerCallback = resetAutoHideTimer
            this.isVisibleCallback = isVisible
            this.findFocusCallback = findFocus
            this.postCallback = post
        }

        /**
         * Odaklanmış view'ın ismini döndüren yardımcı metod.
         */
        private fun getFocusedViewName(focusedView: View?): String {
            val binding = this.binding ?: return "UNKNOWN"
            return when (focusedView) {
                binding.seekbarProgress -> "SeekBar"
                binding.btnPlay -> "Play"
                binding.btnStop -> "Stop"
                binding.btnBackward -> "Backward"
                binding.btnForward -> "Forward"
                binding.btnSpeak -> "Speak"
                binding.btnSubtitle -> "Subtitle"
                null -> "NULL"
                else -> focusedView.javaClass.simpleName
            }
        }

        private fun isSelectKey(keyCode: Int): Boolean {
            return keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                keyCode == KeyEvent.KEYCODE_ENTER ||
                keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
        }

        /**
         * Ana tuş olayı işleme metodu.
         * PlayerControlView'dan çağrılır.
         *
         * @param event KeyEvent - İşlenecek tuş olayı
         * @param findFocus View'dan findFocus metodunu çağıran lambda
         * @return Boolean? - true ise olay işlendi, false ise işlenmedi, null ise super'e devret
         */
        fun handleKeyEvent(event: KeyEvent?): Boolean? {
            if (event == null) return null

            val binding = this.binding ?: return null
            val findFocus = this.findFocusCallback ?: return null

            // Ayarlar paneli açıksa, player kontrollerinin tuş olaylarını engelle (focus trap)
            if (isSettingsPanelOpenCallback?.invoke() == true) {
                // BACK tuşu hariç, tüm navigasyon tuşlarını engelle (panel kendi tuş yönetimini yapacak)
                when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP,
                    KeyEvent.KEYCODE_DPAD_DOWN,
                    KeyEvent.KEYCODE_DPAD_LEFT,
                    KeyEvent.KEYCODE_DPAD_RIGHT,
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                        // Panel açıkken player kontrollerindeki navigasyon tuşlarını engelle
                        return false
                    }
                    KeyEvent.KEYCODE_BACK -> {
                        // BACK tuşu panel tarafından handle edilecek
                        return null
                    }
                    else -> {
                        // Diğer tuşları engelle
                        return false
                    }
                }
            }

            // Canlı yayında tüm tuş işlemlerini engelle (BACK, Page Up/Down, Channel Up/Down ve DPAD_LEFT hariç)
            // Gerçek TV kumandalarında genellikle Channel Up/Down tuşları kullanılır
            // DPAD_LEFT kanal listesi panelini açar
            if (isLiveStream && event.keyCode != KeyEvent.KEYCODE_BACK &&
                event.keyCode != KeyEvent.KEYCODE_PAGE_UP &&
                event.keyCode != KeyEvent.KEYCODE_PAGE_DOWN &&
                event.keyCode != KeyEvent.KEYCODE_CHANNEL_UP &&
                event.keyCode != KeyEvent.KEYCODE_CHANNEL_DOWN &&
                event.keyCode != KeyEvent.KEYCODE_DPAD_LEFT
            ) {
                // Canlı yayında sadece panel'i gösterme/gizleme ve BACK tuşu çalışsın
                if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_BACK) {
                    val isVisible = isVisibleCallback?.invoke() ?: false
                    if (isVisible) {
                        hideControlsCallback?.invoke()
                        return true
                    }
                }
                // Diğer tüm tuşları engelle
                return false
            }

            val focusedView = findFocus()

            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    // Panel görünürken herhangi bir tuş olayı (BACK hariç) timer'ı sıfırlar
                    val isVisible = isVisibleCallback?.invoke() ?: false
                    if (isVisible && event.keyCode != KeyEvent.KEYCODE_BACK) {
                        resetAutoHideTimerCallback?.invoke()
                    }

                    // Geri tuşu - Panel açıksa kapat
                    if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                        if (isVisible) {
                            hideControlsCallback?.invoke()
                            return true // Olayı tüket
                        }
                    }
                    // Play/Stop butonlarında OK tuşu - ÖNCE kontrol et (SeekBar'dan önce)
                    if (isSelectKey(event.keyCode) && event.action == KeyEvent.ACTION_DOWN) {
                        // Play butonu görünür ve odaklanmışsa
                        if (binding.btnPlay.visibility == View.VISIBLE && focusedView == binding.btnPlay) {
                            // Aynı path'te ilerlemek için performClick kullan
                            binding.btnPlay.performClick()
                            return true // Olayı tüket
                        }
                        // Stop butonu görünür ve odaklanmışsa
                        else if (binding.btnStop.visibility == View.VISIBLE && focusedView == binding.btnStop) {
                            // Aynı path'te ilerlemek için performClick kullan
                            binding.btnStop.performClick()
                            return true // Olayı tüket
                        }
                    }
                    // SeekBar odaktaysa, tuş olaylarını özel olarak yönet
                    if (focusedView == binding.seekbarProgress) {
                        val handled = handleSeekBarKeyEvent(event, true)
                        if (handled) {
                            // LEFT/RIGHT tuşları için focus'un SeekBar'da kalmasını garanti et
                            // DOWN tuşu için focus butona geçmeli, geri almayalım
                            if (event.keyCode != KeyEvent.KEYCODE_DPAD_DOWN) {
                                postCallback?.invoke(
                                    Runnable {
                                        if (findFocus() != binding.seekbarProgress) {
                                            binding.seekbarProgress.requestFocus()
                                        }
                                    },
                                )
                            }
                            return true
                        }
                    }
                    // Diğer butonlarda DPAD DOWN kontrol bar'ı kapatsın (ayarlar paneli açık değilse)
                    if (event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        // Ayarlar paneli açıksa kontrol bar kapanmasın
                        if (isSettingsPanelOpenCallback?.invoke() == true) {
                            return false // Normal focus geçişine izin ver
                        }

                        if (focusedView == binding.btnPlay ||
                            focusedView == binding.btnStop ||
                            focusedView == binding.btnBackward ||
                            focusedView == binding.btnForward ||
                            focusedView == binding.btnSpeak ||
                            focusedView == binding.btnSubtitle
                        ) {
                            hideControlsCallback?.invoke()
                            return true
                        }
                    }
                    // Backward butonunda DPAD LEFT - Play/Stop'a dinamik focus
                    // NOT: Canlı yayında bu kontrol atlanıyor çünkü DPAD_LEFT kanal listesi panelini açar
                    if (!isLiveStream && event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT && focusedView == binding.btnBackward) {
                        val targetButton =
                            if (listener?.isPlayingState() == true) {
                                binding.btnStop
                            } else {
                                binding.btnPlay
                            }
                        if (targetButton.visibility == View.VISIBLE) {
                            targetButton.requestFocus()
                            return true
                        }
                    }
                }
                KeyEvent.ACTION_UP -> {
                    val newFocusedView = findFocus()

                    // ACTION_UP'da focus kontrolü - ACTION_DOWN'da zaten focus verildi, burada sadece kontrol et
                    if (isSelectKey(event.keyCode)) {
                        val currentFocus = findFocus()
                        // Eğer focus SeekBar'a kaymışsa, doğru butona geri al
                        if (currentFocus == binding.seekbarProgress) {
                            val targetButton = if (focusedView == binding.btnPlay) binding.btnStop else binding.btnPlay
                            if (targetButton.visibility == View.VISIBLE) {
                                targetButton.requestFocus()
                            }
                            return true
                        }
                    }

                    if (focusedView == binding.seekbarProgress) {
                        val handled = handleSeekBarKeyEvent(event, false)
                        // LEFT/RIGHT tuşları için focus değiştiyse geri al
                        // DOWN tuşu için focus butona geçmeli, geri almayalım
                        if (event.keyCode != KeyEvent.KEYCODE_DPAD_DOWN) {
                            if (newFocusedView != binding.seekbarProgress && newFocusedView != focusedView) {
                                postCallback?.invoke(
                                    Runnable {
                                        binding.seekbarProgress.requestFocus()
                                    },
                                )
                            }
                        }
                        return handled
                    }
                }
            }
            return null // Super'e devret
        }

        /**
         * SeekBar için tuş olaylarını işler.
         */
        private fun handleSeekBarKeyEvent(
            event: KeyEvent,
            isKeyDown: Boolean,
        ): Boolean {
            // Sadece tuşa ilk basılma anıyla ilgileniyoruz.
            if (!isKeyDown) return false

            val binding = this.binding ?: return false
            val calculateSeekAmount = this.calculateSeekAmountCallback ?: return false
            val handleSeekButtonClick = this.handleSeekButtonClickCallback ?: return false
            val commitSeekPosition = this.commitSeekPositionCallback ?: return false

            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    val seekAmount = calculateSeekAmount(event, true)
                    handleSeekButtonClick(seekAmount)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    val seekAmount = calculateSeekAmount(event, false)
                    handleSeekButtonClick(seekAmount)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    val targetButton = if (listener?.isPlayingState() == true) binding.btnStop else binding.btnPlay
                    if (targetButton.visibility == View.VISIBLE) {
                        targetButton.requestFocus()
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_CENTER -> {
                    // OK tuşuna basıldığında, zamanlayıcının dolmasını beklemeden
                    // mevcut hesaplanan yeni pozisyonu hemen commit et.
                    if (isUserSeeking) {
                        val duration = listener?.getDuration() ?: 0L
                        if (duration > 0) {
                            // SeekBar yüzde bazlı (max=100), pozisyona çevir
                            val progressPercent = binding.seekbarProgress.progress
                            val newPosition = (duration * progressPercent) / 100
                            commitSeekPosition(newPosition)
                        }
                    }
                    return true
                }
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER,
                -> {
                    // OK/ENTER tuşları ile de aynı davranış
                    if (isUserSeeking) {
                        val duration = listener?.getDuration() ?: 0L
                        if (duration > 0) {
                            // SeekBar yüzde bazlı (max=100), pozisyona çevir
                            val progressPercent = binding.seekbarProgress.progress
                            val newPosition = (duration * progressPercent) / 100
                            commitSeekPosition(newPosition)
                        }
                    }
                    return true
                }
            }
            return false
        }

        /**
         * Play/Stop butonlarından hangisi görünürse ona focus verir.
         */
        fun requestFocusOnPlayStopButton() {
            val binding = this.binding ?: return
            val isPlaying = listener?.isPlayingState() ?: false
            val targetButton = if (isPlaying) binding.btnStop else binding.btnPlay

            if (targetButton.visibility == View.VISIBLE) {
                targetButton.requestFocus()
            } else {
                // Eğer hedef buton görünür değilse, diğerine ver
                val fallbackButton = if (isPlaying) binding.btnPlay else binding.btnStop
                if (fallbackButton.visibility == View.VISIBLE) {
                    fallbackButton.requestFocus()
                }
            }
        }
    }
