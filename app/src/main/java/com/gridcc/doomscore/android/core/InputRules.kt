package com.gridcc.doomscore.android.core

import java.net.URI
import java.text.Normalizer
import java.util.Locale

/** Input and endpoint rules shared by UI, deep links and network payloads. */
object InputRules {
    val avatars = listOf("🫠", "💀", "🐸", "🧊", "👽", "🔥")
    private val codePattern = Regex("[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{10}")
    private val uuidPattern = Regex("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}")
    fun uuid(value: String): String {
        require(uuidPattern.matches(value)) { "Invalid account identifier" }
        return value.lowercase(Locale.ROOT)
    }
    fun handle(value: String): String {
        require(value.length <= 80) { "Use a shorter handle" }
        return value.trim().lowercase(Locale.ROOT).also {
            require(Regex("[a-z0-9._]{3,20}").matches(it)) { "Use 3–20 letters, numbers, dots or underscores." }
        }
    }
    fun safeText(value: String, max: Int): String {
        require(value.length <= max * 4 && validUnicode(value)) { "Invalid text" }
        val normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFC)
        require(normalized.codePointCount(0, normalized.length) in 1..max) { "Use 1–$max characters." }
        require(normalized.none { Character.isISOControl(it) || it in '\u202a'..'\u202e' || it in '\u2066'..'\u2069' }) { "Control characters are not allowed" }
        return normalized
    }
    private fun validUnicode(value: String): Boolean {
        var i = 0
        while (i < value.length) {
            val c = value[i++]
            if (Character.isHighSurrogate(c)) {
                if (i >= value.length || !Character.isLowSurrogate(value[i++])) return false
            } else if (Character.isLowSurrogate(c)) return false
        }
        return true
    }
    fun displayName(value: String, handle: String) = safeText(value.ifBlank { handle }, 30)
    fun avatar(value: String) = value.also { require(it in avatars) { "Choose an avatar from the list" } }
    fun period(value: String) = value.also { require(it in setOf("day", "week", "month")) { "Invalid leaderboard period" } }
    fun invite(value: String): String {
        require(value.length <= 256) { "Use a valid invite link or code." }
        val text = value.trim()
        val code = if (text.contains("://")) {
            val uri = runCatching { URI(text) }.getOrElse { throw IllegalArgumentException("Use a valid invite link or code.") }
            require(uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null && uri.port == -1) { "Use a valid invite link or code." }
            when {
                uri.scheme.equals("https", true) && uri.host.equals("doomscore.gridcc.tech", true) && uri.rawPath.startsWith("/i/") -> uri.rawPath.removePrefix("/i/")
                uri.scheme.equals("doomscore", true) && uri.host.equals("invite", true) -> uri.rawPath.removePrefix("/")
                else -> throw IllegalArgumentException("Use a Doomscore invite link or code.")
            }
        } else text
        return code.uppercase(Locale.ROOT).also { require(codePattern.matches(it)) { "Use the 10-character invite code." } }
    }
    fun backend(base: String, key: String): URI {
        val uri = runCatching { URI(base) }.getOrElse { throw IllegalArgumentException("Battles unavailable in this version.") }
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null && uri.rawPath.orEmpty() in setOf("", "/") && (uri.port == -1 || uri.port == 443)) { "Battles unavailable in this version." }
        require(Regex("sb_publishable_[A-Za-z0-9_-]{16,128}").matches(key)) { "Battles unavailable in this version." }
        return uri
    }
}
