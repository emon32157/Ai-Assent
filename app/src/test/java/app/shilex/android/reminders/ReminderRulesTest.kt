package app.shilex.android.reminders

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ReminderRulesTest {
    private val now = LocalDateTime.of(2026, 11, 1, 18, 0, 30)

    @Test
    fun minutesFromNow() {
        assertEquals(LocalDateTime.of(2026, 11, 1, 18, 20), ReminderRules.whenDue("", 20, now))
    }

    @Test
    fun clockTimeIsTheNextOne() {
        assertEquals(LocalDateTime.of(2026, 11, 1, 21, 15), ReminderRules.whenDue("21:15", 0, now))
        assertEquals(LocalDateTime.of(2026, 11, 2, 7, 0), ReminderRules.whenDue("07:00", 0, now))
    }

    @Test
    fun fullDateTimeMustBeInTheFuture() {
        assertEquals(LocalDateTime.of(2026, 11, 3, 9, 0), ReminderRules.whenDue("2026-11-03T09:00", 0, now))
        assertNull(ReminderRules.whenDue("2026-10-30T09:00", 0, now))
        assertNull(ReminderRules.whenDue("tomorrow", 0, now))
        assertNull(ReminderRules.whenDue("", 0, now))
    }

    @Test
    fun storageRoundTripsSortedByTime() {
        val later = Reminder(2, "call mummy", LocalDateTime.of(2026, 11, 2, 9, 0))
        val sooner = Reminder(1, "take medicine", LocalDateTime.of(2026, 11, 1, 21, 0))
        val back = ReminderRules.decode(ReminderRules.encode(listOf(later, sooner)))
        assertEquals(listOf(sooner, later), back)
        assertEquals(emptyList<Reminder>(), ReminderRules.decode("not json"))
    }

    @Test
    fun legacyReminderJsonMigratesEpochAndZone() {
        val legacyJson = """[{"id":5,"text":"Doctor appointment","at":"2026-11-05T10:30:00"}]"""
        val decoded = ReminderRules.decode(legacyJson)
        assertEquals(1, decoded.size)
        val reminder = decoded.first()
        assertEquals(5L, reminder.id)
        assertEquals("Doctor appointment", reminder.text)
        assertEquals(LocalDateTime.of(2026, 11, 5, 10, 30), reminder.at)
        assertTrue(reminder.epochMillis > 0)
        assertEquals(ZoneId.systemDefault().id, reminder.zoneId)
    }

    @Test
    fun cancelMatchesWords() {
        val list = listOf(Reminder(1, "Take medicine", now), Reminder(2, "Call Mummy", now))
        assertEquals(listOf(2L), ReminderRules.matching(list, "mummy").map { it.id })
        assertEquals(emptyList<Reminder>(), ReminderRules.matching(list, " "))
    }
}
