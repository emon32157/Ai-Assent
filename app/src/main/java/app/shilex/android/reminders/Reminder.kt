package app.shilex.android.reminders

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

data class Reminder(
    val id: Long,
    val text: String,
    val at: LocalDateTime,
    val epochMillis: Long = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
    val zoneId: String = ZoneId.systemDefault().id,
)

/** Pure parts of reminders (time parsing, storage format), unit-tested without a phone. */
object ReminderRules {
    private val SPOKEN = DateTimeFormatter.ofPattern("EEE d MMM, h:mm a")

    /**
     * [at] is "YYYY-MM-DDTHH:MM" or "HH:MM" (today, or tomorrow if that time has passed);
     * [inMinutes] counts from [now]. Returns null if neither gives a future time.
     */
    fun whenDue(at: String, inMinutes: Int, now: LocalDateTime): LocalDateTime? {
        if (inMinutes > 0) return now.plusMinutes(inMinutes.toLong()).withSecond(0).withNano(0)
        val text = at.trim()
        if (text.isEmpty()) return null
        val parsed = try {
            if ('T' in text) {
                LocalDateTime.parse(text)
            } else {
                val time = LocalTime.parse(text)
                val today = LocalDateTime.of(LocalDate.from(now), time)
                if (today.isAfter(now)) today else today.plusDays(1)
            }
        } catch (_: DateTimeParseException) {
            return null
        }
        return parsed.takeIf { it.isAfter(now) }
    }

    fun spoken(at: LocalDateTime): String = at.format(SPOKEN)

    fun encode(reminders: List<Reminder>): String = JSONArray().apply {
        reminders.forEach {
            put(
                JSONObject()
                    .put("id", it.id)
                    .put("text", it.text)
                    .put("at", it.at.toString())
                    .put("epochMillis", it.epochMillis)
                    .put("zoneId", it.zoneId),
            )
        }
    }.toString()

    fun decode(json: String?): List<Reminder> {
        val list = runCatching { JSONArray(json ?: "[]") }.getOrDefault(JSONArray())
        return (0 until list.length()).mapNotNull { i ->
            val item = list.optJSONObject(i) ?: return@mapNotNull null
            runCatching {
                val id = item.getLong("id")
                val text = item.getString("text")
                val atStr = item.getString("at")
                val at = LocalDateTime.parse(atStr)
                val zoneIdStr = item.optString("zoneId").ifBlank { ZoneId.systemDefault().id }
                val zone = runCatching { ZoneId.of(zoneIdStr) }.getOrDefault(ZoneId.systemDefault())
                val epoch = if (item.has("epochMillis")) {
                    item.getLong("epochMillis")
                } else {
                    at.atZone(zone).toInstant().toEpochMilli()
                }
                Reminder(id, text, at, epoch, zone.id)
            }.getOrNull()
        }.sortedBy { it.epochMillis }
    }

    /** Reminders whose text contains [words] (case-insensitive). */
    fun matching(reminders: List<Reminder>, words: String): List<Reminder> {
        val q = words.lowercase().trim()
        return if (q.isEmpty()) emptyList() else reminders.filter { q in it.text.lowercase() }
    }
}
