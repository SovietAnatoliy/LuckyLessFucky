package com.luckylessfucky.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "luckylessfucky_prefs")

data class UserSettingsState(
    val userProfileId: String = "",
    val username: String = DEFAULT_USERNAME,
    val activeTargetProfileId: String? = null,
    val isOverlayEnabled: Boolean = false,
    val isAutoScanEnabled: Boolean = false
) {
    companion object {
        const val DEFAULT_USERNAME = "Trainer"
    }
}

class AppPreferencesRepository(private val context: Context) {

    private object Keys {
        val USER_PROFILE_ID = stringPreferencesKey("user_profile_id")
        val USERNAME = stringPreferencesKey("username")
        val ACTIVE_TARGET_PROFILE_ID = stringPreferencesKey("active_target_profile_id")
        val IS_OVERLAY_ENABLED = booleanPreferencesKey("is_overlay_enabled")
        val IS_AUTO_SCAN_ENABLED = booleanPreferencesKey("is_auto_scan_enabled")
    }

    val settingsFlow: Flow<UserSettingsState> = context.dataStore.data.map { prefs ->
        val targetId = prefs[Keys.ACTIVE_TARGET_PROFILE_ID]?.takeIf { it.isNotBlank() }
        UserSettingsState(
            userProfileId = prefs[Keys.USER_PROFILE_ID] ?: "",
            username = prefs[Keys.USERNAME] ?: UserSettingsState.DEFAULT_USERNAME,
            activeTargetProfileId = targetId,
            isOverlayEnabled = prefs[Keys.IS_OVERLAY_ENABLED] ?: false,
            isAutoScanEnabled = prefs[Keys.IS_AUTO_SCAN_ENABLED] ?: false
        )
    }

    suspend fun getOrCreateUserProfileId(): String {
        val current = context.dataStore.data.first()[Keys.USER_PROFILE_ID]
        if (!current.isNullOrBlank()) return current
        val newId = UUID.randomUUID().toString()
        context.dataStore.edit { prefs ->
            prefs[Keys.USER_PROFILE_ID] = newId
        }
        return newId
    }

    suspend fun setUsername(username: String) {
        val trimmed = username.trim().ifEmpty { UserSettingsState.DEFAULT_USERNAME }
        context.dataStore.edit { prefs ->
            prefs[Keys.USERNAME] = trimmed
        }
    }

    suspend fun setUserProfileIdentity(profileId: String, username: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.USER_PROFILE_ID] = profileId.ifBlank { UUID.randomUUID().toString() }
            prefs[Keys.USERNAME] = username.trim().ifEmpty { UserSettingsState.DEFAULT_USERNAME }
        }
    }

    /**
     * Sets the active Lucky Trade Target profile ID, or clears it when null is passed.
     * By default, no profile is the lucky trade Target (null).
     */
    suspend fun setActiveTargetProfileId(profileId: String?) {
        context.dataStore.edit { prefs ->
            if (profileId.isNullOrBlank()) {
                prefs.remove(Keys.ACTIVE_TARGET_PROFILE_ID)
            } else {
                prefs[Keys.ACTIVE_TARGET_PROFILE_ID] = profileId
            }
        }
    }

    suspend fun setOverlayEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.IS_OVERLAY_ENABLED] = enabled
        }
    }

    suspend fun setAutoScanEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.IS_AUTO_SCAN_ENABLED] = enabled
        }
    }
}
