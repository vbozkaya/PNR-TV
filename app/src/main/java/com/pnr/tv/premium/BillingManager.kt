package com.pnr.tv.premium

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryPurchasesParams
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google Play Billing Library ile in-app purchase işlemlerini yönetir.
 * Ana koordinatör olarak bağlantı yönetimini üstlenir ve diğer işlemleri handler sınıflarına delege eder.
 */
@Singleton
class BillingManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val premiumManager: PremiumManager,
    ) {
        private var billingClient: BillingClient? = null
        private var connectionHandler: BillingConnectionHandler? = null
        private var purchaseProcessor: BillingPurchaseProcessor? = null
        private var flowHandler: BillingFlowHandler? = null
        private var flowErrorCollectionJob: Job? = null

        private val _billingConnectionState =
            MutableStateFlow<BillingConnectionHandler.BillingConnectionState>(
                BillingConnectionHandler.BillingConnectionState.DISCONNECTED,
            )
        val billingConnectionState: StateFlow<BillingConnectionHandler.BillingConnectionState> =
            _billingConnectionState.asStateFlow()

        private val _purchaseFlowError = MutableSharedFlow<String>()
        val purchaseFlowError: SharedFlow<String> = _purchaseFlowError.asSharedFlow()

        // Acknowledge işleminin tamamlanmasını beklemek için sinyal
        private val _acknowledgeCompleted = MutableSharedFlow<Boolean>()
        val acknowledgeCompleted: SharedFlow<Boolean> = _acknowledgeCompleted.asSharedFlow()

        private val billingScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        // Premium ürün ID'si - Google Play Console'da tanımlanmalı
        val PREMIUM_PRODUCT_ID = "pnrtvfullversion"

        // Product type: inapp (tek seferlik satın alma) veya subs (abonelik)
        val PRODUCT_TYPE = BillingClient.ProductType.INAPP

        private val purchasesUpdatedListener =
            PurchasesUpdatedListener { billingResult, purchases ->
                when (billingResult.responseCode) {
                    BillingClient.BillingResponseCode.OK -> {
                        purchases?.let {
                            purchaseProcessor?.handlePurchases(
                                purchases = it,
                                isRestore = false,
                                showRestoreToast = true,
                                callback = object : BillingPurchaseProcessor.AcknowledgeCallback {
                                    override fun onAcknowledgeCompleted(success: Boolean) {
                                        billingScope.launch {
                                            _acknowledgeCompleted.emit(success)
                                        }
                                    }
                                },
                            )
                        }
                    }
                    BillingClient.BillingResponseCode.USER_CANCELED -> {
                        // Kullanıcı satın alma işlemini iptal etti
                    }
                    BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                        Timber.d("ITEM_ALREADY_OWNED - Ürün zaten satın alınmış, mevcut satın alımlar sorgulanıyor")
                        // Google Play'in yerel önbelleği hemen güncellenmeyebilir, 1 saniye gecikme ile sorgula
                        billingScope.launch {
                            delay(1000) // 1 saniye gecikme - Google Play önbelleğinin güncellenmesi için
                            Timber.d("ITEM_ALREADY_OWNED - Gecikme sonrası queryPurchases çağrılıyor")
                            queryPurchases(isRestore = true, showRestoreToast = true)
                        }
                    }
                    else -> {
                        Timber.e("Satın alma hatası: ${billingResult.debugMessage}")
                    }
                }
            }

        /**
         * BillingClient'ı başlatır ve bağlantı kurar.
         * Asenkron olarak çalışır ve uygulamanın donmasını engeller.
         */
        fun initialize() {
            Timber.d("BillingManager.initialize() çağrıldı")

            // Eğer zaten bir billingClient varsa önce kapat
            try {
                billingClient?.endConnection()
            } catch (e: Exception) {
                Timber.w(e, "Eski BillingClient kapatılırken hata")
            }

            // Eski handler'ı temizle
            connectionHandler?.endConnection()

            billingClient =
                BillingClient.newBuilder(context)
                    .setListener(purchasesUpdatedListener)
                    .enablePendingPurchases()
                    .build()

            Timber.d("BillingClient oluşturuldu, bağlantı başlatılıyor...")

            // Handler'ları oluştur
            purchaseProcessor =
                BillingPurchaseProcessor(
                    billingClient = billingClient,
                    premiumManager = premiumManager,
                    context = context,
                    billingScope = billingScope,
                    premiumProductId = PREMIUM_PRODUCT_ID,
                )

            flowHandler =
                BillingFlowHandler(
                    billingClient = billingClient,
                    context = context,
                    billingScope = billingScope,
                    premiumProductId = PREMIUM_PRODUCT_ID,
                    productType = PRODUCT_TYPE,
                )

            // Önceki collection job'ı iptal et
            flowErrorCollectionJob?.cancel()

            // Flow handler'ın error flow'unu BillingManager'ın flow'una yönlendir
            flowErrorCollectionJob = billingScope.launch {
                flowHandler?.purchaseFlowError?.collect { error ->
                    _purchaseFlowError.emit(error)
                }
            }

            // Connection handler'ı oluştur ve bağlantıyı başlat
            connectionHandler =
                BillingConnectionHandler(
                    billingClient = billingClient,
                    onConnected = {
                        // Mevcut satın alımları kontrol et (uygulama başlangıcında restore olarak işaretle, toast gösterme)
                        queryPurchases(isRestore = true, showRestoreToast = false)
                    },
                )

            // Connection handler'ın state'ini BillingManager'ın state'ine yansıt
            billingScope.launch {
                connectionHandler?.connectionState?.collect { state ->
                    _billingConnectionState.value = state
                }
            }

            connectionHandler?.startConnection()
        }

        /**
         * Mevcut satın alımları sorgular ve premium durumunu günceller.
         * @param isRestore true ise, restore işlemi olduğunu belirtir ve uygun mesaj gösterilir.
         * @param showRestoreToast true ise, restore toast mesajını gösterir. false ise sadece premium durumunu günceller.
         */
        fun queryPurchases(
            isRestore: Boolean = false,
            showRestoreToast: Boolean = true,
        ) {
            val billingClient =
                this.billingClient ?: run {
                    Timber.e("queryPurchases: BillingClient null")
                    // BillingClient null ise acknowledgeCompleted sinyalini false olarak gönder
                    billingScope.launch {
                        _acknowledgeCompleted.emit(false)
                    }
                    return
                }

            val params =
                QueryPurchasesParams.newBuilder()
                    .setProductType(PRODUCT_TYPE)
                    .build()

            // billing-ktx:7.0.0 extension'ı kullanıldığında callback imzası: (BillingResult, List<Purchase>?) -> Unit
            billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
                when (billingResult.responseCode) {
                    BillingClient.BillingResponseCode.OK -> {
                        // Billing Library v7 standartlarına uygun olarak direkt purchases parametresini kullan
                        val purchasesList = purchases ?: emptyList()

                        if (purchasesList.isEmpty()) {
                            // Boş liste durumunda premium durumunu false olarak güncelle
                            billingScope.launch {
                                premiumManager.setPremiumStatus(false)
                                _acknowledgeCompleted.emit(false)
                            }
                        } else {
                            purchaseProcessor?.handlePurchases(
                                purchases = purchasesList,
                                isRestore = isRestore,
                                showRestoreToast = showRestoreToast,
                                callback = object : BillingPurchaseProcessor.AcknowledgeCallback {
                                    override fun onAcknowledgeCompleted(success: Boolean) {
                                        billingScope.launch {
                                            _acknowledgeCompleted.emit(success)
                                        }
                                    }
                                },
                            )
                        }
                    }
                    else -> {
                        Timber.e("Satın alma sorgulama hatası (Kod: ${billingResult.responseCode}): ${billingResult.debugMessage}")
                        // Hata durumunda acknowledgeCompleted sinyalini false olarak gönder
                        billingScope.launch {
                            _acknowledgeCompleted.emit(false)
                        }
                    }
                }
            }
        }

        /**
         * Premium ürünü satın almak için billing flow'u başlatır.
         */
        fun launchPurchaseFlow(activity: Activity) {
            if (billingConnectionState.value != BillingConnectionHandler.BillingConnectionState.CONNECTED) {
                Timber.e("BillingClient bağlı değil, durum: ${billingConnectionState.value}")
                return
            }

            flowHandler?.launchPurchaseFlow(
                activity = activity,
                onQueryPurchasesCallback = {
                    queryPurchases(isRestore = true, showRestoreToast = false)
                },
            )
        }

        /**
         * BillingClient bağlantısını yeniden dener.
         */
        fun retryConnection() {
            connectionHandler?.retryConnection()
        }

        /**
         * BillingClient'ı kapatır ve memory'yi temizler.
         * Memory safety için kritik - tüm referansları serbest bırakır.
         */
        fun endConnection() {
            // Flow error collection job'ı iptal et
            flowErrorCollectionJob?.cancel()
            flowErrorCollectionJob = null

            // Connection handler'ı kapat
            connectionHandler?.endConnection()
            connectionHandler = null

            // BillingClient'ı kapat
            try {
                billingClient?.endConnection()
            } catch (e: Exception) {
                Timber.w(e, "BillingClient endConnection hatası")
            }

            // Referansları temizle
            billingClient = null
            purchaseProcessor = null
            flowHandler = null
            _billingConnectionState.value = BillingConnectionHandler.BillingConnectionState.DISCONNECTED
        }
    }