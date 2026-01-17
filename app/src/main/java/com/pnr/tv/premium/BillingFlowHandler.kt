package com.pnr.tv.premium

import android.app.Activity
import android.content.Context
import android.widget.Toast
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.QueryProductDetailsParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Premium ürün satın alma akışını (billing flow) yönetir.
 * Ürün sorgulama ve launchBillingFlow işlemlerini gerçekleştirir.
 */
class BillingFlowHandler(
    private val billingClient: BillingClient?,
    private val context: Context,
    private val billingScope: CoroutineScope,
    private val premiumProductId: String,
    private val productType: String,
) {
        private val _purchaseFlowError = MutableSharedFlow<String>()
        val purchaseFlowError: SharedFlow<String> = _purchaseFlowError.asSharedFlow()

        /**
         * Premium ürünü satın almak için billing flow'u başlatır.
         * @param activity Activity referansı (launchBillingFlow için gerekli)
         * @param onQueryPurchasesCallback Satın alımları sorgulama işlemi için callback (ITEM_ALREADY_OWNED durumunda).
         */
        fun launchPurchaseFlow(
            activity: Activity,
            onQueryPurchasesCallback: (() -> Unit)? = null,
        ) {
            Timber.d("launchPurchaseFlow çağrıldı")
            val billingClient =
                this.billingClient ?: run {
                    Timber.e("BillingClient henüz başlatılmamış")
                    return
                }

            Timber.d("Ürün detayları sorgulanıyor - Product ID: $premiumProductId, Type: $productType")
            val productList =
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(premiumProductId)
                        .setProductType(productType)
                        .build(),
                )

            val params =
                QueryProductDetailsParams.newBuilder()
                    .setProductList(productList)
                    .build()

            billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
                Timber.d(
                    "queryProductDetailsAsync callback - Response Code: ${billingResult.responseCode}, Product Count: ${productDetailsList.size}",
                )

                when (billingResult.responseCode) {
                    BillingClient.BillingResponseCode.OK -> {
                        if (productDetailsList.isNotEmpty()) {
                            val productDetails = productDetailsList[0]
                            Timber.d("Ürün detayları alındı: ${productDetails.productId}")

                            val productDetailsParamsList =
                                listOf(
                                    BillingFlowParams.ProductDetailsParams.newBuilder()
                                        .setProductDetails(productDetails)
                                        .build(),
                                )

                            val flowParams =
                                BillingFlowParams.newBuilder()
                                    .setProductDetailsParamsList(productDetailsParamsList)
                                    .build()

                            Timber.d("Billing flow başlatılıyor...")
                            val responseCode = billingClient.launchBillingFlow(activity, flowParams).responseCode

                            when (responseCode) {
                                BillingClient.BillingResponseCode.OK -> {
                                    Timber.d("Billing flow başarıyla başlatıldı")
                                }
                                BillingClient.BillingResponseCode.USER_CANCELED -> {
                                    Timber.d("Kullanıcı satın alma akışını iptal etti")
                                }
                                BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                                    Timber.w("Ürün zaten satın alınmış, satın alımları sorgulanıyor")
                                    // Hemen toast mesajını göster ve sonra satın alımları sorgula (toast tekrar gösterilmesin)
                                    showPremiumRestoredMessage(activity)
                                    // Google Play önbelleğinin güncellenmesi için 1 saniye gecikme ile sorgula
                                    billingScope.launch {
                                        delay(1000)
                                        Timber.d("ITEM_ALREADY_OWNED (launchPurchaseFlow) - Gecikme sonrası queryPurchases çağrılıyor")
                                        onQueryPurchasesCallback?.invoke()
                                    }
                                }
                                else -> {
                                    Timber.e("Billing flow başlatma hatası (Kod: $responseCode): ${billingResult.debugMessage}")
                                }
                            }
                        } else {
                            Timber.e("Ürün detayları listesi boş - Product ID bulunamadı: $premiumProductId")
                            activity.runOnUiThread {
                                Toast.makeText(
                                    activity,
                                    context.getString(com.pnr.tv.R.string.error_billing_product_not_found, premiumProductId),
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        }
                    }
                    BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> {
                        Timber.e("BILLING_UNAVAILABLE - Google Play Services mevcut değil")
                        // Hata mesajını callback ile bildir
                        billingScope.launch {
                            _purchaseFlowError.emit(context.getString(com.pnr.tv.R.string.error_billing_unavailable))
                        }
                    }
                    BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE -> {
                        Timber.e("SERVICE_UNAVAILABLE - Google Play Services geçici olarak kullanılamıyor")
                        billingScope.launch {
                            _purchaseFlowError.emit(context.getString(com.pnr.tv.R.string.error_billing_service_unavailable))
                        }
                    }
                    BillingClient.BillingResponseCode.ITEM_NOT_OWNED -> {
                        Timber.e("ITEM_NOT_OWNED - Ürün bulunamadı veya satın alınmamış")
                        billingScope.launch {
                            _purchaseFlowError.emit(context.getString(com.pnr.tv.R.string.error_billing_item_not_owned))
                        }
                    }
                    else -> {
                        Timber.e("Ürün detayları alınamadı (Kod: ${billingResult.responseCode}): ${billingResult.debugMessage}")
                        billingScope.launch {
                            _purchaseFlowError.emit(context.getString(com.pnr.tv.R.string.error_billing_product_details_failed, billingResult.debugMessage ?: ""))
                        }
                    }
                }
            }
        }

        /**
         * Premium geri yüklendi mesajını gösterir.
         */
        private fun showPremiumRestoredMessage(activity: Activity) {
            val message = context.getString(com.pnr.tv.R.string.toast_premium_restored)
            activity.runOnUiThread {
                Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
            }
        }
    }