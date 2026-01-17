package com.pnr.tv.util

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.pnr.tv.util.error.ErrorCategory
import com.pnr.tv.util.error.ErrorContext
import timber.log.Timber

/**
 * Firebase Crashlytics'i daha detaylı ve sınıflandırılmış şekilde kullanmak için helper sınıfı.
 *
 * Bu sınıf ile:
 * - Hataları kategorilere göre sınıflandırabilirsiniz
 * - Kullanıcı akışı bilgileri ekleyebilirsiniz
 * - Özel etiketler ve öncelik seviyeleri ekleyebilirsiniz
 * - Non-fatal exception'ları loglayabilirsiniz
 *
 * NOT: Kullanıcı verileri (PII) kesinlikle eklenmez (username, password, DNS, email, vb.)
 */
object CrashlyticsHelper {
    private val crashlytics = FirebaseCrashlytics.getInstance()

    /**
     * Hata öncelik seviyeleri - Crashlytics'te filtreleme için kullanılır
     */
    enum class Priority {
        LOW, // Düşük öncelik - bilgilendirme amaçlı
        MEDIUM, // Orta öncelik - dikkat gerektiren
        HIGH, // Yüksek öncelik - acil müdahale gerektiren
        CRITICAL, // Kritik - uygulama çökebilir
    }

    /**
     * Hata etiketleri - Crashlytics'te gruplama için kullanılır
     */
    object Tags {
        const val NETWORK = "network"
        const val DATABASE = "database"
        const val UI = "ui"
        const val PLAYER = "player"
        const val AUTH = "auth"
        const val CACHE = "cache"
        const val SYNC = "sync"
        const val BACKGROUND = "background"
        const val STARTUP = "startup"
        const val MEMORY = "memory"
    }

    /**
     * Detaylı hata kaydı yapar.
     *
     * @param exception Exception
     * @param category Hata kategorisi (ErrorCategory'dan)
     * @param priority Öncelik seviyesi
     * @param tag Etiket (Tags'dan)
     * @param errorContext Hata context'i (ErrorContext veya String)
     * @param userAction Kullanıcının yaptığı son işlem (opsiyonel)
     * @param screenName Hatanın oluştuğu ekran adı (opsiyonel)
     * @param additionalKeys Ek key-value çiftleri (PII içermez)
     */
    fun recordDetailedError(
        exception: Throwable,
        category: String = ErrorCategory.UNKNOWN_ERROR,
        priority: Priority = Priority.MEDIUM,
        tag: String? = null,
        errorContext: Any? = null,
        userAction: String? = null,
        screenName: String? = null,
        additionalKeys: Map<String, String> = emptyMap(),
    ) {
        try {
            // Temel bilgiler
            crashlytics.setCustomKey("error_category", category)
            crashlytics.setCustomKey("error_priority", priority.name)

            // Etiket ekle
            tag?.let {
                crashlytics.setCustomKey("error_tag", it)
            }

            // Kullanıcı akışı bilgileri
            userAction?.let {
                crashlytics.setCustomKey("user_action", it)
            }

            screenName?.let {
                crashlytics.setCustomKey("screen_name", it)
            }

            // ErrorContext bilgileri
            when (errorContext) {
                is ErrorContext -> {
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
                }
                is String -> {
                    crashlytics.setCustomKey("error_context", errorContext)
                }
            }

            // Ek bilgiler
            additionalKeys.forEach { (key, value) ->
                crashlytics.setCustomKey(key, value)
            }

            // Exception tipi
            crashlytics.setCustomKey("exception_type", exception.javaClass.simpleName)

            // Exception mesajı (PII kontrolü yapılmış olmalı)
            exception.message?.let {
                if (!containsPII(it)) {
                    crashlytics.setCustomKey("exception_message", it)
                }
            }

            // Stack trace'in ilk satırı (dosya ve satır bilgisi)
            exception.stackTrace.firstOrNull()?.let {
                crashlytics.setCustomKey("error_file", it.fileName ?: "unknown")
                crashlytics.setCustomKey("error_line", it.lineNumber)
            }

            // Exception'ı kaydet
            crashlytics.recordException(exception)

            // Detaylı log mesajı
            val logMessage =
                buildString {
                    append("[$priority] ")
                    tag?.let { append("[$it] ") }
                    append("$category")
                    errorContext?.let { append(" - $it") }
                    userAction?.let { append(" (User action: $it)") }
                    screenName?.let { append(" (Screen: $it)") }
                }
            crashlytics.log(logMessage)

            // CRITICAL ve HIGH öncelikli hatalar için hemen gönder
            // Diğer hatalar bir sonraki uygulama açılışında otomatik gönderilecek
            if (priority == Priority.CRITICAL || priority == Priority.HIGH) {
                try {
                    crashlytics.sendUnsentReports()
                } catch (e: Exception) {
                    Timber.w(e, "Crashlytics: Yüksek öncelikli hata raporu gönderilemedi")
                }
            }

            Timber.e(exception, logMessage)
        } catch (e: Exception) {
            Timber.e(e, "Crashlytics'e detaylı hata kaydedilemedi")
        }
    }

