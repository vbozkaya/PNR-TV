package com.pnr.tv.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import timber.log.Timber
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android Keystore kullanarak hassas verileri güvenli bir şekilde saklar ve yönetir.
 * API key'ler ve diğer hassas bilgiler için kullanılır.
 */
object KeystoreManager {
    private const val KEYSTORE_ALIAS = "PNR_TV_KEYSTORE"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 16

    /**
     * Keystore'tan veya EncryptedSharedPreferences'ten API key'i alır.
     * Eğer yoksa BuildConfig'den alır ve şifreleyerek saklar.
     */
    fun getApiKey(
        context: Context,
        buildConfigKey: String,
    ): String {
        return try {
            // Önce EncryptedSharedPreferences'ten dene
            val masterKey =
                MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()

            val encryptedPrefs =
                EncryptedSharedPreferences.create(
                    context,
                    "secure_prefs",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )

            val storedKey = encryptedPrefs.getString("tmdb_api_key", null)
            if (storedKey != null) {
                Timber.d("✅ API key EncryptedSharedPreferences'ten alındı")
                return storedKey
            }

            // Yoksa BuildConfig'den al, şifrele ve sakla
            encryptedPrefs.edit()
                .putString("tmdb_api_key", buildConfigKey)
                .apply()

            Timber.d("✅ API key şifrelenerek saklandı")
            buildConfigKey
        } catch (e: Exception) {
            Timber.e(e, "❌ Keystore hatası, BuildConfig'den alınıyor")
            // Fallback: BuildConfig'den al (güvenlik riski var ama uygulama çalışır)
            buildConfigKey
        }
    }

    /**
     * Hassas bir string'i şifreler (AES/GCM)
     */
    fun encrypt(
        plaintext: String,
        context: Context,
    ): String? {
        return try {
            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

            // IV + encrypted data'yı birleştir
            val combined = ByteArray(GCM_IV_LENGTH + encrypted.size)
            System.arraycopy(iv, 0, combined, 0, GCM_IV_LENGTH)
            System.arraycopy(encrypted, 0, combined, GCM_IV_LENGTH, encrypted.size)

            android.util.Base64.encodeToString(combined, android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            Timber.e(e, "❌ Şifreleme hatası")
            null
        }
    }

    /**
     * Şifrelenmiş string'i çözer
     */
    fun decrypt(
        encryptedData: String,
        context: Context,
    ): String? {
        return try {
            val secretKey = getOrCreateSecretKey()
            val combined = android.util.Base64.decode(encryptedData, android.util.Base64.NO_WRAP)

            // IV ve encrypted data'yı ayır
            val iv = ByteArray(GCM_IV_LENGTH)
            val encrypted = ByteArray(combined.size - GCM_IV_LENGTH)
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH)
            System.arraycopy(combined, GCM_IV_LENGTH, encrypted, 0, encrypted.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH * 8, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            val decrypted = cipher.doFinal(encrypted)

            String(decrypted, Charsets.UTF_8)
        } catch (e: Exception) {
            Timber.e(e, "❌ Şifre çözme hatası")
            null
        }
    }

    /**
     * Keystore'tan secret key'i alır veya yoksa oluşturur
     */
    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)

        val existingKey = keyStore.getEntry(KEYSTORE_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existingKey != null) {
            return existingKey.secretKey
        }

        // Yeni key oluştur
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val keyGenParameterSpec =
            KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()

        keyGenerator.init(keyGenParameterSpec)
        return keyGenerator.generateKey()
    }
}
