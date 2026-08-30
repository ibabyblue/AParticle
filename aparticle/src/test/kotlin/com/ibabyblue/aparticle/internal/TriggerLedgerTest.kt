package com.ibabyblue.aparticle.internal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TriggerLedgerTest {
    @Test
    fun initialAndEqualValuesDoNotPlay() {
        val ledger = TriggerLedger(0)

        assertFalse(ledger.observe(0))
        assertTrue(ledger.observe(1))
        assertFalse(ledger.observe(1))
        assertTrue(ledger.observe(2))
    }
}
