package com.sermonprep.app.data.repo

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val ctx: Context) {
    private val THEME = stringPreferencesKey("theme")          // system | light | dark
    private val VERSION = stringPreferencesKey("version_id")
    private val LANG = stringPreferencesKey("language")        // "" | en | fil | ceb
    private val SEEDED = booleanPreferencesKey("seeded")
    private val MODEL = stringPreferencesKey("model_path")

    val theme: Flow<String> = ctx.dataStore.data.map { it[THEME] ?: "system" }
    val versionId: Flow<String?> = ctx.dataStore.data.map { it[VERSION] }
    val language: Flow<String> = ctx.dataStore.data.map { it[LANG] ?: "" }
    val modelPath: Flow<String?> = ctx.dataStore.data.map { it[MODEL] }

    suspend fun setTheme(v: String) = ctx.dataStore.edit { it[THEME] = v }
    suspend fun setVersion(v: String) = ctx.dataStore.edit { it[VERSION] = v }
    suspend fun setLanguage(v: String) = ctx.dataStore.edit { it[LANG] = v }
    suspend fun setModelPath(v: String?) = ctx.dataStore.edit { if (v == null) it.remove(MODEL) else it[MODEL] = v }

    suspend fun isSeeded(): Boolean = ctx.dataStore.data.first()[SEEDED] == true
    suspend fun markSeeded() { ctx.dataStore.edit { it[SEEDED] = true } }
}
