package com.pnr.tv.ui.player.component

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.widget.SeekBar
import androidx.constraintlayout.widget.ConstraintLayout
import com.pnr.tv.databinding.ViewPlayerControlsBinding
import com.pnr.tv.ui.player.coordinator.PlayerVisibilityCoordinator
import com.pnr.tv.ui.player.handler.PlayerFocusHandler
import com.pnr.tv.ui.player.manager.PlayerSeekManager
import com.pnr.tv.ui.player.state.PlayerStateHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

interface PlayerControlListener {
    fun onPlayClicked()

    fun onPauseClicked()

    fun onSeekTo(newPositionMs: Long)

    fun onSubtitleClicked()

    fun onSpeakClicked()

    fun onChannelListRequested() // Kanal listesi paneli açılması için

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

        // Focus handler - tuş yönetimi için
        private val focusHandler: PlayerFocusHandler

        // Focus manager - focus yönetimi ve dispatchKeyEvent
        private lateinit var focusManager: PlayerFocusManager

        // Interaction handler - buton setup ve click listener'ları
        private lateinit var interactionHandler: ControlInteractionHandler

        // Seek manager - seek işlemlerini yönetir
        private val seekManager: PlayerSeekManager

        // Visibility coordinator - panel görünürlük ve animasyon mantığını yönetir
        private var visibilityCoordinator: PlayerVisibilityCoordinator? = null

        // State helper - veri formatlama ve buton durum yönetimi
        private val stateHelper: PlayerStateHelper

        // State manager - PlayerStateHelper ve PlayerControlListener ile etkileşim
        private lateinit var stateManager: PlayerStateManager

        // Coroutine scope - coordinator'lar için gerekli
        private var viewScope: CoroutineScope? = null

        init {
            // Binding'i oluştur
            binding = ViewPlayerControlsBinding.inflate(LayoutInflater.from(context), this, true)
            
            // Root view focusable olmamalı - sadece child view'lar focus alabilmeli
            isFocusable = false
            isFocusableInTouchMode = false

            // 1. StateHelper'ı oluştur (diğer manager'lar buna bağımlı)
            stateHelper = PlayerStateHelper(binding, context).also { helper ->
                helper.setFocusCallbacks(
                    requestFocus = { view -> view.requestFocus() },
                    findFocus = { findFocus() },
                    post = { runnable -> post(runnable) },
                )
            }

            // 2. StateManager'ı oluştur (state yönetimi için merkezi nokta)
            stateManager = PlayerStateManager(binding, context, stateHelper, listener)

            // 3. SeekManager'ı oluştur (viewScope henüz null, onAttachedToWindow'da güncellenecek)
            seekManager = PlayerSeekManager(binding, listener, viewScope).also {
                initializeSeekManager(it)
            }

            // 4. FocusHandler'ı oluştur (Hilt inject edilemediği için manuel)
            focusHandler = PlayerFocusHandler(context).also {
                initializeFocusHandler(it)
            }

            // 5. FocusManager'ı oluştur (focus yönetimi ve dispatchKeyEvent)
            focusManager = PlayerFocusManager(
                binding = binding,
                focusHandler = focusHandler,
                getIsUserSeeking = { stateManager.isUserSeeking() },
                getIsLiveStream = { stateManager.isLiveStream() },
                findFocus = { findFocus() },
                post = { runnable -> post(runnable) },
            )

            // 6. InteractionHandler'ı oluştur (buton setup ve click listener'ları)
            interactionHandler = ControlInteractionHandler(
                binding = binding,
                listener = listener,
                seekManager = seekManager,
                updatePlayStopButtons = { isPlaying -> stateManager.updatePlayStopButtons(isPlaying) },
                showControls = { showControls() },
            )

            // 7. StateHelper'a button setup callback'lerini ayarla
            stateHelper.setButtonSetupCallbacks(
                setupSeekBar = { setupSeekBar() },
                setupPlayStopButtons = { interactionHandler.setupPlayStopButtons() },
                setupForwardBackwardButtons = { interactionHandler.setupForwardBackwardButtons() },
                setupSubtitleButton = { interactionHandler.setupSubtitleButton() },
                setupSpeakButton = { interactionHandler.setupSpeakButton() },
            )

            // 8. UI setup'ı yap
            focusManager.ensureAllViewsFocusable()
            setupSeekBar()
            interactionHandler.setupPlayStopButtons()
            interactionHandler.setupForwardBackwardButtons()
            interactionHandler.setupSubtitleButton()
            interactionHandler.setupSpeakButton()
            
            // 9. Butonları aktif modda başlat (canlı yayın değil)
            stateHelper.updateButtonsState(false)
        }

