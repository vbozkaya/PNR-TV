package com.pnr.tv.ui.player.panel

import android.view.View
import android.view.animation.AnimationUtils
import com.pnr.tv.R
import com.pnr.tv.databinding.PlayerChannelListPanelBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Kanal listesi paneli görünürlük ve animasyon yönetimi için handler sınıfı.
 */
class ChannelPanelVisibilityHandler(
    private val binding: PlayerChannelListPanelBinding,
    private val lifecycleScope: CoroutineScope,
) {
    companion object {
        private const val PANEL_TOGGLE_DEBOUNCE_MS = 300L
        private const val AUTO_HIDE_DELAY_MS = 5000L
    }

    private var channelListAutoHideJob: Job? = null
    private var isChannelListPanelAnimating = false
    private var lastPanelToggleTime = 0L

    /**
     * Panel'in animasyon halinde olup olmadığını döndürür.
     */
    fun isAnimating(): Boolean {
        return isChannelListPanelAnimating
    }

    /**
     * Panel'in görünür olup olmadığını döndürür.
     */
    fun isVisible(): Boolean {
        return binding.playerChannelListPanel.visibility == View.VISIBLE
    }

    /**
     * Panel'i gösterir (animasyon ile).
     * @param categoryChanged Kategori değişikliği flag'i (callback'te kullanılmak üzere)
     * @param onShowComplete Panel gösterildikten sonra çağrılacak callback
     */
    fun show(
        @Suppress("UNUSED_PARAMETER") categoryChanged: Boolean,
        onShowComplete: (() -> Unit)? = null,
    ) {
        // Debounce kontrolü: Son işlemden bu yana 300ms geçmediyse işlem yapma
        val now = System.currentTimeMillis()
        if (now - lastPanelToggleTime < PANEL_TOGGLE_DEBOUNCE_MS) {
            return
        }

        // Animasyon kontrolü: Panel animasyon halindeyse işlem yapma
        if (isChannelListPanelAnimating) {
            return
        }

        // Visibility kontrolü: Panel zaten görünürse işlem yapma
        if (binding.playerChannelListPanel.visibility == View.VISIBLE) {
            return
        }

        // İşlem başladı - flag'leri ayarla
        isChannelListPanelAnimating = true
        lastPanelToggleTime = now

        // Önce paneli görünür yap (RecyclerView'ın layout'u yapılabilmesi için)
        binding.playerChannelListPanel.visibility = View.VISIBLE

        // Animasyon başlat
        val slideIn = AnimationUtils.loadAnimation(binding.root.context, R.anim.slide_in_left)
        slideIn.setAnimationListener(
            object : android.view.animation.Animation.AnimationListener {
                override fun onAnimationStart(animation: android.view.animation.Animation?) {
                    // Animasyon başladı
                }

                override fun onAnimationRepeat(animation: android.view.animation.Animation?) {
                    // Tekrar etme
                }

                override fun onAnimationEnd(animation: android.view.animation.Animation?) {
                    // Animasyon bitti - flag'i false yap
                    isChannelListPanelAnimating = false

                    // Callback'i çağır
                    onShowComplete?.invoke()

                    // Otomatik kapanma timer'ını başlat (animasyon bittikten sonra)
                    resetAutoHideTimer()
                }
            },
        )

        binding.playerChannelListPanel.startAnimation(slideIn)
    }

    /**
     * Panel'i gizler (animasyon ile).
     * @param onHideComplete Panel gizlendikten sonra çağrılacak callback
     */
    fun hide(onHideComplete: (() -> Unit)? = null) {
        // Visibility kontrolü: Panel görünür değilse işlem yapma
        if (binding.playerChannelListPanel.visibility != View.VISIBLE) {
            return
        }

        // Debounce kontrolü: Son işlemden bu yana 300ms geçmediyse işlem yapma
        val now = System.currentTimeMillis()
        if (now - lastPanelToggleTime < PANEL_TOGGLE_DEBOUNCE_MS) {
            return
        }

        // Animasyon kontrolü: Panel animasyon halindeyse işlem yapma
        if (isChannelListPanelAnimating) {
            return
        }

        // İşlem başladı - flag'leri ayarla
        isChannelListPanelAnimating = true
        lastPanelToggleTime = now

        // Timer'ı iptal et
        channelListAutoHideJob?.cancel()
        channelListAutoHideJob = null

        // Animasyon başlat ve listener ekle
        val slideOut = AnimationUtils.loadAnimation(binding.root.context, R.anim.slide_out_left)
        slideOut.setAnimationListener(
            object : android.view.animation.Animation.AnimationListener {
                override fun onAnimationStart(animation: android.view.animation.Animation?) {
                    // Animasyon başladı
                }

                override fun onAnimationRepeat(animation: android.view.animation.Animation?) {
                    // Tekrar etme
                }

                override fun onAnimationEnd(animation: android.view.animation.Animation?) {
                    // Animasyon bitti - flag'i false yap ve visibility'yi GONE yap
                    isChannelListPanelAnimating = false
                    binding.playerChannelListPanel.visibility = View.GONE

                    // Callback'i çağır
                    onHideComplete?.invoke()
                }
            },
        )

        binding.playerChannelListPanel.startAnimation(slideOut)
    }

    /**
     * Otomatik kapanma timer'ını sıfırlar.
     */
    fun resetAutoHideTimer() {
        // Önceki timer'ı iptal et
        channelListAutoHideJob?.cancel()

        // Yeni timer başlat (5 saniye)
        channelListAutoHideJob =
            lifecycleScope.launch {
                delay(AUTO_HIDE_DELAY_MS)
                if (binding.playerChannelListPanel.visibility == View.VISIBLE && isActive) {
                    hide()
                }
            }
    }

    /**
     * Timer'ı iptal eder (cleanup için).
     */
    fun cancelAutoHideTimer() {
        channelListAutoHideJob?.cancel()
        channelListAutoHideJob = null
    }
}
