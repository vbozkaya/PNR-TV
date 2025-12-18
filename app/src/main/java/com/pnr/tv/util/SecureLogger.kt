package com.pnr.tv.util

import timber.log.Timber

/**
 * Hassas bilgileri loglardan filtreleyen güvenli logging utility.
 * Password, API key, DNS gibi hassas bilgileri otomatik olarak maskeleme yapar.
 */
object SecureLogger {
    // Hassas bilgileri tespit etmek için pattern'ler
    private val SENSITIVE_PATTERNS =
        listOf(
            Regex("(?i)(password|passwd|pwd)\\s*[:=]\\s*([^\\s,;]+)", RegexOption.IGNORE_CASE),
            Regex("(?i)(api[_-]?key|apikey)\\s*[:=]\\s*([^\\s,;]+)", RegexOption.IGNORE_CASE),
            Regex("(?i)(dns|server)\\s*[:=]\\s*([^\\s,;]+)", RegexOption.IGNORE_CASE),
            Regex("(?i)(token|auth[_-]?token)\\s*[:=]\\s*([^\\s,;]+)", RegexOption.IGNORE_CASE),
            Regex("(?i)(secret|secret[_-]?key)\\s*[:=]\\s*([^\\s,;]+)", RegexOption.IGNORE_CASE),
            Regex("(?i)(username|user)\\s*[:=]\\s*([^\\s,;]+)", RegexOption.IGNORE_CASE),
        )

    /**
     * Hassas bilgileri maskeleyerek loglar
     */
    fun d(
        message: String,
        tag: String? = null,
    ) {
        val sanitized = sanitize(message)
        if (tag != null) {
            Timber.tag(tag).d(sanitized)
        } else {
            Timber.d(sanitized)
        }
    }

    fun i(
        message: String,
        tag: String? = null,
    ) {
        val sanitized = sanitize(message)
        if (tag != null) {
            Timber.tag(tag).i(sanitized)
        } else {
            Timber.i(sanitized)
        }
    }

    fun w(
        message: String,
        tag: String? = null,
    ) {
        val sanitized = sanitize(message)
        if (tag != null) {
            Timber.tag(tag).w(sanitized)
        } else {
            Timber.w(sanitized)
        }
    }

    fun e(
        message: String,
        throwable: Throwable? = null,
        tag: String? = null,
    ) {
        val sanitized = sanitize(message)
        if (tag != null) {
            if (throwable != null) {
                Timber.tag(tag).e(throwable, sanitized)
            } else {
                Timber.tag(tag).e(sanitized)
            }
        } else {
            if (throwable != null) {
                Timber.e(throwable, sanitized)
            } else {
                Timber.e(sanitized)
            }
        }
    }

    /**
     * Mesajdaki hassas bilgileri maskeleme yapar
     */
    fun sanitize(message: String): String {
        var sanitized = message

        // Her pattern için maskeleme yap
        SENSITIVE_PATTERNS.forEach { pattern ->
            sanitized =
                pattern.replace(sanitized) { matchResult ->
                    val key = matchResult.groupValues[1]
                    val value = matchResult.groupValues[2]
                    val maskedValue = maskSensitiveValue(value)
                    "${matchResult.groupValues[0].substringBefore(value)}$maskedValue"
                }
        }

        // URL'lerdeki query parametrelerini kontrol et
        sanitized = sanitizeUrl(sanitized)

        return sanitized
    }

    /**
     * Hassas bir değeri maskeleme yapar (örneğin: "secret123" -> "sec***123")
     */
    private fun maskSensitiveValue(value: String): String {
        if (value.length <= 3) {
            return "***"
        }
        if (value.length <= 6) {
            return "${value.first()}***${value.last()}"
        }
        // İlk 3 ve son 3 karakteri göster, ortasını maskele
        val start = value.take(3)
        val end = value.takeLast(3)
        return "$start***$end"
    }

    /**
     * URL'lerdeki hassas query parametrelerini maskeleme yapar
     */
    private fun sanitizeUrl(url: String): String {
        val sensitiveParams = listOf("password", "passwd", "pwd", "api_key", "apikey", "token", "secret", "dns")
        var sanitized = url

        sensitiveParams.forEach { param ->
            val pattern = Regex("([?&])$param=([^&\\s]+)", RegexOption.IGNORE_CASE)
            sanitized =
                pattern.replace(sanitized) { matchResult ->
                    val separator = matchResult.groupValues[1]
                    val value = matchResult.groupValues[2]
                    val masked = maskSensitiveValue(value)
                    "$separator$param=$masked"
                }
        }

        return sanitized
    }
}
