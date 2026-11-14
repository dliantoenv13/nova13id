package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.remote.AiModelCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.novaDataStore by preferencesDataStore(name = "nova_preferences")

data class NovaSettings(
    val selectedModelId: String = AiModelCatalog.DEFAULT_MODEL.id,
    val floatingPopupEnabled: Boolean = true,
    val homeScreenOnlyMode: Boolean = true,
    val wakeWordEnabled: Boolean = true,
    val autoSpeakEnabled: Boolean = true
)

class NovaPreferencesRepository(private val context: Context) {
    private object Keys {
        val SELECTED_MODEL = stringPreferencesKey("selected_model_id")
        val FLOATING_POPUP_ENABLED = booleanPreferencesKey("floating_popup_enabled")
        val HOME_SCREEN_ONLY = booleanPreferencesKey("home_screen_only_mode")
        val WAKE_WORD_ENABLED = booleanPreferencesKey("wake_word_enabled")
        val AUTO_SPEAK_ENABLED = booleanPreferencesKey("auto_speak_enabled")
    }

    val settingsFlow: Flow<NovaSettings> = context.novaDataStore.data.map { prefs ->
        NovaSettings(
            selectedModelId = prefs[Keys.SELECTED_MODEL] ?: AiModelCatalog.DEFAULT_MODEL.id,
            floatingPopupEnabled = prefs[Keys.FLOATING_POPUP_ENABLED] ?: true,
            homeScreenOnlyMode = prefs[Keys.HOME_SCREEN_ONLY] ?: true,
            wakeWordEnabled = prefs[Keys.WAKE_WORD_ENABLED] ?: true,
            autoSpeakEnabled = prefs[Keys.AUTO_SPEAK_ENABLED] ?: true
        )
    }

    suspend fun setSelectedModel(modelId: String) {
        context.novaDataStore.edit { it[Keys.SELECTED_MODEL] = modelId }
    }

    suspend fun setFloatingPopupEnabled(enabled: Boolean) {
        context.novaDataStore.edit { it[Keys.FLOATING_POPUP_ENABLED] = enabled }
    }

    suspend fun setHomeScreenOnlyMode(enabled: Boolean) {
        context.novaDataStore.edit { it[Keys.HOME_SCREEN_ONLY] = enabled }
    }

    suspend fun setWakeWordEnabled(enabled: Boolean) {
        context.novaDataStore.edit { it[Keys.WAKE_WORD_ENABLED] = enabled }
    }

    suspend fun setAutoSpeakEnabled(enabled: Boolean) {
        context.novaDataStore.edit { it[Keys.AUTO_SPEAK_ENABLED] = enabled }
    }
}
