package com.pnr.tv.premium

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.pnr.tv.BuildConfig
import com.pnr.tv.core.constants.UIConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ödüllü reklam (Rewarded Ad) yönetimi için manager sınıfı.
 * Adult filtresi geçici erişimi için kullanılır.
 */
@Singleton
class RewardedAdManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        // Test Ad Unit ID - Production'da gerçek ID kullanılacak
        private val TEST_REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"

        // Production Ad Unit ID - UIConstants'tan alınır
        private val PRODUCTION_REWARDED_AD_UNIT_ID = UIConstants.Ads.REWARDED_AD_UNIT_ID

        // Rewarded Ad instance
        private var rewardedAd: RewardedAd? = null
        private var isRewardedAdLoading: Boolean = false

        // Coroutine scope for ad loading
        private val adScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /**
         * Rewarded reklam için Ad Unit ID döndürür.
         */
        private fun getRewardedAdUnitId(): String {
            return try {
                val adUnitId =
                    if (BuildConfig.IS_PRODUCTION) {
                        PRODUCTION_REWARDED_AD_UNIT_ID
                    } else {
                        TEST_REWARDED_AD_UNIT_ID
                    }
                // AdUnit ID validasyonu
                if (adUnitId.isBlank() || !adUnitId.startsWith("ca-app-pub-")) {
                    Timber.e("Geçersiz Rewarded AdUnit ID: $adUnitId")
                    ""
                } else {
                    adUnitId
                }
            } catch (e: Exception) {
                Timber.e(e, "Rewarded AdUnit ID alınırken hata oluştu")
                ""
            }
        }

        /**
         * Ödüllü reklamı yükler.
         * Reklam yüklenmiş veya yükleniyorsa tekrar yükleme yapmaz.
         */
        fun loadRewardedAd() {
            // Zaten yüklenmiş veya yükleniyorsa tekrar yükleme
            if (rewardedAd != null || isRewardedAdLoading) {
                Timber.d("Rewarded reklam zaten yüklenmiş veya yükleniyor")
                return
            }

            val adUnitId = getRewardedAdUnitId()
            if (adUnitId.isBlank()) {
                Timber.e("Rewarded Ad Unit ID boş - reklam yüklenemeyecek")
                return
            }

            isRewardedAdLoading = true
            val adRequest = AdRequest.Builder().build()

            RewardedAd.load(
                context,
                adUnitId,
                adRequest,
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        Timber.d("Rewarded reklam başarıyla yüklendi")
                        rewardedAd = ad
                        isRewardedAdLoading = false

                        // Reklam gösterildikten sonra otomatik olarak temizle
                        ad.fullScreenContentCallback =
                            object : FullScreenContentCallback() {
                                override fun onAdDismissedFullScreenContent() {
                                    Timber.d("Rewarded reklam kapatıldı")
                                    rewardedAd = null
                                }

                                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                                    Timber.e("Rewarded reklam gösterilirken hata: ${error.message}")
                                    rewardedAd = null
                                }

                                override fun onAdShowedFullScreenContent() {
                                    Timber.d("Rewarded reklam gösterildi")
                                    // Reklam gösterildiğinde instance'ı temizle
                                    rewardedAd = null
                                }
                            }
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        Timber.e("Rewarded reklam yüklenemedi: ${error.message}, Code: ${error.code}")
                        rewardedAd = null
                        isRewardedAdLoading = false
                        // Hata durumunda kullanıcı deneyimini bozmadan devam et
                    }
                },
            )
        }

        /**
         * Ödüllü reklamı gösterir (eğer yüklüyse).
         *
         * @param activity Reklamın gösterileceği Activity
         * @param onUserEarnedReward Reklam izlendiğinde çalıştırılacak callback
         * @param onAdClosed Reklam kapandığında çalıştırılacak callback (önce onUserEarnedReward çağrılır)
         * @return Reklam gösterildiyse true, aksi halde false
         */
        fun showRewardedAd(
            activity: Activity,
            onUserEarnedReward: (() -> Unit)? = null,
            onAdClosed: (() -> Unit)? = null,
        ): Boolean {
            val ad = rewardedAd
            return if (ad != null) {
                Timber.d("Rewarded reklam gösteriliyor")
                // Reklam gösterildikten sonra callback'leri çağır
                val originalCallback = ad.fullScreenContentCallback
                ad.fullScreenContentCallback =
                    object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            Timber.d("Rewarded reklam kapatıldı")
                            originalCallback?.onAdDismissedFullScreenContent()
                            onAdClosed?.invoke()
                        }

                        override fun onAdFailedToShowFullScreenContent(error: AdError) {
                            Timber.e("Rewarded reklam gösterilirken hata: ${error.message}")
                            originalCallback?.onAdFailedToShowFullScreenContent(error)
                            onAdClosed?.invoke()
                        }

                        override fun onAdShowedFullScreenContent() {
                            originalCallback?.onAdShowedFullScreenContent()
                        }
                    }

                ad.show(
                    activity,
                ) { rewardItem: RewardItem ->
                    Timber.d("Kullanıcı ödül kazandı: ${rewardItem.amount} ${rewardItem.type}")
                    onUserEarnedReward?.invoke()
                }
                true
            } else {
                Timber.d("Rewarded reklam yüklenmemiş - gösterilmiyor")
                onAdClosed?.invoke()
                false
            }
        }

        /**
         * Rewarded reklamın yüklenip yüklenmediğini kontrol eder.
         */
        fun isRewardedAdLoaded(): Boolean {
            return rewardedAd != null
        }
    }
