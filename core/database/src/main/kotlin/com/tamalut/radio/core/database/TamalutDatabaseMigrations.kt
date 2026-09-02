package com.tamalut.radio.core.database

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

const val TAMALUT_DATABASE_VERSION = 2
const val LEGACY_CUSTOM_RADIO_CATEGORY = "Altro"

internal val MIGRATION_1_2_SQL = listOf(
    "ALTER TABLE radio_stations ADD COLUMN custom_category TEXT",
    "UPDATE radio_stations SET custom_category = 'Altro' WHERE is_custom = 1 AND (custom_category IS NULL OR TRIM(custom_category) = '')",
)

val MIGRATION_1_2 = object : Migration(1, 2) {
    override suspend fun migrate(connection: SQLiteConnection) {
        MIGRATION_1_2_SQL.forEach { statement -> connection.execSQL(statement) }
    }
}
