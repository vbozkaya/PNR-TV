package com.pnr.tv.ui.player.coordinator

import android.content.Context
import androidx.lifecycle.LifecycleCoroutineScope
import com.pnr.tv.R
import com.pnr.tv.databinding.ActivityPlayerBinding
import com.pnr.tv.databinding.PlayerChannelListPanelBinding
import com.pnr.tv.databinding.PlayerSettingsPanelBinding
import com.pnr.tv.repository.ContentRepository
import com.pnr.tv.ui.player.PlayerViewModel
import com.pnr.tv.ui.player.handler.ChannelListListener
import com.pnr.tv.ui.player.panel.ChannelListPanel
import com.pnr.tv.ui.player.panel.PlayerSettingsPanel
import kotlinx.coroutines.launch

/**
 * PlayerActivity içindeki panel yönetimi sorumluluklarını yöneten coordinator sınıfı.
 * Activity'yi sadece bir 'host' haline getirmek ve UI panellerinin yönetimini izole etmek için oluşturulmuştur.
 */
class PlayerPanelCoordinator(
    private val binding: ActivityPlayerBinding,
    private val viewModel: PlayerViewModel,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val contentRepository: ContentRepository,
    private val context: Context,
) {
    private lateinit var settingsPanelBinding: PlayerSettingsPanelBinding
    private lateinit var channelListPanelBinding: PlayerChannelListPanelBinding

    // Ayarlar paneli için
    private var settingsPanel: PlayerSettingsPanel? = null

    // Kanal listesi paneli için
    private var channelListPanel: ChannelListPanel? = null

    // CategoryId'yi saklamak için (channelListPanel listener'ında kullanılıyor)
    private var categoryId: Int? = null

    /**
     * Panelleri başlatır (setup).
     */
    fun setupPanels() {
        // Ayarlar paneli binding'i
        settingsPanelBinding = PlayerSettingsPanelBinding.bind(binding.root.findViewById(R.id.settings_panel_container))
        settingsPanel =
            PlayerSettingsPanel(
                binding = settingsPanelBinding,
                context = context,
                viewModel = viewModel,
                onSubtitleButtonFocus = {
                    // PlayerControlView'ı görünür yap ve altyazı butonuna focus ver
                    binding.playerControlView.showControls()
                    binding.playerControlView.post {
                        val btnSubtitle = binding.playerControlView.findViewById<android.widget.ImageButton>(R.id.btn_subtitle)
                        btnSubtitle?.requestFocus()
                    }
                },
                onAudioButtonFocus = {
                    // PlayerControlView'ı görünür yap ve ses butonuna focus ver
                    binding.playerControlView.showControls()
                    binding.playerControlView.post {
                        val btnSpeak = binding.playerControlView.findViewById<android.widget.ImageButton>(R.id.btn_speak)
                        btnSpeak?.requestFocus()
                    }
                },
            )

        // Kanal listesi paneli binding'i
        channelListPanelBinding =
            PlayerChannelListPanelBinding.bind(
                binding.root.findViewById(R.id.channel_list_panel_container),
            )
        setupChannelListPanel()
    }

    /**
     * Kanal listesi panelini setup eder.
     */
    private fun setupChannelListPanel() {
        channelListPanel =
            ChannelListPanel(
                binding = channelListPanelBinding,
                lifecycleScope = lifecycleScope,
                contentRepository = contentRepository,
                listener =
                    object : ChannelListListener {
                        override fun onChannelSelected(channel: com.pnr.tv.db.entity.LiveStreamEntity) {
                            // Kanal seçildiğinde playlist'te o kanala geç
                            lifecycleScope.launch {
                                try {
                                    val currentCategoryId = categoryId
                                    if (currentCategoryId != null) {
                                        val channels = contentRepository.getLiveStreamsByCategoryIdSync(currentCategoryId)
                                        val targetIndex = channels.indexOfFirst { it.streamId == channel.streamId }
                                        if (targetIndex != -1) {
                                            // Playlist'te o index'e geç
                                            val player = viewModel.getPlayer()
                                            if (player != null && player.mediaItemCount > targetIndex) {
                                                player.seekTo(targetIndex, 0L)
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    // Kanal seçimi hatası - sessizce devam et
                                }
                            }
                            // Panel'i kapat
                            channelListPanel?.hide()
                        }
                    },
            )
    }

    /**
     * Kanal listesi panelini gösterir.
     * Ayarlar paneli açıksa önce kapatır.
     */
    fun showChannelListPanel(
        categoryId: Int?,
        channelId: Int?,
    ) {
        // CategoryId'yi sakla (listener'da kullanılacak)
        this.categoryId = categoryId

        // Ayarlar paneli açıksa önce kapat
        if (settingsPanel?.isVisible() == true) {
            if (settingsPanel?.isAudioPanelOpen() == true) {
                settingsPanel?.hideAudioPanel()
            } else {
                settingsPanel?.hideSubtitlePanel()
            }
        }

        channelListPanel?.show(categoryId, channelId)
    }

    /**
     * Kanal listesi panelini gizler.
     */
    fun hideChannelListPanel() {
        channelListPanel?.hide()
    }

    /**
     * Ayarlar panelini gösterir (alt yazı paneli).
     */
    fun showSettingsPanel() {
        settingsPanel?.showSubtitlePanel()
    }

    /**
     * Ayarlar panelini gösterir (ses paneli).
     */
    fun showAudioPanel() {
        settingsPanel?.showAudioPanel()
    }

    /**
     * Ayarlar panelinin görünür olup olmadığını kontrol eder.
     */
    fun isSettingsPanelVisible(): Boolean {
        return settingsPanel?.isVisible() == true
    }

    /**
     * Kanal listesi panelinin görünür olup olmadığını kontrol eder.
     */
    fun isChannelListPanelVisible(): Boolean {
        return channelListPanel?.isVisible() == true
    }

    /**
     * Kanal listesi panelinin animasyon halinde olup olmadığını kontrol eder.
     */
    fun isChannelListPanelAnimating(): Boolean {
        return channelListPanel?.isAnimating() == true
    }

    /**
     * Kanal listesi panelindeki mevcut kanal ID'sini günceller.
     */
    fun updateChannelListPanelCurrentChannelId(channelId: Int?) {
        channelListPanel?.updateCurrentChannelId(channelId)
    }

    /**
     * Panel referanslarını döndürür (PlayerKeyHandler için).
     */
    fun getSettingsPanel(): PlayerSettingsPanel? = settingsPanel

    fun getChannelListPanel(): ChannelListPanel? = channelListPanel

    fun getSettingsPanelBinding(): PlayerSettingsPanelBinding = settingsPanelBinding
}
