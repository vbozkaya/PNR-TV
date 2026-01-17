package com.pnr.tv.ui.player.panel

import android.view.KeyEvent
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.pnr.tv.databinding.PlayerChannelListPanelBinding

/**
 * Kanal listesi paneli focus ve tuş olayları yönetimi için handler sınıfı.
 */
class ChannelPanelFocusHandler(
    private val binding: PlayerChannelListPanelBinding,
    private val visibilityHandler: ChannelPanelVisibilityHandler,
    private val onHideRequested: () -> Unit,
) {
    /**
     * RecyclerView'ı setup eder ve focus scroll listener'ını ekler.
     */
    fun setupRecyclerViewFocusScroll(
        recyclerView: RecyclerView,
        layoutManager: LinearLayoutManager,
    ) {
        // Animasyonları devre dışı bırak - sadece direkt scroll
        recyclerView.itemAnimator = null

        // RecyclerView'a focus değişikliğini dinleyecek bir listener ekle
        recyclerView.addOnChildAttachStateChangeListener(
            object : RecyclerView.OnChildAttachStateChangeListener {
                override fun onChildViewAttachedToWindow(view: View) {
                    // TrackSelectionAdapter'da zaten ekleniyor
                }

                override fun onChildViewDetachedFromWindow(view: View) {
                    // Cleanup
                }
            },
        )

        // RecyclerView'ın kendi scroll listener'ı - direkt scroll, animasyon yok
        recyclerView.addOnScrollListener(
            object : RecyclerView.OnScrollListener() {
                override fun onScrolled(
                    recyclerView: RecyclerView,
                    dx: Int,
                    dy: Int,
                ) {
                    super.onScrolled(recyclerView, dx, dy)
                    // Scroll olduğunda focus'lu item'ın görünürlüğünü kontrol et
                    val focusedView = recyclerView.focusedChild
                    if (focusedView != null) {
                        val position = recyclerView.getChildAdapterPosition(focusedView)
                        if (position != RecyclerView.NO_POSITION) {
                            val firstVisible = layoutManager.findFirstVisibleItemPosition()
                            val lastVisible = layoutManager.findLastVisibleItemPosition()

                            if (position < firstVisible || position > lastVisible) {
                                // Focus görünür değilse, direkt scroll yap (animasyon yok)
                                recyclerView.post {
                                    layoutManager.scrollToPositionWithOffset(position, 0)
                                }
                            }
                        }
                    }
                }
            },
        )
    }

    /**
     * Panel açıkken tuş olaylarını handle eder.
     * @param keyCode Tuş kodu
     * @param event Tuş olayı
     * @return Olay işlendi ise true, aksi halde false
     */
    fun handleKeyEvent(
        keyCode: Int,
        event: KeyEvent?,
    ): Boolean {
        if (!visibilityHandler.isVisible()) {
            return false
        }

        // Her tuş basıldığında timer'ı sıfırla
        visibilityHandler.resetAutoHideTimer()

        // BACK tuşu - paneli kapat
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            onHideRequested()
            return true
        }

        // DPAD_RIGHT - paneli kapat
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            onHideRequested()
            return true
        }

        // OK tuşu (DPAD_CENTER) - ilk öğeye focus ver
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER && event?.action == KeyEvent.ACTION_DOWN) {
            val focusedView = binding.root.rootView?.findFocus()
            val isFocusInPanel = isFocusInPanel(focusedView)

            // Focus panel içinde değilse, ilk öğeye focus ver
            if (!isFocusInPanel) {
                binding.recyclerChannelList.post {
                    val firstChannel = binding.recyclerChannelList.findViewHolderForAdapterPosition(0)
                    firstChannel?.itemView?.requestFocus()
                }
                return true
            }
            // Focus zaten panel içindeyse, false döndür (normal OK tuşu davranışı)
            return false
        }

        // Focus yönetimi - panel açıkken focus'un panel içinde kalmasını sağla
        val focusedView = binding.root.rootView?.findFocus()
        val isFocusInPanel = isFocusInPanel(focusedView)

        // Focus panel dışındaysa, ilk kanal öğesine geri dön
        if (!isFocusInPanel && focusedView != null && focusedView != binding.playerChannelListPanel) {
            binding.playerChannelListPanel.post {
                val firstChannel = binding.recyclerChannelList.findViewHolderForAdapterPosition(0)
                firstChannel?.itemView?.requestFocus()
            }
            return true
        }

        // DPAD_DOWN/UP tuşları - panel içindeki öğeler arasında gezinme
        // Bu durumda false döndür, parent'ın handle etmesine izin ver
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            return false
        }

        // DPAD_LEFT - panel içinde hiçbir şekilde çalışmasın (focus kaybını önlemek için)
        // Animasyon halindeyse de engelle
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            if (visibilityHandler.isAnimating()) {
                // Panel animasyon halindeyse tuş basımını yoksay
                return true
            }
            // Panel içinde sol yön tuşunu engelle
            return true
        }

        // Panel içindeki diğer tuş olayları için false döndür
        return false
    }

    /**
     * Focus'un panel içinde olup olmadığını kontrol eder.
     */
    private fun isFocusInPanel(focusedView: View?): Boolean {
        return focusedView?.let { view ->
            val panel = binding.playerChannelListPanel
            var currentParent: android.view.ViewParent? = view.parent
            while (currentParent != null) {
                if (currentParent == panel) {
                    return@let true
                }
                val current = currentParent
                val nextParent = current.parent
                currentParent = nextParent
            }
            false
        } ?: false
    }
}
