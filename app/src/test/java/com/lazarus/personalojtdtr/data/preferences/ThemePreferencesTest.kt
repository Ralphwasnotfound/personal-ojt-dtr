package com.lazarus.personalojtdtr.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.lazarus.personalojtdtr.domain.model.ThemeMode
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class ThemePreferencesTest {
    @get:Rule val folder = TemporaryFolder()

    private suspend fun withStore(file: File = File(folder.root, "appearance.preferences_pb"), block: suspend (DataStore<Preferences>) -> Unit) {
        val job = SupervisorJob()
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO), produceFile = { file })
        try { block(store) } finally { job.cancelAndJoin() }
    }

    @Test fun `new installation defaults to system without writing a preference`() = runBlocking {
        withStore { store ->
            assertEquals(ThemeMode.SYSTEM, ThemePreferences(store).mode.first())
            assertNull(store.data.first()[stringPreferencesKey("theme_mode")])
        }
    }

    @Test fun `every selection transition updates observers and persists the stable value`() = runBlocking {
        withStore { store ->
            val preferences = ThemePreferences(store)
            val transitions = listOf(
                ThemeMode.SYSTEM to ThemeMode.LIGHT, ThemeMode.SYSTEM to ThemeMode.DARK,
                ThemeMode.LIGHT to ThemeMode.DARK, ThemeMode.DARK to ThemeMode.LIGHT,
                ThemeMode.LIGHT to ThemeMode.SYSTEM, ThemeMode.DARK to ThemeMode.SYSTEM,
            )
            for ((from, to) in transitions) {
                preferences.setMode(from)
                assertEquals(from, preferences.mode.first())
                preferences.setMode(to)
                assertEquals(to, preferences.mode.first())
                assertEquals(to.storageValue, store.data.first()[stringPreferencesKey("theme_mode")])
            }
        }
    }

    @Test fun `all choices survive a closed store and fresh instance`() = runBlocking {
        for (mode in ThemeMode.entries) {
            val file = File(folder.root, "${mode.storageValue}.preferences_pb")
            withStore(file) { ThemePreferences(it).setMode(mode) }
            withStore(file) { assertEquals(mode, ThemePreferences(it).mode.first()) }
        }
    }

    @Test fun `existing explicit preferences and unrelated keys are preserved`() = runBlocking {
        withStore { store ->
            val unrelated = stringPreferencesKey("unrelated_setting")
            store.edit { it[stringPreferencesKey("theme_mode")] = "dark"; it[unrelated] = "keep" }
            val preferences = ThemePreferences(store)
            assertEquals(ThemeMode.DARK, preferences.mode.first())
            preferences.setMode(ThemeMode.LIGHT)
            assertEquals("keep", store.data.first()[unrelated])
        }
    }

    @Test fun `unknown future value falls back to system without overwriting disk`() = runBlocking {
        withStore { store ->
            store.edit { it[stringPreferencesKey("theme_mode")] = "future-mode" }
            assertEquals(ThemeMode.SYSTEM, ThemePreferences(store).mode.first())
            assertEquals("future-mode", store.data.first()[stringPreferencesKey("theme_mode")])
        }
    }

    @Test fun `failed write reports failure and leaves the prior selection intact`() = runBlocking {
        val store = object : DataStore<Preferences> {
            override val data = flowOf(preferencesOf(stringPreferencesKey("theme_mode") to "dark"))
            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = throw IOException("No space")
        }
        val preferences = ThemePreferences(store)
        try { preferences.setMode(ThemeMode.LIGHT); fail("Expected write failure") } catch (_: IOException) { }
        assertEquals(ThemeMode.DARK, preferences.mode.first())
    }

    @Test fun `system mode follows configuration while explicit modes override either system setting`() {
        assertFalse(ThemeMode.SYSTEM.isDark(false))
        assertTrue(ThemeMode.SYSTEM.isDark(true))
        for (systemDark in listOf(false, true)) {
            assertFalse(ThemeMode.LIGHT.isDark(systemDark))
            assertTrue(ThemeMode.DARK.isDark(systemDark))
        }
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorage(null))
    }
}
