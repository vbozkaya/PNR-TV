package com.pnr.tv.premium

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.premiumDataStore: DataStore<Preferences> by preferencesDataStore(name = "premium_preferences")

@Singleton
class PremiumManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        companion object {
            private val IS_PREMIUM_KEY = booleanPreferencesKey("is_premium")
        }

        private val dataStore = context.premiumDataStore

        /**
         * Premium durumunu kaydeder.
         */
        suspend fun setPremiumStatus(isPremium: Boolean) {
            dataStore.edit { preferences ->
                preferences[IS_PREMIUM_KEY] = isPremium
            }
        }

        /**
         * Premium durumunu kontrol eder.
         */
        fun isPremium(): Flow<Boolean> {
            return dataStore.data.map { preferences ->
                preferences[IS_PREMIUM_KEY] ?: false
            }
        }

        /**
         * Premium durumunu senkron olarak kontrol eder (suspend fonksiyon).
         */
        suspend fun isPremiumSync(): Boolean {
            return dataStore.data.map { preferences ->
                preferences[IS_PREMIUM_KEY] ?: false
            }.first()
        }

        /**
         * Premium durumunu temizler (test veya cache temizleme için).
         */
        suspend fun clearPremiumStatus() {
            dataStore.edit { preferences ->
                preferences.remove(IS_PREMIUM_KEY)
            }
        }
    }
