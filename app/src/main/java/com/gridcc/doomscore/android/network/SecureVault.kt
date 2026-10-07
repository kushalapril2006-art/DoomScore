package com.gridcc.doomscore.android.network

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Session and scoped device credentials are encrypted with a non-exportable Android Keystore key. */
class SecureVault(context: Context) {
    private val prefs = context.getSharedPreferences("secure_session", Context.MODE_PRIVATE)
    private val alias = "doomscore.credentials.v1"
    private val names = setOf("session", "profile", "device", "league_profile", "trophy_proofs")
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    @Synchronized fun get(name: String): String? {
        require(name in names)
        return try {
            val encoded = prefs.getString(name, null) ?: return null
            require(encoded.length <= 90_000)
            val pieces = encoded.split(':')
            val versioned = pieces.size == 3 && pieces[0] == "v2"
            require(versioned || pieces.size == 2)
            val iv = Base64.decode(pieces[if (versioned) 1 else 0], Base64.NO_WRAP)
            val ciphertext = Base64.decode(pieces[if (versioned) 2 else 1], Base64.NO_WRAP)
            require(iv.size == 12 && ciphertext.size in 16..65_552)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            if (versioned) cipher.updateAAD("$alias|$name".toByteArray(Charsets.UTF_8))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8).also { if (!versioned) put(name, it) }
        } catch (_: Exception) { prefs.edit().remove(name).commit(); null }
    }
    @Synchronized fun put(name: String, value: String) {
        require(name in names && value.toByteArray(Charsets.UTF_8).size <= 65_536)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD("$alias|$name".toByteArray(Charsets.UTF_8))
        val encoded = "v2:" + Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        check(prefs.edit().putString(name, encoded).commit()) { "Could not save account credentials" }
    }
    @Synchronized fun remove(name: String) { require(name in names); prefs.edit().remove(name).commit() }
    @Synchronized fun clear() { prefs.edit().clear().commit() }
}
