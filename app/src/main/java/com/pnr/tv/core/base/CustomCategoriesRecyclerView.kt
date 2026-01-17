package com.pnr.tv.core.base

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import androidx.recyclerview.widget.RecyclerView

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
        private val throttleDelay = 200L // Kategoriler için throttle (200ms)

        // Kategori -> İçerik geçişi için callback (DPAD_RIGHT için)
        var onNavigateToContentCallback: (() -> Unit)? = null

        // Kategori seçimi için callback (DPAD_CENTER/OK tuşu için)
        var onCategoryClickCallback: (() -> Unit)? = null

        override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
            if (event == null) return super.dispatchKeyEvent(event)

            if (event.action == KeyEvent.ACTION_DOWN) {
                val currentTime = System.currentTimeMillis()
                val focusedView = findFocus()
                val layoutManager = this.layoutManager
                var focusedPosition = RecyclerView.NO_POSITION
                if (focusedView != null && layoutManager != null) {
                    try {
                        var parentView: android.view.View? = focusedView
                        while (parentView != null && parentView.parent != this) {
                            parentView = parentView.parent as? android.view.View
                        }
                        if (parentView != null) {
                            focusedPosition = layoutManager.getPosition(parentView)
                        }
                    } catch (e: Exception) {
                        // Ignore
                    }
                }

                when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        // Kategori -> İçerik geçişi: SAĞ tuşuna basıldığında içerik grid'ine git
                        onNavigateToContentCallback?.invoke()
                        return true // Event'i tüket
                    }
                    KeyEvent.KEYCODE_DPAD_CENTER -> {
                        // OK tuşu: Sadece kategori seçimi yap, içerik grid'ine gitme
                        onCategoryClickCallback?.invoke()
                        return true // Event'i tüket (click listener'ın çalışmasını engelle)
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        // Throttle kontrolü - sadece basılı tutma (repeatCount > 0) için uygula
                        if (event.repeatCount > 0) {
                            val timeSinceLastKey = currentTime - lastUpTime
                            if (timeSinceLastKey < throttleDelay) {
                                return true // Çok hızlı, ignore et
                            }
                            lastUpTime = currentTime
                        } else {
                            lastUpTime = currentTime
                        }
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        // Son kategori kontrolü - ÖNCE kontrol et, throttle'dan önce
                        try {
                            val focusedView = findFocus()
                            val adapter = this.adapter
                            val layoutManager = this.layoutManager

                            if (focusedView != null && adapter != null && layoutManager != null) {
                                // Güvenli pozisyon alma - view'ın parent'ını kontrol et
                                var focusedPosition = RecyclerView.NO_POSITION
                                var parentView: android.view.View? = focusedView

                                // RecyclerView'ın direkt child'ını bul
                                while (parentView != null && parentView.parent != this) {
                                    parentView = parentView.parent as? android.view.View
                                }

                                if (parentView != null) {
                                    try {
                                        focusedPosition = layoutManager.getPosition(parentView)
                                    } catch (e: ClassCastException) {
                                        // Layout params uyumsuzluğu - güvenli fallback
                                    }
                                }

                                val itemCount = adapter.itemCount

                                // Son kategorideyse, olayı tüket (navbar'a veya başka yere gitmesin)
                                if (focusedPosition != RecyclerView.NO_POSITION && itemCount > 0 && focusedPosition == itemCount - 1) {
                                    // Focus'u mevcut view'da tut
                                    if (focusedView.isFocusable && focusedView.isFocusableInTouchMode) {
                                        focusedView.requestFocus()
                                    }
                                    return true // Event'i tüket, super.dispatchKeyEvent çağrılmasın
                                }
                            }
                        } catch (e: Exception) {
                            // Hata durumunda normal akışa devam et
                        }

                        // Throttle kontrolü - sadece basılı tutma (repeatCount > 0) için uygula
                        if (event.repeatCount > 0) {
                            val timeSinceLastKey = currentTime - lastDownTime
                            if (timeSinceLastKey < throttleDelay) {
                                return true // Çok hızlı, ignore et
                            }
                            lastDownTime = currentTime
                        } else {
                            lastDownTime = currentTime
                        }
                    }
                }
            }

            // RecyclerView'ın normal key handling'ine devam et
            return super.dispatchKeyEvent(event)
        }
    }
