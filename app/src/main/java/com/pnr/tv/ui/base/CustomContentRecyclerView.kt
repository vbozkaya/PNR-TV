package com.pnr.tv.ui.base

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import timber.log.Timber

/**
 * İçerik grid'i için özel RecyclerView.
 * - Focus gezinme hızını kontrol eder (throttle)
 * - Son satırda aşağı tuşuna basıldığında focus'u tüketir
 */
class CustomContentRecyclerView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : RecyclerView(context, attrs, defStyleAttr) {
        // Kategorilere geçiş için callback (sol yön tuşu için)
        var onNavigateToCategoriesCallback: (() -> Unit)? = null

        init {
            // Child view'ların focus değişikliklerini dinle
            addOnChildAttachStateChangeListener(
                object : OnChildAttachStateChangeListener {
                    override fun onChildViewAttachedToWindow(view: View) {
                        // Her child view'a focus change listener ekle
                        view.setOnFocusChangeListener { focusedView, hasFocus ->
                            if (hasFocus) {
                                val layoutManager = layoutManager as? CustomGridLayoutManager
                                if (layoutManager != null) {
                                    val focusedPosition = layoutManager.getPosition(focusedView)
                                    val adapter = adapter
                                    val itemCount = adapter?.itemCount ?: 0
                                    val spanCount = layoutManager.spanCount

                                    if (focusedPosition != RecyclerView.NO_POSITION && itemCount > 0 && spanCount > 0) {
                                        val lastRowNumber = (itemCount - 1) / spanCount
                                        val currentRowNumber = focusedPosition / spanCount
                                        val isLastRow = currentRowNumber == lastRowNumber

                                        Timber.tag(
                                            "ContentGrid",
                                        ).d(
                                            "👁️ Focus değişti: pos=$focusedPosition, isLastRow=$isLastRow, lastRow=$lastRowNumber, lastFocusedPos=$lastFocusedPosition",
                                        )

                                        // Eğer son satırdan dışarı çıktıysak, SADECE AŞAĞI yönünde geri al
                                        // Yukarı, sağ, sol yönlerinde serbest bırak
                                        if (lastFocusedPosition != RecyclerView.NO_POSITION) {
                                            val lastRowNumberOld = (itemCount - 1) / spanCount
                                            val currentRowNumberOld = lastFocusedPosition / spanCount
                                            val wasLastRow = currentRowNumberOld == lastRowNumberOld

                                            // Sadece aşağı yönünde kontrol yap (pozisyon artıyorsa aşağı gidiyor demektir)
                                            // Ama aslında satır numarasına bakmalıyız - satır numarası artıyorsa aşağı gidiyor
                                            val movedDown = currentRowNumber > currentRowNumberOld

                                            if (wasLastRow && !isLastRow && movedDown) {
                                                Timber.tag(
                                                    "ContentGrid",
                                                ).d("🛑 Focus son satırdan AŞAĞI yönünde dışarı çıktı! Geri alınıyor...")
                                                // Son satırdaki bir view'a geri dön
                                                val lastRowStartIndex = lastRowNumberOld * spanCount
                                                val lastRowEndIndex = (itemCount - 1).coerceAtLeast(lastRowStartIndex)

                                                post {
                                                    val targetPosition = lastRowEndIndex.coerceAtMost(lastRowStartIndex + spanCount - 1)
                                                    val targetViewHolder = findViewHolderForAdapterPosition(targetPosition)
                                                    targetViewHolder?.itemView?.requestFocus() ?: run {
                                                        // Eğer view holder yoksa, scroll yap ve focus ver
                                                        scrollToPosition(targetPosition)
                                                        post {
                                                            findViewHolderForAdapterPosition(targetPosition)?.itemView?.requestFocus()
                                                        }
                                                    }
                                                    Timber.tag("ContentGrid").d("🔒 Focus son satıra geri alındı: pos=$targetPosition")
                                                }
                                            } else if (wasLastRow && !isLastRow) {
                                                Timber.tag(
                                                    "ContentGrid",
                                                ).d("ℹ️ Focus son satırdan dışarı çıktı ama yukarı/sağ/sol yönünde, izin veriliyor")
                                            }
                                        }

                                        lastFocusedPosition = focusedPosition
                                    }
                                }
                            }
                        }
                    }

                    override fun onChildViewDetachedFromWindow(view: View) {
                        // Cleanup - focus listener'ı kaldır
                        view.setOnFocusChangeListener(null)
                    }
                },
            )
        }

