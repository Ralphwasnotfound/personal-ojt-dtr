package com.lazarus.personalojtdtr.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lazarus.personalojtdtr.domain.model.ThemeMode
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.appearanceDataStore by preferencesDataStore(name = "appearance")

/** One application-owned store, separate from Room, backup records and Glance widget state. */
class ThemePreferences(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.appearanceDataStore)

    val mode = store.data.map { ThemeMode.fromStorage(it[MODE]) }.distinctUntilChanged()

    suspend fun setMode(mode: ThemeMode) {
        store.edit { it[MODE] = mode.storageValue }
    }

    private companion object { val MODE = stringPreferencesKey("theme_mode") }
}
