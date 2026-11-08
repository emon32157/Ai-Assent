package app.shilex.android.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import app.shilex.android.notify.Replies
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import org.json.JSONObject
import java.util.Locale

import app.shilex.android.notify.CallConsent

/**
 * Calls and messages. Each one opens the right app with everything filled in and the
 * user taps Call or Send there, so the model never completes these on its own.
 */
object CommsTools {
    fun all(): List<PhoneTool> = listOf(CallContact, CallConfirm, WhatsAppSend, SmsSend)

    private val who = arrayOf(
        Schema.string("name", "Contact name as the user said it. Leave empty if they gave a number."),
        Schema.string("number", "Phone number, only if the user said one."),
        Schema.string("which", "Which of the contact's numbers, e.g. mobile, work, or the last digits."),
    )

    private object CallContact : PhoneTool {
        override val name = "call_contact"

        override fun declaration() = Schema.function(
            name,
            "Resolves a contact's number and prepares a call confirmation. Never dials directly.",
            *who,
        )

        override suspend fun run(args: JSONObject, host: ToolHost): JSONObject {
            val to = when (val r = Recipients.resolve(args, host, "call")) {
                is Recipients.Result.Found -> r
                is Recipients.Result.Answer -> return r.json
            }
            val displayName = to.name.ifBlank { to.number }
            val explicitNumber = args.text("number").count(Char::isDigit) >= 6 &&
                args.text("number").filter(Char::isDigit) == to.number.filter(Char::isDigit)
            val readBack = CallConsent.prepare(displayName, to.number, System.currentTimeMillis(), explicitNumber)
            return Results.done("Say exactly: $readBack Then wait. Only if the user answers yes, call call_confirm.")
        }
    }

    private object CallConfirm : PhoneTool {
        override val name = "call_confirm"

        override fun declaration() = Schema.function(
            name,
            "Places the pending call if the user confirmed with yes. Must only be called after user confirms.",
        )

        override suspend fun run(args: JSONObject, host: ToolHost): JSONObject {
            val outcome = CallConsent.checkAndClear(System.currentTimeMillis())
            val confirmed = when (outcome) {
                is CallConsent.Outcome.Confirmed -> outcome
                is CallConsent.Outcome.Refused -> return Results.failed("refused", outcome.why)
            }
            val number = confirmed.number
            val name = confirmed.name

            // Never auto-call emergency numbers (999, 112, 911, 16xxx short codes): always use ACTION_DIAL for them.
            if (isEmergencyOrSpecial(number)) {
                val dialIntent = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                return host.openApp(dialIntent, "Phone", "Emergency or special service number. Dialing $name in the phone app; tap Call.")
            }

            if (!host.hasPermission(android.Manifest.permission.CALL_PHONE)) {
                host.requestPermission(android.Manifest.permission.CALL_PHONE)
                val dialIntent = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                return host.openApp(dialIntent, "Phone", "Call permission not granted. Dialing $name in the phone app; tap Call.")
            }

            val callIntent = Intent(Intent.ACTION_CALL, Uri.fromParts("tel", number, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return host.openApp(callIntent, "Phone", "Calling $name.")
        }
    }

    fun isEmergencyOrSpecial(number: String): Boolean {
        val digits = number.filter(Char::isDigit)
        if (digits in setOf("999", "112", "911", "100", "101", "102", "108")) return true
        if (digits.length == 5 && digits.startsWith("16")) return true
        if (digits.length in 3..5 && (digits.startsWith("10") || digits.startsWith("16") || digits.startsWith("99"))) return true
        return false
    }

    private object WhatsAppSend : PhoneTool {
        override val name = "whatsapp_send"

        override fun declaration() = Schema.function(
            name,
            "Opens a WhatsApp chat with the message typed in; the user taps Send.",
            *who,
            Schema.string("message", "The message, in the user's words and language.", required = true),
        )

        override suspend fun run(args: JSONObject, host: ToolHost): JSONObject {
            val message = args.text("message")
            if (message.isEmpty()) return Results.failed("no_message", "Ask what the message should say.")
            handsFree(args, WHATSAPP)?.let { return it }
            val to = when (val r = Recipients.resolve(args, host, "message")) {
                is Recipients.Result.Found -> r
                is Recipients.Result.Answer -> return r.json
            }
            val digits = internationalDigits(host.context, to.number)
                ?: return Results.failed("bad_number", "That number doesn't look complete. Ask for it with the country code.")
            val link = Uri.parse("https://api.whatsapp.com/send?phone=$digits&text=${Uri.encode(message)}")
            val intent = Intent(Intent.ACTION_VIEW, link)
            return host.openApp(
                intent,
                "WhatsApp",
                "WhatsApp will open with the message to ${to.name} after you speak; the user checks it and taps Send. " +
                    "It is not sent yet: never say it was sent unless the app tells you it saw it in the chat.",
            ) { SendCheck.watchWhatsApp(message) }
        }
    }

    private object SmsSend : PhoneTool {
        override val name = "sms_send"

        override fun declaration() = Schema.function(
            name,
            "Opens the Messages app with an SMS typed in; the user taps Send.",
            *who,
            Schema.string("message", "The message, in the user's words and language.", required = true),
        )

        override suspend fun run(args: JSONObject, host: ToolHost): JSONObject {
            val message = args.text("message")
            if (message.isEmpty()) return Results.failed("no_message", "Ask what the message should say.")
            handsFree(args, SMS_APPS)?.let { return it }
            val to = when (val r = Recipients.resolve(args, host, "message")) {
                is Recipients.Result.Found -> r
                is Recipients.Result.Answer -> return r.json
            }
            val intent = Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", to.number, null))
                .putExtra("sms_body", message)
            return host.openApp(intent, "Messages", "Messages will open with the SMS to ${to.name} after you speak; the user taps Send.")
        }
    }

    /** E.164 digits without "+", using the SIM's country for local numbers. */
    private fun internationalDigits(context: Context, number: String): String? {
        val telephony = context.getSystemService(TelephonyManager::class.java)
        val region = (telephony?.simCountryIso?.takeIf { it.isNotEmpty() } ?: Locale.getDefault().country)
            .uppercase()
        val e164 = PhoneNumberUtils.formatNumberToE164(number, region)
            ?: number.takeIf { it.trim().startsWith("+") }
        return e164?.filter(Char::isDigit)?.takeIf { it.length >= 8 }
    }
}

/** Turns "Rahul" / "Rahul's work number" / a spoken number into one number, or a question. */
/**
 * If [args]' person just messaged in one of [packages] and the notification has a Reply box,
 * the answer goes through it hands-free (Replies) instead of opening the app.
 */
private fun handsFree(args: JSONObject, packages: Set<String>): JSONObject? {
    val name = args.text("name")
    val id = Replies.idFor(name, packages, System.currentTimeMillis()) ?: return null
    return Results.failed(
        "use_reply",
        "$name just messaged and can be answered without opening the app: call reply_prepare with reply_id $id " +
            "and this message, read its line back and wait for the user's yes.",
    )
}

private val WHATSAPP = setOf("com.whatsapp", "com.whatsapp.w4b")
private val SMS_APPS = setOf("com.google.android.apps.messaging", "com.samsung.android.messaging")

object Recipients {
    sealed interface Result {
        data class Found(val name: String, val number: String) : Result
        data class Answer(val json: JSONObject) : Result
    }

