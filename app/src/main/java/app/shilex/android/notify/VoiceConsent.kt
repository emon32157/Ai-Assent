package app.shilex.android.notify

/**
 * Whether what the user said after JARVIS read a reply or call back is a clear "yes, do it". The
 * model never decides this: the words come from the user's own speech (or typing) heard
 * after the read-back. Anything doubtful counts as no. Pure, so it is unit-tested.
 */
object VoiceConsent {
    // Word boundary that respects Indic vowels/modifiers (\p{M}) as part of words
    // (?<![\p{L}\p{M}\p{N}]) ... (?![\p{L}\p{M}\p{N}])
    private val NO = Regex(
        """(?<![\p{L}\p{M}\p{N}])(no|nope|not|don't|dont|stop|cancel|wait|wrong|change|nahi|nahin|na|mat|ruko|ruk|galat|badlo|नहीं|मत|रुको|रुक|না|নাহ|থামো|বাতিল|করো\s*না|দিও\s*না|থাম|হবে\s*না)(?![\p{L}\p{M}\p{N}])""",
        RegexOption.IGNORE_CASE,
    )

    private val YES = Regex(
        """(?<![\p{L}\p{M}\p{N}])(yes|yeah|yep|yup|ok|okay|sure|send|send\s+it|call|call\s+him|call\s+her|go\s+ahead|correct|right|haan|han|haa|ha|ji|bhej|bhejo|bhej\s+do|kar\s+do|theek|thik|हाँ|हां|हा|जी|भेजो|भेজ\s+दो|ठीक|হ্যাঁ|হ্যা|জি|ঠিক\s*আছে|করো|কল|দাও|পাঠাও|পাঠিয়ে\s*দাও|পাঠিয়ে\s*দাও)(?![\p{L}\p{M}\p{N}])""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * [heard]: the user's words since the read-back. [message]: the reply being confirmed.
     * [readBack]: what JARVIS said; repeating its words (the name, number, the app) means echo, not consent.
     */
    fun confirms(heard: String, message: String, readBack: String = ""): Boolean {
        val said = heard.trim()
        if (said.isEmpty() || said.split(Regex("\\s+")).size > MAX_WORDS) return false
        // "No" always wins: any negation anywhere disqualifies consent
        if (NO.containsMatchIn(said)) return false
        // JARVIS's own read-back leaking into the mic would contain the message: not the user.
        val msg = message.lowercase().trim()
        if (msg.length >= 4 && said.lowercase().contains(msg)) return false
        // Number digits or words from the read-back repeated by the user or echo
        val echoed = words(said).count { it in words(readBack) - setOf("send", "call", "করো", "কল", "দাও", "পাঠাও") }
        if (echoed >= 2) return false
        return YES.containsMatchIn(said)
    }

    /** Tokenizes words keeping letters, vowel marks/combining marks (\p{M}), and digits. */
    fun words(text: String): Set<String> =
        text.lowercase()
            .split(Regex("""[^\p{L}\p{M}\p{N}]+"""))
            .filter { it.length >= 3 }
            .toSet()

    private const val MAX_WORDS = 8
}
