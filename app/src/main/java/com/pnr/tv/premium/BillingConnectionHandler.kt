package com.pnr.tv.premium

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.math.min
import kotlin.random.Random

/**
 * BillingClient bağlantı yönetimi ve retry mantığını yönetir.
 * BillingManager'dan bağlantı yönetimi sorumluluklarını ayırmak için oluşturulmuştur.
 */
class BillingConnectionHandler(
    private val billingClient: BillingClient?,
    private val onConnected: () -> Unit,
) {
    private val _billingConnectionState = MutableStateFlow<BillingConnectionState>(BillingConnectionState.DISCONNECTED)
    val connectionState: StateFlow<BillingConnectionState> = _billingConnectionState.asStateFlow()

    private val billingScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Retry logic için state
    private var retryJob: Job? = null
    private var retryAttempt = 0
    private val maxRetryAttempts = 5
    private val baseRetryDelayMs = 1000L // 1 saniye
    private val maxRetryDelayMs = 30000L // 30 saniye

    /**
     * BillingClient bağlantısını başlatır.
     */
    fun startConnection() {
        // Önceki retry job'ı iptal et
        retryJob?.cancel()
        retryAttempt = 0

        Timber.d("BillingConnectionHandler.startConnection() çağrıldı")
        _billingConnectionState.value = BillingConnectionState.CONNECTING

        if (billingClient == null) {
            Timber.e("BillingClient null, bağlantı başlatılamıyor")
            _billingConnectionState.value = BillingConnectionState.ERROR
            return
        }

        Timber.d("BillingClient bağlantısı başlatılıyor...")
        billingClient.startConnection(createBillingClientStateListener())
    }

    /**
     * BillingClient bağlantısını kapatır ve retry işlemlerini iptal eder.
     */
    fun endConnection() {
        // Retry job'ı iptal et
        retryJob?.cancel()
        retryJob = null
        retryAttempt = 0
        _billingConnectionState.value = BillingConnectionState.DISCONNECTED
    }

    /**
     * BillingClient bağlantısını yeniden dener.
     */
    fun retryConnection() {
        // Retry attempt'i sıfırla (başarılı bağlantı sonrası)
        retryAttempt = 0
        retryJob?.cancel()
        retryJob = null

        if (billingClient == null) {
            Timber.e("BillingClient null, retry başlatılamıyor")
            _billingConnectionState.value = BillingConnectionState.ERROR
            return
        }

        billingClient.startConnection(createBillingClientStateListener())
    }

    /**
     * BillingClientStateListener oluşturur.
     */
    private fun createBillingClientStateListener(): BillingClientStateListener {
        return object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                Timber.d(
                    "onBillingSetupFinished çağrıldı - Response Code: ${billingResult.responseCode}, Message: ${billingResult.debugMessage}",
                )
                when (billingResult.responseCode) {
                    BillingClient.BillingResponseCode.OK -> {
                        Timber.d("BillingClient bağlantısı başarılı")
                        _billingConnectionState.value = BillingConnectionState.CONNECTED
                        retryAttempt = 0 // Başarılı bağlantı sonrası sıfırla
                        onConnected()
                    }
                    BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> {
                        Timber.e("BillingClient hatası: BILLING_UNAVAILABLE - Google Play Services mevcut değil veya güncel değil")
                        _billingConnectionState.value = BillingConnectionState.ERROR
                    }
                    BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE -> {
                        Timber.e("BillingClient hatası: SERVICE_UNAVAILABLE - Google Play Services geçici olarak kullanılamıyor")
                        _billingConnectionState.value = BillingConnectionState.ERROR
                    }
                    BillingClient.BillingResponseCode.SERVICE_DISCONNECTED -> {
                        Timber.e("BillingClient hatası: SERVICE_DISCONNECTED - Bağlantı kesildi")
                        _billingConnectionState.value = BillingConnectionState.DISCONNECTED
                        // Exponential backoff ile yeniden bağlanmayı dene
                        scheduleRetryConnection()
                    }
                    else -> {
                        Timber.e(
                            "BillingClient bağlantı hatası (Kod: ${billingResult.responseCode}): ${billingResult.debugMessage}",
                        )
                        _billingConnectionState.value = BillingConnectionState.ERROR
                        // Hata durumunda da retry dene (exponential backoff ile)
                        scheduleRetryConnection()
                    }
                }
            }

            override fun onBillingServiceDisconnected() {
                Timber.w("BillingClient bağlantısı kesildi - exponential backoff ile yeniden denenecek")
                _billingConnectionState.value = BillingConnectionState.DISCONNECTED
                // Exponential backoff ile yeniden bağlanmayı dene
                scheduleRetryConnection()
            }
        }
    }

    /**
     * Exponential backoff ile BillingClient bağlantısını yeniden dener.
     */
    private fun scheduleRetryConnection() {
        // Önceki retry job'ı iptal et
        retryJob?.cancel()

        // Maksimum deneme sayısını kontrol et
        if (retryAttempt >= maxRetryAttempts) {
            Timber.e("BillingClient yeniden bağlantı denemeleri tükendi (max: $maxRetryAttempts)")
            retryAttempt = 0
            _billingConnectionState.value = BillingConnectionState.ERROR
            return
        }

        // Exponential backoff delay hesapla
        val delayMs = calculateExponentialBackoffDelay(retryAttempt, baseRetryDelayMs, maxRetryDelayMs)
        retryAttempt++

        Timber.d("BillingClient yeniden bağlantı denemesi $retryAttempt/$maxRetryAttempts - ${delayMs}ms sonra")

        retryJob =
            billingScope.launch {
                delay(delayMs)
                retryConnection()
            }
    }

    /**
     * Exponential backoff delay hesaplar.
     */
    private fun calculateExponentialBackoffDelay(
        attempt: Int,
        baseDelayMs: Long,
        maxDelayMs: Long,
    ): Long {
        // Exponential backoff: baseDelay * 2^attempt
        val exponentialDelay = baseDelayMs * (1 shl attempt)

        // Jitter ekle (rastgele gecikme, 0 ile 500ms arası)
        val jitter = Random.nextLong(0, 500)

        // Maksimum delay'i aşmamak için min kullan
        return min(exponentialDelay + jitter, maxDelayMs)
    }

    enum class BillingConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        ERROR,
    }
}
