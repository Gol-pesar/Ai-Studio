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
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val Context.studioDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "prompt_studio_preferences"
)

data class AiStudioBookmark(
    val id: String,
    val title: String,
    val url: String
)

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val defaultModelId: String = GeminiModelCatalog.DEFAULT_MODEL_ID,
    val useSignedInAccountForWeb: Boolean = true,
    val desktopModeWebView: Boolean = false,
    val webBookmarks: List<AiStudioBookmark> = emptyList()
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val DEFAULT_MODEL_ID = stringPreferencesKey("default_model_id")
        val USE_SIGNED_IN_ACCOUNT_WEB = booleanPreferencesKey("use_signed_in_account_web")
        val DESKTOP_MODE_WEBVIEW = booleanPreferencesKey("desktop_mode_webview")
        val WEB_BOOKMARKS_JSON = stringPreferencesKey("web_bookmarks_json")
    }

    val preferencesFlow: Flow<UserPreferences> = context.studioDataStore.data.map { prefs ->
        UserPreferences(
            themeMode = ThemeMode.fromId(prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.id),
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: false,
            defaultModelId = prefs[Keys.DEFAULT_MODEL_ID] ?: GeminiModelCatalog.DEFAULT_MODEL_ID,
            useSignedInAccountForWeb = prefs[Keys.USE_SIGNED_IN_ACCOUNT_WEB] ?: true,
            desktopModeWebView = prefs[Keys.DESKTOP_MODE_WEBVIEW] ?: false,
            webBookmarks = parseBookmarks(prefs[Keys.WEB_BOOKMARKS_JSON])
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

    suspend fun setUseSignedInAccountForWeb(enabled: Boolean) {
        context.studioDataStore.edit { it[Keys.USE_SIGNED_IN_ACCOUNT_WEB] = enabled }
    }

    suspend fun setDesktopModeWebView(enabled: Boolean) {
        context.studioDataStore.edit { it[Keys.DESKTOP_MODE_WEBVIEW] = enabled }
    }

    suspend fun addWebBookmark(title: String, url: String) {
        context.studioDataStore.edit { prefs ->
            val current = parseBookmarks(prefs[Keys.WEB_BOOKMARKS_JSON]).toMutableList()
            val item = AiStudioBookmark(
                id = UUID.randomUUID().toString(),
                title = title.trim().take(80),
                url = url.trim().take(500)
            )
            current.add(0, item)
            prefs[Keys.WEB_BOOKMARKS_JSON] = encodeBookmarks(current.take(30))
        }
    }

    suspend fun deleteWebBookmark(bookmarkId: String) {
        context.studioDataStore.edit { prefs ->
            val updated = parseBookmarks(prefs[Keys.WEB_BOOKMARKS_JSON])
                .filterNot { it.id == bookmarkId }
            prefs[Keys.WEB_BOOKMARKS_JSON] = encodeBookmarks(updated)
        }
    }

    private fun parseBookmarks(raw: String?): List<AiStudioBookmark> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val id = obj.optString("id")
                    val title = obj.optString("title")
                    val url = obj.optString("url")
                    if (id.isNotBlank() && title.isNotBlank() && url.isNotBlank()) {
                        add(AiStudioBookmark(id = id, title = title, url = url))
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun encodeBookmarks(bookmarks: List<AiStudioBookmark>): String {
        val array = JSONArray()
        bookmarks.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("title", item.title)
                    put("url", item.url)
                }
            )
        }
        return array.toString()
    }
}
