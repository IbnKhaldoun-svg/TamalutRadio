package com.tamalut.radio.core.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TamalutDatabaseMigrationContractTest {
    @Test
    fun v1ToV2AddsNullableCategoryAndBackfillsOnlyLegacyCustomRows() {
        assertEquals(2, TAMALUT_DATABASE_VERSION)
        assertEquals(2, MIGRATION_1_2_SQL.size)
        assertEquals(
            "ALTER TABLE radio_stations ADD COLUMN custom_category TEXT",
            MIGRATION_1_2_SQL[0],
        )
        val backfill = MIGRATION_1_2_SQL[1]
        assertTrue(backfill.contains("SET custom_category = 'Altro'"))
        assertTrue(backfill.contains("WHERE is_custom = 1"))
        assertTrue(backfill.contains("custom_category IS NULL"))
    }
}
