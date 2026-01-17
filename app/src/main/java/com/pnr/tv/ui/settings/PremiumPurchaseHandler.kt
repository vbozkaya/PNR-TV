package com.pnr.tv.ui.settings

import android.app.Activity
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.LifecycleCoroutineScope
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.pnr.tv.R
import com.pnr.tv.extensions.showCustomToast
import com.pnr.tv.premium.BillingConnectionHandler
import com.pnr.tv.premium.BillingManager
import com.pnr.tv.premium.PremiumManager
import com.pnr.tv.util.CrashlyticsHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject

/**
 * Premium satın alma işlemlerini yöneten yardımcı sınıf.
 * PremiumSettingsFragment'teki satın alma mantığını buraya taşır.
 */
class PremiumPurchaseHandler
    @Inject
    constructor(
        private val billingManager: BillingManager,
        private val premiumManager: PremiumManager,
    ) {
        /**
         * Premium durum text'ini güncellemek için callback.
         */
        interface StatusUpdateCallback {
            fun updateStatus(text: String)
        }

        /**
         * Premium satın alma işlemini başlatır.
         * @param activity Activity referansı (launchPurchaseFlow için gerekli)
         * @param context Context (toast mesajları için)
         * @param lifecycleScope LifecycleCoroutineScope (coroutine'ler için)
         * @param statusCallback TextView güncellemeleri için callback
         */
        fun handlePurchase(
            activity: Activity,
            context: Context,
            lifecycleScope: LifecycleCoroutineScope,
            statusCallback: StatusUpdateCallback,
        ) {
            lifecycleScope.launch {
                val connectionState = billingManager.billingConnectionState.value

                // Crashlytics'e log gönder
                CrashlyticsHelper.setCustomKey("premium_button_clicked_timestamp", System.currentTimeMillis())
                CrashlyticsHelper.setCustomKey("premium_button_clicked_connection_state", connectionState.toString())
                CrashlyticsHelper.log("Premium button: Connection state - $connectionState", "INFO")
                FirebaseCrashlytics.getInstance().sendUnsentReports()

                when (connectionState) {
                    BillingConnectionHandler.BillingConnectionState.CONNECTED -> {
                        handlePremiumPurchaseConnected(activity, context, lifecycleScope, statusCallback)
                    }
                    BillingConnectionHandler.BillingConnectionState.CONNECTING -> {
                        handlePremiumPurchaseConnecting(activity, context, lifecycleScope, statusCallback)
                    }
                    BillingConnectionHandler.BillingConnectionState.DISCONNECTED -> {
                        handlePremiumPurchaseDisconnected(activity, context, lifecycleScope, statusCallback)
                    }
                    BillingConnectionHandler.BillingConnectionState.ERROR -> {
                        handlePremiumPurchaseError(context, statusCallback)
                    }
                }
            }
        }

        private fun handlePremiumPurchaseConnected(
            activity: Activity,
            context: Context,
            lifecycleScope: LifecycleCoroutineScope,
            statusCallback: StatusUpdateCallback,
        ) {
            Timber.d("BillingClient bağlı, restore ve satın alma işlemi başlatılıyor")
            CrashlyticsHelper.log("Premium button: BillingClient CONNECTED, starting restore", "INFO")
            FirebaseCrashlytics.getInstance().sendUnsentReports()

            // Status text'i güncelle (TV'de görünür)
            statusCallback.updateStatus(context.getString(R.string.error_billing_connecting))

            // Önce mevcut satın alımları kontrol et (restore)
            CrashlyticsHelper.log("Premium button: Calling queryPurchases()", "INFO")
            FirebaseCrashlytics.getInstance().sendUnsentReports()
            billingManager.queryPurchases(isRestore = true, showRestoreToast = true)

            lifecycleScope.launch {
                // Acknowledge işleminin tamamlanmasını bekle (max 5 saniye)
                val acknowledgeCompleted =
                    withTimeoutOrNull(5000) {
                        billingManager.acknowledgeCompleted.firstOrNull()
                    }
                Timber.d("Acknowledge completed: $acknowledgeCompleted")

                // Premium durumunu kontrol et
                val stillNotPremium = !premiumManager.isPremium().first()

                CrashlyticsHelper.setCustomKey("premium_button_restore_result", !stillNotPremium)
                CrashlyticsHelper.log("Premium button: After restore, stillNotPremium: $stillNotPremium", "INFO")
                FirebaseCrashlytics.getInstance().sendUnsentReports()

                if (stillNotPremium) {
                    // Restore sonrası hala premium değilse yeni satın alma akışını başlat
                    Timber.d("Restore sonrası premium yok, satın alma akışı başlatılıyor")
                    CrashlyticsHelper.log("Premium button: No premium after restore, launching purchase flow", "INFO")
                    FirebaseCrashlytics.getInstance().sendUnsentReports()
                    statusCallback.updateStatus(context.getString(R.string.settings_premium_off))
                    billingManager.launchPurchaseFlow(activity)

                    // launchPurchaseFlow() sonrası ITEM_ALREADY_OWNED durumunda queryPurchases() çağrılabilir
                    // Premium durumunu periyodik olarak kontrol et (max 10 saniye, her 500ms'de bir)
                    // Her 2 saniyede bir queryPurchases'i zorla tetikle (defensive programming)
                    var checked = false
                    var attempts = 0
                    val maxAttempts = 20 // 10 saniye (20 * 500ms)
                    var lastQueryTime = System.currentTimeMillis()

                    while (!checked && attempts < maxAttempts) {
                        delay(500)
                        attempts++

                        // Her 2 saniyede bir queryPurchases'i zorla tetikle
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastQueryTime >= 2000) {
                            Timber.d("Periyodik queryPurchases tetikleniyor (attempt: $attempts)")
                            billingManager.queryPurchases(isRestore = true, showRestoreToast = false)
                            lastQueryTime = currentTime
                        }

                        val isPremiumNow = premiumManager.isPremium().first()
                        if (isPremiumNow) {
                            checked = true
                            Timber.d("Premium aktif edildi (ITEM_ALREADY_OWNED durumundan sonra) - Deneme: $attempts")
                            CrashlyticsHelper.log(
                                "Premium button: Premium activated after ITEM_ALREADY_OWNED (attempt: $attempts)",
                                "INFO",
                            )
                            FirebaseCrashlytics.getInstance().sendUnsentReports()
                            statusCallback.updateStatus(context.getString(R.string.settings_premium_on))
                            context.showCustomToast(
                                context.getString(R.string.toast_premium_restored),
                                Toast.LENGTH_LONG,
                            )
                        }
                    }

                    if (!checked) {
                        Timber.w("Premium durumu kontrol edildi ama hala aktif değil ($attempts deneme)")
                        CrashlyticsHelper.log("Premium button: Premium still not active after $attempts attempts", "WARN")
                        FirebaseCrashlytics.getInstance().sendUnsentReports()
                    }
                } else {
                    // Restore başarılı, premium aktif edildi
                    Timber.d("Restore başarılı, premium aktif edildi")
                    CrashlyticsHelper.log("Premium button: Restore successful, premium activated", "INFO")
                    FirebaseCrashlytics.getInstance().sendUnsentReports()
                    statusCallback.updateStatus(context.getString(R.string.settings_premium_on))
                    context.showCustomToast(
                        context.getString(R.string.toast_premium_restored),
                        Toast.LENGTH_LONG,
                    )
                }
            }
        }

        private fun handlePremiumPurchaseConnecting(
            activity: Activity,
            context: Context,
            lifecycleScope: LifecycleCoroutineScope,
            statusCallback: StatusUpdateCallback,
        ) {
            Timber.w("BillingClient bağlanıyor, bekleniyor")
            CrashlyticsHelper.log("Premium button: BillingClient CONNECTING - waiting for connection", "WARN")
            FirebaseCrashlytics.getInstance().sendUnsentReports()

            statusCallback.updateStatus(context.getString(R.string.error_billing_connecting))
            context.showCustomToast(
                context.getString(R.string.error_billing_connecting),
                Toast.LENGTH_LONG,
            )

            lifecycleScope.launch {
                // Bağlantıyı bekle (max 10 saniye)
                var waited = 0
                while (waited < 10000 && billingManager.billingConnectionState.value == BillingConnectionHandler.BillingConnectionState.CONNECTING) {
                    delay(500)
                    waited += 500
                }

                val finalState = billingManager.billingConnectionState.value
                CrashlyticsHelper.log("Premium button: After waiting, connection state: $finalState", "INFO")
                FirebaseCrashlytics.getInstance().sendUnsentReports()

                // Bağlantı kurulduysa işlemi devam ettir
                if (finalState == BillingConnectionHandler.BillingConnectionState.CONNECTED) {
                    handlePremiumPurchaseConnected(activity, context, lifecycleScope, statusCallback)
                } else {
                    // Hala bağlanamadı
                    statusCallback.updateStatus(context.getString(R.string.error_billing_error))
                    context.showCustomToast(
                        context.getString(R.string.error_billing_error),
                        Toast.LENGTH_LONG,
                    )
                }
            }
        }

        private fun handlePremiumPurchaseDisconnected(
            activity: Activity,
            context: Context,
            lifecycleScope: LifecycleCoroutineScope,
            statusCallback: StatusUpdateCallback,
        ) {
            // BillingManager henüz bağlanmadıysa, bağlanmayı bekle
            Timber.w("BillingClient bağlı değil, initialize ediliyor")
            CrashlyticsHelper.log("Premium button: BillingClient DISCONNECTED, initializing", "WARN")
            FirebaseCrashlytics.getInstance().sendUnsentReports()
            statusCallback.updateStatus(context.getString(R.string.error_billing_not_connected))

            // BillingManager'ı initialize et
            billingManager.initialize()

            lifecycleScope.launch {
                // Bağlantıyı bekle (max 10 saniye)
                var waited = 0
                while (waited < 10000 && billingManager.billingConnectionState.value == BillingConnectionHandler.BillingConnectionState.CONNECTING) {
                    delay(500)
                    waited += 500
                }

                val finalState = billingManager.billingConnectionState.value
                CrashlyticsHelper.log("Premium button: After initialize wait, connection state: $finalState", "INFO")
                FirebaseCrashlytics.getInstance().sendUnsentReports()

                if (finalState == BillingConnectionHandler.BillingConnectionState.CONNECTED) {
                    handlePremiumPurchaseConnected(activity, context, lifecycleScope, statusCallback)
                } else {
                    context.showCustomToast(
                        context.getString(R.string.error_billing_not_connected),
                        Toast.LENGTH_LONG,
                    )
                }
            }
        }

        private fun handlePremiumPurchaseError(
            context: Context,
            statusCallback: StatusUpdateCallback,
        ) {
            // Hata durumunda yeniden bağlanmayı dene
            Timber.e("BillingClient hata durumunda, retry ediliyor")
            CrashlyticsHelper.log("Premium button: BillingClient ERROR, retrying", "ERROR")
            FirebaseCrashlytics.getInstance().sendUnsentReports()
            statusCallback.updateStatus(context.getString(R.string.error_billing_error))
            billingManager.retryConnection()
            context.showCustomToast(
                context.getString(R.string.error_billing_error),
                Toast.LENGTH_LONG,
            )
        }
    }