    fun resolve(args: JSONObject, host: ToolHost, verb: String): Result {
        val spoken = args.text("number")
        if (spoken.count(Char::isDigit) >= 6) return Result.Found(args.text("name").ifEmpty { spoken }, spoken)
        val name = args.text("name")
        if (name.isEmpty()) return Result.Answer(Results.failed("no_recipient", "Ask who to $verb."))
        if (!host.hasPermission(ContactsReader.PERMISSION)) {
            host.requestPermission(ContactsReader.PERMISSION)
            return Result.Answer(Results.needsPermission("Contacts"))
        }
        val matches = ContactsReader(host.context).search(name)
        return when {
            matches.isEmpty() -> Result.Answer(
                Results.failed("not_found", "No contact called $name. Ask the user to spell it or say the number."),
            )
            matches.size > 1 -> Result.Answer(
                Results.ask("Several contacts match \"$name\". Ask which one.", matches.take(6).map { it.name }),
            )
            else -> pick(matches.single(), args.text("which"))
        }
    }

    /** One contact: its only number, the one [which] names, or a question. */
    fun pick(contact: Contact, which: String): Result {
        val numbers = contact.numbers
        if (numbers.isEmpty()) return Result.Answer(Results.failed("no_number", "${contact.name} has no phone number saved."))
        if (numbers.size == 1) return Result.Found(contact.name, numbers.single().number)
        val w = which.lowercase().trim()
        val chosen = if (w.isEmpty()) {
            emptyList()
        } else {
            numbers.filter { w in it.label || (w.any(Char::isDigit) && it.number.filter(Char::isDigit).endsWith(w.filter(Char::isDigit))) }
        }
        if (chosen.size == 1) return Result.Found(contact.name, chosen.single().number)
        return Result.Answer(
            Results.ask(
                "${contact.name} has several numbers. Ask which one.",
                numbers.map { "${it.label} ending ${it.number.filter(Char::isDigit).takeLast(4)}" },
            ),
        )
    }
}