        /**
         * PlayerSeekManager'ı başlatır ve gerekli callback'leri ayarlar.
         * State senkronizasyonu: isUserSeeking ve watchedPosition stateManager ile senkronize edilir.
         */
        private fun initializeSeekManager(manager: PlayerSeekManager) {
            // isUserSeeking state senkronizasyonu
            manager.setIsUserSeekingCallbacks(
                get = { stateManager.isUserSeeking() },
                set = { stateManager.setIsUserSeeking(it) },
            )
            
            // watchedPosition state senkronizasyonu
            manager.setWatchedPositionCallbacks(
                get = { stateManager.getWatchedPosition() },
                set = { stateManager.setWatchedPosition(it) },
                updateProgress = { stateManager.updateWatchedProgress() },
            )
            
            // cachedDuration state senkronizasyonu
            manager.setGetCachedDurationCallback { stateManager.getCachedDuration() }
            
            // Visibility coordinator callback'leri
            manager.setCancelAutoHideTimerCallback { visibilityCoordinator?.cancelAutoHideTimer() }
            manager.setResetAutoHideTimerCallback { visibilityCoordinator?.resetAutoHideTimer() }
        }

        /**
         * PlayerFocusHandler'ı başlatır ve gerekli referansları ayarlar.
         * State senkronizasyonu: isUserSeeking ve isLiveStream stateManager'dan alınır.
         */
        private fun initializeFocusHandler(handler: PlayerFocusHandler) {
            handler.setBinding(binding)
            handler.setListener(listener)
            // State senkronizasyonu: stateManager'dan state'leri al
            handler.setIsUserSeeking(stateManager.isUserSeeking())
            handler.setIsLiveStream(stateManager.isLiveStream())
            handler.setSettingsPanelOpenCallback(isSettingsPanelOpenCallback)

            // Callback'leri ayarla - seek işlemleri seekManager üzerinden
            handler.setCallbacks(
                calculateSeekAmount = { event, isForward -> seekManager.calculateSeekAmount(event, isForward) },
                handleSeekButtonClick = { amount -> seekManager.handleSeekButtonClick(amount) },
                commitSeekPosition = { position -> seekManager.commitSeekPosition(position) },
                showControls = { showControls() },
                hideControls = { hideControls() },
                resetAutoHideTimer = { visibilityCoordinator?.resetAutoHideTimer() },
                isVisible = { isVisible() },
                findFocus = { findFocus() },
                post = { runnable -> post(runnable) },
            )
        }


        /**
         * PlayerControlListener'ı ayarlar ve tüm manager'lara senkronize eder.
         */
        fun setPlayerControlListener(listener: PlayerControlListener) {
            this.listener = listener
            
            // State manager'ı yeniden oluştur (listener değişti)
            stateManager = PlayerStateManager(binding, context, stateHelper, listener)
            
            // Tüm manager'lara listener'ı senkronize et
            focusManager.setListener(listener)
            interactionHandler.setListener(listener)
            seekManager.setListener(listener)
            focusHandler.setListener(listener)
        }

