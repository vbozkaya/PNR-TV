package com.pnr.tv

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.HiltAndroidApp
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class PnrTvApplication : Application(), ImageLoaderFactory, Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory
    override fun onCreate() {
        super.onCreate()
        initializeCrashlytics()
        setupTimber()
    }

    /**
     * Uygulama boyunca kullanılacak merkezi ImageLoader'ı oluşturur.
     * İstek sayısı limitlendirilmiş (maksimum 1) bir ImageLoader sağlar.
     * Düşük limit, sunucunun istekleri reddetmesini önler ve daha güvenilir indirme sağlar.
     */
    override fun newImageLoader(): ImageLoader {
        // Özel bir Dispatcher oluştur - aynı anda maksimum 1 istek
        val dispatcher =
            Dispatcher().apply {
                maxRequests = 1
                maxRequestsPerHost = 1
            }

        // Özel OkHttpClient oluştur - limitli dispatcher ile
        val okHttpClient =
            OkHttpClient.Builder()
                .dispatcher(dispatcher)
                .build()

        // ImageLoader'ı özel OkHttpClient ile oluştur
        return ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .build()
    }

    /**
     * Firebase Crashlytics'i başlatır ve yapılandırır
     */
    private fun initializeCrashlytics() {
        val crashlytics = FirebaseCrashlytics.getInstance()
        
        // Kullanıcı bilgilerini ayarla (opsiyonel)
        // crashlytics.setUserId("user_id")
        
        // Custom key'ler ekle (opsiyonel)
        // crashlytics.setCustomKey("app_version", BuildConfig.VERSION_NAME)
        
        val isDebug = applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (isDebug) {
            // Debug modda Crashlytics'i devre dışı bırak (opsiyonel)
            // crashlytics.setCrashlyticsCollectionEnabled(false)
            Timber.d("🔥 Crashlytics başlatıldı (DEBUG mod)")
        } else {
            Timber.d("🔥 Crashlytics başlatıldı (PRODUCTION mod)")
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
            // Production için Crashlytics entegre edilmiş Timber Tree
            Timber.plant(CrashlyticsTree())
        }
    }

    /**
     * Timber Tree implementation that sends logs to Firebase Crashlytics
     */
    private class CrashlyticsTree : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
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
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(
                if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                    android.util.Log.DEBUG
                } else {
                    android.util.Log.ERROR
                }
            )
            .build()
}
