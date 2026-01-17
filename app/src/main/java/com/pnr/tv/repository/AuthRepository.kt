package com.pnr.tv.repository

import android.content.Context
import com.pnr.tv.di.IptvRetrofit
import com.pnr.tv.network.XtreamApiService
import com.pnr.tv.util.error.ErrorHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import retrofit2.Retrofit
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Kullanıcı doğrulama (authentication) işlemlerini yöneten repository.
 * Xtream Codes API kullanarak kullanıcı bilgilerini doğrular.
 */
@Singleton
class AuthRepository
    @Inject
    constructor(
        @IptvRetrofit private val retrofitBuilder: Retrofit.Builder,
        @ApplicationContext private val context: Context,
    ) {
        /**
         * Kullanıcı bilgilerini sunucuya göndererek doğrular.
         *
         * @param dns IPTV sunucu DNS adresi (örn: https://example.com)
         * @param username Kullanıcı adı
         * @param password Şifre
         * @return Result<Boolean> - true ise doğrulama başarılı, false ise başarısız
         */
        suspend fun verifyUser(
            dns: String,
            username: String,
            password: String,
        ): Result<Boolean> {
            return try {
                // DNS'i normalize et (http/https ekle, sonunda / olmasın)
                val normalizedDns = normalizeDns(dns)

                // Retrofit instance'ı dinamik baseUrl ile oluştur
                val retrofit =
                    retrofitBuilder
                        .baseUrl(normalizedDns)
                        .build()

                // XtreamApiService oluştur
                val apiService = retrofit.create(XtreamApiService::class.java)

                // API çağrısı yap
                val response =
                    apiService.verifyUser(
                        username = username,
                        password = password,
                    )

                // Yanıtı kontrol et
                val userInfo = response.extractUserInfo()

                if (userInfo != null && userInfo.auth == 1) {
                    // Doğrulama başarılı
                    Result.Success(true)
                } else {
                    // Doğrulama başarısız - user_info yok veya auth != 1
                    Timber.tag("ADD_USER_FLOW").w("[AuthRepository] ❌ Doğrulama başarısız - userInfo: $userInfo, auth: ${userInfo?.auth}")
                    Result.Error(
                        message = context.getString(com.pnr.tv.R.string.error_user_invalid_credentials),
                        exception = null,
                    )
                }
            } catch (e: Exception) {
                // Hata durumu
                Timber.tag("ADD_USER_FLOW").e(e, "[AuthRepository] ❌ Exception yakalandı: ${e.javaClass.simpleName} - ${e.message}")
                ErrorHelper.createError(
                    exception = e,
                    context = context,
                    customMessage = context.getString(com.pnr.tv.R.string.error_user_verification_failed),
                )
            }
        }

        /**
         * DNS adresini normalize eder.
         * - http:// veya https:// ekler (yoksa)
         * - Sonundaki / karakterini kaldırır
         *
         * @param dns Ham DNS adresi
         * @return Normalize edilmiş DNS adresi
         */
        private fun normalizeDns(dns: String): String {
            var normalized = dns.trim()

            // http:// veya https:// yoksa ekle
            if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
                normalized = "https://$normalized"
            }

            // Sonundaki / karakterini kaldır
            normalized = normalized.trimEnd('/')

            // Base URL için sonuna / ekle
            return "$normalized/"
        }
    }
