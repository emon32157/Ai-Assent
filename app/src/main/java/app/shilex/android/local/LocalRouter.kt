package app.shilex.android.local

/** A request the phone handles by itself, without Gemini. */
sealed interface LocalCommand {
    data class SetVolume(val percent: Int) : LocalCommand
    data class StepVolume(val up: Boolean) : LocalCommand
    data class Mute(val on: Boolean) : LocalCommand
    data class Media(val key: MediaKey) : LocalCommand
    data class Torch(val on: Boolean) : LocalCommand
    data object Battery : LocalCommand
    data object Time : LocalCommand
    data object Date : LocalCommand
    data class Timer(val seconds: Int) : LocalCommand
    /** [hour] as said (1-12 with a [period], or 0-23); null period means "the next one". */
    data class Alarm(val hour: Int, val minute: Int, val period: Period?) : LocalCommand
    data class OpenApp(val name: String) : LocalCommand
    data object Stop : LocalCommand
}

enum class MediaKey { Play, Pause, Next, Previous }

enum class Period { Am, Pm }

/**
 * Matches short English requests ("open YouTube", "set volume to 30 percent")
 * to [LocalCommand]s. Pure and deterministic, so it is unit-tested. Anything it isn't sure
 * about returns null and goes to Gemini instead: a wrong local guess is worse than a slower
 * right answer.
 */
object LocalRouter {
    /** The first recogniser alternative that matches wins. */
    fun match(alternatives: List<String>): LocalCommand? = alternatives.firstNotNullOfOrNull { match(it) }

    fun isBengali(text: String): Boolean = text.any { it in '\u0980'..'\u09FF' }

    fun match(text: String): LocalCommand? {
        if (isBengali(text)) return null
        val s = normalize(text)
        if (s.isEmpty()) return null
        if (MORE.containsMatchIn(s)) return null // several requests or a message: Gemini's job
        return stop(s) ?: timer(s) ?: alarm(s) ?: torch(s) ?: volume(s) ?: mute(s) ?: media(s) ?: battery(s) ?: time(s)
            ?: openApp(s)
    }

