package com.astrawave.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeTvQualificationTest {
    @Test
    fun requiresThreeReliableRunsAndGuideCoverage() {
        val passingRun = List(20) { FreeTvProbe(true, it < 16) }
        assertTrue(FreeTvQualification.evaluate(listOf(passingRun, passingRun, passingRun)).qualified)
        assertFalse(FreeTvQualification.evaluate(listOf(passingRun, passingRun)).qualified)
    }

    @Test
    fun deadPlayableEntryFailsQualification() {
        val run = List(20) { index -> FreeTvProbe(index != 0, true, exposedAsPlayable = true) }
        assertFalse(FreeTvQualification.evaluate(listOf(run, run, run)).qualified)
    }
}
