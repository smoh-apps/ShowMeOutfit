package com.smoh.showmeoutfit.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.smoh.showmeoutfit.domain.model.AgeBand
import com.smoh.showmeoutfit.domain.model.GenderPreference
import com.smoh.showmeoutfit.domain.model.LocationInfo
import com.smoh.showmeoutfit.domain.model.ThermalSensitivity
import com.smoh.showmeoutfit.domain.model.UserProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class UserPreferencesStoreTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: UserPreferencesStore

    // Mirrors the private keys in UserPreferencesStore so the tests can write payloads that the
    // store itself would never produce (corrupt writes, Sprint 1 payloads).
    private val rawGender = stringPreferencesKey("gender_preference")
    private val rawThermal = stringPreferencesKey("thermal_sensitivity")
    private val rawHour = intPreferencesKey("notification_hour")
    private val rawMinute = intPreferencesKey("notification_minute")

    @Before
    fun setUp() {
        val testDispatcher = UnconfinedTestDispatcher()
        val testScope = TestScope(testDispatcher)
        dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { tmpFolder.newFile("user_prefs_test.preferences_pb") }
        )
        store = UserPreferencesStore(dataStore)
    }

    @Test
    fun `getUserProfile returns default UserProfile when no data saved`() = runTest {
        val profile = store.getUserProfile().first()
        assertEquals(GenderPreference.ALL, profile.genderPreference)
        assertEquals(ThermalSensitivity.NEUTRAL, profile.thermalSensitivity)
        assertTrue(profile.isNotificationEnabled)
        assertEquals(LocalTime.of(7, 0), profile.morningNotificationTime)
        assertNull(profile.selectedLocation)
        assertEquals(AgeBand.UNSPECIFIED, profile.ageBand)
    }

    @Test
    fun `saveUserProfile and getUserProfile round-trips all fields`() = runTest {
        val profile = UserProfile(
            genderPreference = GenderPreference.WOMEN,
            thermalSensitivity = ThermalSensitivity.FEELS_COLD_EASILY,
            morningNotificationTime = LocalTime.of(6, 30),
            isNotificationEnabled = false,
            selectedLocation = LocationInfo(
                latitude = 48.8566,
                longitude = 2.3522,
                cityName = "Paris",
                country = "France",
                timezone = "Europe/Paris"
            )
        )
        store.saveUserProfile(profile)

        val retrieved = store.getUserProfile().first()
        assertEquals(GenderPreference.WOMEN, retrieved.genderPreference)
        assertEquals(ThermalSensitivity.FEELS_COLD_EASILY, retrieved.thermalSensitivity)
        assertEquals(LocalTime.of(6, 30), retrieved.morningNotificationTime)
        assertEquals(false, retrieved.isNotificationEnabled)
        assertNotNull(retrieved.selectedLocation)
        assertEquals("Paris", retrieved.selectedLocation!!.cityName)
        assertEquals(48.8566, retrieved.selectedLocation!!.latitude, 0.001)
        assertEquals("France", retrieved.selectedLocation!!.country)
    }

    @Test
    fun `saveUserProfile with null location clears location fields`() = runTest {
        // First save with location
        store.saveUserProfile(
            UserProfile(
                selectedLocation = LocationInfo(1.0, 2.0, "Tokyo", "Japan")
            )
        )
        // Then overwrite with no location
        store.saveUserProfile(UserProfile(selectedLocation = null))

        val retrieved = store.getUserProfile().first()
        assertNull(retrieved.selectedLocation)
    }

    @Test
    fun `thermal sensitivity offset correctly stored and retrieved`() = runTest {
        store.saveUserProfile(UserProfile(thermalSensitivity = ThermalSensitivity.FEELS_WARM_EASILY))
        val retrieved = store.getUserProfile().first()
        assertEquals(ThermalSensitivity.FEELS_WARM_EASILY, retrieved.thermalSensitivity)
        assertEquals(-2.5, retrieved.thermalSensitivity.tempOffsetCelsius, 0.001)
    }

    @Test
    fun `age band and configuration flag round-trip`() = runTest {
        store.saveUserProfile(
            UserProfile(ageBand = AgeBand.CHILD_6_12, isConfigured = true)
        )

        val retrieved = store.getUserProfile().first()

        assertEquals(AgeBand.CHILD_6_12, retrieved.ageBand)
        assertTrue(retrieved.isConfigured)
    }

    @Test
    fun `a corrupt notification hour falls back to the default instead of crashing`() = runTest {
        // Simulates a hand-edited preference file or a partial write: hour 99 is not a valid hour.
        dataStore.edit { prefs ->
            prefs[rawHour] = 99
            prefs[rawMinute] = 15
        }

        val profile = store.getUserProfile().first()

        assertEquals(LocalTime.of(7, 0), profile.morningNotificationTime)
    }

    @Test
    fun `unknown persisted enum values fall back to neutral defaults`() = runTest {
        dataStore.edit { prefs ->
            prefs[rawGender] = "ROBOT"
            prefs[rawThermal] = "SUPERHUMAN"
        }

        val profile = store.getUserProfile().first()

        assertEquals(GenderPreference.ALL, profile.genderPreference)
        assertEquals(ThermalSensitivity.NEUTRAL, profile.thermalSensitivity)
    }

    @Test
    fun `a profile written by Sprint 1 loads with the new fields at their defaults`() = runTest {
        // Sprint 1 persisted only these keys; ageBand and isConfigured did not exist yet.
        dataStore.edit { prefs ->
            prefs[rawGender] = "MEN"
            prefs[rawThermal] = "NEUTRAL"
        }

        val profile = store.getUserProfile().first()

        assertEquals(GenderPreference.MEN, profile.genderPreference)
        assertEquals(AgeBand.UNSPECIFIED, profile.ageBand)
        assertEquals(false, profile.isConfigured)
    }
}
