package com.pnr.tv

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.SeekBar
import androidx.constraintlayout.widget.ConstraintLayout
import com.pnr.tv.databinding.ViewPlayerControlsBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

interface PlayerControlListener {
    fun onPlayClicked()

    fun onPauseClicked()

    fun onSeekTo(newPositionMs: Long)

    fun onSubtitleClicked()

    fun onSpeakClicked()

    fun isPlayingState(): Boolean

    fun getDuration(): Long

    fun getCurrentPosition(): Long // Mevcut video pozisyonunu döndürür (ms cinsinden)
}

class PlayerControlView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : ConstraintLayout(context, attrs, defStyleAttr) {
        private val binding: ViewPlayerControlsBinding
        private var listener: PlayerControlListener? = null

        // State değişkenleri
        private var hideControlsJob: Job? = null
        private var isUserSeeking = false
        private var seekCommitJob: Job? = null
        private var isFirstPanelOpen = true
        private var isInitialFocus = false
        private var isLiveStream = false // Canlı yayın modu

        // Mevcut video pozisyonu (ms cinsinden)
        private var currentVideoPosition = 0L

        // Coroutine scope
        private var viewScope: CoroutineScope? = null

        companion object {
            private const val CONTROLS_AUTO_HIDE_DELAY = 8000L // 8 saniye
            private const val SEEK_AUTO_COMMIT_DELAY = 1000L // 1 saniye
            private const val SEEK_INCREMENT = 30000L // 30 saniye
            private const val BUTTON_SEEK_AMOUNT = 30000L // 30 saniye
        }

        init {
            binding = ViewPlayerControlsBinding.inflate(LayoutInflater.from(context), this, true)
            // Root view focusable olmamalı - sadece child view'lar focus alabilmeli
            isFocusable = false
            isFocusableInTouchMode = false
            // Tüm focusable view'ların focusable olduğundan emin ol
            ensureAllViewsFocusable()
            setupSeekBar()
            setupPlayStopButtons()
            setupForwardBackwardButtons()
            setupSubtitleButton()
            setupSpeakButton()
        }

        /**
         * Tüm kontrol view'larının focusable olduğundan emin olur
         * Focus chain XML'de tanımlı, burada sadece focusable özelliklerini garanti ediyoruz
         */
        private fun ensureAllViewsFocusable() {
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

            timber.log.Timber.d("✅ Tüm view'lar focusable yapıldı")
        }

        fun setPlayerControlListener(listener: PlayerControlListener) {
            this.listener = listener
        }

        /**
         * Kullanıcının şu anda seek yapıp yapmadığını döndürür.
         */
        fun isSeeking(): Boolean {
            return isUserSeeking
        }

        /**
         * Kontrol panelinin şu anda görünür (VISIBLE) olup olmadığını döndürür.
         */
        fun isVisible(): Boolean {
            return visibility == View.VISIBLE
        }

        /**
         * Ayarlar panelinin açık olup olmadığını kontrol eden callback
         */
        private var isSettingsPanelOpenCallback: (() -> Boolean)? = null

        fun setSettingsPanelOpenCallback(callback: () -> Boolean) {
            isSettingsPanelOpenCallback = callback
        }

