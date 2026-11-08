package app.shilex.android.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CallConsentTest {
    @Before
    fun setup() {
        CallConsent.clear()
    }

    @Test
    fun speechBeforeReadBackFinishedIsIgnored() {
        CallConsent.prepare("Rahul", "+8801712345678", 10_000L)
        CallConsent.heard("yes")
        val outcome = CallConsent.checkAndClear(10_500L)
        assertTrue(outcome is CallConsent.Outcome.Refused)
    }

    @Test
    fun yesAfterReadBackFinishedConfirms() {
        CallConsent.prepare("Rahul", "+8801712345678", 10_000L)
        CallConsent.readBackFinished()
        CallConsent.heard("yes")
        val outcome = CallConsent.checkAndClear(10_500L)
        assertTrue(outcome is CallConsent.Outcome.Confirmed)
        assertEquals("Rahul", (outcome as CallConsent.Outcome.Confirmed).name)
        assertEquals("+8801712345678", outcome.number)

        // One-shot: checking clears it
        val next = CallConsent.checkAndClear(10_600L)
        assertTrue(next is CallConsent.Outcome.Refused)
    }

    @Test
    fun readBackIncludesGroupedNumber() {
        val readBack = CallConsent.prepare("Rahim", "01711234567", 10_000L, explicitNumberSpokenByUser = false)
        assertEquals("Call Rahim on 01711 234 567?", readBack)

        val nameOnlyReadBack = CallConsent.prepare("Rahim", "01711234567", 10_000L, explicitNumberSpokenByUser = true)
        assertEquals("Call Rahim?", nameOnlyReadBack)
    }

    @Test
    fun bengaliYesConfirms() {
        CallConsent.prepare("রফিক", "01811223344", 20_000L)
        CallConsent.readBackFinished()
        CallConsent.heard("হ্যাঁ কল করো")
        val outcome = CallConsent.checkAndClear(20_500L)
        assertTrue(outcome is CallConsent.Outcome.Confirmed)
        assertEquals("01811223344", (outcome as CallConsent.Outcome.Confirmed).number)
    }

    @Test
    fun noCancelsPendingCall() {
        CallConsent.prepare("Rahul", "+8801712345678", 30_000L)
        CallConsent.readBackFinished()
        CallConsent.heard("no wait don't call")
        val outcome = CallConsent.checkAndClear(30_500L)
        assertTrue(outcome is CallConsent.Outcome.Refused)
    }

    @Test
    fun timeoutExpiresConsent() {
        val now = 100_000L
        CallConsent.prepare("Rahul", "+8801712345678", now)
        CallConsent.readBackFinished()
        CallConsent.heard("yes")
        val outcome = CallConsent.checkAndClear(now + 130_000L) // past 2 minutes (120_000 ms)
        assertTrue(outcome is CallConsent.Outcome.Refused)
    }
}