        /**
         * Kullanıcının şu anda seek yapıp yapmadığını döndürür.
         */
        fun isSeeking(): Boolean {
            return stateManager.isUserSeeking()
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
        
        /**
         * Kullanıcı manuel olarak panel açtığında çağrılacak callback
         */
        private var onControlsShownManuallyCallback: (() -> Unit)? = null

        fun setSettingsPanelOpenCallback(callback: () -> Boolean) {
            isSettingsPanelOpenCallback = callback
            focusManager.setSettingsPanelOpenCallback(callback)
            visibilityCoordinator?.setSettingsPanelOpenCallback(callback)
        }
        
        fun setOnControlsShownManuallyCallback(callback: () -> Unit) {
            onControlsShownManuallyCallback = callback
        }

        fun setContentInfo(
            title: String?,
            rating: Double?,
        ) {
            stateManager.setContentInfo(title, rating)
        }

        /**
         * Canlı yayın modunu ayarlar - canlı yayında tüm kontrol butonları devre dışı olur.
         * State senkronizasyonu: isLiveStream stateManager ve focusManager arasında senkronize edilir.
         */
        fun setLiveStreamMode(enabled: Boolean) {
            // State manager'a kaydet (bu setIsLiveStream ve updateButtonsState yapıyor)
            stateManager.setLiveStreamMode(enabled)
            // Focus manager'a da senkronize et
            focusManager.setIsLiveStream(enabled)
        }

        /**
         * İzlenen pozisyonu ayarlar (SeekBar'da kırmızı olarak gösterilecek).
         * @param positionMs İzlenen pozisyon (milisaniye cinsinden)
         */
        fun setWatchedPosition(positionMs: Long) {
            stateManager.setWatchedPosition(positionMs)
        }

        fun showControls() {
            visibilityCoordinator?.showControls()
        }

        fun hideControls() {
            visibilityCoordinator?.hideControls()
        }

        fun updatePlayStopButtons(isPlaying: Boolean) {
            stateManager.updatePlayStopButtons(isPlaying)
        }

        /**
         * Play/Stop butonlarından hangisi görünürse ona focus verir
         */
        fun requestFocusOnPlayStopButton() {
            focusManager.requestFocusOnPlayStopButton()
        }

        fun updateProgress(position: Long) {
            stateManager.updateProgress(position)
        }

        fun updateProgressWithDuration(
            position: Long,
            duration: Long,
        ) {
            stateManager.updateProgressWithDuration(position, duration)
        }

        override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
            return focusManager.dispatchKeyEvent(event) { superEvent ->
                super.dispatchKeyEvent(superEvent)
            }
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
                        // Programatik veya kullanıcı değişikliklerinde görsel güncellemeyi garanti et
                        seekBar?.jumpDrawablesToCurrentState()
                        seekBar?.postInvalidateOnAnimation()
                        
                        if (fromUser || stateManager.isUserSeeking()) {
                            // Kullanıcı seek yapıyor (touch veya DPAD), süreyi güncelle
                            val duration = listener?.getDuration() ?: 0L
                            // Yüzde bazlı progress'i pozisyona çevir
                            val positionMs = if (duration > 0) {
                                (duration * progress) / 100
                            } else {
                                0L
                            }
                            seekManager.updatePreviewTime(positionMs)

                            // İzlenen pozisyonu anlık olarak güncelle (touch ile seek yaparken)
                            stateManager.setWatchedPosition(positionMs)
                            // İzlenen progress'i de anlık olarak güncelle
                            stateManager.updateWatchedProgress(duration)
                        }
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) {
                        // State senkronizasyonu: isUserSeeking stateManager'a kaydedilir
                        // (seekManager callback'leri üzerinden otomatik senkronize olur)
                        stateManager.setIsUserSeeking(true)
                        visibilityCoordinator?.cancelAutoHideTimer() // Seek yaparken panel gizlenmesin
                    }

                    override fun onStopTrackingTouch(seekBar: SeekBar?) {
                        // Touch ile seek bittiğinde commit
                        val duration = listener?.getDuration() ?: 0L
                        val progress = binding.seekbarProgress.progress
                        val newPosition = if (duration > 0) {
                            (duration * progress) / 100
                        } else {
                            0L
                        }
                        // İzlenen pozisyonu güncelle
                        stateManager.setWatchedPosition(newPosition)
                        // İzlenen progress'i güncelle
                        stateManager.updateWatchedProgress(duration)
                        // Seek'i commit et
                        seekManager.commitSeekPosition(newPosition)
                    }
                },
            )
        }


        override fun onAttachedToWindow() {
            super.onAttachedToWindow()

            // View her ekrana bağlandığında yeni, aktif bir scope oluştur
            if (viewScope == null) {
                viewScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
            }

            // Coordinator'ların lifecycle yönetimi
            // SeekManager'a viewScope'u güncelle
            seekManager.setViewScope(viewScope)

            // VisibilityCoordinator'ı oluştur ve callback'leri ayarla
            if (visibilityCoordinator == null && viewScope != null) {
                visibilityCoordinator =
                    PlayerVisibilityCoordinator(
                        binding = binding,
                        viewScope = viewScope!!,
                        context = context,
                        rootView = this,
                    ).also { coordinator ->
                        // Callback'leri ayarla
                        coordinator.setSettingsPanelOpenCallback { isSettingsPanelOpenCallback?.invoke() ?: false }
                        coordinator.setOnControlsShownCallback { requestFocusOnPlayStopButton() }
                        coordinator.setIsPlayingStateCallback { listener?.isPlayingState() ?: false }
                        coordinator.setIsLiveStreamCallback { stateManager.isLiveStream() }
                        coordinator.setUpdateButtonsStateCallback { stateManager.setLiveStreamMode(stateManager.isLiveStream()) }
                        coordinator.setOnFirstPanelOpenChangedCallback { /* İlk panel açılış durumu gerekirse burada yönetilebilir */ }
                        coordinator.setOnControlsShownManuallyCallback { 
                            onControlsShownManuallyCallback?.invoke()
                        }
                    }
            }
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()

            // Coordinator'ların lifecycle yönetimi - temizlik işlemleri
            // VisibilityCoordinator'ı temizle (animasyon ve timer yönetimi)
            visibilityCoordinator?.cleanup()
            visibilityCoordinator = null

            // Coroutine scope'u iptal et
            viewScope?.cancel()
            viewScope = null

            // SeekManager'ı temizle
            seekManager.cleanup()
        }
    }
