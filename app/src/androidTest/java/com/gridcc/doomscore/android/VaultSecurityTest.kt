package com.gridcc.doomscore.android

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.util.Base64
import androidx.test.platform.app.InstrumentationRegistry
import com.gridcc.doomscore.android.network.SecureVault
import org.junit.Assert.*
import org.junit.Test
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.SecretKey

class VaultSecurityTest {
    private fun isolated(block:(SecureVault,SharedPreferences)->Unit) {
        val base=InstrumentationRegistry.getInstrumentation().targetContext
        val prefs=base.getSharedPreferences("vault_test_"+UUID.randomUUID(),Context.MODE_PRIVATE)
        val context=object:ContextWrapper(base) {
            override fun getSharedPreferences(name:String,mode:Int)=prefs
        }
        try {block(SecureVault(context),prefs)} finally {prefs.edit().clear().commit()}
    }
    @Test fun encryptedCredentialsCannotBeReadOrSwappedBetweenFields() = isolated {vault,prefs ->
        val secret="synthetic-test-credential"
        vault.put("session",secret)
        val encrypted=prefs.getString("session",null)!!
        assertFalse(encrypted.contains(secret));assertTrue(encrypted.startsWith("v2:"))
        assertEquals(secret,vault.get("session"))
        prefs.edit().putString("device",encrypted).commit()
        assertNull(vault.get("device"));assertFalse(prefs.contains("device"))
        assertEquals(secret,vault.get("session"))
    }
    @Test fun tamperedAndWrongTypeCiphertextIsDiscarded() = isolated {vault,prefs ->
        vault.put("session","synthetic")
        val encoded=prefs.getString("session",null)!!
        val pieces=encoded.split(':')
        val bytes=Base64.decode(pieces[2],Base64.NO_WRAP);bytes[0]=(bytes[0].toInt() xor 1).toByte()
        prefs.edit().putString("session",pieces[0]+":"+pieces[1]+":"+Base64.encodeToString(bytes,Base64.NO_WRAP)).commit()
        assertNull(vault.get("session"))
        prefs.edit().putInt("profile",12).commit();assertNull(vault.get("profile"))
    }
    @Test fun legacyCredentialsMigrateToFieldBoundEncryption() = isolated {vault,prefs ->
        vault.put("session","initialize-key")
        val key=(KeyStore.getInstance("AndroidKeyStore").apply{load(null)}).getKey("doomscore.credentials.v1",null) as SecretKey
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply{init(Cipher.ENCRYPT_MODE,key)}
        val legacy=Base64.encodeToString(cipher.iv,Base64.NO_WRAP)+":"+Base64.encodeToString(cipher.doFinal("legacy-test".toByteArray()),Base64.NO_WRAP)
        prefs.edit().putString("session",legacy).commit()
        assertEquals("legacy-test",vault.get("session"));assertTrue(prefs.getString("session","")!!.startsWith("v2:"))
    }
}
