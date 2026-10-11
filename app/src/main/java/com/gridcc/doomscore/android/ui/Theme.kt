package com.gridcc.doomscore.android.ui

import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gridcc.doomscore.android.core.ScrollTier

@Immutable private data class ScoreColors(val bg:Color,val surface:Color,val line:Color,val accent:Color,val soft:Color,val text:Color,val dim:Color,val pink:Color,val violet:Color,val orange:Color,val cyan:Color)
private val LightScoreColors=ScoreColors(Color(0xFFF5F3EF),Color.White,Color(0xFFDDDDE4),Color(0xFF477515),Color(0xFFE7EFDC),Color(0xFF172019),Color(0xFF616977),Color(0xFF9C3E69),Color(0xFF7241C3),Color(0xFFA45613),Color(0xFF176C84))
private val DarkScoreColors=ScoreColors(Color(0xFF11131A),Color(0xFF1B1E28),Color(0xFF343845),Color(0xFFC0F15D),Color(0xFF1C2717),Color(0xFFF1F1F5),Color(0xFFB0B7C8),Color(0xFFFFB0C6),Color(0xFFCFADFF),Color(0xFFFFB877),Color(0xFF9CDAE8))
private val LocalScoreColors=staticCompositionLocalOf {DarkScoreColors}
internal val LocalMascotMotion=staticCompositionLocalOf {true}

object Palette {
    val Bg:Color @Composable @ReadOnlyComposable get()=LocalScoreColors.current.bg
    val Surface:Color @Composable @ReadOnlyComposable get()=LocalScoreColors.current.surface
    val High:Color @Composable @ReadOnlyComposable get()=LocalScoreColors.current.line
    val Lime:Color @Composable @ReadOnlyComposable get()=LocalScoreColors.current.accent
    val Cyan:Color @Composable @ReadOnlyComposable get()=LocalScoreColors.current.cyan
    val AccentSurface:Color @Composable @ReadOnlyComposable get()=LocalScoreColors.current.soft
    val Text:Color @Composable @ReadOnlyComposable get()=LocalScoreColors.current.text
    val Dim:Color @Composable @ReadOnlyComposable get()=LocalScoreColors.current.dim
    val Faint:Color @Composable @ReadOnlyComposable get()=LocalScoreColors.current.dim
    val Pink:Color @Composable @ReadOnlyComposable get()=LocalScoreColors.current.pink
    val Violet:Color @Composable @ReadOnlyComposable get()=LocalScoreColors.current.violet
    val Orange:Color @Composable @ReadOnlyComposable get()=LocalScoreColors.current.orange
    val Champion:Color @Composable @ReadOnlyComposable get()=if(LocalScoreColors.current===DarkScoreColors) Color(0xFFFFD76B) else Color(0xFF8A5A00)
    val ChampionSurface:Color @Composable @ReadOnlyComposable get()=if(LocalScoreColors.current===DarkScoreColors) Color(0xFF332913) else Color(0xFFFFF0C7)
    val Brand:Brush @Composable @ReadOnlyComposable get()=Brush.linearGradient(listOf(Lime,Cyan))
    fun rank(level:Int)=Color(ScrollTier.color(level))
}

@Composable private fun mascotMotionAllowed():Boolean {
    val context=LocalContext.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val power=remember(context) {context.getSystemService(PowerManager::class.java)}
    fun allowed()=runCatching {Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)>0f}.getOrElse {ValueAnimator.areAnimatorsEnabled()} && power?.isPowerSaveMode!=true
    var enabled by remember {mutableStateOf(allowed())}
    DisposableEffect(context,lifecycle) {
        val observer=object:ContentObserver(Handler(Looper.getMainLooper())) {override fun onChange(selfChange:Boolean) {enabled=allowed()}}
        val receiver=object:BroadcastReceiver() {override fun onReceive(context:Context?,intent:Intent?) {enabled=allowed()}}
        val lifecycleObserver=LifecycleEventObserver {_,event->if(event==Lifecycle.Event.ON_RESUME) enabled=allowed()}
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),false,observer)
        ContextCompat.registerReceiver(context,receiver,IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),ContextCompat.RECEIVER_NOT_EXPORTED)
        lifecycle.addObserver(lifecycleObserver)
        onDispose {context.contentResolver.unregisterContentObserver(observer);context.unregisterReceiver(receiver);lifecycle.removeObserver(lifecycleObserver)}
    }
    return enabled
}

@Composable fun DoomTheme(content:@Composable ()->Unit) {
    val dark=isSystemInDarkTheme()
    val colors=if(dark) DarkScoreColors else LightScoreColors
    val motion=mascotMotionAllowed()
    val scheme=if(dark) darkColorScheme(primary=colors.accent,secondary=colors.violet,tertiary=colors.orange,background=colors.bg,surface=colors.surface,surfaceVariant=colors.soft,onPrimary=Color(0xFF172019),onSecondary=Color(0xFF172019),onSurface=colors.text,onBackground=colors.text,onSurfaceVariant=colors.dim,outline=colors.line)
        else lightColorScheme(primary=colors.accent,secondary=colors.violet,tertiary=colors.orange,background=colors.bg,surface=colors.surface,surfaceVariant=colors.soft,onPrimary=Color.White,onSecondary=Color.White,onSurface=colors.text,onBackground=colors.text,onSurfaceVariant=colors.dim,outline=colors.line)
    CompositionLocalProvider(LocalScoreColors provides colors,LocalMascotMotion provides motion) {
        MaterialTheme(colorScheme=scheme.copy(primaryContainer=colors.soft,onPrimaryContainer=colors.text,secondaryContainer=colors.violet.copy(alpha=.12f),onSecondaryContainer=colors.violet,tertiaryContainer=colors.orange.copy(alpha=.12f),onTertiaryContainer=colors.orange,surfaceContainerLowest=colors.bg,surfaceContainerLow=colors.surface,surfaceContainer=colors.surface,surfaceContainerHigh=colors.surface,surfaceContainerHighest=colors.line,surfaceTint=colors.accent,outlineVariant=colors.line),typography=DoomFonts.Type,shapes=Shapes(small=androidx.compose.foundation.shape.RoundedCornerShape(12.dp),medium=androidx.compose.foundation.shape.RoundedCornerShape(18.dp),large=androidx.compose.foundation.shape.RoundedCornerShape(24.dp)),content=content)
    }
}
