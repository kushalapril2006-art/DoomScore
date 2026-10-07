package com.gridcc.doomscore.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import com.gridcc.doomscore.android.ui.DoomApp
import com.gridcc.doomscore.android.core.InputRules
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    val pendingInvite = MutableStateFlow<String?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle=SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),navigationBarStyle=SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        readInvite(intent)
        setContent { DoomApp(application as DoomApplication, pendingInvite) }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); readInvite(intent) }
    private fun readInvite(intent: Intent?) {
        val uri = intent?.data ?: return
        pendingInvite.value = runCatching { InputRules.invite(uri.toString()) }.getOrNull()
    }
}
