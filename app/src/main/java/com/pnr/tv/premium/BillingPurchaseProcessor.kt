package com.pnr.tv.premium

import android.content.Context
import android.widget.Toast
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.Purchase
import com.pnr.tv.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Satın alma onaylama (acknowledgment) ve premium durum güncelleme işlemlerini yönetir.
 */
class BillingPurchaseProcessor(
    private val billingClient: BillingClient?,
    private val premiumManager: PremiumManager,
    private val context: Context,
    private val billingScope: CoroutineScope,
    private val premiumProductId: String,
) {
        /**
         * Acknowledge işleminin tamamlanmasını bildirmek için callback.
         */
        interface AcknowledgeCallback {
            fun onAcknowledgeCompleted(success: Boolean)
        }

        /**
         * Satın alımları işler ve premium durumunu günceller.
         * @param purchases Satın alımlar listesi
         * @param isRestore true ise, restore işlemi olduğunu belirtir ve uygun mesaj gösterilir.
         * @param showRestoreToast true ise, restore toast mesajını gösterir. false ise sadece premium durumunu günceller.
         * @param callback Acknowledge işleminin tamamlanmasını bildirmek için callback.
         */
        fun handlePurchases(
            purchases: List<Purchase>,
            isRestore: Boolean = false,
            showRestoreToast: Boolean = true,
            callback: AcknowledgeCallback? = null,
        ) {
            var hasPremium = false

            for (purchase in purchases) {
                val purchaseState =
                    try {
                        purchase.purchaseState
                    } catch (e: Exception) {
                        null
                    }

                // Premium ürün kontrolü ve PurchaseState kontrolü
                if (purchase.products.contains(premiumProductId)) {
                    // PurchaseState kontrolü - sadece PURCHASED durumundaki satın alımları kabul et
                    if (purchaseState == null || purchaseState == Purchase.PurchaseState.PURCHASED) {
                        hasPremium = true

                        // Satın alma onaylanmamışsa onayla
                        if (!purchase.isAcknowledged) {
                            acknowledgePurchase(purchase, isRestore, showRestoreToast, callback)
                        } else {
                            // Zaten onaylanmışsa premium durumunu hemen güncelle
                            billingScope.launch {
                                premiumManager.setPremiumStatus(true)
                                callback?.onAcknowledgeCompleted(true)
                                // Restore durumunda ve toast gösterilmesi gerekiyorsa restore mesajını göster, değilse aktif edildi mesajını göster
                                if (isRestore && showRestoreToast) {
                                    showPremiumRestoredMessage()
                                } else if (!isRestore) {
                                    showPremiumActivatedMessage()
                                }
                            }
                        }
                    } else {
                        Timber.w("Premium ürün bulundu ama PurchaseState PURCHASED değil: ${purchase.purchaseState}")
                    }
                }
            }

            // Eğer premium yoksa durumu güncelle (acknowledge callback'inde zaten güncellenmiş olabilir)
            // NOT: Sadece purchases listesi boş değilse ve hiç premium ürün bulunamadıysa false yap
            if (!hasPremium && purchases.isNotEmpty()) {
                billingScope.launch {
                    premiumManager.setPremiumStatus(false)
                    callback?.onAcknowledgeCompleted(false)
                }
            }
        }

        /**
         * Satın almayı onaylar (Google Play gereksinimi).
         * @param purchase Onaylanacak satın alma
         * @param isRestore true ise, restore işlemi olduğunu belirtir ve uygun mesaj gösterilir.
         * @param showRestoreToast true ise, restore toast mesajını gösterir. false ise sadece premium durumunu günceller.
         * @param callback Acknowledge işleminin tamamlanmasını bildirmek için callback.
         */
        private fun acknowledgePurchase(
            purchase: Purchase,
            isRestore: Boolean = false,
            showRestoreToast: Boolean = true,
            callback: AcknowledgeCallback? = null,
        ) {
            val billingClient = this.billingClient ?: run {
                Timber.e("acknowledgePurchase: BillingClient null")
                billingScope.launch {
                    callback?.onAcknowledgeCompleted(false)
                }
                return
            }

            val acknowledgePurchaseParams =
                AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()

            billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    // Satın alma onaylandıktan sonra premium durumunu güncelle
                    billingScope.launch {
                        premiumManager.setPremiumStatus(true)
                        callback?.onAcknowledgeCompleted(true)
                        // Restore durumunda ve toast gösterilmesi gerekiyorsa restore mesajını göster, değilse aktif edildi mesajını göster
                        if (isRestore && showRestoreToast) {
                            showPremiumRestoredMessage()
                        } else if (!isRestore) {
                            showPremiumActivatedMessage()
                        }
                    }
                } else {
                    // Detaylı hata loglama - Google Play taraflı sorun olup olmadığını anlamak için
                    Timber.e("Satın alma onaylama hatası:")
                    Timber.e("  - Response Code: ${billingResult.responseCode}")
                    Timber.e("  - Debug Message: ${billingResult.debugMessage}")
                    Timber.e("  - PurchaseToken: ${purchase.purchaseToken}")
                    Timber.e("  - Products: ${purchase.products}")
                    val purchaseState =
                        try {
                            purchase.purchaseState
                        } catch (e: Exception) {
                            null
                        }
                    Timber.e("  - PurchaseState: $purchaseState")
                    Timber.e("  - IsAcknowledged: ${purchase.isAcknowledged}")

                    // Google Play ürünü "satın alındı" olarak gösteriyorsa bile acknowledge başarısız olabilir
                    // Bu durumda premium durumunu yine de true yap (defensive programming)
                    if (purchaseState == null || purchaseState == Purchase.PurchaseState.PURCHASED) {
                        Timber.w("Acknowledge başarısız ama PurchaseState PURCHASED veya null - Premium durumu yine de true yapılıyor")
                        billingScope.launch {
                            premiumManager.setPremiumStatus(true)
                            callback?.onAcknowledgeCompleted(true)
                        }
                    } else {
                        // Hata durumunda sinyal gönder (başarısız olduğunu belirtmek için)
                        billingScope.launch {
                            callback?.onAcknowledgeCompleted(false)
                        }
                    }
                }
            }
        }

        /**
         * Premium aktif edildi mesajını gösterir.
         */
        private fun showPremiumActivatedMessage() {
            val message = context.getString(R.string.toast_premium_activated)
            // UI thread'de toast göster
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }

        /**
         * Premium geri yüklendi mesajını gösterir.
         */
        private fun showPremiumRestoredMessage() {
            val message = context.getString(R.string.toast_premium_restored)
            // UI thread'de toast göster
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }