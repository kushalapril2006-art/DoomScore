package com.gridcc.doomscore.android.core

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

object NetworkRules {
    const val MAX_RESPONSE = 256 * 1024
    const val MAX_ERROR = 8 * 1024
    fun readBounded(stream: InputStream, limit: Int): String {
        require(limit in 1..MAX_RESPONSE)
        val bytes = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (true) {
            val count = stream.read(buffer)
            if (count == -1) break
            require(bytes.size() + count <= limit) { "Invalid server response" }
            bytes.write(buffer, 0, count)
        }
        val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        val text = decoder.decode(ByteBuffer.wrap(bytes.toByteArray())).toString()
        checkNesting(text)
        return text
    }
    private fun checkNesting(text: String) {
        var depth = 0; var quoted = false; var escaped = false
        for (c in text) {
            if (quoted) {
                if (escaped) escaped = false
                else if (c == '\\') escaped = true
                else if (c == '"') quoted = false
            } else when (c) {
                '"' -> quoted = true
                '[', '{' -> { depth++; require(depth <= 32) { "Invalid server response" } }
                ']', '}' -> { depth--; require(depth >= 0) { "Invalid server response" } }
            }
        }
        require(depth == 0 && !quoted) { "Invalid server response" }
    }
}
