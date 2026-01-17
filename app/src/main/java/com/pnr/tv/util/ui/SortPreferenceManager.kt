package com.pnr.tv.util.ui

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pnr.tv.model.ContentType
import com.pnr.tv.model.SortOrder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "sort_preferences")

@Singleton
class SortPreferenceManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private fun getSortKey(contentType: ContentType): Preferences.Key<String> {
            return stringPreferencesKey("sort_order_${contentType.name}")
        }

        suspend fun saveSortOrder(
            contentType: ContentType,
            sortOrder: SortOrder,
        ) {
            context.dataStore.edit { preferences ->
                preferences[getSortKey(contentType)] = sortOrder.name
            }
        }

        fun getSortOrder(contentType: ContentType): Flow<SortOrder?> {
            return context.dataStore.data.map { preferences ->
                val sortOrderName = preferences[getSortKey(contentType)]
                sortOrderName?.let { SortOrder.valueOf(it) }
            }
        }
    }
