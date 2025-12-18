package com.pnr.tv.security

import android.content.Context
import timber.log.Timber

/**
 * Database'de saklanan hassas verileri (password, DNS) şifrelemek için kullanılır.
 * KeystoreManager'ı kullanarak güvenli şifreleme sağlar.
 */
object DataEncryption {
    /**
     * Hassas bir string'i şifreler (örneğin password, DNS)
     */
    fun encryptSensitiveData(
        data: String,
        context: Context,
    ): String {
        if (data.isBlank()) {
            return data
        }

        return try {
            val encrypted = KeystoreManager.encrypt(data, context)
            encrypted ?: data // Şifreleme başarısız olursa orijinal veriyi döndür
        } catch (e: Exception) {
            Timber.e(e, "❌ Veri şifreleme hatası")
            data // Hata durumunda orijinal veriyi döndür
        }
    }

    /**
     * Şifrelenmiş veriyi çözer
     */
    fun decryptSensitiveData(
        encryptedData: String,
        context: Context,
    ): String {
        if (encryptedData.isBlank()) {
            return encryptedData
        }

        // Eğer veri şifrelenmemişse (eski veriler için backward compatibility)
        if (!isEncrypted(encryptedData)) {
            return encryptedData
        }

        return try {
            val decrypted = KeystoreManager.decrypt(encryptedData, context)
            decrypted ?: encryptedData // Şifre çözme başarısız olursa şifrelenmiş veriyi döndür
        } catch (e: Exception) {
            Timber.e(e, "❌ Veri şifre çözme hatası")
            encryptedData // Hata durumunda şifrelenmiş veriyi döndür
        }
    }

    /**
     * Verinin şifrelenmiş olup olmadığını kontrol eder
     * Base64 formatında ve yeterince uzunsa şifrelenmiş kabul edilir
     */
    private fun isEncrypted(data: String): Boolean {
        return try {
            // Şifrelenmiş veri Base64 formatında ve genellikle daha uzun olur
            android.util.Base64.decode(data, android.util.Base64.NO_WRAP)
            data.length > 20 // Şifrelenmiş veri genellikle daha uzun
        } catch (e: Exception) {
            false
        }
    }
}
