package com.pnr.tv.util.network

import android.content.Context
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import com.google.android.gms.common.GooglePlayServicesNotAvailableException
import com.google.android.gms.common.GooglePlayServicesRepairableException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google Play Services Advertising ID'yi almak için helper sınıfı.
 */
@Singleton
class AdvertisingIdHelper
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        /**
         * Advertising ID'yi alır ve Firebase Crashlytics'e gönderir.
         * Bu işlem arka plan thread'inde çalışmalıdır.
         */
        suspend fun getAdvertisingId(): String? {
            return withContext(Dispatchers.IO) {
                try {
                    val adInfo = AdvertisingIdClient.getAdvertisingIdInfo(context)
                    val advertisingId = adInfo?.id

                    if (advertisingId != null) {
                        Timber.d("Advertising ID alındı: ${advertisingId.take(10)}...")
                        // Firebase Crashlytics'e gönder (opsiyonel)
                        try {
                            com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance()
                                .setCustomKey("advertising_id", advertisingId)
                        } catch (e: Exception) {
                            Timber.w(e, "Firebase Crashlytics'e advertising ID gönderilemedi")
                        }
                    }

                    advertisingId
                } catch (e: GooglePlayServicesNotAvailableException) {
                    Timber.w(e, "Google Play Services mevcut değil")
                    null
                } catch (e: GooglePlayServicesRepairableException) {
                    Timber.w(e, "Google Play Services onarılabilir hata")
                    null
                } catch (e: Exception) {
                    Timber.e(e, "Advertising ID alınamadı")
                    null
                }
            }
        }
    }
