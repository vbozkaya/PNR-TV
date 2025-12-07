package com.pnr.tv

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "pnr_tv_session")

@Singleton
class SessionManager
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val dataStore = context.dataStore

        companion object {
            private val CURRENT_USER_ID_KEY = intPreferencesKey("current_user_id")
        }

        suspend fun saveCurrentUser(userId: Int) {
            dataStore.edit { preferences ->
                preferences[CURRENT_USER_ID_KEY] = userId
            }
        }

        fun getCurrentUserId(): Flow<Int?> {
            return dataStore.data.map { preferences ->
                preferences[CURRENT_USER_ID_KEY]
            }
        }
    }
