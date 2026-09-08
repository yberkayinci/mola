package com.yberkayinci.mola.data

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InterventionRecordTest {
    @Test
    fun groupsRecordsUsingTheSuppliedLocalZone() {
        val record = InterventionRecord(
            timestampEpochMillis = 0L,
            usageMinutes = 60,
            text = "örnek",
            choice = UserChoice.CONTINUE,
        )

        assertTrue(record.occursOn(LocalDate.of(1970, 1, 1), ZoneId.of("UTC")))
        assertFalse(record.occursOn(LocalDate.of(1970, 1, 2), ZoneId.of("UTC")))
    }
}
