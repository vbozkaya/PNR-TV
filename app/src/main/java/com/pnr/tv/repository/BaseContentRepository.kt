package com.pnr.tv.repository

import android.content.Context
import com.pnr.tv.di.IptvRetrofit
import com.pnr.tv.extensions.normalizeDnsUrl
import com.pnr.tv.network.ApiService
import com.pnr.tv.repository.Result.Success
import com.pnr.tv.repository.UserRepository
import com.pnr.tv.util.ErrorHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.firstOrNull
import retrofit2.HttpException
import retrofit2.Retrofit
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

/**
 * Tüm content repository'ler için ortak API çağrı mantığını içeren base sınıf.
 * Retrofit instance yönetimi ve safe API call'ları sağlar.
 */
open class BaseContentRepository(
    @IptvRetrofit protected val retrofitBuilder: Retrofit.Builder,
    protected val userRepository: UserRepository,
    @ApplicationContext protected val context: Context,
) {
        // Retrofit örnekleri için önbellek - Her DNS için tek bir ApiService
        private val apiServiceCache = mutableMapOf<String, ApiService>()

        /**
         * Kullanıcı bilgileriyle birlikte ApiService döndürür.
         * Önbellek kullanarak aynı DNS için tek bir instance oluşturur.
         */
        protected suspend fun getApiServiceWithUser(): Pair<ApiService, com.pnr.tv.db.entity.UserAccountEntity>? {
            val user = userRepository.currentUser.firstOrNull() ?: return null
            val dns = user.dns.trim()
            if (dns.isEmpty()) return null

            val baseUrl = dns.normalizeDnsUrl()

            // 1. Önbelleği kontrol et
            val cachedApiService = apiServiceCache[baseUrl]
            if (cachedApiService != null) {
                Timber.v("♻️ Retrofit önbellekten alındı: $baseUrl")
                return Pair(cachedApiService, user)
            }

            // 2. Önbellekte yoksa oluştur, ekle ve döndür
            Timber.d("🔨 Yeni Retrofit örneği oluşturuluyor: $baseUrl")
            val newApiService = retrofitBuilder.baseUrl(baseUrl).build().create(ApiService::class.java)
            apiServiceCache[baseUrl] = newApiService
            return Pair(newApiService, user)
        }

        /**
         * Güvenli API çağrısı yapar. Hata durumlarını yakalar ve Result döndürür.
         */
        internal suspend fun <T> safeApiCall(apiCall: suspend (ApiService, String, String) -> T): Result<T> {
            return try {
                val (apiService, user) = getApiServiceWithUser() ?: return ErrorHelper.createUserNotFoundError(context)
                Success(apiCall(apiService, user.username, user.password))
            } catch (e: HttpException) {
                ErrorHelper.createHttpError(e, context)
            } catch (e: IOException) {
                ErrorHelper.createNetworkError(e, context)
            } catch (e: Exception) {
                ErrorHelper.createUnexpectedError(e, context)
            }
        }
    }