        // Her yön tuşu için ayrı throttle mekanizması
        private var lastDownTime = 0L
        private var lastUpTime = 0L
        private var lastLeftTime = 0L
        private var lastRightTime = 0L
        private val throttleDelay = 500L // Hızlı gezinmeyi yavaşlatmak için (500ms)

        // Son satır kontrolü için focus change listener
        private var lastFocusedPosition = RecyclerView.NO_POSITION

        override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
            if (event == null) return super.dispatchKeyEvent(event)

            val actionName =
                when (event.action) {
                    KeyEvent.ACTION_DOWN -> "DOWN"
                    KeyEvent.ACTION_UP -> "UP"
                    else -> "OTHER(${event.action})"
                }

            val keyName =
                when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_DOWN -> "DPAD_DOWN"
                    KeyEvent.KEYCODE_DPAD_UP -> "DPAD_UP"
                    KeyEvent.KEYCODE_DPAD_LEFT -> "DPAD_LEFT"
                    KeyEvent.KEYCODE_DPAD_RIGHT -> "DPAD_RIGHT"
                    else -> "OTHER(${event.keyCode})"
                }

            Timber.tag("ContentGrid").d("⌨️ dispatchKeyEvent: $keyName, action=$actionName, repeatCount=${event.repeatCount}")

            if (event.action == KeyEvent.ACTION_DOWN) {
                val currentTime = System.currentTimeMillis()

                when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        // Son satır kontrolü - ÖNCE kontrol et, throttle'dan önce
                        val layoutManager = this.layoutManager as? CustomGridLayoutManager
                        val focusedView = findFocus()

                        Timber.tag("ContentGrid").d("🔍 DPAD_DOWN: layoutManager=$layoutManager, focusedView=$focusedView")

                        if (focusedView != null && layoutManager != null) {
                            val focusedPosition =
                                try {
                                    layoutManager.getPosition(focusedView)
                                } catch (e: Exception) {
                                    Timber.tag("ContentGrid").e("❌ getPosition hatası: ${e.message}")
                                    RecyclerView.NO_POSITION
                                }

                            val adapter = this.adapter
                            val itemCount = adapter?.itemCount ?: 0
                            val spanCount = layoutManager.spanCount

                            Timber.tag(
                                "ContentGrid",
                            ).d("🔍 DPAD_DOWN: focusedPosition=$focusedPosition, itemCount=$itemCount, spanCount=$spanCount")

                            if (focusedPosition != RecyclerView.NO_POSITION && itemCount > 0 && spanCount > 0) {
                                // Son satır hesaplaması - daha güvenilir
                                val lastRowNumber = (itemCount - 1) / spanCount
                                val currentRowNumber = focusedPosition / spanCount
                                val lastRowStartIndex = lastRowNumber * spanCount
                                val isLastRow = focusedPosition >= lastRowStartIndex
                                val isLastRowByRowNumber = currentRowNumber == lastRowNumber

                                Timber.tag("ContentGrid").d(
                                    "🔽 DPAD_DOWN: pos=$focusedPosition, itemCount=$itemCount, " +
                                        "spanCount=$spanCount, currentRow=$currentRowNumber, lastRow=$lastRowNumber, " +
                                        "lastRowStart=$lastRowStartIndex, isLastRow=$isLastRow, " +
                                        "isLastRowByRowNumber=$isLastRowByRowNumber, repeatCount=${event.repeatCount}",
                                )

                                // Son satırdaysak, focus'u tüket (atlamayı engelle) - throttle'dan BAĞIMSIZ
                                if (isLastRow || isLastRowByRowNumber) {
                                    Timber.tag(
                                        "ContentGrid",
                                    ).d(
                                        "🛑🛑🛑 DPAD_DOWN: SON SATIR TESPİT EDİLDİ! Focus tüketiliyor (dispatchKeyEvent) - event engellendi",
                                    )
                                    // Event'i tüket ve focus'u mevcut view'da tut
                                    // post yerine hemen focus'u geri al
                                    if (focusedView.isFocusable && focusedView.isFocusableInTouchMode) {
                                        focusedView.requestFocus()
                                        Timber.tag("ContentGrid").d("🔒 Focus hemen mevcut view'a geri alındı")
                                    }
                                    // post ile de tekrar kontrol et
                                    focusedView.post {
                                        val currentFocus = findFocus()
                                        if (currentFocus != focusedView && focusedView.isFocusable && focusedView.isFocusableInTouchMode) {
                                            focusedView.requestFocus()
                                            Timber.tag(
                                                "ContentGrid",
                                            ).d("🔒 Focus post ile mevcut view'a geri alındı (currentFocus=$currentFocus)")
                                        }
                                    }
                                    return true // Event'i tüket, super.dispatchKeyEvent çağrılmasın
                                }
                            } else {
                                Timber.tag(
                                    "ContentGrid",
                                ).w(
                                    "⚠️ DPAD_DOWN: Geçersiz pozisyon veya değerler - focusedPosition=$focusedPosition, itemCount=$itemCount, spanCount=$spanCount",
                                )
                            }
                        } else {
                            Timber.tag(
                                "ContentGrid",
                            ).w(
                                "⚠️ DPAD_DOWN: focusedView veya layoutManager null - focusedView=$focusedView, layoutManager=$layoutManager",
                            )
                        }

                        // Throttle kontrolü - sadece basılı tutma (repeatCount > 0) için uygula
                        // Yeni tıklamalar (repeatCount == 0) için throttle uygulanmaz
                        if (event.repeatCount > 0) {
                            // Basılı tutma: throttle uygula
                            val timeSinceLastKey = currentTime - lastDownTime
                            if (timeSinceLastKey < throttleDelay) {
                                Timber.tag(
                                    "ContentGrid",
                                ).d(
                                    "⏱️ DPAD_DOWN: Throttle aktif (basılı tutma, ${timeSinceLastKey}ms < ${throttleDelay}ms), ignore ediliyor",
                                )
                                return true // Çok hızlı, ignore et
                            }
                            lastDownTime = currentTime
                            Timber.tag("ContentGrid").d("✅ DPAD_DOWN: Basılı tutma - İzin veriliyor")
                        } else {
                            // Yeni tıklama: throttle uygulanmaz, hemen işle
                            lastDownTime = currentTime // Zaman damgasını güncelle ama throttle kontrolü yapma
                            Timber.tag("ContentGrid").d("✅ DPAD_DOWN: Yeni tıklama - Throttle uygulanmıyor, hemen işleniyor")
                        }
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        // Throttle kontrolü - sadece basılı tutma (repeatCount > 0) için uygula
                        if (event.repeatCount > 0) {
                            val timeSinceLastKey = currentTime - lastUpTime
                            if (timeSinceLastKey < throttleDelay) {
                                Timber.tag(
                                    "ContentGrid",
                                ).d(
                                    "⏱️ DPAD_UP: Throttle aktif (basılı tutma, ${timeSinceLastKey}ms < ${throttleDelay}ms), ignore ediliyor",
                                )
                                return true // Çok hızlı, ignore et
                            }
                            lastUpTime = currentTime
                            Timber.tag("ContentGrid").d("✅ DPAD_UP: Basılı tutma - İzin veriliyor")
                        } else {
                            lastUpTime = currentTime
                            Timber.tag("ContentGrid").d("✅ DPAD_UP: Yeni tıklama - Throttle uygulanmıyor, hemen işleniyor")
                        }
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        // En sol kolondaysak, kategorilere geç
                        val layoutManager = this.layoutManager as? CustomGridLayoutManager
                        val focusedView = findFocus()

                        if (focusedView != null && layoutManager != null) {
                            val focusedPosition =
                                try {
                                    layoutManager.getPosition(focusedView)
                                } catch (e: Exception) {
                                    Timber.tag("ContentGrid").e("❌ getPosition hatası: ${e.message}")
                                    RecyclerView.NO_POSITION
                                }

                            val adapter = this.adapter
                            val itemCount = adapter?.itemCount ?: 0
                            val spanCount = layoutManager.spanCount

                            if (focusedPosition != RecyclerView.NO_POSITION && itemCount > 0 && spanCount > 0) {
                                val isLeftmostColumn = (focusedPosition % spanCount) == 0

                                Timber.tag(
                                    "ContentGrid",
                                ).d("🔍 DPAD_LEFT: pos=$focusedPosition, spanCount=$spanCount, isLeftmostColumn=$isLeftmostColumn")

                                // En sol kolondaysak, kategorilere geç
                                if (isLeftmostColumn) {
                                    Timber.tag("ContentGrid").d("⬅️ DPAD_LEFT: En sol kolon, kategorilere geçiliyor")
                                    // Callback varsa çağır
                                    onNavigateToCategoriesCallback?.invoke()
                                    // Event'i tüket - callback focus'u kategorilere taşıyacak
                                    return true
                                }
                            }
                        }

                        // Throttle kontrolü - sadece basılı tutma (repeatCount > 0) için uygula
                        if (event.repeatCount > 0) {
                            val timeSinceLastKey = currentTime - lastLeftTime
                            if (timeSinceLastKey < throttleDelay) {
                                Timber.tag(
                                    "ContentGrid",
                                ).d(
                                    "⏱️ DPAD_LEFT: Throttle aktif (basılı tutma, ${timeSinceLastKey}ms < ${throttleDelay}ms), ignore ediliyor",
                                )
                                return true // Çok hızlı, ignore et
                            }
                            lastLeftTime = currentTime
                            Timber.tag("ContentGrid").d("✅ DPAD_LEFT: Basılı tutma - İzin veriliyor")
                        } else {
                            lastLeftTime = currentTime
                            Timber.tag("ContentGrid").d("✅ DPAD_LEFT: Yeni tıklama - Throttle uygulanmıyor, hemen işleniyor")
                        }
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        // Throttle kontrolü - sadece basılı tutma (repeatCount > 0) için uygula
                        if (event.repeatCount > 0) {
                            val timeSinceLastKey = currentTime - lastRightTime
                            if (timeSinceLastKey < throttleDelay) {
                                Timber.tag(
                                    "ContentGrid",
                                ).d(
                                    "⏱️ DPAD_RIGHT: Throttle aktif (basılı tutma, ${timeSinceLastKey}ms < ${throttleDelay}ms), ignore ediliyor",
                                )
                                return true // Çok hızlı, ignore et
                            }
                            lastRightTime = currentTime
                            Timber.tag("ContentGrid").d("✅ DPAD_RIGHT: Basılı tutma - İzin veriliyor")
                        } else {
                            lastRightTime = currentTime
                            Timber.tag("ContentGrid").d("✅ DPAD_RIGHT: Yeni tıklama - Throttle uygulanmıyor, hemen işleniyor")
                        }
                    }
                }
            }

            // Normal davranışa izin ver
            Timber.tag("ContentGrid").d("➡️ dispatchKeyEvent: Normal davranışa izin veriliyor")
            return super.dispatchKeyEvent(event)
        }
    }
