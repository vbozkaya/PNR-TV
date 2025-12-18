package com.pnr.tv.ui.base

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import androidx.recyclerview.widget.RecyclerView
import timber.log.Timber

/**
 * Kategoriler listesi için özel RecyclerView.
 * - Focus gezinme hızını kontrol eder (throttle)
 * - Basılı tutma hızını sınırlar
 */
class CustomCategoriesRecyclerView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : RecyclerView(context, attrs, defStyleAttr) {
        // Her yön tuşu için ayrı throttle mekanizması
        private var lastUpTime = 0L
        private var lastDownTime = 0L
        private val throttleDelay = 500L // Kategoriler için throttle (500ms)

        override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
            if (event == null) return super.dispatchKeyEvent(event)

            if (event.action == KeyEvent.ACTION_DOWN) {
                val currentTime = System.currentTimeMillis()

                when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        // Throttle kontrolü - sadece basılı tutma (repeatCount > 0) için uygula
                        if (event.repeatCount > 0) {
                            val timeSinceLastKey = currentTime - lastUpTime
                            if (timeSinceLastKey < throttleDelay) {
                                Timber.tag(
                                    "GRID_UPDATE",
                                ).d("⏱️ Kategoriler DPAD_UP: Throttle aktif (${timeSinceLastKey}ms < ${throttleDelay}ms)")
                                return true // Çok hızlı, ignore et
                            }
                            lastUpTime = currentTime
                            Timber.tag("GRID_UPDATE").d("✅ Kategoriler DPAD_UP: Basılı tutma - İzin veriliyor")
                        } else {
                            lastUpTime = currentTime
                            Timber.tag("GRID_UPDATE").d("✅ Kategoriler DPAD_UP: Yeni tıklama - Throttle uygulanmıyor")
                        }
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        // Son kategori kontrolü - ÖNCE kontrol et, throttle'dan önce
                        try {
                            val focusedView = findFocus()
                            val adapter = this.adapter
                            val layoutManager = this.layoutManager
                            
                            if (focusedView != null && adapter != null && layoutManager != null) {
                                val focusedPosition = layoutManager.getPosition(focusedView)
                                val itemCount = adapter.itemCount
                                
                                // Son kategorideyse, olayı tüket (navbar'a veya başka yere gitmesin)
                                if (focusedPosition != RecyclerView.NO_POSITION && itemCount > 0 && focusedPosition == itemCount - 1) {
                                    Timber.tag("GRID_UPDATE").d("🛑 Kategoriler DPAD_DOWN: Son kategori (pozisyon: $focusedPosition/$itemCount), olay tüketiliyor")
                                    // Focus'u mevcut view'da tut
                                    if (focusedView.isFocusable && focusedView.isFocusableInTouchMode) {
                                        focusedView.requestFocus()
                                    }
                                    return true // Event'i tüket, super.dispatchKeyEvent çağrılmasın
                                }
                            }
                        } catch (e: Exception) {
                            Timber.tag("GRID_UPDATE").e(e, "❌ DPAD_DOWN kontrolü sırasında hata")
                            // Hata durumunda normal akışa devam et
                        }
                        
                        // Throttle kontrolü - sadece basılı tutma (repeatCount > 0) için uygula
                        if (event.repeatCount > 0) {
                            val timeSinceLastKey = currentTime - lastDownTime
                            if (timeSinceLastKey < throttleDelay) {
                                Timber.tag(
                                    "GRID_UPDATE",
                                ).d("⏱️ Kategoriler DPAD_DOWN: Throttle aktif (${timeSinceLastKey}ms < ${throttleDelay}ms)")
                                return true // Çok hızlı, ignore et
                            }
                            lastDownTime = currentTime
                            Timber.tag("GRID_UPDATE").d("✅ Kategoriler DPAD_DOWN: Basılı tutma - İzin veriliyor")
                        } else {
                            lastDownTime = currentTime
                            Timber.tag("GRID_UPDATE").d("✅ Kategoriler DPAD_DOWN: Yeni tıklama - Throttle uygulanmıyor")
                        }
                    }
                }
            }

            // RecyclerView'ın normal key handling'ine devam et
            return super.dispatchKeyEvent(event)
        }
    }
