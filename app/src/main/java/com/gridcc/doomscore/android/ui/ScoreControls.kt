package com.gridcc.doomscore.android.ui

import androidx.compose.animation.core.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import com.gridcc.doomscore.android.network.LeagueGateway
import kotlinx.coroutines.launch

/** Explicit colors keep both the off and disabled thumb distinct from the track. */
@Composable fun ScoreSwitch(checked:Boolean,onCheckedChange:((Boolean)->Unit)?,modifier:Modifier=Modifier,enabled:Boolean=true) {
    Switch(checked=checked,onCheckedChange=onCheckedChange,modifier=modifier,enabled=enabled,
        colors=SwitchDefaults.colors(
            checkedThumbColor=Palette.Bg,checkedTrackColor=Palette.Lime,checkedBorderColor=Palette.Lime,
            uncheckedThumbColor=Palette.Text,uncheckedTrackColor=Palette.High,uncheckedBorderColor=Palette.Dim,
            disabledCheckedThumbColor=Palette.Dim,disabledCheckedTrackColor=Palette.Lime.copy(alpha=.3f),disabledCheckedBorderColor=Palette.Lime.copy(alpha=.4f),
            disabledUncheckedThumbColor=Palette.Dim,disabledUncheckedTrackColor=Palette.High,disabledUncheckedBorderColor=Palette.Dim.copy(alpha=.55f)))
}

/** A tap bypasses the standings cache; the job itself owns the refreshing state. */
@Composable fun LeagueRefreshButton(client:LeagueGateway,onResult:(Boolean)->Unit) {
    val state=client.state.collectAsState().value
    val scope=rememberCoroutineScope()
    var refreshing by remember(client) {mutableStateOf(false)}
    val angle=if(refreshing && LocalMascotMotion.current) {
        val motion=rememberInfiniteTransition(label="standings request")
        motion.animateFloat(0f,360f,infiniteRepeatable(tween(850,easing=LinearEasing),RepeatMode.Restart),label="refresh rotation")
    } else remember {mutableFloatStateOf(0f)}
    IconButton(onClick={
        if(!refreshing && !state.busy) {
            refreshing=true
            scope.launch {
                try {client.refresh(force=true);onResult(client.state.value.error==null)}
                finally {refreshing=false}
            }
        }
    },enabled=client.configured && !state.busy && !refreshing,
        modifier=Modifier.semantics {
            contentDescription=if(refreshing) "Refreshing leaderboard" else "Refresh leaderboard"
            if(refreshing || state.busy || !client.configured) disabled()
        }) {
        DoomIcon(Mark.REPEAT,if(client.configured) Palette.Lime else Palette.Dim,
            Modifier.graphicsLayer {rotationZ=angle.value})
    }
}
