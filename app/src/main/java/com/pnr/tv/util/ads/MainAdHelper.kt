package com.pnr.tv.util.ads

import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.pnr.tv.premium.AdManager
import com.pnr.tv.premium.PremiumManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * MainActivity için banner reklam yönetimi helper sınıfı.
 * Reklam gösterimi ve lifecycle yönetimini merkezi bir şekilde yönetir.
 * Premium kontrolü ve crash-proof lifecycle yönetimi sağlar.
 */
class MainAdHelper
    @Inject
    constructor(
        private val premiumManager: PremiumManager,
        private val adManager: AdManager,
    ) {
        /**
         * Banner reklamı yükler ve premium durumuna göre gösterir/gizler.
         * Crash-proof lifecycle yönetimi ile güvenli reklam yönetimi sağlar.
         *
         * @param lifecycleScope Coroutine scope for premium state observation
         * @param adView Banner reklamın gösterileceği AdView
         */
        fun setupBannerAd(
            lifecycleScope: CoroutineScope,
            adView: AdView?,
        ) {
            try {
                // Ad Unit ID'yi AdManager'dan al ve validasyon yap
                val adUnitId = adManager.getBannerAdUnitId()
                if (adUnitId.isBlank()) {
                    Timber.e("Ad Unit ID boş veya geçersiz - reklam gösterilmeyecek")
                    // Reklam alanını gizle
                    adView?.visibility = android.view.View.GONE
                    return
                }

                if (adView == null) {
                    Timber.w("AdView null - layout henüz hazır olmayabilir")
                    return
                }

                // Premium durumunu gözlemle
                lifecycleScope.launch {
                    premiumManager.isPremium().collectLatest { _ ->
                        try {
                            // Son kez premium kontrolü yap (race condition önleme)
                            val finalIsPremium = premiumManager.isPremiumSync()

                            if (finalIsPremium) {
                                // Premium kullanıcı - reklamı tamamen durdur ve temizle
                                safeHideAdView(adView)
                            } else {
                                // Premium değil - reklamı göster
                                safeShowAdView(adView, adUnitId)
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Reklam gösterimi/gizleme hatası")
                            // Hata durumunda reklamı gizle
                            try {
                                adView.visibility = android.view.View.GONE
                            } catch (ex: Exception) {
                                Timber.w(ex, "AdView gizlenirken hata")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "AdView kurulum hatası")
                // Hata durumunda reklam alanını gizle
                try {
                    adView?.visibility = android.view.View.GONE
                } catch (ex: Exception) {
                    Timber.w(ex, "AdView gizlenirken hata")
                }
            }
        }

        /**
         * AdView'u güvenli bir şekilde gizler ve temizler.
         */
        fun safeHideAdView(adView: AdView) {
            try {
                adView.visibility = android.view.View.GONE
                // Pause ve destroy işlemlerini try-catch ile koru
                try {
                    adView.pause()
                } catch (e: Exception) {
                    Timber.w(e, "AdView pause edilemedi (muhtemelen zaten destroy edilmiş)")
                }
                try {
                    adView.destroy()
                } catch (e: Exception) {
                    Timber.w(e, "AdView destroy edilemedi (muhtemelen zaten destroy edilmiş)")
                }
            } catch (e: Exception) {
                Timber.e(e, "AdView gizlenirken beklenmeyen hata")
            }
        }

        /**
         * AdView'u güvenli bir şekilde gösterir ve reklam yükler.
         */
        fun safeShowAdView(
            adView: AdView,
            adUnitId: String,
        ) {
            try {
                // AdView'un adUnitId'si zaten set edilmişse tekrar set etme (crash önleme)
                val currentAdUnitId = adView.adUnitId
                if (currentAdUnitId.isNullOrBlank()) {
                    // AdUnitId henüz set edilmemiş, güvenle set edebiliriz
                    adView.adUnitId = adUnitId
                } else if (currentAdUnitId != adUnitId) {
                    // AdUnitId zaten set edilmiş ve farklı bir ID gelmiş
                    // Google Ads SDK bir AdView instance'ına adUnitId'yi sadece bir kez set etmeye izin verir
                    // Bu yüzden mevcut ID ile devam ediyoruz ve sadece log yazıyoruz
                    Timber.w(
                        "AdView adUnitId zaten set edilmiş ve farklı: mevcut=$currentAdUnitId, yeni=$adUnitId - mevcut ID ile devam ediliyor",
                    )
                    // Mevcut ID ile devam et, yeni ID'yi set etmeye çalışma (crash önleme)
                    // AdView zaten çalışıyor, sadece görünürlüğü kontrol et
                } else {
                    // AdUnitId aynı, normal akışa devam et
                }

                // Reklam alanının boyutlarını önceden rezerve et (ekran zıplamasını önle)
                if (adView.visibility != android.view.View.VISIBLE) {
                    adView.visibility = android.view.View.VISIBLE
                }

                // AdListener ekle - sadece hataları logla
                adView.adListener =
                    object : AdListener() {
                        override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                            when (loadAdError.code) {
                                2 -> {
                                    // Network error - emülatörde normal, loglama yapma
                                }
                                else -> {
                                    // Diğer hatalar için kısa log
                                    Timber.e("MainAdHelper - Banner reklam yüklenemedi: Code ${loadAdError.code}")
                                }
                            }
                        }
                    }

                // Sadece reklam yüklenmemişse yükle
                try {
                    val adRequest = AdRequest.Builder().build()
                    adView.loadAd(adRequest)
                } catch (e: Exception) {
                    Timber.e(e, "MainAdHelper - Reklam yükleme hatası: ${e.message}")
                }
            } catch (e: Exception) {
                Timber.e(e, "AdView gösterilirken beklenmeyen hata")
            }
        }

        /**
         * AdView'u güvenli bir şekilde pause eder.
         * Lifecycle-aware ve crash-proof.
         */
        fun safePauseAdView(adView: AdView?) {
            try {
                if (adView != null && adView.visibility == android.view.View.VISIBLE) {
                    try {
                        adView.pause()
                    } catch (e: IllegalStateException) {
                        // AdView destroy edilmişse veya geçersiz durumda
                        Timber.w(e, "AdView pause edilemedi (muhtemelen destroy edilmiş veya geçersiz durumda)")
                    } catch (e: Exception) {
                        Timber.w(e, "AdView pause edilemedi")
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "AdView pause işlemi sırasında beklenmeyen hata")
            }
        }

        /**
         * AdView'u güvenli bir şekilde resume eder.
         * Premium kontrolü ve lifecycle-aware.
         */
        fun safeResumeAdView(
            lifecycleScope: CoroutineScope,
            adView: AdView?,
        ) {
            lifecycleScope.launch {
                try {
                    // Premium kontrolü - premium kullanıcılar için reklam resume edilmez
                    if (premiumManager.isPremiumSync()) {
                        return@launch
                    }

                    if (adView != null && adView.visibility == android.view.View.VISIBLE) {
                        try {
                            adView.resume()
                        } catch (e: IllegalStateException) {
                            // AdView destroy edilmişse veya geçersiz durumda
                            Timber.w(e, "AdView resume edilemedi (muhtemelen destroy edilmiş veya geçersiz durumda)")
                        } catch (e: Exception) {
                            Timber.w(e, "AdView resume edilemedi")
                        }
                    }
                } catch (e: Exception) {
                    Timber.w(e, "AdView resume işlemi sırasında beklenmeyen hata")
                }
            }
        }

        /**
         * AdView'u güvenli bir şekilde destroy eder.
         * Memory safety için kritik.
         */
        fun safeDestroyAdView(adView: AdView?) {
            try {
                if (adView != null) {
                    try {
                        // Önce pause et (eğer pause edilmemişse)
                        try {
                            adView.pause()
                        } catch (e: Exception) {
                            // Pause hatası önemli değil, destroy devam eder
                        }
                        // Sonra destroy et
                        adView.destroy()
                    } catch (e: IllegalStateException) {
                        // AdView zaten destroy edilmiş
                        Timber.w(e, "AdView zaten destroy edilmiş")
                    } catch (e: Exception) {
                        Timber.w(e, "AdView destroy edilemedi")
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "AdView destroy işlemi sırasında beklenmeyen hata")
            }
        }
    }
