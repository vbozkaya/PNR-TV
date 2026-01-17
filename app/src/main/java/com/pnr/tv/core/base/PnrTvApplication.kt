package com.pnr.tv.core.base

import android.app.Application
import android.content.Context
import androidx.multidex.MultiDex
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_UNSPECIFIED
import com.google.android.gms.ads.RequestConfiguration.TAG_FOR_UNDER_AGE_OF_CONSENT_UNSPECIFIED
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.pnr.tv.BuildConfig
import com.pnr.tv.util.network.AdvertisingIdHelper
import com.pnr.tv.util.CrashlyticsHelper
import com.pnr.tv.util.LifecycleTracker
import com.pnr.tv.util.ui.BackgroundManager
import com.pnr.tv.util.ui.LocaleHelper
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class PnrTvApplication : Application(), ImageLoaderFactory, Configuration.Provider {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.wrapContext(base))
        // MultiDex desteği - API 21-22 için gerekli (API 23+ native destekliyor)
        MultiDex.install(this)
    }

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var imageLoader: ImageLoader

    @Inject
    lateinit var advertisingIdHelper: AdvertisingIdHelper

    @Inject
    lateinit var billingManager: com.pnr.tv.premium.BillingManager

    override fun onCreate() {
        super.onCreate()
        initializeCrashlytics()
        setupTimber()
        // Lifecycle tracking'i başlat (TV'de arka plan sorunlarını tespit etmek için)
        LifecycleTracker.startTracking()
        // Periyodik Crashlytics rapor gönderimini başlat
        startPeriodicCrashlyticsReportSending()
        // AdMob'u önce başlat (reklam yüklemeden önce)
        initializeAdMob()
        // Reklam kimliğini al ve Firebase'e gönder
        initializeAdvertisingId()
        // Google Play Billing'i başlat ve mevcut satın alımları kontrol et
        initializeBilling()
        // LeakCanary kaldırıldı - TV'de sorun çıkarıyor
        // initializeLeakCanary()
    }

    /**
     * Sistem düşük bellek uyarısı verdiğinde çağrılır.
     * Arkaplan cache'ini temizleyerek bellek kullanımını azaltır.
     */
    override fun onLowMemory() {
        super.onLowMemory()
        Timber.tag("BACKGROUND").w("⚠️ Sistem düşük bellek uyarısı - Arkaplan cache temizleniyor")
        BackgroundManager.clearCache()
    }

    /**
     * Android 14+ için trim memory callback.
     * Farklı seviyelerde bellek temizliği yapar.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        when (level) {
            TRIM_MEMORY_RUNNING_CRITICAL,
            TRIM_MEMORY_COMPLETE,
            -> {
                // Kritik bellek durumu - arkaplan cache'ini temizle
                Timber.tag("BACKGROUND").w("⚠️ Kritik bellek durumu (level: $level) - Arkaplan cache temizleniyor")
                BackgroundManager.clearCache()
            }
            TRIM_MEMORY_MODERATE,
            TRIM_MEMORY_BACKGROUND,
            -> {
                // Orta seviye bellek durumu - log'la ama cache'i koru
            }
        }
    }

    /**
     * Uygulama boyunca kullanılacak merkezi ImageLoader'ı döndürür.
     * Hilt'ten inject edilen ImageLoader'ı kullanır.
     * CoilImageLoaderModule'de yapılandırılmıştır.
     */
    override fun newImageLoader(): ImageLoader {
        return imageLoader
    }

    /**
     * Firebase Crashlytics'i başlatır ve yapılandırır
     */
    private fun initializeCrashlytics() {
        val crashlytics = FirebaseCrashlytics.getInstance()

        val isDebug = applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0

        // Crashlytics collection durumunu ayarla
        // Release build'de varsayılan olarak aktif, debug build'de manuel aktif etmeliyiz
        try {
            crashlytics.setCrashlyticsCollectionEnabled(true)
            Timber.i("✅ Crashlytics collection aktif edildi (debug: $isDebug)")

            // Test exception gönder - Crashlytics'in çalışıp çalışmadığını test et
            if (isDebug) {
                try {
                    crashlytics.recordException(Exception("Crashlytics Test - App Startup"))
                    crashlytics.log("✅ Crashlytics başarıyla başlatıldı - Test log")
                    Timber.i("✅ Crashlytics test exception kaydedildi")
                } catch (e: Exception) {
                    Timber.e(e, "❌ Crashlytics test exception kaydedilemedi")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "❌ Crashlytics collection durumu ayarlanamadı")
        }

        // Custom key'ler ekle - her crash'te bu bilgiler gönderilecek
        // NOT: Kullanıcı verileri (PII) kesinlikle eklenmez (username, password, DNS, email, vb.)
        try {
            crashlytics.setCustomKey("app_version", BuildConfig.VERSION_NAME)
            crashlytics.setCustomKey("version_code", BuildConfig.VERSION_CODE)
            crashlytics.setCustomKey("build_type", if (isDebug) "debug" else "release")
            crashlytics.setCustomKey("package_name", packageName)

            // Cihaz bilgileri (güvenli, kişisel bilgi içermez)
            crashlytics.setCustomKey("device_model", android.os.Build.MODEL)
            crashlytics.setCustomKey("android_version", android.os.Build.VERSION.SDK_INT)
            crashlytics.setCustomKey("device_manufacturer", android.os.Build.MANUFACTURER)
        } catch (e: Exception) {
            Timber.e(e, "Crashlytics custom key'ler ayarlanırken hata oluştu")
        }

        // Önceki default handler'ı sakla
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        // Uncaught exception handler ekle - yakalanmayan tüm exception'ları Firebase'e gönder
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            try {
                // Önce Crashlytics'e gönder
                crashlytics.recordException(exception)

                // Custom log ekle
                crashlytics.log("Uncaught exception in thread: ${thread.name}")
                crashlytics.setCustomKey("crash_thread_name", thread.name)
                crashlytics.setCustomKey("crash_timestamp", System.currentTimeMillis())

                // CRITICAL: Crash anında raporları hemen gönder
                // Crashlytics varsayılan olarak raporları bir sonraki açılışta gönderir,
                // ancak uygulama çökünce ve bir sonraki açılışta da çökerse raporlar hiç gönderilmeyebilir
                // Bu yüzden crash anında göndermeyi denememiz gerekiyor
                try {
                    // sendUnsentReports() çağrısı asenkron çalışır, ANR yapmaz
                    crashlytics.sendUnsentReports()
                } catch (e: Exception) {
                    // Gönderim hatası olursa sessizce geç - en azından exception kaydedildi
                    Timber.e(e, "Crashlytics: sendUnsentReports() başarısız")
                }
            } catch (e: Exception) {
                // Crashlytics'e gönderirken hata olursa en azından log'la
                Timber.e(e, "Crashlytics: Exception gönderilemedi")
            }

            // Önceki handler'ı çağır (genellikle sistem default handler)
            defaultHandler?.uncaughtException(thread, exception)
        }

        // Uygulama başlatıldığında bekleyen raporları gönder
        try {
            crashlytics.sendUnsentReports()
        } catch (e: Exception) {
            Timber.w(e, "Crashlytics bekleyen raporlar gönderilemedi")
        }
    }

    /**
     * Periyodik olarak Crashlytics raporlarını gönderir.
     * Bu, uygulama çöktüğünde ve bir sonraki açılışta da çökerse
     * raporların kaybolmasını önler.
     *
     * Her 5 dakikada bir gönderilmesi planlanmıştır.
     */
    private fun startPeriodicCrashlyticsReportSending() {
        val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        applicationScope.launch {
            while (true) {
                try {
                    delay(5 * 60 * 1000L) // 5 dakika bekle
                    val crashlytics = FirebaseCrashlytics.getInstance()
                    crashlytics.sendUnsentReports()
                    Timber.d("Crashlytics: Periyodik rapor gönderimi denendi")
                } catch (e: Exception) {
                    Timber.w(e, "Crashlytics: Periyodik rapor gönderimi başarısız")
                }
            }
        }
    }

    /**
     * Reklam kimliğini alır ve Firebase'e gönderir.
     */
    private fun initializeAdvertisingId() {
        val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        applicationScope.launch {
            try {
                advertisingIdHelper.getAdvertisingId()
            } catch (e: Exception) {
                Timber.e(e, "Reklam kimliği başlatılamadı")
                // Advertising ID hatasını Crashlytics'e gönder
                try {
                    CrashlyticsHelper.recordNonFatalException(
                        exception = e,
                        category = "advertising",
                        priority = CrashlyticsHelper.Priority.LOW,
                        tag = CrashlyticsHelper.Tags.STARTUP,
                        errorContext = "PnrTvApplication.initializeAdvertisingId",
                    )
                } catch (ex: Exception) {
                    Timber.w(ex, "Crashlytics'e advertising hatası kaydedilemedi")
                }
            }
        }
    }

    /**
     * Google Play Billing'i başlatır ve mevcut satın alımları kontrol eder.
     * Uygulama her açıldığında kullanıcının premium ürüne sahip olup olmadığını sorgular.
     */
    private fun initializeBilling() {
        try {
            billingManager.initialize()
            Timber.d("BillingManager başarıyla başlatıldı")
        } catch (e: Exception) {
            Timber.e(e, "BillingManager başlatılamadı: ${e.message}")
            try {
                CrashlyticsHelper.recordNonFatalException(
                    exception = e,
                    category = "billing",
                    priority = CrashlyticsHelper.Priority.MEDIUM,
                    tag = CrashlyticsHelper.Tags.STARTUP,
                    errorContext = "PnrTvApplication.initializeBilling",
                )
            } catch (ex: Exception) {
                // Silent fail - Crashlytics hatası
                Timber.w(ex, "Crashlytics'e billing hatası kaydedilemedi")
            }
        }
    }

    /**
     * AdMob SDK'yı başlatır
     */
    private fun initializeAdMob() {
        try {
            // Debug build'de test modunu aktif et ve emülatörü test device olarak ekle
            val isDebug = applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
            if (isDebug) {
                // Emülatörün gerçek device ID'sini al (test device olarak eklemek için)
                val deviceId =
                    try {
                        android.provider.Settings.Secure.getString(
                            contentResolver,
                            android.provider.Settings.Secure.ANDROID_ID,
                        )
                    } catch (e: Exception) {
                        null
                    }

                val testDeviceIds = mutableListOf<String>()

                // Device ID varsa ekle
                deviceId?.let {
                    testDeviceIds.add(it)
                }

                // Emülatör kullanılıyorsa bilgilendir
                val isEmulator =
                    deviceId != null && (
                        deviceId.contains("9774d56d682e549c") ||
                            android.os.Build.FINGERPRINT.contains("generic") ||
                            android.os.Build.FINGERPRINT.contains("unknown") ||
                            android.os.Build.MODEL.contains("Emulator") ||
                            android.os.Build.MODEL.contains("Android SDK") ||
                            android.os.Build.MANUFACTURER.contains("Genymotion") ||
                            android.os.Build.HARDWARE.contains("goldfish") ||
                            android.os.Build.HARDWARE.contains("ranchu") ||
                            android.os.Build.PRODUCT.contains("sdk") ||
                            android.os.Build.BOARD.contains("unknown") ||
                            android.os.Build.BRAND.contains("generic") ||
                            android.os.Build.DEVICE.contains("generic")
                    )

                // Emülatör tespit edildi, sessizce devam et

                if (testDeviceIds.isNotEmpty()) {
                    val requestConfiguration =
                        RequestConfiguration.Builder()
                            .setTestDeviceIds(testDeviceIds)
                            .setTagForChildDirectedTreatment(TAG_FOR_CHILD_DIRECTED_TREATMENT_UNSPECIFIED)
                            .setTagForUnderAgeOfConsent(TAG_FOR_UNDER_AGE_OF_CONSENT_UNSPECIFIED)
                            .build()

                    MobileAds.setRequestConfiguration(requestConfiguration)
                }
            }

            MobileAds.initialize(this) { initializationStatus ->
                // Firebase Crashlytics'e sadece kritik durumları gönder
                try {
                    CrashlyticsHelper.setCustomKey("admob_sdk_initialized", true)
                    CrashlyticsHelper.setCustomKey("admob_adapter_count", initializationStatus.adapterStatusMap.size)
                    CrashlyticsHelper.setCustomKey("admob_is_debug_mode", isDebug)
                } catch (e: Exception) {
                    // Sessizce devam et
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "AdMob SDK başlatılamadı: ${e.message}")
            // Firebase Crashlytics'e sadece kritik hataları gönder
            try {
                CrashlyticsHelper.setCustomKey("admob_sdk_initialized", false)
                CrashlyticsHelper.recordNonFatalException(
                    exception = e,
                    category = "ads",
                    priority = CrashlyticsHelper.Priority.HIGH,
                    tag = CrashlyticsHelper.Tags.STARTUP,
                    errorContext = "PnrTvApplication.initializeAdMob",
                )
                // sendUnsentReports() çağrısı kaldırıldı - Crashlytics otomatik olarak gönderir
            } catch (ex: Exception) {
                Timber.w(ex, "Crashlytics'e AdMob hatası kaydedilemedi")
            }
        }
    }

    /**
     * Timber logging'i yapılandırır
     * Production'da Crashlytics ile entegre edilmiştir
     */
    private fun setupTimber() {
        val isDebug = applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (isDebug) {
            Timber.plant(Timber.DebugTree())
        } else {
            // Production için sadece Crashlytics kullan
            Timber.plant(CrashlyticsTree())
        }
    }

    // LeakCanary kaldırıldı - TV'de sorun çıkarıyor
    // İhtiyaç olursa sadece geliştirme sırasında manuel olarak eklenebilir
    /*
    private fun initializeLeakCanary() {
        // LeakCanary kaldırıldı
    }
     */

    /**
     * Timber Tree implementation that sends logs to Firebase Crashlytics
     */
    private class CrashlyticsTree : Timber.Tree() {
        override fun log(
            priority: Int,
            tag: String?,
            message: String,
            t: Throwable?,
        ) {
            val crashlytics = FirebaseCrashlytics.getInstance()

            // Sadece warning ve error seviyesindeki logları Crashlytics'e gönder
            if (priority >= android.util.Log.WARN) {
                // Log mesajını Crashlytics'e gönder
                crashlytics.log("[$tag] $message")

                // Exception varsa Crashlytics'e gönder
                if (t != null) {
                    crashlytics.recordException(t)
                } else if (priority >= android.util.Log.ERROR) {
                    // Error seviyesinde exception yoksa, mesajı exception olarak kaydet
                    crashlytics.recordException(Exception(message))
                }
            }
        }
    }

    /**
     * WorkManager konfigürasyonu
     * Hilt ile Worker dependency injection için gerekli
     */
    override val workManagerConfiguration: Configuration
        get() =
            Configuration.Builder()
                .setWorkerFactory(workerFactory)
                .setMinimumLoggingLevel(
                    if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                        android.util.Log.DEBUG
                    } else {
                        android.util.Log.ERROR
                    },
                )
                .build()
}
