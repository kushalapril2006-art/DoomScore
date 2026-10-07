package com.gridcc.doomscore.android.core

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.Locale

class SecurityRulesTest {
    private fun rejected(action: () -> Unit) { try { action(); fail("Unsafe input was accepted") } catch (_: IllegalArgumentException) {} }
    @Test fun handleUsesLocaleIndependentNormalization() {
        val old = Locale.getDefault()
        try { Locale.setDefault(Locale.forLanguageTag("tr-TR")); assertEquals("vibing", InputRules.handle(" VIBING ")) } finally { Locale.setDefault(old) }
    }
    @Test fun handleRejectsSqlAndFilterSyntax() { listOf("abc,or=(id.eq.foo)", "x';drop table profiles;--", "..", "юзер").forEach { rejected {InputRules.handle(it)} } }
    @Test fun displayNamesAllowUnicodeButRejectControlAndBidiSpoofing() {
        assertEquals("你好 🧊", InputRules.displayName(" 你好 🧊 ", "hello"))
        listOf("abc\nxyz", "abc\u202exyz", "a".repeat(31), "\ud800").forEach { rejected {InputRules.safeText(it, 30)} }
    }
    @Test fun onlyKnownAvatarFieldsCanBeSubmitted() { assertEquals("🐸", InputRules.avatar("🐸")); rejected {InputRules.avatar("admin")} }
    @Test fun inviteAcceptsOnlyExactOwnedLinksAndCodes() {
        assertEquals("ABCD234567", InputRules.invite("abcd234567"))
        assertEquals("ABCD234567", InputRules.invite("https://doomscore.gridcc.tech/i/ABCD234567"))
        assertEquals("ABCD234567", InputRules.invite("doomscore://invite/ABCD234567"))
        listOf("https://evil.test/i/ABCD234567", "http://doomscore.gridcc.tech/i/ABCD234567", "https://doomscore.gridcc.tech@evil.test/i/ABCD234567", "https://doomscore.gridcc.tech/i/ABCD234567/extra", "https://doomscore.gridcc.tech/i/ABCD234567?user_id=evil", "ABCD%32234567", "A".repeat(1000)).forEach { rejected {InputRules.invite(it)} }
    }
    @Test fun uuidCannotInjectPostgrestFilters() { assertEquals("11111111-1111-1111-1111-111111111111", InputRules.uuid("11111111-1111-1111-1111-111111111111")); rejected {InputRules.uuid("1-1-1-1-1")}; rejected {InputRules.uuid("id,or=(true)")} }
    @Test fun backendForcesHttpsAndOnlyPublicKeys() {
        val publicKey = "sb_publishable_" + "a".repeat(24)
        assertEquals("https", InputRules.backend("https://example.supabase.co", publicKey).scheme)
        listOf("http://example.supabase.co", "https://user:pass@example.supabase.co", "https://example.supabase.co/path", "https://example.supabase.co?key=hello").forEach { rejected {InputRules.backend(it, publicKey)} }
        rejected {InputRules.backend("https://example.supabase.co", "sb_secret_" + "a".repeat(24))}
        rejected {InputRules.backend("https://example.supabase.co", "legacy.jwt.token")}
    }
    @Test fun rangesRejectFieldTampering() { assertEquals("week", InputRules.period("week")); rejected {InputRules.period("all_users")}; rejected {InputRules.avatar("untrusted")} }
    @Test fun boundedResponsesAllowExactLimitAndRejectExtraByte() {
        assertEquals("{}", NetworkRules.readBounded(ByteArrayInputStream("{}".toByteArray()), 2))
        rejected {NetworkRules.readBounded(ByteArrayInputStream("{} ".toByteArray()), 2)}
    }
    @Test fun nestingLimitDoesNotTreatBracketsInsideStringsAsObjects() {
        assertEquals("{\"value\":\"[[[\"}", NetworkRules.readBounded(ByteArrayInputStream("{\"value\":\"[[[\"}".toByteArray()), 100))
        rejected {NetworkRules.readBounded(ByteArrayInputStream(("[".repeat(33)+"0"+"]".repeat(33)).toByteArray()), 100)}
    }
    @Test fun malformedUtf8IsRejected() {
        try {NetworkRules.readBounded(ByteArrayInputStream(byteArrayOf(0xc3.toByte(),0x28)), 100);fail("Malformed UTF-8 accepted")} catch (_: java.nio.charset.CharacterCodingException) {}
    }
}
