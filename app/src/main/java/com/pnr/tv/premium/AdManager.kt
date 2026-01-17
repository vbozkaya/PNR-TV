package com.pnr.tv.premium

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.pnr.tv.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AdMob reklam yönetimi için manager sınıfı.
 * Premium kullanıcılar için reklamları göstermez.
 * Android TV için banner ve interstitial reklamlar desteklenir.
 */
@Singleton
class AdManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val premiumManager: PremiumManager,
    ) {
        // Test Ad Unit ID'leri - Production'da gerçek ID'lerle değiştirilmeli
        // Test ID'leri: https://developers.google.com/admob/android/test-ads
        private val TEST_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
        private val TEST_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"

        // Production Ad Unit ID'leri - Google AdMob Console'dan alınan gerçek ID'ler
        private val PRODUCTION_BANNER_AD_UNIT_ID = "ca-app-pub-8479210484809920/7876750236"
        private val PRODUCTION_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-8479210484809920/6724640037"

        // Interstitial Ad instance
        private var interstitialAd: InterstitialAd? = null
        private var isInterstitialAdLoading: Boolean = false

        // Coroutine scope for premium checks
        private val adScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /**
         * Banner reklam için Ad Unit ID döndürür.
         * Güvenli bir şekilde validasyon yapar ve hatalı ID'lerde boş string döndürür.
         */
        fun getBannerAdUnitId(): String {
            return try {
                val adUnitId =
                    if (BuildConfig.IS_PRODUCTION) {
                        PRODUCTION_BANNER_AD_UNIT_ID
                    } else {
                        TEST_BANNER_AD_UNIT_ID
                    }
                // AdUnit ID validasyonu
                if (adUnitId.isBlank() || !adUnitId.startsWith("ca-app-pub-")) {
                    Timber.e("Geçersiz AdUnit ID: $adUnitId")
                    ""
                } else {
                    adUnitId
                }
            } catch (e: Exception) {
                Timber.e(e, "AdUnit ID alınırken hata oluştu")
                ""
            }
        }

        /**
         * Interstitial reklam için Ad Unit ID döndürür.
         */
        fun getInterstitialAdUnitId(): String {
            return try {
                val adUnitId =
                    if (BuildConfig.IS_PRODUCTION) {
                        PRODUCTION_INTERSTITIAL_AD_UNIT_ID
                    } else {
                        TEST_INTERSTITIAL_AD_UNIT_ID
                    }
                if (adUnitId.isBlank() || !adUnitId.startsWith("ca-app-pub-")) {
                    Timber.e("Geçersiz Interstitial AdUnit ID: $adUnitId")
                    ""
                } else {
                    adUnitId
                }
            } catch (e: Exception) {
                Timber.e(e, "Interstitial AdUnit ID alınırken hata oluştu")
                ""
            }
        }

        /**
         * Interstitial reklamı arka planda önceden yükler.
         * Premium kullanıcılar için reklam yüklenmez.
         */
        fun preloadInterstitialAd() {
            // Premium kontrolü - coroutine scope içinde kontrol et
            adScope.launch {
                try {
                    val isPremium = premiumManager.isPremiumSync()
                    if (isPremium) {
                        Timber.d("Premium kullanıcı - Interstitial reklam yüklenmeyecek")
                        return@launch
                    }

                    // Premium değilse reklam yükleme işlemini başlat
                    loadInterstitialAdInternal()
                } catch (e: Exception) {
                    Timber.e(e, "Premium kontrolü sırasında hata")
                    // Hata durumunda reklam yüklemeyi dene
                    loadInterstitialAdInternal()
                }
            }
        }

        /**
         * Interstitial reklam yükleme işlemini gerçekleştirir.
         */
        private fun loadInterstitialAdInternal() {
            // Zaten yüklenmiş veya yükleniyorsa tekrar yükleme
            if (interstitialAd != null || isInterstitialAdLoading) {
                Timber.d("Interstitial reklam zaten yüklenmiş veya yükleniyor")
                return
            }

            val adUnitId = getInterstitialAdUnitId()
            if (adUnitId.isBlank()) {
                Timber.e("Interstitial Ad Unit ID boş - reklam yüklenemeyecek")
                return
            }

            isInterstitialAdLoading = true
            val adRequest = AdRequest.Builder().build()

            InterstitialAd.load(
                context,
                adUnitId,
                adRequest,
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        Timber.d("Interstitial reklam başarıyla yüklendi")
                        interstitialAd = ad
                        isInterstitialAdLoading = false

                        // Reklam kapandıktan sonra otomatik olarak tekrar yükle
                        ad.fullScreenContentCallback =
                            object : FullScreenContentCallback() {
                                override fun onAdDismissedFullScreenContent() {
                                    Timber.d("Interstitial reklam kapatıldı - tekrar yükleniyor")
                                    interstitialAd = null
                                    // Bir sonraki gösterim için tekrar yükle
                                    preloadInterstitialAd()
                                }

                                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                                    Timber.e("Interstitial reklam gösterilirken hata: ${error.message}")
                                    interstitialAd = null
                                    // Hata durumunda tekrar yükle
                                    preloadInterstitialAd()
                                }

                                override fun onAdShowedFullScreenContent() {
                                    Timber.d("Interstitial reklam gösterildi")
                                    // Reklam gösterildiğinde instance'ı temizle
                                    interstitialAd = null
                                }
                            }
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        Timber.e("Interstitial reklam yüklenemedi: ${error.message}, Code: ${error.code}")
                        interstitialAd = null
                        isInterstitialAdLoading = false
                        // Hata durumunda kullanıcı deneyimini bozmadan devam et
                    }
                },
            )
        }

        /**
         * Interstitial reklamı gösterir (eğer yüklüyse).
         * Premium kullanıcılar için reklam gösterilmez.
         * Premium kontrolü async yapıldığı için, eğer reklam yüklüyse gösterilir.
         * Premium kullanıcılar için reklam zaten yüklenmemiş olacaktır.
         *
         * @param activity Reklamın gösterileceği Activity
         * @param onAdClosed Reklam kapandıktan sonra çalıştırılacak callback
         * @return Reklam gösterildiyse true, aksi halde false
         */
        fun showInterstitialAd(
            activity: Activity,
            onAdClosed: (() -> Unit)? = null,
        ): Boolean {
            // Premium kontrolü - eğer reklam yüklenmemişse premium olabilir veya yüklenmemiştir
            // Reklam yüklüyse göster, yoksa callback'i çağır
            val ad = interstitialAd
            return if (ad != null) {
                Timber.d("Interstitial reklam gösteriliyor")
                // Reklam kapandıktan sonra callback'i çağır
                val originalCallback = ad.fullScreenContentCallback
                ad.fullScreenContentCallback =
                    object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            Timber.d("Interstitial reklam kapatıldı")
                            originalCallback?.onAdDismissedFullScreenContent()
                            onAdClosed?.invoke()
                        }

                        override fun onAdFailedToShowFullScreenContent(error: AdError) {
                            Timber.e("Interstitial reklam gösterilirken hata: ${error.message}")
                            originalCallback?.onAdFailedToShowFullScreenContent(error)
                            onAdClosed?.invoke()
                        }

                        override fun onAdShowedFullScreenContent() {
                            originalCallback?.onAdShowedFullScreenContent()
                        }
                    }
                ad.show(activity)
                true
            } else {
                Timber.d("Interstitial reklam yüklenmemiş - gösterilmiyor")
                onAdClosed?.invoke()
                false
            }
        }

        /**
         * Interstitial reklamın yüklenip yüklenmediğini kontrol eder.
         */
        fun isInterstitialAdLoaded(): Boolean {
            return interstitialAd != null
        }
    }
