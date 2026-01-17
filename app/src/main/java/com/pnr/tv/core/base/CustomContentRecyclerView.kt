package com.pnr.tv.core.base

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import androidx.recyclerview.widget.RecyclerView

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

        // Restore işlemi devam ediyor mu? (odak kaybı koruması için)
        var isRestoringFocus: Boolean = false

        /**
         * Focus restore işlemi tamamlandığında çağrılır.
         * Bu method, restore sonrası cooldown süresini başlatır.
         * Cooldown süresi boyunca (500ms) tuş girişleri ignore edilir ve focus o öğede kalır.
         * Cooldown sonrası focus'un hala restore edilen öğede olduğundan emin olur.
         *
         * @param restoredPosition Restore edilen pozisyon (cooldown sonrası kontrol için)
         */
        fun onFocusRestoreCompleted(restoredPosition: Int) {
            focusRestoredTimestamp = System.currentTimeMillis()
            focusRestoredPosition = restoredPosition
            // Cooldown süresi sona erdiğinde timestamp'i sıfırla
            // NOT: Cooldown sonrası focus kontrolü kaldırıldı - görsel sıçrama yaratıyordu
            postDelayed({
                focusRestoredTimestamp = 0
                focusRestoredPosition = RecyclerView.NO_POSITION
            }, focusRestoreCooldownMs)
        }

        // Focus restore işlemi tamamlandığında zamanı kaydet (cooldown için)
        // Bu süre boyunca (500ms) tuş girişleri ignore edilir, focus o öğede kalır
        private var focusRestoredTimestamp: Long = 0
        private var focusRestoredPosition: Int = RecyclerView.NO_POSITION // Restore edilen pozisyon (cooldown sonrası kontrol için)
        private val focusRestoreCooldownMs = 500L // Focus restore sonrası cooldown süresi

        override fun onFocusChanged(
            gainFocus: Boolean,
            direction: Int,
            previouslyFocusedRect: android.graphics.Rect?,
        ) {
            super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)

            // NOT: Cooldown sonrası focus kontrolü kaldırıldı - görsel sıçrama yaratıyordu

            // Eğer restore işlemi devam ediyorsa ve odak kaybedildiyse, içeriye hapset
            if (isRestoringFocus && !gainFocus) {
                val currentFocus = findFocus()
                // Eğer odak sistem seviyesindeki bir bileşene (Back tuşu gibi) gitmeye çalışıyorsa
                if (currentFocus == null || (currentFocus.parent != this && currentFocus.parent?.parent != this)) {
                    // İlk öğeye veya son odaklanılan öğeye geri dön
                    val layoutManager = this.layoutManager as? CustomGridLayoutManager
                    if (layoutManager != null && adapter != null) {
                        val itemCount = adapter?.itemCount ?: 0
                        if (itemCount > 0) {
                            val targetPosition = lastFocusedPosition.takeIf { it >= 0 && it < itemCount } ?: 0
                            layoutManager.scrollToPositionWithOffset(targetPosition, 200)
                            post {
                                val viewHolder = findViewHolderForAdapterPosition(targetPosition)
                                if (viewHolder != null) {
                                    viewHolder.itemView.requestFocus()
                                }
                            }
                        }
                    }
                }
            }
        }

        init {
            // Child view'ların focus değişikliklerini dinle
            // Not: Son satır kontrolü CustomGridLayoutManager.onFocusSearchFailed() içinde zaten yapılıyor,
            // bu yüzden burada sadece lastFocusedPosition'ı takip ediyoruz (debug/log amaçlı)
            addOnChildAttachStateChangeListener(
                object : OnChildAttachStateChangeListener {
                    override fun onChildViewAttachedToWindow(view: View) {
                        // Her child view'a focus change listener ekle
                        view.setOnFocusChangeListener { focusedView, hasFocus ->
                            val layoutManager = layoutManager as? CustomGridLayoutManager
                            if (layoutManager != null) {
                                val focusedPosition = layoutManager.getPosition(focusedView)
                                if (focusedPosition != RecyclerView.NO_POSITION) {
                                    lastFocusedPosition = focusedPosition
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
        private val throttleDelay = 200L // Hızlı gezinmeyi yavaşlatmak için (200ms)

        // Son satır kontrolü için focus change listener
        private var lastFocusedPosition = RecyclerView.NO_POSITION

        override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
            if (event == null) return super.dispatchKeyEvent(event)

            if (event.action == KeyEvent.ACTION_DOWN) {
                val currentTime = System.currentTimeMillis()

                // Focus restore cooldown kontrolü: Eğer restore işlemi yakın zamanda tamamlandıysa (500ms içinde),
                // tuş girişlerini tamamen ignore et - focus hiçbir şekilde başka yere gitmeye çalışmayacak
                val timeSinceRestore = currentTime - focusRestoredTimestamp
                if (focusRestoredTimestamp > 0 && timeSinceRestore < focusRestoreCooldownMs) {
                    // Cooldown aktif: Tüm tuş girişlerini ignore et, focus olduğu yerde kalsın
                    // Focus'u zorla geri döndürmüyoruz - bu görsel olarak rahatsız edici olur
                    return true // Event'i tüket, hiçbir işlem yapma
                }

                val focusedView = findFocus()
                val layoutManager = this.layoutManager as? CustomGridLayoutManager
                var focusedPosition = RecyclerView.NO_POSITION
                if (focusedView != null && layoutManager != null) {
                    try {
                        focusedPosition = layoutManager.getPosition(focusedView)
                    } catch (e: Exception) {
                        // Ignore
                    }
                }

                when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        // Manuel focus navigation: Bir sonraki satırdaki aynı kolondaki öğeye focus ver
                        val layoutManager = this.layoutManager as? CustomGridLayoutManager
                        val focusedView = findFocus()

                        if (focusedView != null && layoutManager != null) {
                            val focusedPosition =
                                try {
                                    layoutManager.getPosition(focusedView)
                                } catch (e: Exception) {
                                    RecyclerView.NO_POSITION
                                }

                            val adapter = this.adapter
                            val itemCount = adapter?.itemCount ?: 0
                            val spanCount = layoutManager.spanCount

                            if (focusedPosition != RecyclerView.NO_POSITION && itemCount > 0 && spanCount > 0) {
                                // Son satır hesaplaması
                                val lastRowNumber = (itemCount - 1) / spanCount
                                val currentRowNumber = focusedPosition / spanCount
                                val currentColumn = focusedPosition % spanCount

                                // Son satırdaysak, focus'u tüket (atlamayı engelle)
                                if (currentRowNumber >= lastRowNumber) {
                                    if (focusedView.isFocusable && focusedView.isFocusableInTouchMode) {
                                        focusedView.requestFocus()
                                    }
                                    return true // Event'i tüket
                                }

                                // Bir sonraki satırdaki aynı kolondaki pozisyonu hesapla
                                val nextRowNumber = currentRowNumber + 1
                                val nextPosition = nextRowNumber * spanCount + currentColumn

                                // Pozisyon geçerli mi kontrol et
                                if (nextPosition < itemCount) {
                                    // Throttle kontrolü - sadece basılı tutma için
                                    if (event.repeatCount > 0) {
                                        val timeSinceLastKey = currentTime - lastDownTime
                                        if (timeSinceLastKey < throttleDelay) {
                                            return true // Çok hızlı, ignore et
                                        }
                                        lastDownTime = currentTime
                                    } else {
                                        lastDownTime = currentTime
                                    }

                                    // Bir sonraki pozisyona scroll yap ve focus ver
                                    layoutManager.scrollToPositionWithOffset(nextPosition, 200)
                                    post {
                                        val nextViewHolder = findViewHolderForAdapterPosition(nextPosition)
                                        if (nextViewHolder != null) {
                                            nextViewHolder.itemView.requestFocus()
                                            // Manuel focus değişikliği için ses efekti çal
                                            nextViewHolder.itemView.playSoundEffect(android.view.SoundEffectConstants.NAVIGATION_DOWN)
                                        } else {
                                            // ViewHolder henüz oluşturulmamış, biraz bekle
                                            postDelayed({
                                                val delayedViewHolder = findViewHolderForAdapterPosition(nextPosition)
                                                if (delayedViewHolder != null) {
                                                    delayedViewHolder.itemView.requestFocus()
                                                    // Manuel focus değişikliği için ses efekti çal
                                                    delayedViewHolder.itemView.playSoundEffect(
                                                        android.view.SoundEffectConstants.NAVIGATION_DOWN,
                                                    )
                                                }
                                            }, 50)
                                        }
                                    }

                                    return true // Event'i tüket, sistemin otomatik navigation'ını engelle
                                }
                            }
                        }

                        // Fallback: Throttle kontrolü
                        if (event.repeatCount > 0) {
                            val timeSinceLastKey = currentTime - lastDownTime
                            if (timeSinceLastKey < throttleDelay) {
                                return true
                            }
                            lastDownTime = currentTime
                        } else {
                            lastDownTime = currentTime
                        }
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        // Android TV'nin doğal Focus Search mekanizmasına izin ver.
                        // Navbar'a geçiş sistem tarafından otomatik olarak yönetilecek.
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
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        // En sol kolondaysak, kategorilere geç
                        val layoutManager = this.layoutManager as? CustomGridLayoutManager
                        val focusedView = findFocus()

                        if (focusedView != null && layoutManager != null) {
                            val focusedPosition =
                                try {
                                    layoutManager.getPosition(focusedView)
                                } catch (e: Exception) {
                                    RecyclerView.NO_POSITION
                                }

                            val adapter = this.adapter
                            val itemCount = adapter?.itemCount ?: 0
                            val spanCount = layoutManager.spanCount

                            if (focusedPosition != RecyclerView.NO_POSITION && itemCount > 0 && spanCount > 0) {
                                val isLeftmostColumn = (focusedPosition % spanCount) == 0

                                // SOLA GEÇİŞ: En sol kolondaysak, kategorilere geç
                                // Sistemin otomatik focus bulmasına izin ver. Eğer sistem bulamıyorsa findViewHolderForAdapterPosition kullan.
                                if (isLeftmostColumn) {
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
                                return true // Çok hızlı, ignore et
                            }
                            lastLeftTime = currentTime
                        } else {
                            lastLeftTime = currentTime
                        }
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        // En sağ kolonda focus'u tüket - grid dışına çıkmasın
                        val layoutManager = this.layoutManager as? CustomGridLayoutManager
                        val focusedView = findFocus()

                        if (focusedView != null && layoutManager != null) {
                            val focusedPosition =
                                try {
                                    layoutManager.getPosition(focusedView)
                                } catch (e: Exception) {
                                    RecyclerView.NO_POSITION
                                }

                            val adapter = this.adapter
                            val itemCount = adapter?.itemCount ?: 0
                            val spanCount = layoutManager.spanCount

                            if (focusedPosition != RecyclerView.NO_POSITION && itemCount > 0 && spanCount > 0) {
                                val currentColumn = focusedPosition % spanCount
                                val isRightmostColumn = currentColumn == spanCount - 1

                                // En sağ kolondaysak, focus'u tüket (grid dışına çıkmasın)
                                if (isRightmostColumn) {
                                    if (focusedView.isFocusable && focusedView.isFocusableInTouchMode) {
                                        focusedView.requestFocus()
                                    }
                                    return true // Event'i tüket
                                }
                            }
                        }

                        // Throttle kontrolü - sadece basılı tutma (repeatCount > 0) için uygula
                        if (event.repeatCount > 0) {
                            val timeSinceLastKey = currentTime - lastRightTime
                            if (timeSinceLastKey < throttleDelay) {
                                return true // Çok hızlı, ignore et
                            }
                            lastRightTime = currentTime
                        } else {
                            lastRightTime = currentTime
                        }
                    }
                    KeyEvent.KEYCODE_BACK -> {
                        // Back tuşu artık BaseBrowseFragment'ta merkezi olarak yönetiliyor
                        // Bu handler kaldırıldı - çakışmayı önlemek için
                        // Event'i tüketme, sistemin merkezi handler'ına bırak
                    }
                }
            }

            // Normal davranışa izin ver
            val result = super.dispatchKeyEvent(event)

            // SEARCH DISPATCHER TEMİZLİĞİ: Eğer sistem odağı taşıyamıyorsa (result=false) ve SOL tuşuna basıldıysa,
            // manuel olarak navigateFocusToCategories() metodunu tetikle
            if (!result && event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT && event.action == KeyEvent.ACTION_DOWN) {
                val layoutManager = this.layoutManager as? CustomGridLayoutManager
                val focusedView = findFocus()

                if (focusedView != null && layoutManager != null) {
                    val focusedPosition =
                        try {
                            layoutManager.getPosition(focusedView)
                        } catch (e: Exception) {
                            RecyclerView.NO_POSITION
                        }

                    val adapter = this.adapter
                    val itemCount = adapter?.itemCount ?: 0
                    val spanCount = layoutManager.spanCount

                    if (focusedPosition != RecyclerView.NO_POSITION && itemCount > 0 && spanCount > 0) {
                        val isLeftmostColumn = (focusedPosition % spanCount) == 0

                        // En sol kolondaysak ve sistem odağı taşıyamadıysa, manuel olarak kategorilere geç
                        if (isLeftmostColumn) {
                            onNavigateToCategoriesCallback?.invoke()
                            return true // Event'i tüket
                        }
                    }
                }
            }

            return result
        }
    }
