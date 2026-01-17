package com.pnr.tv.util.error

import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.analytics.ktx.logEvent
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.ktx.Firebase
import timber.log.Timber

/**
 * Hata loglama işlemleri için utility sınıfı.
 * Crashlytics ve Firebase Analytics'e güvenli hata bilgileri ekler (PII içermez).
 */
object ErrorLogger {
    /**
     * Crashlytics ve Firebase Analytics'e güvenli hata bilgileri ekler.
     * NOT: Kullanıcı verileri (username, password, DNS, email) kesinlikle eklenmez.
     *
     * @param exception Exception
     * @param errorCategory Hata kategorisi (ErrorCategory'dan)
     * @param errorContext Hatanın oluştuğu context (ErrorContext veya basit string)
     */
    fun recordError(
        exception: Throwable,
        errorCategory: String,
        errorContext: Any? = null,
    ) {
        try {
            val crashlytics = FirebaseCrashlytics.getInstance()

            // Hata kategorisi
            crashlytics.setCustomKey("error_category", errorCategory)

            // Hata oluştuğu context (ErrorContext veya basit string)
            val contextString =
                when (errorContext) {
                    is ErrorContext -> {
                        // Detaylı context bilgisi
                        crashlytics.setCustomKey("error_context", errorContext.toCrashlyticsContext())
                        errorContext.repository?.let {
                            crashlytics.setCustomKey("error_repository", it)
                        }
                        errorContext.operation?.let {
                            crashlytics.setCustomKey("error_operation", it)
                        }
                        errorContext.categoryId?.let {
                            crashlytics.setCustomKey("error_category_id", it)
                        }
                        errorContext.contentId?.let {
                            crashlytics.setCustomKey("error_content_id", it)
                        }
                        errorContext.toCrashlyticsContext()
                    }
                    is String -> {
                        // Basit string context (geriye dönük uyumluluk)
                        crashlytics.setCustomKey("error_context", errorContext)
                        errorContext
                    }
                    else -> null
                }

            // Exception tipi
            val exceptionType = exception.javaClass.simpleName
            crashlytics.setCustomKey("exception_type", exceptionType)

            // Exception'ı kaydet
            crashlytics.recordException(exception)

            // Log mesajı (kişisel bilgi içermez)
            crashlytics.log("Error in $errorCategory${contextString?.let { " at $it" } ?: ""}: $exceptionType")

            // Firebase Analytics'e error event gönder
            recordErrorToAnalytics(errorCategory, exceptionType, contextString)
        } catch (e: Exception) {
            // Crashlytics'e kaydetme hatası - sessizce geç
            Timber.e(e, "Crashlytics'e hata kaydedilemedi")
        }
    }

    /**
     * Firebase Analytics'e error event kaydeder.
     * NOT: Kullanıcı verileri (username, password, DNS, email) kesinlikle eklenmez.
     *
     * @param errorCategory Hata kategorisi
     * @param exceptionType Exception tipi
     * @param errorContext Hata context string'i (opsiyonel)
     */
    private fun recordErrorToAnalytics(
        errorCategory: String,
        exceptionType: String,
        errorContext: String? = null,
    ) {
        try {
            val analytics = Firebase.analytics
            analytics.logEvent("error_occurred") {
                param("error_category", errorCategory)
                param("exception_type", exceptionType)
                errorContext?.let {
                    param("error_context", it)
                }
            }
        } catch (e: Exception) {
            // Analytics'e kaydetme hatası - sessizce geç
            Timber.e(e, "Firebase Analytics'e hata kaydedilemedi")
        }
    }

    /**
     * HTTP hatası için özel loglama yapar.
     *
     * @param exception HttpException
     * @param errorContext Hata context (opsiyonel)
     */
    fun recordHttpError(
        exception: retrofit2.HttpException,
        errorContext: Any? = null,
    ) {
        try {
            val crashlytics = FirebaseCrashlytics.getInstance()
            val httpStatusCode = exception.code()
            crashlytics.setCustomKey("http_status_code", httpStatusCode)

            // Firebase Analytics'e HTTP error event gönder
            val analytics = Firebase.analytics
            analytics.logEvent("http_error_occurred") {
                param("http_status_code", httpStatusCode.toLong())
                param("error_category", ErrorCategory.HTTP_ERROR)
            }

            // Genel hata kaydı
            recordError(exception, ErrorCategory.HTTP_ERROR, errorContext)
        } catch (e: Exception) {
            // Sessizce geç
            Timber.e(e, "HTTP hatası kaydedilemedi")
        }
    }
}
