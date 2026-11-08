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
            "app.shilex.android", "com.android.settings", "com.android.vending",
            "com.google.android.apps.nbu.paisa.user", "net.one97.paytm", "com.phonepe.app",
            "in.org.npci.upiapp", "in.amazon.mShop.android.shopping.pay", "com.mobikwik_new",
            "com.sbi.lotusintouch", "com.sbi.SBIFreedomPlus", "com.csam.icici.bank.imobile",
            "com.snapwork.hdfc", "com.axis.mobile", "com.msf.kbank.mobile", "com.bankofbaroda.mconnect",
            "com.x8bit.bitwarden", "com.onepassword.android", "com.lastpass.lpandroid",
            "com.google.android.apps.authenticator2", "com.azure.authenticator",
            // Bangladeshi MFS and Banks
            "com.bKash.customerapp", "com.konasl.nagad", "com.dbbl.mbs.apps.main", "com.dbbl.mbs.apps.rocket",
            "bd.com.upay.customer", "bd.com.upay.agent", "com.ibbl.cellfin", "com.bracbank.astha",
            "co.bracbank.astha", "com.thecitybank.citytouch", "com.dbbl.nexus.pay", "bd.com.sonalibank.ss",
            "bd.com.sonalibank.sw", "com.sonalibank.esheba", "com.ebl.skybanking",
            "eraapps.bankasia.bdinternetbanking.apps", "bd.com.bankasia.smartapp",
        ).forEach { assertTrue(it, ControlRules.blocked(it)) }
        listOf("com.whatsapp", "com.google.android.apps.youtube.music", "com.instagram.android", "com.android.chrome")
            .forEach { assertFalse(it, ControlRules.blocked(it)) }
    }

    @Test
    fun newBankAsiaIdIsBlocked() {
        assertTrue(ControlRules.blocked("eraapps.bankasia.bdinternetbanking.apps"))
    }

    @Test
    fun blockedLabelsAreBlockedEvenWithUnknownPackageId() {
        val unknownPkg = "com.example.unknown.app"
        val labels = listOf(
            "bank", "bkash", "nagad", "rocket", "upay", "mfs", "wallet",
            "cellfin", "astha", "citytouch", "nexuspay", "esheba", "skybanking",
            "বিকাশ", "নগদ", "রকেট", "উপায়", "ব্যাংক"
        )
        labels.forEach { label ->
            assertTrue(
                "Label '$label' should be blocked",
                ControlRules.blocked(packageName = unknownPkg, appLabel = label)
            )
        }
    }

    @Test
    fun normalAppIsNotBlocked() {
        assertFalse(ControlRules.blocked(packageName = "com.android.chrome", appLabel = "Chrome"))
        assertFalse(ControlRules.blocked(packageName = "com.google.android.apps.maps", appLabel = "Google Maps"))
    }

    @Test
    fun noDuplicateIdsInBlockedPackagesAndNoVerifiedComments() {
        val rulesFile = java.io.File("src/main/java/app/shilex/android/control/ControlRules.kt")
            .takeIf { it.exists() }
            ?: java.io.File("app/src/main/java/app/shilex/android/control/ControlRules.kt")
        assertTrue("ControlRules.kt must exist", rulesFile.exists())
        val lines = rulesFile.readLines()

        // 1. Verify no comment contains "Verified"
        val verifiedComments = lines.filter { line ->
            val trimmed = line.trim()
            trimmed.startsWith("//") && trimmed.contains("Verified", ignoreCase = false)
        }
        assertTrue(
            "ControlRules.kt must not contain 'Verified' in comments: $verifiedComments",
            verifiedComments.isEmpty()
        )

        // 2. Verify no duplicate IDs appear in BLOCKED_PACKAGES
        val idRegex = Regex("""^\s*"([a-zA-Z0-9_\.]+)",?\s*(?://.*)?$""")
        val inBlockedPackages = mutableListOf<String>()
        var inList = false
        for (line in lines) {
            if ("BLOCKED_PACKAGES" in line) inList = true
            if (inList) {
                val match = idRegex.find(line)
                if (match != null) {
                    inBlockedPackages.add(match.groupValues[1])
                }
                if (line.trim() == ")") inList = false
            }
        }
        val duplicates = inBlockedPackages.groupBy { it }.filter { it.value.size > 1 }.keys
        assertTrue("BLOCKED_PACKAGES must not contain duplicate IDs: $duplicates", duplicates.isEmpty())
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
