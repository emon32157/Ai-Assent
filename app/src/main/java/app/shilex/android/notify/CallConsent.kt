package app.shilex.android.notify

/**
 * Hands-free confirmation before placing a phone call.
 * Stores a pending call (name, number, timestamp) in memory. Placing the call via
 * call_confirm requires:
 * 1. A call is pending and under 2 minutes old.
 * 2. The user's own speech heard AFTER the read-back finished playing is a clear yes.
 * The pending call is cleared after one use whatever the result.
 */
object CallConsent {
    data class Pending(val name: String, val number: String, val at: Long, val readBack: String)

    @Volatile private var pending: Pending? = null
    private val heard = StringBuilder()
    @Volatile private var readyForConsent = false

    /** Formats a phone number into readable groups, e.g. "01711 234 567" */
    fun formatGrouped(number: String): String {
        val clean = number.trim()
        val digits = clean.filter(Char::isDigit)
        return when {
            digits.length == 11 -> "${digits.take(5)} ${digits.substring(5, 8)} ${digits.substring(8)}"
            digits.length == 10 -> "${digits.take(5)} ${digits.substring(5)}"
            digits.length == 13 && clean.startsWith("+") -> "${clean.take(4)} ${clean.substring(4, 9)} ${clean.substring(9)}"
            clean.length > 7 -> clean.chunked(4).joinToString(" ")
            else -> clean
        }
    }

    @Synchronized
    fun prepare(name: String, number: String, now: Long, explicitNumberSpokenByUser: Boolean = false): String {
        val readBack = if (explicitNumberSpokenByUser) {
            "Call $name?"
        } else {
            "Call $name on ${formatGrouped(number)}?"
        }
        pending = Pending(name, number, now, readBack)
        heard.clear()
        readyForConsent = false
        return readBack
    }

    /** Called once TurnComplete has arrived and speaker is no longer audible. */
    @Synchronized
    fun readBackFinished() {
        readyForConsent = true
    }

    /** Everything the user says or types; only words after a read-back has finished playing count for consent. */
    @Synchronized
    fun heard(text: String) {
        if (pending != null && readyForConsent && text.isNotBlank()) {
            heard.append(' ').append(text)
        }
    }

    @Synchronized
    fun pendingNumber(): String = pending?.number.orEmpty()

    @Synchronized
    fun pendingCall(): Pending? = pending

    @Synchronized
    fun clear() {
        pending = null
        heard.clear()
        readyForConsent = false
    }

    sealed interface Outcome {
        data class Confirmed(val name: String, val number: String) : Outcome
        data class Refused(val why: String) : Outcome
    }

    /**
     * Checks consent and immediately clears the pending call (one-shot).
     */
    @Synchronized
    fun checkAndClear(now: Long): Outcome {
        val p = pending
        val spoken = heard.toString()
        pending = null
        heard.clear()
        readyForConsent = false

        if (p == null) return Outcome.Refused("No call is waiting to be confirmed. Ask who to call first.")
        if (now - p.at > CONSENT_MS) return Outcome.Refused("The confirmation timed out. Ask again if the user still wants to call.")
        if (!VoiceConsent.confirms(spoken, "", p.readBack)) {
            return Outcome.Refused("The user didn't clearly say yes, so the call was not placed. Don't retry unless they ask.")
        }
        return Outcome.Confirmed(p.name, p.number)
    }

    private const val CONSENT_MS = 2 * 60_000L
}
