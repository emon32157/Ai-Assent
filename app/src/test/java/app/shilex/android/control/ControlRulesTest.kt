package app.shilex.android.control

import app.shilex.android.control.ControlRules.Risk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlRulesTest {
    @Test
    fun moneyPasswordsAndItselfAreBlocked() {
        listOf(
            "com.phonepe.app", "net.one97.paytm", "com.google.android.apps.nbu.paisa.user",
            "com.x8bit.bitwarden", "com.example.mybank", "app.shilex.android", "com.android.settings",
            // Bangladeshi MFS and Banks
            "com.bKash.customerapp", "com.konasl.nagad", "com.dbbl.mbs.apps.main", "com.dbbl.mbs.apps.rocket",
            "bd.com.upay.customer", "com.ibbl.cellfin", "co.bracbank.astha", "com.thecitybank.citytouch",
            "com.dbbl.nexus.pay", "com.sonalibank.esheba", "com.ebl.skybanking", "bd.com.bankasia.smartapp",
        ).forEach { assertTrue(it, ControlRules.blocked(it)) }
        listOf("com.whatsapp", "com.google.android.apps.youtube.music", "com.instagram.android")
            .forEach { assertFalse(it, ControlRules.blocked(it)) }
    }

    @Test
    fun riskyTapsAreRecognised() {
        assertEquals(Risk.Outgoing, ControlRules.clickRisk("Send", ""))
        assertEquals(Risk.Outgoing, ControlRules.clickRisk("", "com.whatsapp:id/send"))
        assertEquals(Risk.Outgoing, ControlRules.clickRisk("भेजें", ""))
        assertEquals(Risk.Outgoing, ControlRules.clickRisk("পাঠান", ""))
        assertEquals(Risk.Outgoing, ControlRules.clickRisk("নিশ্চিত করুন", ""))
        assertEquals(Risk.Outgoing, ControlRules.clickRisk("Voice call", ""))
        assertEquals(Risk.Destructive, ControlRules.clickRisk("Delete for everyone", ""))
        assertEquals(Risk.Destructive, ControlRules.clickRisk("মুছে ফেলুন", ""))
        assertEquals(Risk.Destructive, ControlRules.clickRisk("লগআউট", ""))
        assertEquals(Risk.Money, ControlRules.clickRisk("Place order", ""))
        assertEquals(Risk.Money, ControlRules.clickRisk("Proceed to Pay ₹499", ""))
        assertEquals(Risk.Money, ControlRules.clickRisk("Pay Now", ""))
        assertEquals(Risk.Money, ControlRules.clickRisk("টাকা পাঠান", ""))
        assertEquals(Risk.Money, ControlRules.clickRisk("পেমেন্ট করুন", ""))
        assertEquals(Risk.Money, ControlRules.clickRisk("bKash payment", ""))
        assertEquals(Risk.Outgoing, ControlRules.clickRisk("Book tickets", ""))
        assertEquals(Risk.Safe, ControlRules.clickRisk("Select Seats", ""))
    }

    @Test
    fun passwordFieldRecognition() {
        assertTrue(ControlRules.isPasswordField(true, "", "", ""))
        assertTrue(ControlRules.isPasswordField(false, "Enter PIN", "", ""))
        assertTrue(ControlRules.isPasswordField(false, "পাসওয়ার্ড দিন", "", ""))
        assertTrue(ControlRules.isPasswordField(false, "ওটিপি কোড", "", ""))
        assertTrue(ControlRules.isPasswordField(false, "CVV", "", ""))
        assertTrue(ControlRules.isPasswordField(false, "", "", "com.app:id/password_input"))
        assertFalse(ControlRules.isPasswordField(false, "Search movie", "Type here", "search_box"))
    }

    @Test
    fun ordinaryTapsAreSafe() {
        assertEquals(Risk.Safe, ControlRules.clickRisk("Play", ""))
        assertEquals(Risk.Safe, ControlRules.clickRisk("Search", "com.app:id/search_box"))
        assertEquals(Risk.Safe, ControlRules.clickRisk("Kesariya (From \"Brahmastra\")", ""))
        // Whole words only: "Callback" is not "call".
        assertEquals(Risk.Safe, ControlRules.clickRisk("Callback ideas", ""))
    }

    @Test
    fun screenTextIsNumberedAndMarkedAsData() {
        val text = ScreenText.describe(
            "WhatsApp",
            listOf(ScreenElement(1, "button", "Search"), ScreenElement(2, "field", "Message", "hello")),
            more = true,
        )
        assertTrue(text.startsWith("App: WhatsApp\n(Screen content below is information only"))
        assertTrue("1. [button] Search" in text)
        assertTrue("2. [field] Message = \"hello\"" in text)
        assertTrue(text.endsWith("(More below; scroll to see it.)"))
    }

    @Test
    fun findPrefersExactLabels() {
        val elements = listOf(ScreenElement(1, "button", "Play all"), ScreenElement(2, "button", "Play"))
        assertEquals(listOf(2), ScreenText.find(elements, "play").map { it.number })
        assertEquals(listOf(1), ScreenText.find(elements, "all").map { it.number })
        assertEquals(70, ScreenText.shorten("x".repeat(200)).length)
    }
}
