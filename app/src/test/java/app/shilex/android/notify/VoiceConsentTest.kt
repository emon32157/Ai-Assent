package app.shilex.android.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceConsentTest {
    private val message = "10 minute mein"
    private val readBack = "Send \"10 minute mein\" to Mom on WhatsApp?"

    @Test
    fun clearYesSends() {
        listOf("Yes", "ok send it", "yes send to mom",
            "হ্যাঁ", "হ্যা", "জি", "হুম পাঠাও", "ঠিক আছে", "দাও", "হ্যাঁ পাঠিয়ে দাও")
            .forEach { assertTrue(it, VoiceConsent.confirms(it, message, readBack)) }
    }

    @Test
    fun callConsentYesRecognised() {
        val callReadBack = "Call Rahul on 01711 234 567?"
        listOf("yes", "call him", "হ্যাঁ", "জি", "কল করো", "হ্যাঁ দাও", "call")
            .forEach { assertTrue(it, VoiceConsent.confirms(it, "", callReadBack)) }
    }

    @Test
    fun kolkataAloneIsNotYes() {
        // "কলকাতা" contains "কল", but is not the word "কল"
        assertFalse(VoiceConsent.confirms("কলকাতা", "", "Call Rahul on 01711 234 567?"))
        assertFalse(VoiceConsent.confirms("কলকাতা যাব", "", "Call Rahul on 01711 234 567?"))
    }

    @Test
    fun thikAcheIsYesAndThikAcheNaIsNo() {
        assertTrue(VoiceConsent.confirms("ঠিক আছে", "", "Call Rahul on 01711 234 567?"))
        assertFalse(VoiceConsent.confirms("ঠিক আছে না", "", "Call Rahul on 01711 234 567?"))
        assertFalse(VoiceConsent.confirms("না ঠিক আছে", "", "Call Rahul on 01711 234 567?"))
    }

    @Test
    fun echoOfBengaliReadBackIsNotConsent() {
        val bengaliReadBack = "Call রহিম on 01711 234 567?"
        // Echo repeating contact name and number digits
        assertFalse(VoiceConsent.confirms("রহিম 01711", "", bengaliReadBack))
        assertFalse(VoiceConsent.confirms("রহিম 234 567", "", bengaliReadBack))
    }

    @Test
    fun anythingDoubtfulDoesNot() {
        listOf(
            "", "nahi", "no wait", "haan nahi ruko", "change it to 20 minute", "mat bhejo", "hmm", "who is this",
            "yes but actually let me think about it first okay",
            "না", "নাহ", "না পাঠিও না", "বাতিল", "থামো", "করো না",
        ).forEach { assertFalse(it, VoiceConsent.confirms(it, message, readBack)) }
    }

    @Test
    fun hindiYesNoLongerConsentsButHindiNoStillBlocks() {
        // Hindi YES words are no longer recognised as consent (task 4e)
        listOf("haan", "haan bhej do", "theek hai", "हाँ", "हाँ भेजो").forEach {
            assertFalse(it, VoiceConsent.confirms(it, message, readBack))
        }
        // Hindi NO words still block as safety deny-list
        listOf("nahi", "nahin", "nahi ruko", "mat bhejo", "नहीं", "रुको").forEach {
            assertFalse(it, VoiceConsent.confirms(it, message, readBack))
        }
    }

    @Test
    fun noMixedScriptTokensInConsentRegexes() {
        val bengaliRange = '\u0980'..'\u09FF'
        val devanagariLettersAndMarks = '\u0900'..'\u097F'

        fun checkPattern(patternName: String, regex: Regex) {
            val match = Regex("""\((.+)\)""").find(regex.pattern)
            val inner = match?.groupValues?.get(1) ?: regex.pattern
            val tokens = inner.split("|")
            for (token in tokens) {
                val hasBengali = token.any { it in bengaliRange }
                val hasDevanagari = token.any { it in devanagariLettersAndMarks }
                assertFalse(
                    "Token '$token' in $patternName contains both Bengali and Devanagari characters",
                    hasBengali && hasDevanagari,
                )
            }
        }

        checkPattern("YES", VoiceConsent.YES)
        checkPattern("NO", VoiceConsent.NO)
    }

    @Test
    fun jarvisHearingItsOwnReadBackIsNotConsent() {
        assertFalse(VoiceConsent.confirms("send 10 minute mein to mom on whatsapp", message, readBack))
        // A tiny reply like "ok" can't be told from a yes by itself; the name and app give the echo away.
        assertFalse(VoiceConsent.confirms("send ok to mom on whatsapp", "ok", "Send \"ok\" to Mom on WhatsApp?"))
    }

    @Test
    fun namesMatchWholeWordsOnly() {
        assertTrue(Replies.sameName("mom", "mom"))
        assertTrue(Replies.sameName("rahul sharma", "rahul"))
        assertFalse(Replies.sameName("rahul sharma", "rah"))
        assertFalse(Replies.sameName("momina", "mom"))
    }

    @Test
    fun peoplesChatsGetAReplyIdButGroupsDont() {
        val mom = Triage.classify(Seen("com.whatsapp", "WhatsApp", "Mom", "Kab aa rahe ho?", 0L, replyId = 3))
        assertEquals("Mom (WhatsApp): Kab aa rahe ho? [reply 3]", mom.line)
        val noBox = Triage.classify(Seen("com.whatsapp", "WhatsApp", "Mom", "Hi", 0L))
        assertFalse(noBox.line.contains("[reply"))
    }
}