    /**
     * Non-fatal exception kaydeder (uygulama çökmeden devam eder).
     *
     * @param exception Exception
     * @param category Hata kategorisi
     * @param priority Öncelik seviyesi
     * @param tag Etiket
     * @param errorContext Hata context'i
     */
    fun recordNonFatalException(
        exception: Throwable,
        category: String = ErrorCategory.UNKNOWN_ERROR,
        priority: Priority = Priority.LOW,
        tag: String? = null,
        errorContext: Any? = null,
    ) {
        recordDetailedError(
            exception = exception,
            category = category,
            priority = priority,
            tag = tag,
            errorContext = errorContext,
        )
        // recordDetailedError içinde zaten sendUnsentReports() çağrılıyor
        // (CRITICAL ve HIGH öncelikli hatalar için)
    }

    /**
     * Kullanıcı akışı bilgisi ekler (hata oluşmadan önce).
     * Bu bilgiler sonraki hata raporlarında görünecektir.
     *
     * @param action Kullanıcının yaptığı işlem
     * @param screenName Ekran adı
     */
    fun setUserFlow(
        action: String,
        screenName: String? = null,
    ) {
        try {
            crashlytics.setCustomKey("last_user_action", action)
            screenName?.let {
                crashlytics.setCustomKey("current_screen", it)
            }
            crashlytics.log("User flow: $action${screenName?.let { " on $it" } ?: ""}")
        } catch (e: Exception) {
            Timber.e(e, "Crashlytics'e kullanıcı akışı bilgisi eklenemedi")
        }
    }

    /**
     * Özel log mesajı ekler (hata olmadan).
     *
     * @param message Log mesajı
     * @param level Log seviyesi (DEBUG, INFO, WARN, ERROR)
     */
    fun log(
        message: String,
        level: String = "INFO",
    ) {
        try {
            crashlytics.log("[$level] $message")
            when (level) {
                "ERROR" -> Timber.e(message)
                "WARN" -> Timber.w(message)
                "INFO" -> Timber.i(message)
                else -> Timber.d(message)
            }
        } catch (e: Exception) {
            Timber.e(e, "Crashlytics'e log eklenemedi")
        }
    }

    /**
     * Özel key-value çifti ekler.
     *
     * @param key Key
     * @param value Value (String, Int, Long, Float, Boolean)
     */
    fun setCustomKey(
        key: String,
        value: Any,
    ) {
        try {
            when (value) {
                is String -> crashlytics.setCustomKey(key, value)
                is Int -> crashlytics.setCustomKey(key, value)
                is Long -> crashlytics.setCustomKey(key, value)
                is Float -> crashlytics.setCustomKey(key, value)
                is Boolean -> crashlytics.setCustomKey(key, value)
                else -> crashlytics.setCustomKey(key, value.toString())
            }
        } catch (e: Exception) {
            Timber.e(e, "Crashlytics'e custom key eklenemedi: $key")
        }
    }

    /**
     * Kullanıcı ID'si ekler (anonim, PII değil).
     *
     * @param userId Anonim kullanıcı ID'si (hash'lenmiş veya UUID)
     */
    fun setUserId(userId: String) {
        try {
            crashlytics.setUserId(userId)
        } catch (e: Exception) {
            Timber.e(e, "Crashlytics'e user ID eklenemedi")
        }
    }

    /**
     * Crashlytics log'larını temizler (test amaçlı).
     */
    fun clearLogs() {
        try {
            crashlytics.log("--- Logs cleared ---")
        } catch (e: Exception) {
            Timber.e(e, "Crashlytics log'ları temizlenemedi")
        }
    }

    /**
     * PII (Personally Identifiable Information) kontrolü yapar.
     *
     * @param text Kontrol edilecek metin
     * @return true ise PII içeriyor
     */
    private fun containsPII(text: String): Boolean {
        val lowerText = text.lowercase()
        val piiPatterns =
            listOf(
                "@", // Email
                "password", "pwd", "pass",
                "username", "user", "login",
                "dns", "url", "http://", "https://",
                "token", "key", "secret",
                "credit", "card", "cvv",
            )
        return piiPatterns.any { lowerText.contains(it) }
    }

    /**
     * Hata istatistikleri için özel event kaydeder.
     *
     * @param eventName Event adı
     * @param parameters Event parametreleri (PII içermez)
     */
    fun logEvent(
        eventName: String,
        parameters: Map<String, String> = emptyMap(),
    ) {
        try {
            val logMessage =
                buildString {
                    append("Event: $eventName")
                    if (parameters.isNotEmpty()) {
                        append(" | ")
                        append(parameters.entries.joinToString(", ") { "${it.key}=${it.value}" })
                    }
                }
            crashlytics.log(logMessage)
            Timber.d(logMessage)
        } catch (e: Exception) {
            Timber.e(e, "Crashlytics event kaydedilemedi")
        }
    }

    /**
     * Gönderilmemiş raporları gönderir.
     */
    fun sendUnsentReports() {
        try {
            crashlytics.sendUnsentReports()
        } catch (e: Exception) {
            Timber.e(e, "Crashlytics raporları gönderilemedi")
        }
    }
}
