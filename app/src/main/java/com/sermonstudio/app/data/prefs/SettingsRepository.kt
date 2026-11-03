package com.sermonstudio.app.data.prefs

import android.content.Context
import android.content.res.Configuration
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val defaultVersionId: String? = null,
    /** Versions shown side by side in the Bible reader's compare mode. */
    val compareVersionIds: List<String> = emptyList(),
    val autoBackup: Boolean = true,
    /** "template" (built-in, always available) or "llm" (on-device model). */
    val aiEngine: String = "template",
    val llmModelPath: String? = null,
    val seeded: Boolean = false,
)

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val ctx: Context) {
    private object K {
        val theme = stringPreferencesKey("theme")
        val dynamic = booleanPreferencesKey("dynamic")
        val defaultVersion = stringPreferencesKey("default_version")
        val compare = stringPreferencesKey("compare_versions")
        val autoBackup = booleanPreferencesKey("auto_backup")
        val aiEngine = stringPreferencesKey("ai_engine")
        val llmPath = stringPreferencesKey("llm_path")
        val seeded = booleanPreferencesKey("seeded")
    }

    val settings: Flow<AppSettings> = ctx.dataStore.data.map { p ->
        AppSettings(
            themeMode = runCatching { ThemeMode.valueOf(p[K.theme] ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM),
            dynamicColor = p[K.dynamic] ?: true,
            defaultVersionId = p[K.defaultVersion],
            compareVersionIds = p[K.compare]?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
            autoBackup = p[K.autoBackup] ?: true,
            aiEngine = p[K.aiEngine] ?: "template",
            llmModelPath = p[K.llmPath],
            seeded = p[K.seeded] ?: false,
        )
    }

    suspend fun setTheme(m: ThemeMode) = ctx.dataStore.edit { it[K.theme] = m.name }
    suspend fun setDynamicColor(v: Boolean) = ctx.dataStore.edit { it[K.dynamic] = v }
    suspend fun setAutoBackup(v: Boolean) = ctx.dataStore.edit { it[K.autoBackup] = v }
    suspend fun setAiEngine(v: String) = ctx.dataStore.edit { it[K.aiEngine] = v }
    suspend fun setSeeded() = ctx.dataStore.edit { it[K.seeded] = true }
    suspend fun setDefaultVersion(id: String?) =
        ctx.dataStore.edit { if (id == null) it.remove(K.defaultVersion) else it[K.defaultVersion] = id }
    suspend fun setLlmModelPath(path: String?) =
        ctx.dataStore.edit { if (path == null) it.remove(K.llmPath) else it[K.llmPath] = path }
    suspend fun setCompareVersions(ids: List<String>) =
        ctx.dataStore.edit { it[K.compare] = ids.joinToString(",") }
}

/**
 * UI language, kept in SharedPreferences because it must be read synchronously in
 * Activity.attachBaseContext. Tags: "en", "tl" (Tagalog), "ceb" (Cebuano/Bisaya).
 */
object LocaleHelper {
    val supported = listOf("en" to "English", "tl" to "Tagalog", "ceb" to "Cebuano / Bisaya")

    fun get(ctx: Context): String = ctx.getSharedPreferences("locale", Context.MODE_PRIVATE).getString("lang", "en") ?: "en"

    fun save(ctx: Context, lang: String) {
        ctx.getSharedPreferences("locale", Context.MODE_PRIVATE).edit().putString("lang", lang).apply()
    }

    fun wrap(base: Context): Context {
        val locale = Locale.forLanguageTag(get(base))
        Locale.setDefault(locale)
        val cfg = Configuration(base.resources.configuration)
        cfg.setLocale(locale)
        return base.createConfigurationContext(cfg)
    }
}
