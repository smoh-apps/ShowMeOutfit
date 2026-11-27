package com.smoh.showmeoutfit.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.smoh.showmeoutfit.core.database.dao.WardrobeDao
import com.smoh.showmeoutfit.core.database.dao.WeatherSnapshotDao
import com.smoh.showmeoutfit.core.database.entity.WardrobeEntity
import com.smoh.showmeoutfit.core.database.entity.WeatherSnapshotEntity

/**
 * v2 adds the user wardrobe next to the weather cache.
 *
 * The file name stays "weather_db": v1.1.0 devices already own that file, and an in-place
 * upgrade through [MIGRATION_1_2] is what keeps the cached forecast (and the user's expectation
 * that the app opens instantly) intact.
 */
@Database(
    entities = [WeatherSnapshotEntity::class, WardrobeEntity::class],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun weatherSnapshotDao(): WeatherSnapshotDao

    abstract fun wardrobeDao(): WardrobeDao

    companion object {
        const val DATABASE_NAME = "weather_db"
    }
}

/**
 * Adds the `wardrobe_items` table (and its category index) without touching `weather_snapshots`.
 *
 * The SQL mirrors [WardrobeEntity] exactly; Room validates the resulting schema on open and throws
 * when a column type, nullability, or index name drifts.
 */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `wardrobe_items` (" +
                "`id` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`category` TEXT NOT NULL, " +
                "`garmentId` TEXT NOT NULL, " +
                "`fit` TEXT NOT NULL, " +
                "`isWaterResistant` INTEGER NOT NULL, " +
                "`photoRelativePath` TEXT, " +
                "`createdAtEpochMillis` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_wardrobe_items_category` " +
                "ON `wardrobe_items` (`category`)"
        )
    }
}
