package com.gridcc.doomscore.android.ui

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gridcc.doomscore.android.DoomApplication
import com.gridcc.doomscore.android.core.*
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable fun TrophyCabinet(app: DoomApplication, onClose: () -> Unit) {
    val revision by app.store.changes.collectAsStateWithLifecycle()
    val league by app.league.state.collectAsStateWithLifecycle()
    val identity by app.battles.state.collectAsStateWithLifecycle()
    var retry by remember {mutableIntStateOf(0)}
    val progress by storeSnapshot(app.store,revision,retry){app.store.trophies()}
    val local=progress.data
    if(local==null) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Text("Brainrot Trophy Cabinet 🏆",color=Palette.Text,modifier=Modifier.padding(horizontal=20.dp))
            TextButton(onClick=onClose,modifier=Modifier.fillMaxWidth()){Text("Close",color=Palette.Cyan)}
            LoadingScores(progress.error){retry++}
        };return
    }
    val verified=league.trophyProofs?.takeIf {it.owner==app.league.accountId}
    val badges=TrophyRules.cabinet(local,verified)
    val earned=badges.count {it.unlocked}
    Column(Modifier.fillMaxWidth().fillMaxHeight(.94f).navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("Brainrot Trophy Cabinet 🏆",fontSize=24.sp,fontWeight=FontWeight.Black,color=Palette.Text,modifier=Modifier.weight(1f))
            TextButton(onClick=onClose){Text("Close",color=Palette.Cyan)}
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(start=20.dp,end=20.dp,bottom=24.dp)) {
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(listOf(Palette.Pink.copy(alpha=.15f),Palette.High))).padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text("$earned / 8 UNLOCKED",color=Palette.Lime,fontWeight=FontWeight.Black,fontSize=14.sp)
                    Text("your thumb left evidence.",color=Palette.Text,fontWeight=FontWeight.Bold,fontSize=23.sp)
                    Text("Eight badges. Extremely questionable bragging rights.",color=Palette.Dim,fontSize=13.sp)
                    LinearProgressIndicator(progress={earned/8f},modifier=Modifier.fillMaxWidth().height(6.dp),color=Palette.Lime,trackColor=Palette.High)
                }
            }
            items(badges,key={it.trophy.id}) {badge -> TrophyCard(badge,verified!=null) }
            item {Text("Unique milestones start with this update. Ads and fast skips don't qualify; watching the same reel again won't boost lifetime progress. Similar reels can sometimes be hard to tell apart.",color=Palette.Faint,fontSize=11.sp,lineHeight=17.sp)}
            item {Text("Scroll streaks count calendar days with at least one counted reel. Earned badges stay earned until you delete their history or identity. Battle wins require completed results; a live lead doesn't count as a win.",color=Palette.Faint,fontSize=11.sp,lineHeight=17.sp)}
        }
    }
}

@Composable private fun TrophyCard(badge: TrophyProgress, online: Boolean) {
    val trophy=badge.trophy
    val tint=when(trophy) {
        Trophy.ONE_MORE,Trophy.CHRONIC -> Palette.Cyan
        Trophy.FOR_YOU,Trophy.OUTSCROLLED -> Palette.Pink
        Trophy.FINAL_BOSS,Trophy.UNEMPLOYED -> Palette.Orange
        else -> Palette.Lime
    }
    val color=if(badge.unlocked) tint else Palette.Faint
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Palette.Surface)
        .border(1.dp,if(badge.unlocked) tint.copy(alpha=.4f) else Palette.High,RoundedCornerShape(22.dp)).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(64.dp),contentAlignment=Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val shield=Path().apply {moveTo(size.width*.5f,0f);lineTo(size.width*.95f,size.height*.2f);lineTo(size.width*.85f,size.height*.75f);lineTo(size.width*.5f,size.height);lineTo(size.width*.15f,size.height*.75f);lineTo(size.width*.05f,size.height*.2f);close()}
                    drawPath(shield,Brush.verticalGradient(listOf(color.copy(alpha=.28f),color.copy(alpha=.07f))))
                    drawPath(shield,color.copy(alpha=.7f),style=androidx.compose.ui.graphics.drawscope.Stroke(width=2.dp.toPx()))
                }
                Text(if(badge.unlocked) trophy.glyph else "🔒",fontSize=27.sp)
            }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(trophy.title,color=if(badge.unlocked) Palette.Text else Palette.Dim,fontWeight=FontWeight.Bold,fontSize=16.sp)
                Text(trophy.rule,color=Palette.Dim,fontSize=12.sp)
                Text(if(badge.unlocked) "UNLOCKED ✦" else "LOCKED",color=color,fontSize=10.sp,fontWeight=FontWeight.Black)
            }
        }
        if(badge.unlocked) {
            val date=badge.earnedAt?.let {Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy"))}
            Text(if(date!=null) "earned $date" else "verified result · earned",color=tint,fontSize=11.sp)
        } else {
            val progress=NumberFormat.getIntegerInstance().format(badge.progress)
            val target=NumberFormat.getIntegerInstance().format(trophy.target)
            val detail=when(trophy) {
                Trophy.OUTSCROLLED,Trophy.UNEMPLOYED -> if(!online) "Completed Battles required" else "$progress / $target · completed Battle results"
                Trophy.GRASS -> if(!online) "Join the global league to compete" else "Reach #10 or higher this season"
                Trophy.CHRONIC -> "$progress / $target · best scrolling streak"
                Trophy.BED_ROT -> "$progress / $target · best unique-reel day"
                else -> "$progress / $target · lifetime unique reels"
            }
            Text(detail,color=Palette.Faint,fontSize=11.sp)
            LinearProgressIndicator(progress={badge.progress.toFloat()/trophy.target},modifier=Modifier.fillMaxWidth().height(4.dp),color=tint,trackColor=Palette.High)
        }
    }
}
