package com.pnr.tv.ui.main

import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.view.Window
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.FragmentManager
import com.pnr.tv.ui.main.MainActivity
import com.pnr.tv.ui.main.MainFragment
import com.pnr.tv.R
import com.pnr.tv.core.constants.UIConstants
import com.pnr.tv.databinding.ActivityMainBinding
import com.pnr.tv.extensions.hide
import com.pnr.tv.extensions.show
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject

/**
 * MainActivity için navigasyon ve UI yönetimi handler sınıfı.
 * Back button handling, exit dialog, top menu button visibility ve focus yönetimini merkezi bir şekilde yönetir.
 * Android TV için kritik olan focus mantığını korur.
 */
class MainNavigationHandler
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private lateinit var activity: MainActivity
        private lateinit var binding: ActivityMainBinding
        private lateinit var fragmentManager: FragmentManager
        private var mainActivityBackPressedCallback: OnBackPressedCallback? = null

        /**
         * Handler'ı başlatır ve gerekli referansları alır.
         * MainActivity'nin onCreate'inde çağrılmalıdır.
         */
        fun setup(
            activity: MainActivity,
            binding: ActivityMainBinding,
        ) {
            this.activity = activity
            this.binding = binding
            this.fragmentManager = activity.supportFragmentManager

            // Back pressed handler'ı başlat
            setupBackPressedHandler()
        }

        /**
         * Geri tuşu davranışını ayarlar.
         * Ana sayfadayken (backstack boşken) geri tuşuna basıldığında çıkış dialogu gösterilir.
         * Backstack'te fragment varsa, fragment geri navigasyonu yapılır.
         * Overlay gösterilirken geri tuşu davranışı engellenir.
         */
        private fun setupBackPressedHandler() {
            mainActivityBackPressedCallback =
                object : OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() {
                        // İlk adım: Yükleme varken işlem yapma
                        if (binding.loadingOverlay.visibility == android.view.View.VISIBLE) {
                            return
                        }

                        // FragmentManager'ın pending transaction'larını execute et
                        fragmentManager.executePendingTransactions()

                        // İkinci adım: Aktif fragment'ı al
                        val currentFragment = fragmentManager.findFragmentById(R.id.fragment_container)

                        // BaseBrowseFragment kendi back tuşu yönetimini yapıyor, bu callback çalışmamalı
                        if (currentFragment is com.pnr.tv.core.base.BaseBrowseFragment) {
                            // BaseBrowseFragment'ın kendi callback'i çalışmalı
                            return
                        }

                        // Üçüncü adım: Backstack kontrolü
                        val backStackCount = fragmentManager.backStackEntryCount

                        if (backStackCount > 0) {
                            // Backstack'te fragment varsa, fragment geri navigasyonu yap
                            hideTopMenuButtons()
                            fragmentManager.popBackStack()
                            binding.btnUpdate.requestFocus()
                            return
                        }

                        // Dördüncü adım: Backstack boş ise (ana sayfadayken) her zaman dialog göster
                        showExitDialog()
                    }
                }
            activity.onBackPressedDispatcher.addCallback(activity, mainActivityBackPressedCallback!!)
        }

        /**
         * onResume içinde çağrılmalıdır.
         * Callback her zaman aktif kalır, mantık handleOnBackPressed içinde yönetilir.
         */
        fun handleResumeBackCallback() {
            // FragmentManager'ın pending transaction'larını execute et
            fragmentManager.executePendingTransactions()

            // Aktif fragment'ı kontrol et
            val currentFragment = fragmentManager.findFragmentById(R.id.fragment_container)

            // BaseBrowseFragment görünürken callback'i disabled yap
            if (currentFragment is com.pnr.tv.core.base.BaseBrowseFragment) {
                mainActivityBackPressedCallback?.isEnabled = false
            } else {
                // Diğer durumlarda callback'i enabled yap
                mainActivityBackPressedCallback?.isEnabled = true
            }
        }

        /**
         * BaseBrowseFragment görünürken callback'i disabled yapmak için çağrılır.
         * BaseBrowseFragment'tan onResume'da çağrılmalıdır.
         */
        fun disableBackCallbackForBrowseFragment() {
            mainActivityBackPressedCallback?.isEnabled = false
        }

        /**
         * BaseBrowseFragment görünmezken callback'i enabled yapmak için çağrılır.
         * BaseBrowseFragment'tan onPause'da çağrılmalıdır.
         *
         * NOT: Fragment lifecycle metodları içinde FragmentManager transaction'ları execute edilemez.
         * Bu yüzden fragment kontrolünü post ile geciktiriyoruz.
         */
        fun enableBackCallbackForBrowseFragment() {
            // Fragment lifecycle metodları içinde FragmentManager transaction'ları execute edilemez.
            // Bu yüzden fragment kontrolünü post ile geciktiriyoruz.
            activity.window?.decorView?.post {
                try {
                    // Aktif fragment'ı kontrol et (executePendingTransactions olmadan)
                    // onPause sırasında FragmentManager transaction'ları execute edilemez
                    val currentFragment = fragmentManager.findFragmentById(R.id.fragment_container)

                    // BaseBrowseFragment görünmüyorsa callback'i enabled yap
                    if (currentFragment !is com.pnr.tv.core.base.BaseBrowseFragment) {
                        mainActivityBackPressedCallback?.isEnabled = true
                    }
                } catch (e: Exception) {
                    // Hata durumunda güvenli yaklaşım: callback'i enabled yap
                    // (FragmentManager erişilemiyorsa bile callback aktif olmalı)
                    mainActivityBackPressedCallback?.isEnabled = true
                }
            }
        }

        /**
         * onResume içinde çağrılmalıdır.
         * Activity geçişlerinden sonra focus kaybını önlemek için agresif focus yönetimi yapar.
         * MainFragment görünürken update button'a focus verir.
         */
        fun handleResumeFocus() {
            // Set initial focus to the update button every time the main screen is visible
            // Activity geçişlerinden sonra focus kaybını önlemek için daha agresif focus yönetimi
            binding.root.post {
                // Check if MainFragment is currently visible
                val visibleFragment = fragmentManager.findFragmentById(R.id.fragment_container)
                if (visibleFragment is MainFragment || visibleFragment == null) {
                    // Önce container'ları focusable yapmayı engelle (MainFragment bunu yapacak)
                    // Sadece update button'a focus ver
                    binding.btnUpdate.requestFocus()

                    // Birden fazla kez deneyelim (Android'in otomatik odak yönetimi ile yarışmak için)
                    binding.root.postDelayed({
                        binding.btnUpdate.requestFocus()
                    }, 50)

                    binding.root.postDelayed({
                        binding.btnUpdate.requestFocus()
                    }, 150)

                    // Activity geçişlerinden sonra daha uzun bir süre sonra da focus ver
                    binding.root.postDelayed({
                        binding.btnUpdate.requestFocus()
                    }, (UIConstants.DelayDurations.FOCUS_CHANGE_DELAY_MS * 3).toLong()) // 300ms

                    // Son bir kontrol daha (container'lar focusable olmadan önce)
                    binding.root.postDelayed({
                        // Eğer focus hala update button'da değilse tekrar ver
                        val currentFocus = activity.window?.currentFocus
                        if (currentFocus?.id != binding.btnUpdate.id) {
                            binding.btnUpdate.requestFocus()
                        }
                    }, 350)
                }
            }
        }

        /**
         * Üst menü butonlarını alpha animasyonuyla gizler.
         */
        fun hideTopMenuButtons() {
            binding.layoutCurrentUser.alpha = 0f
            binding.btnExit.alpha = 0f
            binding.btnSettings.alpha = 0f
            binding.btnUsers.alpha = 0f
            binding.btnUpdate.alpha = 0f
            // Görünürlükleri koru, sadece alpha değiş
        }

        /**
         * Üst menü butonlarını alpha animasyonuyla gösterir.
         */
        fun showTopMenuButtons() {
            binding.layoutCurrentUser.alpha = 1f
            binding.btnExit.alpha = 1f
            binding.btnSettings.alpha = 1f
            binding.btnUsers.alpha = 1f
            binding.btnUpdate.alpha = 1f

            // Butonları görünür yap (alpha animasyonu için)
            binding.layoutCurrentUser.show()
            binding.btnExit.show()
            binding.btnSettings.show()
            binding.btnUsers.show()
            binding.btnUpdate.show()

            // Alpha animasyonu
            binding.layoutCurrentUser.animate().alpha(1f).setDuration(200).start()
            binding.btnExit.animate().alpha(1f).setDuration(200).start()
            binding.btnSettings.animate().alpha(1f).setDuration(200).start()
            binding.btnUsers.animate().alpha(1f).setDuration(200).start()
            binding.btnUpdate.animate().alpha(1f).setDuration(200).start()
        }

        /**
         * Üst menü butonlarından birine focus verilmiş mi kontrol eder
         */
        fun isTopMenuButtonFocused(): Boolean {
            val focusedView = activity.window?.currentFocus ?: return false
            return focusedView.id == binding.btnUpdate.id ||
                focusedView.id == binding.btnUsers.id ||
                focusedView.id == binding.btnSettings.id ||
                focusedView.id == binding.btnExit.id
        }

        /**
         * ToolbarController interface'i için: Üst menüyü gösterir (görünürlük).
         * Alpha animasyonu yapmaz, sadece görünürlüğü değiştirir.
         */
        fun showTopMenu() {
            binding.layoutCurrentUser.show()
            binding.btnExit.show()
            binding.btnSettings.show()
            binding.btnUsers.show()
            binding.btnUpdate.show()
        }

        /**
         * ToolbarController interface'i için: Üst menüyü gizler (görünürlük).
         * Alpha animasyonu yapmaz, sadece görünürlüğü değiştirir.
         */
        fun hideTopMenu() {
            binding.layoutCurrentUser.hide()
            binding.btnExit.hide()
            binding.btnSettings.hide()
            binding.btnUsers.hide()
            binding.btnUpdate.hide()
        }

        /**
         * Çıkış dialogunu gösterir.
         */
        fun showExitDialog() {
            try {
                // Activity kontrolü
                if (activity.isDestroyed || activity.isFinishing) {
                    return
                }

                // Activity window'unun hazır olduğundan emin ol
                val window = activity.window
                if (window == null || window.decorView == null) {
                    activity.window?.decorView?.post {
                        showExitDialogInternal()
                    }
                    return
                }

                // UI thread'de olduğumuzdan emin ol
                if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                    showExitDialogInternal()
                } else {
                    activity.runOnUiThread {
                        showExitDialogInternal()
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error in showExitDialog")
            }
        }

        /**
         * Dialog'u oluşturup gösterir (internal metod).
         */
        private fun showExitDialogInternal() {
            try {
                // Activity'nin hala geçerli olduğunu kontrol et
                if (activity.isDestroyed || activity.isFinishing) {
                    return
                }

                // Activity window'unun token'ının geçerli olduğundan emin ol
                val activityWindow = activity.window
                if (activityWindow == null || activityWindow.decorView == null || activityWindow.decorView.windowToken == null) {
                    // Kısa bir gecikme ile tekrar dene
                    activityWindow?.decorView?.postDelayed({
                        if (!activity.isDestroyed && !activity.isFinishing) {
                            showExitDialogInternal()
                        }
                    }, 100)
                    return
                }

                // Activity context kullan (ApplicationContext değil!)
                val view = LayoutInflater.from(activity).inflate(R.layout.dialog_confirm, null)
                val dialog = Dialog(activity) // Activity context kullan
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
                dialog.setContentView(view)

                val window = dialog.window
                window?.setLayout(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                )
                window?.setBackgroundDrawableResource(android.R.color.transparent)

                val titleTextView = view.findViewById<android.widget.TextView>(R.id.tv_dialog_title)
                val messageTextView = view.findViewById<android.widget.TextView>(R.id.tv_dialog_message)
                val btnYes = view.findViewById<android.widget.TextView>(R.id.btn_dialog_yes)
                val btnNo = view.findViewById<android.widget.TextView>(R.id.btn_dialog_no)

                titleTextView.text = activity.getString(R.string.dialog_exit_title)
                messageTextView.text = activity.getString(R.string.dialog_exit_message)

                btnYes.setOnClickListener {
                    activity.finishAffinity()
                    dialog.dismiss()
                }

                btnNo.setOnClickListener {
                    dialog.dismiss()
                }

                // Kullanıcı yanlışlıkla açarsa geri tuşuyla kapatabilsin
                dialog.setCancelable(true)

                // Dialog'u göster
                dialog.show()

                // Güvenlik için "Hayır" butonuna focus ver (dialog.show()'dan sonra)
                btnNo.requestFocus()
            } catch (e: Exception) {
                Timber.e(e, "Error showing dialog")
            }
        }
    }
