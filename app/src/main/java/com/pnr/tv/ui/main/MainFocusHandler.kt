package com.pnr.tv.ui.main

import android.view.View
import android.view.ViewTreeObserver
import com.pnr.tv.R
import com.pnr.tv.core.constants.UIConstants

/**
 * MainFragment için odak yönetimi işlemlerini yöneten sınıf.
 * Android TV odak yönetimi karmaşıklığını MainFragment'tan ayırarak
 * kodun daha temiz ve bakımı kolay olmasını sağlar.
 *
 * @param fragmentView Fragment'ın root view referansı
 * @param mainActivity MainActivity referansı
 */
class MainFocusHandler(
    private val fragmentView: View,
    private val mainActivity: MainActivity,
) {
    private val pendingRunnables = mutableListOf<Runnable>()

    /**
     * Container'ları focusable yapmayı engeller.
     * Activity geçişlerinden önce ve sonra çağrılır.
     */
    fun ensureContainersNotFocusable() {
        val containers =
            listOf(
                fragmentView.findViewById<View>(R.id.container_live_streams),
                fragmentView.findViewById<View>(R.id.container_movies),
                fragmentView.findViewById<View>(R.id.container_series),
            )
        containers.forEach { container ->
            container?.isFocusable = false
            container?.isFocusableInTouchMode = false
        }
    }

    /**
     * Android'in otomatik odak yönetimini engeller ve update button'a odak verir.
     * ViewTreeObserver kullanarak view'ların tamamen hazır olduğundan emin olur.
     * Activity geçişlerinden sonra container'ları focusable yapmayı geciktirir
     * böylece focus kaybı önlenir.
     */
    fun setupInitialFocus() {
        val containers =
            listOf(
                fragmentView.findViewById<View>(R.id.container_live_streams),
                fragmentView.findViewById<View>(R.id.container_movies),
                fragmentView.findViewById<View>(R.id.container_series),
            )

        // Önce container'ları kesinlikle focusable yapma
        ensureContainersNotFocusable()

        // ViewTreeObserver ile view'ların tamamen hazır olduğundan emin ol
        val observer = fragmentView.viewTreeObserver
        observer.addOnGlobalLayoutListener(
            object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    // View'lar hazır, artık odak ayarlayabiliriz
                    fragmentView.viewTreeObserver.removeOnGlobalLayoutListener(this)

                    // Önce update button'a odak ver (birden fazla kez deneyelim)
                    mainActivity.requestFocusOnUpdateButton()

                    // Activity geçişlerinden sonra focus kaybını önlemek için
                    // container'ları focusable yapmayı daha uzun bir süre geciktir
                    fragmentView.postDelayed({
                        // Container'ları hala focusable yapma
                        ensureContainersNotFocusable()
                        mainActivity.requestFocusOnUpdateButton()
                    }, 50)

                    fragmentView.postDelayed({
                        // Container'ları hala focusable yapma
                        ensureContainersNotFocusable()
                        mainActivity.requestFocusOnUpdateButton()
                    }, 150)

                    fragmentView.postDelayed({
                        // Container'ları hala focusable yapma
                        ensureContainersNotFocusable()
                        mainActivity.requestFocusOnUpdateButton()
                    }, (UIConstants.DelayDurations.FOCUS_CHANGE_DELAY_MS * 3).toLong()) // 300ms

                    // Container'ları odaklanabilir yapmayı daha uzun bir süre geciktir
                    // Bu sayede activity geçişlerinden sonra focus kaybı önlenir
                    val makeContainersFocusableRunnable =
                        Runnable {
                            // Update button'a hala focus verilmiş mi kontrol et
                            val isTopMenuFocused = mainActivity.isTopMenuButtonFocused() == true

                            // Sadece üst menü butonlarından birine focus verilmişse container'ları focusable yap
                            if (isTopMenuFocused) {
                                containers.forEach { container ->
                                    container?.isFocusable = true
                                    container?.isFocusableInTouchMode = true
                                }

                                // Son bir kez daha update button'a odak ver
                                mainActivity.requestFocusOnUpdateButton()
                            } else {
                                // Eğer focus başka bir yerdeyse, update button'a odak ver ve container'ları focusable yap
                                mainActivity.requestFocusOnUpdateButton()
                                fragmentView.postDelayed({
                                    // Tekrar kontrol et
                                    val newIsTopMenuFocused = mainActivity.isTopMenuButtonFocused() == true
                                    if (newIsTopMenuFocused) {
                                        containers.forEach { container ->
                                            container?.isFocusable = true
                                            container?.isFocusableInTouchMode = true
                                        }
                                        mainActivity.requestFocusOnUpdateButton()
                                    }
                                }, 100)
                            }
                        }
                    pendingRunnables.add(makeContainersFocusableRunnable)
                    fragmentView.postDelayed(
                        makeContainersFocusableRunnable,
                        UIConstants.DelayDurations.ACTIVITY_TRANSITION_DELAY_MS,
                    ) // Activity geçişlerinden sonra daha uzun gecikme
                }
            },
        )
    }

    /**
     * Bekleyen focus setup işlemlerini iptal eder.
     * onPause lifecycle metodunda çağrılmalıdır.
     */
    fun clearPendingRunnables() {
        pendingRunnables.clear()
    }
}
