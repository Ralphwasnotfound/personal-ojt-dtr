package com.lazarus.personalojtdtr.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lazarus.personalojtdtr.data.preferences.ThemePreferences
import com.lazarus.personalojtdtr.domain.model.ThemeMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ThemeSettings(val mode: ThemeMode? = null, val saving: Boolean = false, val error: String? = null)

class ThemeSettingsViewModel(private val preferences: ThemePreferences) : ViewModel() {
    private val _state = MutableStateFlow(ThemeSettings())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            try { preferences.mode.collect { _state.value = _state.value.copy(mode = it) } }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                _state.value = _state.value.copy(mode = _state.value.mode ?: ThemeMode.SYSTEM,
                    error = "Appearance could not be loaded. Select a theme to retry.")
            }
        }
    }

    fun select(mode: ThemeMode) {
        if (_state.value.saving) return
        _state.value = _state.value.copy(saving = true, error = null)
        viewModelScope.launch {
            try {
                preferences.setMode(mode)
                _state.value = _state.value.copy(mode = mode, saving = false)
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                _state.value = _state.value.copy(saving = false, error = "Theme could not be saved. Check free storage and retry.")
            }
        }
    }

    companion object {
        fun factory(preferences: ThemePreferences): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(ThemeSettingsViewModel::class.java))
                return ThemeSettingsViewModel(preferences) as T
            }
        }
    }
}
