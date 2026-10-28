package com.smoh.showmeoutfit.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.smoh.showmeoutfit.domain.model.AgeBand
import com.smoh.showmeoutfit.domain.model.GenderPreference
import com.smoh.showmeoutfit.domain.model.LocationInfo
import com.smoh.showmeoutfit.domain.model.ThermalSensitivity
import com.smoh.showmeoutfit.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalTime

class UserPreferencesStore(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        private val KEY_GENDER = stringPreferencesKey("gender_preference")
        private val KEY_AGE_BAND = stringPreferencesKey("age_band")
        private val KEY_IS_CONFIGURED = booleanPreferencesKey("profile_is_configured")
        private val KEY_THERMAL = stringPreferencesKey("thermal_sensitivity")
        private val KEY_NOTIF_ENABLED = booleanPreferencesKey("notification_enabled")
        private val KEY_NOTIF_HOUR = intPreferencesKey("notification_hour")
        private val KEY_NOTIF_MINUTE = intPreferencesKey("notification_minute")
        private val KEY_LOCATION_LAT = doublePreferencesKey("location_lat")
        private val KEY_LOCATION_LON = doublePreferencesKey("location_lon")
        private val KEY_LOCATION_CITY = stringPreferencesKey("location_city")
        private val KEY_LOCATION_COUNTRY = stringPreferencesKey("location_country")
        private val KEY_LOCATION_TIMEZONE = stringPreferencesKey("location_timezone")
    }

    fun getUserProfile(): Flow<UserProfile> = dataStore.data.map { prefs ->
        val hour = prefs[KEY_NOTIF_HOUR] ?: 7
        val minute = prefs[KEY_NOTIF_MINUTE] ?: 0
        val hasLocation = prefs.contains(KEY_LOCATION_LAT) && prefs.contains(KEY_LOCATION_LON)
        UserProfile(
            genderPreference = prefs[KEY_GENDER]
                ?.let { runCatching { GenderPreference.valueOf(it) }.getOrNull() }
                ?: GenderPreference.ALL,
            ageBand = prefs[KEY_AGE_BAND]
                ?.let { runCatching { AgeBand.valueOf(it) }.getOrNull() }
                ?: AgeBand.UNSPECIFIED,
            isConfigured = prefs[KEY_IS_CONFIGURED] ?: false,
            thermalSensitivity = prefs[KEY_THERMAL]
                ?.let { runCatching { ThermalSensitivity.valueOf(it) }.getOrNull() }
                ?: ThermalSensitivity.NEUTRAL,
            morningNotificationTime = LocalTime.of(hour, minute),
            isNotificationEnabled = prefs[KEY_NOTIF_ENABLED] ?: true,
            selectedLocation = if (hasLocation) {
                LocationInfo(
                    latitude = prefs[KEY_LOCATION_LAT]!!,
                    longitude = prefs[KEY_LOCATION_LON]!!,
                    cityName = prefs[KEY_LOCATION_CITY] ?: "",
                    country = prefs[KEY_LOCATION_COUNTRY],
                    timezone = prefs[KEY_LOCATION_TIMEZONE]
                )
            } else null
        )
    }

    suspend fun saveUserProfile(profile: UserProfile) {
        dataStore.edit { prefs ->
            prefs[KEY_GENDER] = profile.genderPreference.name
            prefs[KEY_AGE_BAND] = profile.ageBand.name
            prefs[KEY_IS_CONFIGURED] = profile.isConfigured
            prefs[KEY_THERMAL] = profile.thermalSensitivity.name
            prefs[KEY_NOTIF_ENABLED] = profile.isNotificationEnabled
            prefs[KEY_NOTIF_HOUR] = profile.morningNotificationTime.hour
            prefs[KEY_NOTIF_MINUTE] = profile.morningNotificationTime.minute
            if (profile.selectedLocation != null) {
                val loc = profile.selectedLocation
                prefs[KEY_LOCATION_LAT] = loc.latitude
                prefs[KEY_LOCATION_LON] = loc.longitude
                prefs[KEY_LOCATION_CITY] = loc.cityName
                if (loc.country != null) prefs[KEY_LOCATION_COUNTRY] = loc.country else prefs.remove(KEY_LOCATION_COUNTRY)
                if (loc.timezone != null) prefs[KEY_LOCATION_TIMEZONE] = loc.timezone else prefs.remove(KEY_LOCATION_TIMEZONE)
            } else {
                prefs.remove(KEY_LOCATION_LAT)
                prefs.remove(KEY_LOCATION_LON)
                prefs.remove(KEY_LOCATION_CITY)
                prefs.remove(KEY_LOCATION_COUNTRY)
                prefs.remove(KEY_LOCATION_TIMEZONE)
            }
        }
    }
}
