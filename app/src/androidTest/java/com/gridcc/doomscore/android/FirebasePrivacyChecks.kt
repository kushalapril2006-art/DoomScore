package com.gridcc.doomscore.android

import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import com.gridcc.doomscore.android.network.FirebaseLeague
import com.gridcc.doomscore.android.network.SecureVault
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class FirebasePrivacyChecks {
    @Test fun googleProfileAndProviderCredentialsAreDiscardedAndSessionIsEncrypted() {
        assertTrue("Isolated emulator only",Build.MODEL.startsWith("sdk_gphone"))
        val real=InstrumentationRegistry.getInstrumentation().targetContext
        val name="firebase-privacy-${System.nanoTime()}"
        val isolated=object:ContextWrapper(real) {override fun getSharedPreferences(ignored:String,mode:Int)=real.getSharedPreferences(name,mode)}
        try {
            val app=real.applicationContext as DoomApplication
            val client=FirebaseLeague(isolated,app.store)
            val raw=JSONObject().put("localId","alice_private_uid").put("idToken","aaaa.bbbb.ccccdddd")
                .put("refreshToken","private_refresh_token_123456789").put("expiresIn","3600")
                .put("email","private@example.com").put("displayName","PRIVATE GOOGLE NAME").put("photoUrl","https://example.com/private-photo")
                .put("oauthAccessToken","PRIVATE GOOGLE ACCESS TOKEN").put("rawUserInfo","PRIVATE GOOGLE PROFILE")
            val method=FirebaseLeague::class.java.getDeclaredMethod("trimSession",JSONObject::class.java,Boolean::class.javaPrimitiveType).apply {isAccessible=true}
            val trimmed=method.invoke(client,raw,false) as JSONObject
            assertEquals(setOf("uid","idToken","refreshToken","expiresAt","guest"),trimmed.keys().asSequence().toSet())
            val vault=SecureVault(isolated);vault.put("firebase_session",trimmed.toString())
            val stored=real.getSharedPreferences(name,Context.MODE_PRIVATE).getString("firebase_session",null)!!
            listOf("private_refresh_token","aaaa.bbbb","PRIVATE GOOGLE","private@example.com","private-photo").forEach {assertFalse(stored.contains(it));assertFalse(trimmed.toString().contains(it) && it !in listOf("private_refresh_token","aaaa.bbbb"))}
            assertEquals(trimmed.toString(),vault.get("firebase_session"))
            vault.remove("firebase_session");assertNull(vault.get("firebase_session"))
        } finally {real.deleteSharedPreferences(name)}
    }
    @Test fun malformedAccountIdentifiersCannotEnterTheSession() {
        assertTrue(Build.MODEL.startsWith("sdk_gphone"))
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val client=FirebaseLeague(context,(context.applicationContext as DoomApplication).store)
        val raw=JSONObject().put("localId","alice/../../bob").put("idToken","aaaa.bbbb.ccccdddd").put("refreshToken","private_refresh_token_123456789").put("expiresIn","3600")
        val method=FirebaseLeague::class.java.getDeclaredMethod("trimSession",JSONObject::class.java,Boolean::class.javaPrimitiveType).apply {isAccessible=true}
        try {method.invoke(client,raw,false);fail("Malformed UID accepted")} catch(e:java.lang.reflect.InvocationTargetException) {assertTrue(e.cause is IllegalArgumentException)}
    }
}
