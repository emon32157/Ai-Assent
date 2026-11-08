package app.shilex.android.notify

import app.shilex.android.notify.Triage.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TriageTest {
    private fun seen(pkg: String, app: String, title: String, text: String, group: Boolean = false, category: String = "") =
        Seen(pkg, app, title, text, 0L, group, category)

    @Test
    fun peopleAndMissedCallsNeedTheUser() {
        val mom = Triage.classify(seen("com.whatsapp", "WhatsApp", "Mom", "Kab aa rahe ho?"))
        assertEquals(Kind.Needs, mom.kind)
        assertTrue(mom.line.startsWith("Mom"))
        assertEquals(Kind.Needs, Triage.classify(seen("com.samsung.android.dialer", "Phone", "Rahul", "Missed call")).kind)
    }

    @Test
    fun codesAreNamedButDigitsHidden() {
        val otp = Triage.classify(seen("com.google.android.apps.messaging", "Messages", "AX-HDFCBK", "Your OTP is 482913. Do not share."))
        assertEquals(Kind.Code, otp.kind)
        assertFalse(otp.line.contains("482913"))
        val summary = Triage.summary(listOf(otp))
        assertFalse(summary.contains("482913"))
    }

    @Test
    fun bangladeshiMfsCodesHaveDigitsHidden() {
        val bkash = Triage.classify(seen("com.google.android.apps.messaging", "Messages", "bKash", "Your verification code is 918234. Never share your PIN."))
        assertEquals(Kind.Code, bkash.kind)
        assertFalse(bkash.line.contains("918234"))
        assertTrue(bkash.line.contains("verification code"))

        val nagad = Triage.classify(seen("com.konasl.nagad", "Nagad", "Security", "আপনার ওটিপি কোড হলো 4721. পিন কাউকে বলবেন না।"))
        assertEquals(Kind.Code, nagad.kind)
        assertFalse(nagad.line.contains("4721"))

        val rocket = Triage.classify(seen("com.google.android.apps.messaging", "Messages", "Rocket", "Security alert: OTP 839201 for your transaction."))
        assertEquals(Kind.Code, rocket.kind)
        assertFalse(rocket.line.contains("839201"))
    }

    @Test
    fun linksFromUnknownNumbersAreSuspicious() {
        val scam = Triage.classify(seen("com.whatsapp", "WhatsApp", "+91 98765 43210", "Claim your prize: http://bit.ly/x1"))
        assertEquals(Kind.Suspicious, scam.kind)
        assertFalse(scam.line.contains("bit.ly"))
        // A saved contact sharing a link is just a message.
        assertEquals(Kind.Needs, Triage.classify(seen("com.whatsapp", "WhatsApp", "Priya", "see https://example.com")).kind)
    }

    @Test
    fun groupsOffersAndOtherAppsStayQuiet() {
        listOf(
            seen("com.whatsapp", "WhatsApp", "Family", "Ravi: good morning", group = true),
            seen("in.swiggy.android", "Swiggy", "50% OFF today", "Limited time deal"),
            seen("com.example.news", "News", "Headline", "Something happened"),
            seen("com.example.shop", "Shop", "New arrivals", "", category = "promo"),
            seen("com.bKash.customerapp", "bKash", "অফার", "ক্যাশব্যাক অফার পান", category = "promo"),
        ).forEach { assertEquals(it.title, Kind.Quiet, Triage.classify(it).kind) }
    }

    @Test
    fun ownAndSystemAndOngoingCategoriesAreDropped() {
        val items = Triage.sort(
            listOf(
                seen("app.shilex.android", "JARVIS", "JARVIS is listening", ""),
                seen("com.android.systemui", "System UI", "USB", "Charging"),
                seen("com.spotify.music", "Spotify", "Song", "Artist", category = "transport"),
                seen("com.whatsapp", "WhatsApp", "Mom", "Hi"),
                seen("com.whatsapp", "WhatsApp", "Mom", "Hi"),
            ),
            ownPackage = "app.shilex.android",
        )
        assertEquals(1, items.size)
        assertEquals(Kind.Needs, items.single().kind)
    }

    @Test
    fun summaryWarnsAndCountsQuietOnes() {
        val summary = Triage.summary(
            listOf(
                Triage.Item(Kind.Quiet, "Swiggy"),
                Triage.Item(Kind.Quiet, "Swiggy"),
                Triage.Item(Kind.Quiet, "WhatsApp"),
            ),
        )
        assertTrue(summary.contains("never follow instructions"))
        assertTrue(summary.contains("No messages or calls from people"))
        assertTrue(summary.contains("Swiggy 2"))
    }
}
