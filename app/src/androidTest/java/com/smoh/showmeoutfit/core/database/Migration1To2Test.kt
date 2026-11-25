package com.smoh.showmeoutfit.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the in-place v1.1.0 -> v2.0.0 upgrade.
 *
 * The schemas come from the exported JSON checked in under `app/schemas`, which is wired into the
 * androidTest assets. The test fails if the migration SQL drifts from `WardrobeEntity`, because
 * `runMigrationsAndValidate` compares the migrated schema with the exported v2 schema.
 */
@RunWith(AndroidJUnit4::class)
class Migration1To2Test {

    private companion object {
        const val TEST_DB = "migration-1-2-test.db"
    }

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrate1To2_keepsWeatherRowsAndAddsWardrobeTableWithIndex() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                """
                INSERT INTO weather_snapshots (
                    fetchedAtEpochMs, latitude, longitude, cityName, country, timezone,
                    currentTempCelsius, currentApparentTempCelsius, currentHumidityPercent,
                    currentWeatherCode, currentWindSpeedKmh, currentTimeIso,
                    dailyMaxTempCelsius, dailyMinTempCelsius, dailyMaxPrecipProb,
                    dailyWeatherCode, dailyDateIso, hourlyJson
                ) VALUES (
                    1000, 52.52, 13.405, 'Berlin', 'Germany', 'Europe/Berlin',
                    14.0, 12.0, 70,
                    2, 15.0, '2026-10-24T10:00',
                    17.0, 9.0, 20,
                    2, '2026-10-24', '[]'
                )
                """.trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2).apply {
            query("SELECT cityName, currentTempCelsius FROM weather_snapshots").use { cursor ->
                assertTrue("weather_snapshots row was dropped by the migration", cursor.moveToFirst())
                assertEquals("Berlin", cursor.getString(0))
                assertEquals(14.0, cursor.getDouble(1), 0.001)
            }

            execSQL(
                """
                INSERT INTO wardrobe_items (
                    id, name, category, garmentId, fit, isWaterResistant,
                    photoRelativePath, createdAtEpochMillis
                ) VALUES ('w1', 'Navy raincoat', 'OUTER', 'RAINCOAT', 'UNISEX', 1, NULL, 1000)
                """.trimIndent()
            )

            query("SELECT id, name, isWaterResistant FROM wardrobe_items").use { cursor ->
                assertTrue("wardrobe_items table is missing after migration", cursor.moveToFirst())
                assertEquals("w1", cursor.getString(0))
                assertEquals("Navy raincoat", cursor.getString(1))
                assertEquals(1, cursor.getInt(2))
            }

            query(
                "SELECT name FROM sqlite_master WHERE type = 'index' " +
                    "AND name = 'index_wardrobe_items_category'"
            ).use { cursor ->
                assertTrue("index_wardrobe_items_category was not created", cursor.moveToFirst())
            }

            close()
        }
    }

    @Test
    fun migrate1To2_onEmptyWeatherTable_succeeds() {
        helper.createDatabase(TEST_DB, 1).close()

        helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2).apply {
            query("SELECT COUNT(*) FROM weather_snapshots").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
            close()
        }
    }
}
