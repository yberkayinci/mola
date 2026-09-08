package com.yberkayinci.mola.data

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoDataSeederTest {
    @Test
    fun createsFourDatesWithEightNonFutureRecordsToday() {
        val now = LocalDateTime.of(2026, 8, 29, 12, 0)
        val zone = ZoneId.systemDefault()
        val nowMillis = now.atZone(zone).toInstant().toEpochMilli()
        val records = DemoDataSeeder.records(now)

        assertTrue(records.size == 11)
        assertTrue(records.map { it.localDate(zone) }.distinct().size == 4)
        assertTrue(records.count { it.occursOn(now.toLocalDate(), zone) } == 8)
        assertTrue(records.all { it.timestampEpochMillis <= nowMillis })
    }
}
