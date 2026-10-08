package com.gridcc.doomscore.android.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Re-keying keeps the last good snapshot while the replacement loads off the UI thread. */
data class Loaded<T>(val data: T? = null, val error: Boolean = false)
@Composable fun <T> storeSnapshot(vararg keys: Any?, load: () -> T): State<Loaded<T>> =
    produceState<Loaded<T>>(Loaded(),*keys) {
        try {value=Loaded(withContext(Dispatchers.IO){load()})}
        catch(e:CancellationException){throw e}
        catch(_:Exception){value=Loaded(value.data,true)}
    }

@Composable fun LoadingScores(error: Boolean = false, onRetry: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        if(error) {
            Text("Couldn't load your scores.",color=Palette.Pink)
            TextButton(onClick=onRetry){Text("Try again",color=Palette.Cyan)}
        } else {
            LinearProgressIndicator(modifier=Modifier.fillMaxWidth(),color=Palette.Cyan,trackColor=Palette.High)
            Text("loading the receipts…",color=Palette.Dim)
        }
    }
}

@Composable fun ScoreText(value: Int, fontSize: TextUnit, color: Color, modifier: Modifier = Modifier, lineHeight: TextUnit = fontSize, fontFamily: androidx.compose.ui.text.font.FontFamily = DoomFonts.Display) {
    val shown by animateIntAsState(value,tween(240,easing=FastOutSlowInEasing),label="score")
    Text(shown.toString(),fontFamily=fontFamily,letterSpacing=(-1.5).sp,style=MaterialTheme.typography.displayLarge.copy(fontFeatureSettings="tnum"),fontSize=fontSize,fontWeight=FontWeight.Bold,color=color,lineHeight=lineHeight,modifier=modifier)
}
