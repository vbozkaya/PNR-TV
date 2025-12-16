package com.pnr.tv.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import timber.log.Timber

/**
 * Network connectivity kontrolü için utility sınıfı.
 */
object NetworkUtils {
    /**
     * İnternet bağlantısı olup olmadığını kontrol eder.
     *
     * NOT: NET_CAPABILITY_VALIDATED kontrolü kaldırıldı çünkü gerçek TV cihazlarında
     * bu doğrulama yavaş olabilir veya başarısız olabilir. Sadece NET_CAPABILITY_INTERNET
     * ve transport kontrolü yeterlidir.
     *
     * @param context Context
     * @return true if connected, false otherwise
     */
    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

            // Internet capability kontrolü
            val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)

            // Transport kontrolü (WiFi, Ethernet veya Cellular)
            val hasTransport =
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)

            // NET_CAPABILITY_VALIDATED kontrolü kaldırıldı - gerçek TV'lerde sorun çıkarıyor
            // Sadece internet capability ve transport kontrolü yeterli
            hasInternet && hasTransport
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            networkInfo?.isConnected == true
        }
    }

    /**
     * Network durumunu loglar.
     */
    fun logNetworkStatus(context: Context) {
        val isAvailable = isNetworkAvailable(context)
        Timber.d("🌐 Network status: ${if (isAvailable) "Available" else "Unavailable"}")
    }

    /**
     * İnternet bağlantısı olup olmadığını kontrol eder (isNetworkAvailable için alias).
     * Crashlytics'te kullanım için.
     */
    fun isOnline(context: Context): Boolean {
        return isNetworkAvailable(context)
    }
}
