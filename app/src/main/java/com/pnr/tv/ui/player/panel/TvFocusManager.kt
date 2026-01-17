package com.pnr.tv.ui.player.panel

import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Android TV odaklanma ve tuş dinleme mantığını yöneten helper sınıf.
 * RecyclerView için focus scroll, focus trap, key listener ve panel focus protection
 * işlemlerini yapar.
 */
class TvFocusManager {

    /**
     * RecyclerView'a back tuşu desteği ekler.
     *
     * @param recyclerView Back tuşu dinlenecek RecyclerView
     * @param onBackPressed Back tuşuna basıldığında çağrılacak callback
     */
    fun setupRecyclerViewKeyListener(
        recyclerView: RecyclerView,
        onBackPressed: () -> Unit,
    ) {
        recyclerView.setOnKeyListener { _, keyCode, event ->
            if (keyCode == android.view.KeyEvent.KEYCODE_BACK && event?.action == android.view.KeyEvent.ACTION_DOWN) {
                onBackPressed()
                true
            } else {
                false
            }
        }
    }

    /**
     * RecyclerView'da focus değiştiğinde otomatik scroll yapar - animasyon YOK.
     *
     * @param recyclerView Scroll yapılacak RecyclerView
     * @param layoutManager RecyclerView'ın LayoutManager'ı
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
     * RecyclerView için focus trap ekler - en üst/alt sınırlarda focus'un panel dışına çıkmasını engeller.
     * KEYCODE_DPAD_UP ve KEYCODE_DPAD_DOWN tuşlarını ilk/son öğede engeller.
     * Ayrıca back tuşu desteği de ekler.
     *
     * @param recyclerView Focus trap uygulanacak RecyclerView
     * @param layoutManager RecyclerView'ın LayoutManager'ı
     * @param onBackPressed Back tuşuna basıldığında çağrılacak callback (opsiyonel)
     */
    fun setupRecyclerViewFocusTrap(
        recyclerView: RecyclerView,
        layoutManager: LinearLayoutManager,
        onBackPressed: (() -> Unit)? = null,
    ) {
        recyclerView.setOnKeyListener { _, keyCode, event ->
            if (event?.action != android.view.KeyEvent.ACTION_DOWN) {
                return@setOnKeyListener false
            }

            val focusedView = recyclerView.focusedChild
            if (focusedView == null) {
                return@setOnKeyListener false
            }

            val focusedPosition = recyclerView.getChildAdapterPosition(focusedView)
            if (focusedPosition == RecyclerView.NO_POSITION) {
                return@setOnKeyListener false
            }

            val itemCount = recyclerView.adapter?.itemCount ?: 0
            val firstVisiblePosition = layoutManager.findFirstVisibleItemPosition()
            val lastVisiblePosition = layoutManager.findLastVisibleItemPosition()

            // En üstte yukarı basıldığında - tuşun varsayılan işlemini ENGELLE
            if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP &&
                focusedPosition == 0
            ) {
                // Sadece tuşu yut - donanımsal focus trap adapter'da yapılacak
                return@setOnKeyListener true
            }

            // En altta aşağı basıldığında - tuşun varsayılan işlemini ENGELLE
            if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN &&
                focusedPosition == itemCount - 1
            ) {
                // Sadece tuşu yut - donanımsal focus trap adapter'da yapılacak
                return@setOnKeyListener true
            }

            // Sağ-sol tuşlarını engelle
            if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT ||
                keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT
            ) {
                return@setOnKeyListener true
            }

            // Back tuşu desteği
            if (keyCode == android.view.KeyEvent.KEYCODE_BACK && event?.action == android.view.KeyEvent.ACTION_DOWN) {
                onBackPressed?.invoke()
                return@setOnKeyListener true
            }

            false
        }
    }

    /**
     * Panel ana layout'una nextFocus ID'lerini set eder - odağın panel dışına sıçramasını önler.
     * Panel kapandığında odağın otomatik olarak bir yerlere sıçramasını engeller.
     *
     * @param panelView Panel ana layout view'ı
     */
    fun setupPanelFocusProtection(panelView: View) {
        panelView.apply {
            // Panel ana layout'unun nextFocus ID'lerini kendi ID'si olarak set et
            // Böylece panel kapandığında odağın sıçraması engellenir
            nextFocusUpId = id
            nextFocusDownId = id
            nextFocusLeftId = id
            nextFocusRightId = id
        }
    }
}
