package com.pnr.tv.ui.player.handler

import android.view.KeyEvent
import android.view.Window
import com.pnr.tv.databinding.PlayerSettingsPanelBinding
import com.pnr.tv.ui.player.PlayerViewModel
import com.pnr.tv.ui.player.component.PlayerControlView
import com.pnr.tv.ui.player.panel.ChannelListPanel
import com.pnr.tv.ui.player.panel.PlayerSettingsPanel
import com.pnr.tv.ui.player.state.PlayerAction

/**
 * PlayerActivity içindeki tuş yönetimi mantığını yöneten sınıf.
 * onKeyDown metodunun içeriğini bu sınıfa taşıyarak PlayerActivity'yi sadeleştirmek için oluşturulmuştur.
 */
class PlayerKeyHandler(
    private val settingsPanel: PlayerSettingsPanel?,
    private val channelListPanel: ChannelListPanel?,
    private val playerControlView: PlayerControlView,
    private val viewModel: PlayerViewModel,
    private val getChannelId: () -> Int?,
    private val getCategoryId: () -> Int?,
    private val window: Window?,
    private val settingsPanelBinding: PlayerSettingsPanelBinding,
    private val finishWithResult: () -> Unit,
    private val showChannelListPanel: () -> Unit,
) {
    /**
     * Tuş olayını handle eder.
     * @param keyCode Tuş kodu
     * @param event Tuş olayı
     * @return Olay handle edildiyse true, aksi halde null (super.onKeyDown çağrılacak)
     */
    fun handleKeyDown(
        keyCode: Int,
        event: KeyEvent?,
    ): Boolean? {
        val isSettingsPanelOpen = settingsPanel?.isVisible() == true
        val isChannelListPanelOpen = channelListPanel?.isVisible() == true
        val channelId = getChannelId()
        val categoryId = getCategoryId()

        // Kanal listesi paneli açıkken tuş yönetimi
        if (isChannelListPanelOpen) {
            val handled = channelListPanel?.handleKeyEvent(keyCode, event) ?: false
            if (handled) {
                return true
            }
            // Panel tarafından handle edilmeyen tuşlar (DPAD_DOWN/UP) için super'e ilet
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                return null // super.onKeyDown çağrılacak
            }
            // Diğer tuşlar için super'e ilet
            return null // super.onKeyDown çağrılacak
        }

        // DPAD_LEFT - Kanal listesi panelini aç (sadece canlı yayınlarda ve panel kapalıyken)
        if (channelId != null && categoryId != null && !isChannelListPanelOpen && !isSettingsPanelOpen) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && event?.action == KeyEvent.ACTION_DOWN) {
                // Panel animasyon halindeyse tuş basımını yoksay
                if (channelListPanel?.isAnimating() == true) {
                    return true
                }
                // Panel kapalıyken aç
                showChannelListPanel()
                return true // Event'i tüket, başka yerde işlenmesin
            }
        }

        // Page Up/Down ve Channel Up/Down - Canlı kanallar arasında geçiş (sadece canlı yayınlarda)
        // Gerçek TV kumandalarında genellikle Channel Up/Down tuşları kullanılır
        if (channelId != null && categoryId != null) {
            when (keyCode) {
                KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_CHANNEL_UP -> {
                    // Panel açıksa önce kapat
                    if (isSettingsPanelOpen) {
                        if (settingsPanel?.isAudioPanelOpen() == true) {
                            settingsPanel?.hideAudioPanel()
                        } else {
                            settingsPanel?.hideSubtitlePanel()
                        }
                    }
                    // Önceki kanala geç (playlist kullanarak)
                    viewModel.handleAction(PlayerAction.SeekToPreviousChannel)
                    return true // Olayı tüket
                }
                KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                    // Panel açıksa önce kapat
                    if (isSettingsPanelOpen) {
                        if (settingsPanel?.isAudioPanelOpen() == true) {
                            settingsPanel?.hideAudioPanel()
                        } else {
                            settingsPanel?.hideSubtitlePanel()
                        }
                    }
                    // Sonraki kanala geç (playlist kullanarak)
                    viewModel.handleAction(PlayerAction.SeekToNextChannel)
                    return true // Olayı tüket
                }
            }
        }

        // Panel açıksa (ses veya alt yazı), focus'un panel içinde kalmasını sağla
        if (isSettingsPanelOpen) {
            // BACK tuşu - paneli kapat ve ilgili butona focus dön
            if (keyCode == KeyEvent.KEYCODE_BACK && event?.action == KeyEvent.ACTION_DOWN) {
                if (settingsPanel?.isAudioPanelOpen() == true) {
                    settingsPanel.hideAudioPanel()
                    // onAudioButtonFocus callback'i zaten btnSpeak'a focus verecek
                } else {
                    settingsPanel?.hideSubtitlePanel()
                    // onSubtitleButtonFocus callback'i zaten btnSubtitle'a focus verecek
                }
                return true
            }

            // Panel açıkken focus'un panel dışına çıkmasını ENGELLE
            val focusedView = window?.currentFocus
            val isFocusInPanel =
                focusedView?.let { view ->
                    val panel = settingsPanelBinding.playerSettingsPanel
                    // View panel'in içinde mi kontrol et
                    var currentParent: android.view.ViewParent? = view.parent
                    while (currentParent != null) {
                        if (currentParent == panel) {
                            return@let true
                        }
                        currentParent = (currentParent as? android.view.View)?.parent
                    }
                    false
                } ?: false

            // Focus panel dışındaysa, ilk alt yazı öğesine veya kaydet butonuna geri dön
            if (!isFocusInPanel && focusedView != null && focusedView != settingsPanelBinding.playerSettingsPanel) {
                settingsPanelBinding.playerSettingsPanel.post {
                    val firstSubtitleTrack = settingsPanelBinding.recyclerSubtitleTracks.findViewHolderForAdapterPosition(0)
                    firstSubtitleTrack?.itemView?.requestFocus() ?: settingsPanelBinding.btnSaveSettings.requestFocus()
                }
                return true
            }


            // DPAD_LEFT/RIGHT - panel içinde hiçbir şekilde çalışmasın (focus kaybını önlemek için)
            // Horizontal navigation'ı tamamen öldür
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                // Panel içinde sağ/sol yön tuşlarını tamamen engelle - focus kaybını önle
                return true
            }

            // DPAD_UP/DOWN - panel içinde normal navigasyon (RecyclerView kendi focus trap'ini yönetecek)
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                // RecyclerView'ın kendi focus trap'i (setupRecyclerViewFocusTrap) sınır kontrolü yapacak
                // Burada sadece normal navigasyona izin ver
                return null // super.onKeyDown çağrılacak
            }

            // Panel içindeki diğer tuş olaylarını handle et
            return null // super.onKeyDown çağrılacak
        }

        // Geri tuşu - Panel kapalıyken aktiviteyi kapat
        if (keyCode == KeyEvent.KEYCODE_BACK && !playerControlView.isVisible()) {
            finishWithResult()
            return true
        }

        // Panel kapalıyken D-Pad tuşlarına basıldığında paneli aç
        // Panel açıldıktan sonra tüm yönetim PlayerControlView'a bırakılır
        if (!playerControlView.isVisible()) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER,
                -> {
                    playerControlView.showControls()
                    // Olayı tüketmiyoruz, PlayerControlView ilk açılış odağını ayarlayabilsin
                    return null // super.onKeyDown çağrılacak
                }
            }
        }

        // Panel açıksa veya diğer tuşlar için - PlayerControlView'a bırak
        return null // super.onKeyDown çağrılacak
    }
}