    fun normalize(text: String): String {
        val words = text.lowercase()
            .replace("%", " percent ")
            .replace(Regex("\\ba\\.?\\s?m\\b\\.?"), "am")
            .replace(Regex("\\bp\\.?\\s?m\\b\\.?"), "pm")
            .replace(Regex("[^a-z0-9: ]"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
        var s = " ${numbers(words)} "
        for ((heard, meant) in SOUNDS) s = s.replace(" $heard ", " $meant ")
        for (lead in LEADS) if (s.startsWith(" $lead ")) s = s.removePrefix(" $lead")
        for (filler in FILLERS) s = s.replace(" $filler ", " ")
        for (tail in TAILS) if (s.endsWith(" $tail ")) s = s.removeSuffix("$tail ")
        return s.replace(Regex("\\s+"), " ").trim()
    }

    /** "thirty five" → "35", "one hundred" → "100"; recognisers spell numbers out. */
    fun numbers(text: String): String {
        val out = mutableListOf<String>()
        for (word in text.split(" ")) {
            val n = ONES.indexOf(word).takeIf { it >= 0 } ?: TENS.indexOf(word).takeIf { it >= 2 }?.times(10)
            val last = out.lastOrNull()?.toIntOrNull()
            when {
                word == "hundred" && last != null && last < 10 -> out[out.size - 1] = (last * 100).toString()
                word == "hundred" -> out += "100"
                n == null -> out += word
                n in 1..9 && last != null && last >= 20 && last % 10 == 0 && last < 100 -> out[out.size - 1] = (last + n).toString()
                else -> out += n.toString()
            }
        }
        return out.joinToString(" ")
    }

    private fun has(s: String, vararg words: String) = words.any { Regex("\\b$it\\b").containsMatchIn(s) }

    private fun stop(s: String) = if (s in STOPS) LocalCommand.Stop else null

    private fun timer(s: String): LocalCommand? {
        if (!has(s, "timer")) return null
        var seconds = 0
        Regex("(\\d+)\\s*(seconds?|secs?|minutes?|mins?|hours?|hrs?)\\b").findAll(s).forEach {
            val n = it.groupValues[1].toInt()
            val unit = it.groupValues[2]
            seconds += when {
                unit.startsWith("s") -> n
                unit.startsWith("m") -> n * 60
                else -> n * 3600
            }
        }
        return if (seconds in 1..86_400) LocalCommand.Timer(seconds) else null
    }

    private fun alarm(s: String): LocalCommand? {
        if (!has(s, "alarm") || has(s, "cancel", "delete", "off")) return null
        val time = Regex("\\b(\\d{1,2})(?:[: ](\\d{2}))?\\b").find(s) ?: return null
        val hour = time.groupValues[1].toInt()
        val minute = time.groupValues[2].ifEmpty { "0" }.toInt()
        if (hour > 23 || minute > 59) return null
        val night = has(s, "night")
        val period = when {
            hour > 12 || hour == 0 -> null
            has(s, "am", "morning") -> Period.Am
            night && hour in 1..4 -> Period.Am
            night || has(s, "pm", "evening", "afternoon") -> Period.Pm
            else -> null
        }
        return LocalCommand.Alarm(hour, minute, period)
    }

    private fun torch(s: String): LocalCommand? {
        if (!has(s, "torch", "flashlight", "flash light", "flash")) return null
        return when {
            has(s, "off", "of") -> LocalCommand.Torch(false)
            has(s, "on", "start") -> LocalCommand.Torch(true)
            else -> null
        }
    }

    private fun volume(s: String): LocalCommand? {
        if (!has(s, "volume", "sound")) return null
        Regex("\\b(\\d{1,3})\\b").find(s)?.let { return LocalCommand.SetVolume(it.groupValues[1].toInt().coerceIn(0, 100)) }
        return when {
            has(s, "max", "maximum", "full") -> LocalCommand.SetVolume(100)
            has(s, "unmute") -> LocalCommand.Mute(false)
            has(s, "mute", "zero") -> LocalCommand.Mute(true)
            has(s, "up", "increase", "louder", "high", "raise") -> LocalCommand.StepVolume(true)
            has(s, "down", "decrease", "lower", "low", "reduce") -> LocalCommand.StepVolume(false)
            else -> null
        }
    }

    private fun mute(s: String): LocalCommand? = when {
        Regex("^(phone |mobile )?unmute( phone| mobile)?$").matches(s) -> LocalCommand.Mute(false)
        Regex("^(phone |mobile )?(mute|silent)( phone| mobile| mode)?( on)?$").matches(s) -> LocalCommand.Mute(true)
        else -> null
    }

    private fun media(s: String): LocalCommand? {
        val music = has(s, "music", "song", "track")
        return when {
            has(s, "pause") || (music && has(s, "stop")) -> LocalCommand.Media(MediaKey.Pause)
            s in PLAYS -> LocalCommand.Media(MediaKey.Play)
            s in setOf("next", "skip") || (music && has(s, "next", "skip")) -> LocalCommand.Media(MediaKey.Next)
            s in setOf("previous", "back") || (music && has(s, "previous")) ->
                LocalCommand.Media(MediaKey.Previous)
            else -> null
        }
    }

    private fun battery(s: String): LocalCommand? =
        if (has(s, "battery", "charge", "charging") && !has(s, "saver", "save", "mode")) LocalCommand.Battery else null

    private fun time(s: String): LocalCommand? = when {
        Regex("\\btime in \\w+").containsMatchIn(s) -> null // another city: Gemini knows
        has(s, "time") -> LocalCommand.Time
        has(s, "date", "what day", "which day") -> LocalCommand.Date
        else -> null
    }

    private fun openApp(s: String): LocalCommand? {
        val name = OPEN_FIRST.matchEntire(s)?.groupValues?.get(2) ?: OPEN_LAST.matchEntire(s)?.groupValues?.get(1) ?: return null
        val clean = name.split(" ").filterNot { it in setOf("app", "the", "my", "application") }.joinToString(" ")
        if (clean.isEmpty() || clean.split(" ").size > 3 || clean in NOT_APPS) return null
        if (clean.contains("chatgpt") && has(s, "voice")) return null
        return LocalCommand.OpenApp(clean)
    }

    private val ONES = listOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve",
        "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen",
    )
    private val TENS = listOf("", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety")
    private val SOUNDS = listOf(
        "what s up" to "whatsapp", "whats up" to "whatsapp", "whats app" to "whatsapp", "what s app" to "whatsapp",
        "you tube" to "youtube", "flash light" to "flashlight",
    )
    private val LEADS = listOf("hey jarvis", "hi jarvis", "ok jarvis", "okay jarvis", "jarvis", "hey shilex", "shilex")
    private val FILLERS = listOf("please", "plz", "can you", "could you", "the")
    private val TAILS = listOf("please")
    private val STOPS = setOf(
        "stop", "cancel", "never mind", "nevermind",
        "go to sleep", "standby", "that s all", "thats all", "bye",
    )
    private val PLAYS = setOf(
        "play", "resume", "play music", "resume music", "music play", "music resume", "music on",
        "continue music", "play song",
    )
    private val MORE = Regex("\\b(and|then|message|msg|call|send|search|on whatsapp)\\b.*\\b\\w+")
    private val NOT_APPS = setOf("timer", "alarm", "music", "torch", "flashlight", "song")
    private val OPEN_FIRST = Regex("^(open|launch|start)\\s+(.+)$")
    private val OPEN_LAST = Regex("^(.+?)\\s+(open|start|launch)$")
}
