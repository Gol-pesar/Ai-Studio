package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.GeminiModelCatalog
import com.example.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.studioDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "prompt_studio_preferences"
)

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val defaultModelId: String = GeminiModelCatalog.DEFAULT_MODEL_ID
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val DEFAULT_MODEL_ID = stringPreferencesKey("default_model_id")
    }

    val preferencesFlow: Flow<UserPreferences> = context.studioDataStore.data.map { prefs ->
        UserPreferences(
            themeMode = ThemeMode.fromId(prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.id),
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: false,
            defaultModelId = prefs[Keys.DEFAULT_MODEL_ID] ?: GeminiModelCatalog.DEFAULT_MODEL_ID
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.studioDataStore.edit { it[Keys.THEME_MODE] = mode.id }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.studioDataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun setDefaultModelId(modelId: String) {
        context.studioDataStore.edit { it[Keys.DEFAULT_MODEL_ID] = modelId }
    }
}