        fun setContentInfo(
            title: String?,
            rating: Double?,
        ) {
            val displayText =
                buildString {
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
            binding.txtContentTitle.text = displayText
            timber.log.Timber.d("📝 İçerik bilgisi ayarlandı: $displayText")
        }

        /**
         * Canlı yayın modunu ayarlar - canlı yayında tüm kontrol butonları devre dışı olur
         */
        fun setLiveStreamMode(enabled: Boolean) {
            isLiveStream = enabled
            updateButtonsState()
            timber.log.Timber.d("📺 Canlı yayın modu: ${if (enabled) "Aktif (tuşlar devre dışı)" else "Pasif"}")
        }

        /**
         * Canlı yayın moduna göre buton durumlarını günceller
         */
        private fun updateButtonsState() {
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
                setupSeekBar()
                setupPlayStopButtons()
                setupForwardBackwardButtons()
                setupSubtitleButton()
                setupSpeakButton()

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

        fun showControls() {
            timber.log.Timber.d("🎛️ showControls() çağrıldı")
            timber.log.Timber.d("🔍 showControls: visibility=$visibility, isFirstPanelOpen=$isFirstPanelOpen")

            val isFirstOpen = isFirstPanelOpen && visibility != View.VISIBLE
            timber.log.Timber.d("🔍 showControls: isFirstOpen=$isFirstOpen")

            // Panel zaten görünürse, sadece sayacı sıfırla
            if (visibility == View.VISIBLE) {
                timber.log.Timber.d("ℹ️ Panel zaten görünür, sayaç sıfırlanıyor")
                resetAutoHideTimer()
                return
            }

            timber.log.Timber.d("▶️ Panel gösteriliyor ve animasyon başlatılıyor")

            // Paneli göster ve animasyonu başlat
            visibility = View.VISIBLE

            // Parent view'ı üste getir
            (parent as? ViewGroup)?.bringChildToFront(this)
            bringToFront()

            // Elevation'ı artır (z-order için)
            elevation = 20f

            // Background'u şeffaf yap
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            background = null // Drawable'ı da kaldır

            // Tüm child view'ları da görünür yap
            binding.panelTopSection.visibility = View.VISIBLE
            binding.panelBottomSection.visibility = View.VISIBLE
            binding.panelDivider.visibility = View.VISIBLE
            binding.txtContentTitle.visibility = View.VISIBLE
            binding.seekbarProgress.visibility = View.VISIBLE
            binding.txtCurrentTime.visibility = View.VISIBLE
            binding.txtTotalTime.visibility = View.VISIBLE

            // Butonları da görünür yap - canlı yayında bile görünür ama devre dışı
            binding.btnPlay.visibility = if (listener?.isPlayingState() == false) View.VISIBLE else View.GONE
            binding.btnStop.visibility = if (listener?.isPlayingState() == true) View.VISIBLE else View.GONE
            binding.btnBackward.visibility = View.VISIBLE
            binding.btnForward.visibility = View.VISIBLE
            binding.btnSpeak.visibility = View.VISIBLE
            binding.btnSubtitle.visibility = View.VISIBLE

            // Canlı yayında buton durumlarını güncelle
            if (isLiveStream) {
                updateButtonsState()
            }

            // Child view'ların background'larını da şeffaf yap
            binding.panelTopSection.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            binding.panelBottomSection.setBackgroundColor(android.graphics.Color.TRANSPARENT)

            timber.log.Timber.d(
                "🔍 Visibility ayarlandı: parent=$visibility, top=${binding.panelTopSection.visibility}, bottom=${binding.panelBottomSection.visibility}",
            )
            timber.log.Timber.d("🔍 Elevation: $elevation, Parent: ${parent?.javaClass?.simpleName}")

            requestLayout()
            invalidate()
            post {
                // Child view'ların boyutlarını logla
                timber.log.Timber.d("🔍 Child view boyutları:")
                timber.log.Timber.d(
                    "  - panelTopSection: ${binding.panelTopSection.width}x${binding.panelTopSection.height}, visibility=${binding.panelTopSection.visibility}",
                )
                timber.log.Timber.d(
                    "  - panelBottomSection: ${binding.panelBottomSection.width}x${binding.panelBottomSection.height}, visibility=${binding.panelBottomSection.visibility}",
                )
                timber.log.Timber.d(
                    "  - txtContentTitle: ${binding.txtContentTitle.width}x${binding.txtContentTitle.height}, visibility=${binding.txtContentTitle.visibility}, text='${binding.txtContentTitle.text}'",
                )
                timber.log.Timber.d(
                    "  - seekbarProgress: ${binding.seekbarProgress.width}x${binding.seekbarProgress.height}, visibility=${binding.seekbarProgress.visibility}",
                )
                timber.log.Timber.d(
                    "  - btnPlay: ${binding.btnPlay.width}x${binding.btnPlay.height}, visibility=${binding.btnPlay.visibility}",
                )
                timber.log.Timber.d(
                    "  - btnStop: ${binding.btnStop.width}x${binding.btnStop.height}, visibility=${binding.btnStop.visibility}",
                )

                requestLayout()
                invalidate()
                bringToFront()
            }
            val slideIn = AnimationUtils.loadAnimation(context, R.anim.slide_in_bottom)
            startAnimation(slideIn)

            // HER ZAMAN kontrol bar açıldığında play/stop butonuna focus ver
            // İlk açılışta butona focus ver (ama click çalışmasın)
            val shouldUseInitialFocus = isFirstOpen
            if (shouldUseInitialFocus) {
                isInitialFocus = true
                isFirstPanelOpen = false
            }

            // Animasyon bitince focus ver - HER ZAMAN play/stop butonuna
            slideIn.setAnimationListener(
                object : android.view.animation.Animation.AnimationListener {
                    override fun onAnimationStart(animation: android.view.animation.Animation?) {}

                    override fun onAnimationRepeat(animation: android.view.animation.Animation?) {}

                    override fun onAnimationEnd(animation: android.view.animation.Animation?) {
                        // HER ZAMAN kontrol bar açıldığında Play/Stop butonuna focus ver
                        requestFocusOnPlayStopButton()

                        // İlk açılış için flag'i kısa bir süre sonra sıfırla (ilk focus'ta click çalışmasın)
                        if (shouldUseInitialFocus) {
                            postDelayed({
                                isInitialFocus = false
                                timber.log.Timber.d("✅ İlk focus flag'i sıfırlandı")
                            }, 300)
                        }
                    }
                },
            )

            // Otomatik gizlenme sayacını başlat
            resetAutoHideTimer()

            timber.log.Timber.d("✅ Panel gösterildi, 8 saniye sonra otomatik gizlenecek")
        }

        fun hideControls() {
            if (visibility != View.VISIBLE) return

            // Ayarlar paneli açıksa kontrol bar'ı kapatma
            if (isSettingsPanelOpenCallback?.invoke() == true) {
                timber.log.Timber.d("⚠️ Ayarlar paneli açık, kontrol bar kapanmayacak")
                return
            }

            // Paneli gizle ve animasyonu başlat
            val slideOut = AnimationUtils.loadAnimation(context, R.anim.slide_out_bottom)
            startAnimation(slideOut)

            // Animasyon bitince visibility'yi GONE yap
            slideOut.setAnimationListener(
                object : android.view.animation.Animation.AnimationListener {
                    override fun onAnimationStart(animation: android.view.animation.Animation?) {}

                    override fun onAnimationRepeat(animation: android.view.animation.Animation?) {}

                    override fun onAnimationEnd(animation: android.view.animation.Animation?) {
                        visibility = View.GONE
                        requestLayout()
                        invalidate()
                    }
                },
            )
        }

        fun updatePlayStopButtons(isPlaying: Boolean) {
            if (isPlaying) {
                // Video oynatılıyorsa → Stop görünür, Play gizli
                binding.btnStop.visibility = View.VISIBLE
                binding.btnPlay.visibility = View.GONE
            } else {
                // Video durdurulmuşsa → Play görünür, Stop gizli
                binding.btnPlay.visibility = View.VISIBLE
                binding.btnStop.visibility = View.GONE
            }
            timber.log.Timber.d("🎮 Buton durumu güncellendi: isPlaying=$isPlaying")
        }

        /**
         * Play/Stop butonlarından hangisi görünürse ona focus verir
         */
        fun requestFocusOnPlayStopButton() {
            val isPlaying = listener?.isPlayingState() ?: false
            val targetButton = if (isPlaying) binding.btnStop else binding.btnPlay

            if (targetButton.visibility == View.VISIBLE) {
                targetButton.requestFocus()
                timber.log.Timber.d("🎯 Focus verildi: ${targetButton.javaClass.simpleName}")
            } else {
                // Eğer hedef buton görünür değilse, diğerine ver
                val fallbackButton = if (isPlaying) binding.btnPlay else binding.btnStop
                if (fallbackButton.visibility == View.VISIBLE) {
                    fallbackButton.requestFocus()
                    timber.log.Timber.d("🎯 Fallback focus verildi: ${fallbackButton.javaClass.simpleName}")
                }
            }
        }

        fun updateProgress(position: Long) {
            // Mevcut pozisyonu sakla (seek işlemleri için kullanılacak)
            currentVideoPosition = position

            val duration = listener?.getDuration() ?: 0L
            if (!isUserSeeking) {
                if (duration > 0) {
                    val progress = ((position * 100) / duration).toInt().coerceIn(0, 100)
                    binding.seekbarProgress.progress = progress
                    binding.txtCurrentTime.text = formatTime(position)
                }
            }
            binding.txtTotalTime.text = formatTime(duration)
        }

        override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
            if (event == null) return super.dispatchKeyEvent(event)

            // Canlı yayında tüm tuş işlemlerini engelle (BACK, Page Up/Down ve Channel Up/Down hariç)
            // Gerçek TV kumandalarında genellikle Channel Up/Down tuşları kullanılır
            if (isLiveStream && event.keyCode != KeyEvent.KEYCODE_BACK &&
                event.keyCode != KeyEvent.KEYCODE_PAGE_UP &&
                event.keyCode != KeyEvent.KEYCODE_PAGE_DOWN &&
                event.keyCode != KeyEvent.KEYCODE_CHANNEL_UP &&
                event.keyCode != KeyEvent.KEYCODE_CHANNEL_DOWN
            ) {
                // Canlı yayında sadece panel'i gösterme/gizleme ve BACK tuşu çalışsın
                if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_BACK) {
                    if (isVisible()) {
                        hideControls()
                        return true
                    }
                }
                // Diğer tüm tuşları engelle
                return false
            }

            val focusedView = findFocus()
            val focusedViewName =
                when (focusedView) {
                    binding.seekbarProgress -> "SeekBar"
                    binding.btnPlay -> "Play"
                    binding.btnStop -> "Stop"
                    binding.btnBackward -> "Backward"
                    binding.btnForward -> "Forward"
                    binding.btnSpeak -> "Speak"
                    binding.btnSubtitle -> "Subtitle"
                    null -> "NULL"
                    else -> focusedView?.javaClass?.simpleName ?: "UNKNOWN"
                }

            timber.log.Timber.d("🔍 dispatchKeyEvent: action=${event.action}, keyCode=${event.keyCode}, focusedView=$focusedViewName")

            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    // Panel görünürken herhangi bir tuş olayı (BACK hariç) timer'ı sıfırlar
                    if (isVisible() && event.keyCode != KeyEvent.KEYCODE_BACK) {
                        resetAutoHideTimer()
                        timber.log.Timber.d("⏱️ Timer sıfırlandı (tuş basıldı)")
                    }

                    // Geri tuşu - Panel açıksa kapat
                    if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                        if (isVisible()) {
                            hideControls()
                            return true // Olayı tüket
                        }
                    }
                    // SeekBar odaktaysa, tuş olaylarını özel olarak yönet
                    if (focusedView == binding.seekbarProgress) {
                        timber.log.Timber.d("📊 SeekBar'da tuş basıldı: keyCode=${event.keyCode}")
                        val handled = handleSeekBarKeyEvent(event.keyCode, true)
                        if (handled) {
                            // LEFT/RIGHT tuşları için focus'un SeekBar'da kalmasını garanti et
                            // DOWN tuşu için focus butona geçmeli, geri almayalım
                            if (event.keyCode != KeyEvent.KEYCODE_DPAD_DOWN) {
                                post {
                                    if (findFocus() != binding.seekbarProgress) {
                                        timber.log.Timber.w("⚠️ Focus SeekBar'dan kaydı, geri alınıyor...")
                                        binding.seekbarProgress.requestFocus()
                                    }
                                }
                            } else {
                                timber.log.Timber.d("✅ DPAD_DOWN ile butona focus geçişi yapıldı, geri alınmayacak")
                            }
                            return true
                        }
                    }
                    // Play/Stop butonlarında OK tuşu - Focus değişmesin, sadece click çalışsın
                    if (event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
                        if (focusedView == binding.btnPlay || focusedView == binding.btnStop) {
                            timber.log.Timber.d("🎯 Play/Stop butonunda OK tuşu basıldı, onClick tetiklenecek")
                            // onClick zaten çalışacak, sadece focus'un değişmesini engelle
                            return false // super.dispatchKeyEvent'e gitmesin, onClick çalışsın
                        }
                    }
                    // Diğer butonlarda DPAD DOWN kontrol bar'ı kapatsın (ayarlar paneli açık değilse)
                    if (event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        // Ayarlar paneli açıksa kontrol bar kapanmasın
                        if (isSettingsPanelOpenCallback?.invoke() == true) {
                            timber.log.Timber.d("⚠️ Ayarlar paneli açık, DPAD_DOWN ile kontrol bar kapanmayacak")
                            return false // Normal focus geçişine izin ver
                        }

                        if (focusedView == binding.btnPlay ||
                            focusedView == binding.btnStop ||
                            focusedView == binding.btnBackward ||
                            focusedView == binding.btnForward ||
                            focusedView == binding.btnSpeak ||
                            focusedView == binding.btnSubtitle
                        ) {
                            hideControls()
                            return true
                        }
                    }
                    // Backward butonunda DPAD LEFT - Play/Stop'a dinamik focus
                    if (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT && focusedView == binding.btnBackward) {
                        val targetButton =
                            if (listener?.isPlayingState() == true) {
                                binding.btnStop
                            } else {
                                binding.btnPlay
                            }
                        if (targetButton.visibility == View.VISIBLE) {
                            targetButton.requestFocus()
                            timber.log.Timber.d(
                                "🎯 Backward'dan ${if (listener?.isPlayingState() == true) "Stop" else "Play"} butonuna focus",
                            )
                            return true
                        }
                    }
                }
                KeyEvent.ACTION_UP -> {
                    val newFocusedView = findFocus()
                    val newFocusedViewName =
                        when (newFocusedView) {
                            binding.seekbarProgress -> "SeekBar"
                            binding.btnPlay -> "Play"
                            binding.btnStop -> "Stop"
                            binding.btnBackward -> "Backward"
                            binding.btnForward -> "Forward"
                            binding.btnSubtitle -> "Subtitle"
                            null -> "NULL"
                            else -> newFocusedView?.javaClass?.simpleName ?: "UNKNOWN"
                        }
                    timber.log.Timber.d(
                        "🔍 ACTION_UP: keyCode=${event?.keyCode}, focus değişti mi? ${focusedView != newFocusedView}, yeni focus=$newFocusedViewName",
                    )

                    if (focusedView == binding.seekbarProgress) {
                        val handled = handleSeekBarKeyEvent(event.keyCode, false)
                        // LEFT/RIGHT tuşları için focus değiştiyse geri al
                        // DOWN tuşu için focus butona geçmeli, geri almayalım
                        if (event.keyCode != KeyEvent.KEYCODE_DPAD_DOWN) {
                            if (newFocusedView != binding.seekbarProgress && newFocusedView != focusedView) {
                                timber.log.Timber.w("⚠️ ACTION_UP: Focus SeekBar'dan $newFocusedViewName'a kaydı, geri alınıyor...")
                                post {
                                    binding.seekbarProgress.requestFocus()
                                }
                            }
                        } else {
                            timber.log.Timber.d("✅ ACTION_UP: DPAD_DOWN ile butona focus geçişi korunuyor")
                        }
                        return handled
                    }
                }
            }
            val result = super.dispatchKeyEvent(event)
            val finalFocusedView = findFocus()
            val finalFocusedViewName =
                when (finalFocusedView) {
                    binding.seekbarProgress -> "SeekBar"
                    binding.btnPlay -> "Play"
                    binding.btnStop -> "Stop"
                    binding.btnBackward -> "Backward"
                    binding.btnForward -> "Forward"
                    binding.btnSpeak -> "Speak"
                    binding.btnSubtitle -> "Subtitle"
                    null -> "NULL"
                    else -> finalFocusedView?.javaClass?.simpleName ?: "UNKNOWN"
                }
            timber.log.Timber.d("🔍 dispatchKeyEvent sonu: result=$result, final focus=$finalFocusedViewName")
            return result
        }

        private fun handleSeekBarKeyEvent(
            keyCode: Int,
            isKeyDown: Boolean,
        ): Boolean {
            // Sadece tuşa ilk basılma anıyla ilgileniyoruz.
            if (!isKeyDown) return false

            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    handleSeekButtonClick(SEEK_INCREMENT)
                    timber.log.Timber.d("⏩ SeekBar ileri: ${SEEK_INCREMENT / 1000}s")
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    handleSeekButtonClick(-SEEK_INCREMENT)
                    timber.log.Timber.d("⏪ SeekBar geri: ${SEEK_INCREMENT / 1000}s")
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    val targetButton = if (listener?.isPlayingState() == true) binding.btnStop else binding.btnPlay
                    if (targetButton.visibility == View.VISIBLE) {
                        targetButton.requestFocus()
                        timber.log.Timber.d("🎯 SeekBar'dan ${targetButton.contentDescription} butonuna focus")
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_CENTER -> {
                    // OK tuşuna basıldığında, zamanlayıcının dolmasını beklemeden
                    // mevcut hesaplanan yeni pozisyonu hemen commit et.
                    if (isUserSeeking) {
                        val duration = listener?.getDuration() ?: 0L
                        if (duration > 0) {
                            val progress = binding.seekbarProgress.progress
                            val newPosition = (duration * progress / 100)
                            commitSeekPosition(newPosition)
                            timber.log.Timber.d("✅ SeekBar pozisyonu onaylandı (OK tuşu): ${formatTime(newPosition)}")
                        }
                    } else {
                        timber.log.Timber.d("✅ SeekBar pozisyonu onaylandı (OK tuşu) - seek yapılmadı")
                    }
                    return true
                }
            }
            return false
        }

        private fun updatePreviewTime(positionMs: Long) {
            binding.txtCurrentTime.text = formatTime(positionMs)
        }

        private fun startSeekCommitTimer(newPositionMs: Long) {
            // Önceki timer'ı iptal et
            seekCommitJob?.cancel()

            // 1 saniye sonra otomatik commit
            seekCommitJob =
                viewScope?.launch {
                    delay(SEEK_AUTO_COMMIT_DELAY)
                    commitSeekPosition(newPositionMs)
                    timber.log.Timber.d("⏱️ SeekBar pozisyonu otomatik onaylandı (1 saniye timeout): ${formatTime(newPositionMs)}")
                }
        }

        private fun commitSeekPosition(newPositionMs: Long) {
            timber.log.Timber.d("‼️ commitSeekPosition çağrıldı. isUserSeeking: $isUserSeeking, newPosition: ${formatTime(newPositionMs)}")
            if (!isUserSeeking) {
                timber.log.Timber.w("⚠️ isUserSeeking false olduğu için commit iptal edildi.")
                return
            }

            // Video pozisyonunu değiştir
            listener?.onSeekTo(newPositionMs)
            timber.log.Timber.d("✅ onSeekTo(${newPositionMs}ms) çağrıldı.")

            // Flag'leri sıfırla
            isUserSeeking = false
            seekCommitJob?.cancel()
            timber.log.Timber.d("✅ Kilit kaldırıldı. isUserSeeking: $isUserSeeking")

            // Auto-hide sayacını yeniden başlat
            resetAutoHideTimer()

            timber.log.Timber.d("🎯 Video pozisyonu değiştirildi: ${formatTime(newPositionMs)}")
        }

        private fun setupSeekBar() {
            // SeekBar değişikliklerini dinle
            binding.seekbarProgress.setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(
                        seekBar: SeekBar?,
                        progress: Int,
                        fromUser: Boolean,
                    ) {
                        if (fromUser || isUserSeeking) {
                            // Kullanıcı seek yapıyor (touch veya DPAD), süreyi güncelle
                            val duration = listener?.getDuration() ?: 0L
                            val positionMs = if (duration > 0) (duration * progress / 100) else 0L
                            updatePreviewTime(positionMs)
                        }
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) {
                        isUserSeeking = true
                        hideControlsJob?.cancel() // Seek yaparken panel gizlenmesin
                        timber.log.Timber.d("👆 SeekBar tracking başladı")
                    }

                    override fun onStopTrackingTouch(seekBar: SeekBar?) {
                        // Touch ile seek bittiğinde commit
                        val duration = listener?.getDuration() ?: 0L
                        val progress = binding.seekbarProgress.progress
                        val newPosition = if (duration > 0) (duration * progress / 100) else 0L
                        commitSeekPosition(newPosition)
                        timber.log.Timber.d("👆 SeekBar tracking bitti: ${formatTime(newPosition)}")
                    }
                },
            )
        }

        private fun setupPlayStopButtons() {
            // Play butonuna tıklanınca video oynat
            binding.btnPlay.setOnClickListener {
                val currentFocus = findFocus()
                timber.log.Timber.d(
                    "▶️▶️▶️ PLAY BUTONU onClick TETİKLENDİ! Focus=${currentFocus?.javaClass?.simpleName}, isInitialFocus=$isInitialFocus",
                )

                // İlk focus'ta click çalışmasın
                if (isInitialFocus) {
                    timber.log.Timber.d("⏸️ İlk focus'ta click engellendi (Play)")
                    return@setOnClickListener
                }

                timber.log.Timber.d("▶️ Play butonuna tıklandı, onPlayClicked çağrılıyor...")
                listener?.onPlayClicked()
                showControls() // Panel göster
                // Focus'u Play butonunda tut (OK tuşundan sonra focus değişmesin)
                post {
                    binding.btnPlay.requestFocus()
                }
                timber.log.Timber.d("▶️ Play butonu onClick tamamlandı")
            }

            // Stop butonuna tıklanınca video durdur
            binding.btnStop.setOnClickListener {
                // İlk focus'ta click çalışmasın
                if (isInitialFocus) {
                    timber.log.Timber.d("⏸️ İlk focus'ta click engellendi (Stop)")
                    return@setOnClickListener
                }

                listener?.onPauseClicked()
                showControls() // Panel göster
                // Focus'u Stop butonunda tut (OK tuşundan sonra focus değişmesin)
                post {
                    binding.btnStop.requestFocus()
                }
                timber.log.Timber.d("⏸️ Stop butonuna tıklandı")
            }

            // Play/Stop butonlarında OK tuşu için key listener ekle - focus değişmesin
            binding.btnPlay.setOnKeyListener { view, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER && event.action == KeyEvent.ACTION_DOWN) {
                    // onClick zaten çalışacak, sadece focus'un değişmesini engelle
                    post {
                        view.requestFocus()
                    }
                    false // onClick'in çalışmasına izin ver
                } else {
                    false
                }
            }

            binding.btnStop.setOnKeyListener { view, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER && event.action == KeyEvent.ACTION_DOWN) {
                    // onClick zaten çalışacak, sadece focus'un değişmesini engelle
                    post {
                        view.requestFocus()
                    }
                    false // onClick'in çalışmasına izin ver
                } else {
                    false
                }
            }
        }

        private fun setupForwardBackwardButtons() {
            binding.btnBackward.setOnClickListener {
                handleSeekButtonClick(-BUTTON_SEEK_AMOUNT)
                timber.log.Timber.d("⏪ Geri Sar Butonu tıklandı")
            }

            binding.btnForward.setOnClickListener {
                handleSeekButtonClick(BUTTON_SEEK_AMOUNT)
                timber.log.Timber.d("⏩ İleri Sar Butonu tıklandı")
            }
        }

        private fun setupSubtitleButton() {
            binding.btnSubtitle.setOnClickListener {
                timber.log.Timber.d("📝 Alt yazı butonuna tıklandı")
                listener?.onSubtitleClicked()
            }
        }

        private fun setupSpeakButton() {
            binding.btnSpeak.setOnClickListener {
                timber.log.Timber.d("🔊 Ses butonuna tıklandı")
                listener?.onSpeakClicked()
            }
        }

        private fun handleSeekButtonClick(amount: Long) {
            val focusBefore = findFocus()
            timber.log.Timber.d(
                "🔘 handleSeekButtonClick başladı: amount=${amount / 1000}s, focus önce=${focusBefore?.javaClass?.simpleName}",
            )

            val duration = listener?.getDuration() ?: 0L
            if (duration <= 0) {
                timber.log.Timber.w("⚠️ handleSeekButtonClick: duration <= 0, iptal edildi")
                return
            }

            // 1. Adım: Kilidi etkinleştir ve paneli açık tut
            isUserSeeking = true
            hideControlsJob?.cancel()

            // 2. Adım: Yeni pozisyonu hesapla (GÜVENİLİR KAYNAKTAN OKU: Doğrudan player'dan)
            val currentPosition = listener?.getCurrentPosition() ?: 0L
            val newPosition = (currentPosition + amount).coerceIn(0, duration)
            val newProgress = ((newPosition * 100) / duration).toInt().coerceIn(0, 100)

            timber.log.Timber.d(
                "🔘 handleSeekButtonClick: currentPosition=${currentPosition / 1000}s, newPosition=${newPosition / 1000}s, newProgress=$newProgress, post bloğu çağrılıyor...",
            )

            // 3. Adım: UI'ı manuel olarak güncelle (Görsel geri bildirim) - POST bloğu içinde
            post {
                val focusInPost = findFocus()
                timber.log.Timber.d("🔘 POST bloğu içinde (handleSeekButtonClick): focus=${focusInPost?.javaClass?.simpleName}")
                binding.seekbarProgress.progress = newProgress
                updatePreviewTime(newPosition) // newProgress yerine newPosition ver
                val focusAfterPost = findFocus()
                timber.log.Timber.d("🔘 POST bloğu sonrası (handleSeekButtonClick): focus=${focusAfterPost?.javaClass?.simpleName}")
            }

            // 4. Adım: İşlemi, mevcut sağlam mekanizmaya devret
            startSeekCommitTimer(newPosition) // Zamanlayıcıya yeni pozisyonu ver
            timber.log.Timber.d("🔘 handleSeekButtonClick tamamlandı")
        }

        private fun formatTime(timeMs: Long): String {
            val hours = TimeUnit.MILLISECONDS.toHours(timeMs)
            val minutes = TimeUnit.MILLISECONDS.toMinutes(timeMs) % 60
            val seconds = TimeUnit.MILLISECONDS.toSeconds(timeMs) % 60

            return if (hours > 0) {
                String.format("%02d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
        }

        private fun resetAutoHideTimer() {
            // Önceki sayacı iptal et
            hideControlsJob?.cancel()

            // Yeni sayaç başlat
            hideControlsJob =
                viewScope?.launch {
                    delay(CONTROLS_AUTO_HIDE_DELAY)
                    hideControls()
                }
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            // View her ekrana bağlandığında yeni, aktif bir scope oluştur
            if (viewScope == null) {
                viewScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
                timber.log.Timber.d("✅ CoroutineScope onAttachedToWindow içinde yeniden oluşturuldu.")
            }
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            // View ekrandan kaldırıldığında scope'u iptal et
            viewScope?.cancel()
            viewScope = null // Referansı temizle
            hideControlsJob?.cancel()
            seekCommitJob?.cancel()
            timber.log.Timber.d("❌ CoroutineScope onDetachedFromWindow içinde iptal edildi.")
        }
    }
