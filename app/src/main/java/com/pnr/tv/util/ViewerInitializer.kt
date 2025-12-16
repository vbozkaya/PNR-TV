package com.pnr.tv.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.pnr.tv.R
import com.pnr.tv.db.entity.ViewerEntity
import com.pnr.tv.repository.UserRepository
import com.pnr.tv.repository.ViewerRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "viewer_init")
private val VIEWER_INITIALIZED_KEY = booleanPreferencesKey("viewer_initialized")

@Singleton
class ViewerInitializer
    @Inject
    constructor(
        private val viewerRepository: ViewerRepository,
        private val userRepository: UserRepository,
        @ApplicationContext private val context: Context,
    ) {
        suspend fun initializeIfNeeded() {
            val isInitialized = context.dataStore.data.first()[VIEWER_INITIALIZED_KEY] ?: false
            if (!isInitialized) {
                // Get current user account name
                val currentUser = userRepository.currentUser.firstOrNull()
                val accountName = currentUser?.accountName ?: context.getString(R.string.default_viewer_name)

                // Create default viewer
                val defaultViewer =
                    ViewerEntity(
                        name = accountName,
                        isDeletable = false,
                    )
                viewerRepository.addViewer(defaultViewer)

                // Mark as initialized
                context.dataStore.edit { preferences ->
                    preferences[VIEWER_INITIALIZED_KEY] = true
                }
            }
        }

        /**
         * Viewer initialization flag'ini temizler (cache temizleme için).
         */
        suspend fun clearInitializationFlag() {
            context.dataStore.edit { preferences ->
                preferences.remove(VIEWER_INITIALIZED_KEY)
            }
        }
    }
