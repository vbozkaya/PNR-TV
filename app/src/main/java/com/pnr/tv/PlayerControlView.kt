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
        private var isLiveStream = false // Canlı yayın modu

        // Mevcut video pozisyonu (ms cinsinden)
        private var currentVideoPosition = 0L

        // Kademeli seek için state değişkenleri
        private var pendingSeekAmount = 0L // Biriken seek miktarı
        private var lastSeekTime = 0L // Son seek işleminin zamanı (throttle için)

        // Coroutine scope
        private var viewScope: CoroutineScope? = null

        companion object {
            private const val CONTROLS_AUTO_HIDE_DELAY = 8000L // 8 saniye
            private const val SEEK_AUTO_COMMIT_DELAY = 1000L // 1 saniye
            private const val SEEK_INCREMENT = 30000L // 30 saniye (varsayılan)
            private const val BUTTON_SEEK_AMOUNT = 30000L // 30 saniye (butonlar için)

            // Kademeli seek sabitleri
            private const val SEEK_AMOUNT_SHORT_HOLD = 60000L // 1 dakika (500ms-5s arası)
            private const val SEEK_AMOUNT_LONG_HOLD = 300000L // 5 dakika (5s+)
            private const val HOLD_THRESHOLD_SHORT = 500L // 500ms (kısa basılı tutma başlangıcı)
            private const val HOLD_THRESHOLD_LONG = 5000L // 5 saniye (uzun basılı tutma başlangıcı)
            private const val THROTTLE_DELAY = 300L // 300ms (5 dakikalık atlamalar için throttle)
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
            val isPlaying = listener?.isPlayingState() ?: false
            timber.log.Timber.d("🔍 showControls: isPlaying=$isPlaying, listener null mu? ${listener == null}")
            binding.btnPlay.visibility = if (!isPlaying) View.VISIBLE else View.GONE
            binding.btnStop.visibility = if (isPlaying) View.VISIBLE else View.GONE
            timber.log.Timber.d("🔍 Buton görünürlükleri: Play=${binding.btnPlay.visibility}, Stop=${binding.btnStop.visibility}")
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

            // İlk açılış flag'ini güncelle
            if (isFirstOpen) {
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
            val currentFocus = findFocus()
            val wasFocusOnPlayStop = currentFocus == binding.btnPlay || currentFocus == binding.btnStop

            if (isPlaying) {
                // Video oynatılıyorsa → Stop görünür, Play gizli
                binding.btnStop.visibility = View.VISIBLE
                binding.btnPlay.visibility = View.GONE
                // Eğer focus play/stop butonlarındaysa, yeni görünen stop butonuna ver
                if (wasFocusOnPlayStop) {
                    post {
                        binding.btnStop.requestFocus()
                        timber.log.Timber.d("🎯 Focus Stop butonuna verildi (updatePlayStopButtons)")
                    }
                }
            } else {
                // Video durdurulmuşsa → Play görünür, Stop gizli
                binding.btnPlay.visibility = View.VISIBLE
                binding.btnStop.visibility = View.GONE
                // Eğer focus play/stop butonlarındaysa, yeni görünen play butonuna ver
                if (wasFocusOnPlayStop) {
                    post {
                        binding.btnPlay.requestFocus()
                        timber.log.Timber.d("🎯 Focus Play butonuna verildi (updatePlayStopButtons)")
                    }
                }
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
                    // Play/Stop butonlarında OK tuşu - ÖNCE kontrol et (SeekBar'dan önce)
                    if (event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER && event.action == KeyEvent.ACTION_DOWN) {
                        timber.log.Timber.d(
                            "🔍🔍🔍 OK tuşu (DPAD_CENTER) basıldı! action=${event.action}, focusedView=${focusedView?.javaClass?.simpleName}",
                        )
                        timber.log.Timber.d(
                            "🔍 btnPlay visibility: ${binding.btnPlay.visibility}, btnStop visibility: ${binding.btnStop.visibility}",
                        )
                        timber.log.Timber.d(
                            "🔍 btnPlay == focusedView: ${focusedView == binding.btnPlay}, btnStop == focusedView: ${focusedView == binding.btnStop}",
                        )

                        // Play butonu görünür ve odaklanmışsa
                        if (binding.btnPlay.visibility == View.VISIBLE && focusedView == binding.btnPlay) {
                            timber.log.Timber.d("🎯✅✅✅ Play butonunda OK tuşu basıldı, listener doğrudan çağrılıyor")
                            // Focus'u updatePlayStopButtons() içinde vereceğiz (buton görünür hale geldikten sonra)
                            // Sonra listener'ı çağır ve controls'u göster
                            listener?.onPlayClicked()
                            showControls()
                            return true // Olayı tüket
                        }
                        // Stop butonu görünür ve odaklanmışsa
                        else if (binding.btnStop.visibility == View.VISIBLE && focusedView == binding.btnStop) {
                            timber.log.Timber.d("🎯✅✅✅ Stop butonunda OK tuşu basıldı, listener doğrudan çağrılıyor")
                            // Focus'u updatePlayStopButtons() içinde vereceğiz (buton görünür hale geldikten sonra)
                            // Sonra listener'ı çağır ve controls'u göster
                            listener?.onPauseClicked()
                            showControls()
                            return true // Olayı tüket
                        } else {
                            timber.log.Timber.d("⚠️ OK tuşu basıldı ama focusedView Play/Stop değil: ${focusedView?.javaClass?.simpleName}")
                        }
                    }
                    // SeekBar odaktaysa, tuş olaylarını özel olarak yönet
                    if (focusedView == binding.seekbarProgress) {
                        timber.log.Timber.d("📊 SeekBar'da tuş basıldı: keyCode=${event.keyCode}")
                        val handled = handleSeekBarKeyEvent(event, true)
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

                    // ACTION_UP'da focus kontrolü - ACTION_DOWN'da zaten focus verildi, burada sadece kontrol et
                    if (event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
                        val currentFocus = findFocus()
                        // Eğer focus SeekBar'a kaymışsa, doğru butona geri al
                        if (currentFocus == binding.seekbarProgress) {
                            val targetButton = if (focusedView == binding.btnPlay) binding.btnStop else binding.btnPlay
                            if (targetButton.visibility == View.VISIBLE) {
                                targetButton.requestFocus()
                                timber.log.Timber.d(
                                    "✅ ACTION_UP: Focus SeekBar'dan ${targetButton.javaClass.simpleName} butonuna geri alındı",
                                )
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
            event: KeyEvent,
            isKeyDown: Boolean,
        ): Boolean {
            // Sadece tuşa ilk basılma anıyla ilgileniyoruz.
            if (!isKeyDown) return false

            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    val seekAmount = calculateSeekAmount(event, true)
                    handleSeekButtonClick(seekAmount)
                    timber.log.Timber.d(
                        "⏩ SeekBar ileri: ${seekAmount / 1000}s (holdDuration=${event.eventTime - event.downTime}ms, repeatCount=${event.repeatCount})",
                    )
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    val seekAmount = calculateSeekAmount(event, false)
                    handleSeekButtonClick(seekAmount)
                    timber.log.Timber.d(
                        "⏪ SeekBar geri: ${seekAmount / 1000}s (holdDuration=${event.eventTime - event.downTime}ms, repeatCount=${event.repeatCount})",
                    )
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

        /**
         * Kademeli seek miktarını hesaplar.
         * Basılı tutma süresine göre farklı seek miktarları döndürür.
         *
         * @param event KeyEvent - Tuş event'i
         * @param isForward İleri mi geri mi (true = ileri, false = geri)
         * @return Seek miktarı (milisaniye cinsinden, negatif veya pozitif)
         */
        private fun calculateSeekAmount(
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
                    timber.log.Timber.d("⏱️ Throttle aktif: ${timeSinceLastSeek}ms < ${THROTTLE_DELAY}ms, 1 dakikalık atlama kullanılıyor")
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

            // Biriken seek miktarını sıfırla (commit edildi)
            pendingSeekAmount = 0L
            timber.log.Timber.d("✅ Kilit kaldırıldı. isUserSeeking: $isUserSeeking, pendingSeekAmount sıfırlandı")

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
                    "▶️▶️▶️ PLAY BUTONU onClick TETİKLENDİ! Focus=${currentFocus?.javaClass?.simpleName}",
                )

                // Focus'u updatePlayStopButtons() içinde vereceğiz (buton görünür hale geldikten sonra)
                // Sonra listener'ı çağır ve controls'u göster
                timber.log.Timber.d("▶️ Play butonuna tıklandı, onPlayClicked çağrılıyor...")
                listener?.onPlayClicked()
                showControls() // Panel göster
                timber.log.Timber.d("▶️ Play butonu onClick tamamlandı")
            }

            // Stop butonuna tıklanınca video durdur
            binding.btnStop.setOnClickListener {
                timber.log.Timber.d("⏸️⏸️⏸️ STOP BUTONU onClick TETİKLENDİ!")
                timber.log.Timber.d("⏸️ listener null mu? ${listener == null}")
                // Focus'u updatePlayStopButtons() içinde vereceğiz (buton görünür hale geldikten sonra)
                // Sonra listener'ı çağır ve controls'u göster
                listener?.onPauseClicked()
                timber.log.Timber.d("⏸️ onPauseClicked() çağrıldı")
                showControls() // Panel göster
                timber.log.Timber.d("⏸️ Stop butonu onClick tamamlandı")
            }

            // setOnKeyListener kaldırıldı - dispatchKeyEvent içinde performClick() kullanılıyor
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

            // 2. Adım: Biriken seek miktarına ekle (hızlı basışları doğru hesaplamak için)
            pendingSeekAmount += amount
            timber.log.Timber.d("🔘 Biriken seek miktarı: ${pendingSeekAmount / 1000}s")

            // 3. Adım: Yeni pozisyonu hesapla (GÜVENİLİR KAYNAKTAN OKU: Doğrudan player'dan)
            val currentPosition = listener?.getCurrentPosition() ?: 0L
            // Biriken seek miktarını da hesaba kat
            val newPosition = (currentPosition + pendingSeekAmount).coerceIn(0, duration)
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
