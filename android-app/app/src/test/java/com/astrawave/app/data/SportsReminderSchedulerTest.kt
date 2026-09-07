package com.astrawave.app.data

import org.junit.Assert.assertNotNull
import org.junit.Test

class SportsReminderSchedulerTest {
    @Test
    fun parsesExistingSportsDateTimeFormat() {
        assertNotNull(SportsReminderScheduler.parseStartMs("2026-09-07 19:30:00"))
        assertNotNull(SportsReminderScheduler.parseStartMs("2026-09-07 19:30"))
    }

    @Test
    fun parsesIsoSportsDateTime() {
        assertNotNull(SportsReminderScheduler.parseStartMs("2026-09-07T19:30:00Z"))
    }
}
