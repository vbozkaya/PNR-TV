package com.pnr.tv.util.validation

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pnr.tv.premium.PremiumManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.adultContentDataStore: DataStore<Preferences> by preferencesDataStore(name = "adult_content_preferences")

@Singleton
class AdultContentPreferenceManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val premiumManager: PremiumManager,
    ) {
        private val ADULT_CONTENT_ENABLED_KEY = booleanPreferencesKey("adult_content_enabled")
        private val TEMPORARY_ADULT_ACCESS_UNTIL_KEY = longPreferencesKey("temporary_adult_access_until")

        /**
         * Yetişkin içeriklerin gösterilip gösterilmeyeceğini kaydeder.
         * @param enabled true ise yetişkin içerikler gösterilir, false ise gizlenir
         */
        suspend fun setAdultContentEnabled(enabled: Boolean) {
            context.adultContentDataStore.edit { preferences ->
                preferences[ADULT_CONTENT_ENABLED_KEY] = enabled
            }
        }

        /**
         * Geçici yetişkin içerik erişim süresini kaydeder.
         * @param timestamp Timestamp (milisaniye) - bu zamana kadar erişim verilir
         */
        suspend fun setTemporaryAdultAccessUntil(timestamp: Long) {
            context.adultContentDataStore.edit { preferences ->
                preferences[TEMPORARY_ADULT_ACCESS_UNTIL_KEY] = timestamp
            }
        }

        /**
         * Geçici yetişkin içerik erişim süresini döndürür.
         * @return Timestamp (milisaniye) veya null
         */
        fun getTemporaryAdultAccessUntil(): Flow<Long?> {
            return context.adultContentDataStore.data.map { preferences ->
                preferences[TEMPORARY_ADULT_ACCESS_UNTIL_KEY]
            }
        }

        /**
         * Yetişkin içeriklerin gösterilip gösterilmeyeceğini döndürür.
         * Premium kullanıcılar toggle açabilir, ancak toggle "OFF" ise filtreleme yapılır.
         * Premium olmayan kullanıcılar için geçici erişim süresi dolmamışsa true döner.
         * @return true ise yetişkin içerikler gösterilir, false ise gizlenir
         */
        fun isAdultContentEnabled(): Flow<Boolean> {
            return combine(
                premiumManager.isPremium(),
                context.adultContentDataStore.data.map { preferences ->
                    preferences[ADULT_CONTENT_ENABLED_KEY] ?: false
                },
                getTemporaryAdultAccessUntil(),
            ) { isPremium, isEnabled, temporaryAccessUntil ->
                // Premium kullanıcılar toggle açabilir, ancak toggle durumuna göre filtreleme yapılır
                // Premium olsa bile, kullanıcı toggle'ı "OFF" yapmışsa filtreleme yapılmalı
                if (isPremium) {
                    // Premium kullanıcılar için toggle durumuna göre döndür
                    // Toggle "ON" ise true, "OFF" ise false
                    isEnabled
                } else {
                    // Premium olmayan kullanıcılar için geçici erişim süresi kontrolü
                    val currentTime = System.currentTimeMillis()
                    val hasTemporaryAccess = temporaryAccessUntil != null && currentTime < temporaryAccessUntil
                    // Ya manual olarak açık, ya da geçici erişim var
                    isEnabled || hasTemporaryAccess
                }
            }
        }

        /**
         * Geçici erişim süresini temizler.
         */
        suspend fun clearTemporaryAdultAccess() {
            context.adultContentDataStore.edit { preferences ->
                preferences.remove(TEMPORARY_ADULT_ACCESS_UNTIL_KEY)
            }
        }
    }
