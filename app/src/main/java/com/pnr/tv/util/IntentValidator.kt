package com.pnr.tv.util

import android.content.Intent
import android.net.Uri
import android.util.Patterns
import timber.log.Timber

/**
 * Intent'lerden gelen verileri doğrulamak için utility sınıfı.
 * Güvenlik açıklarını önlemek için kullanılır.
 */
object IntentValidator {
    /**
     * PlayerActivity için intent verilerini doğrular
     */
    fun validatePlayerIntent(intent: Intent): ValidationResult {
        val videoUrl = intent.getStringExtra("extra_video_url")
        val contentId = intent.getStringExtra("extra_content_id")
        val channelId = intent.getIntExtra("extra_channel_id", -1)
        val categoryId = intent.getIntExtra("extra_category_id", -1)

        // Video URL validation
        if (!videoUrl.isNullOrBlank()) {
            val urlValidation = validateUrl(videoUrl)
            if (!urlValidation.isValid) {
                Timber.w("❌ Geçersiz video URL: $videoUrl - ${urlValidation.errorMessage}")
                return ValidationResult(false, "Geçersiz video URL: ${urlValidation.errorMessage}")
            }
        } else {
            // Video URL yoksa en azından content ID veya channel ID olmalı
            if (contentId.isNullOrBlank() && channelId == -1) {
                return ValidationResult(false, "Video URL, Content ID veya Channel ID gerekli")
            }
        }

        // Content ID validation (eğer varsa)
        if (!contentId.isNullOrBlank()) {
            if (contentId.length > 100) {
                return ValidationResult(false, "Content ID çok uzun")
            }
            // Content ID sadece alfanumerik karakterler içermeli
            if (!contentId.matches(Regex("^[a-zA-Z0-9_-]+$"))) {
                return ValidationResult(false, "Content ID geçersiz format")
            }
        }

        // Channel ID validation (eğer varsa)
        if (channelId != -1) {
            if (channelId < 0 || channelId > Int.MAX_VALUE) {
                return ValidationResult(false, "Channel ID geçersiz")
            }
        }

        // Category ID validation (eğer varsa)
        if (categoryId != -1) {
            if (categoryId < 0 || categoryId > Int.MAX_VALUE) {
                return ValidationResult(false, "Category ID geçersiz")
            }
        }

        return ValidationResult(true, null)
    }

    /**
     * URL formatını doğrular
     */
    fun validateUrl(url: String): ValidationResult {
        if (url.isBlank()) {
            return ValidationResult(false, "URL boş olamaz")
        }

        // URL çok uzunsa reddet
        if (url.length > 2048) {
            return ValidationResult(false, "URL çok uzun")
        }

        // URL formatını kontrol et
        val uri =
            try {
                Uri.parse(url)
            } catch (e: Exception) {
                return ValidationResult(false, "URL parse edilemedi: ${e.message}")
            }

        // Scheme kontrolü (http, https, rtmp, rtsp, vs.)
        val scheme = uri.scheme?.lowercase()
        val allowedSchemes = listOf("http", "https", "rtmp", "rtsp", "udp", "tcp")
        if (scheme == null || !allowedSchemes.contains(scheme)) {
            return ValidationResult(false, "Geçersiz URL scheme: $scheme")
        }

        // Host kontrolü (boş olamaz)
        if (uri.host.isNullOrBlank()) {
            return ValidationResult(false, "URL host boş olamaz")
        }

        // IP adresi veya domain kontrolü
        val host = uri.host!!
        if (!isValidHost(host)) {
            return ValidationResult(false, "Geçersiz host: $host")
        }

        return ValidationResult(true, null)
    }

    /**
     * Host'un geçerli olup olmadığını kontrol eder (IP veya domain)
     */
    private fun isValidHost(host: String): Boolean {
        // IP adresi kontrolü
        if (Patterns.IP_ADDRESS.matcher(host).matches()) {
            return true
        }

        // Domain kontrolü (basit format kontrolü)
        if (host.length > 253) {
            return false
        }

        // Domain formatı: en az bir nokta içermeli ve geçerli karakterler
        if (host.contains(".") && host.matches(Regex("^[a-zA-Z0-9.-]+$"))) {
            return true
        }

        // Localhost ve local domain'ler
        if (host.equals("localhost", ignoreCase = true) || host.startsWith("192.168.") || host.startsWith("10.") || host.startsWith("172.")) {
            return true
        }

        return false
    }

    /**
     * Validation sonucu
     */
    data class ValidationResult(
        val isValid: Boolean,
        val errorMessage: String?,
    )
}
