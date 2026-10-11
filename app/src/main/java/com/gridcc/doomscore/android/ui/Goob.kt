package com.gridcc.doomscore.android.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gridcc.doomscore.android.core.GoobShape
import com.gridcc.doomscore.android.core.ScrollTier
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.*

@Composable fun Goob(tier: ScrollTier,animate:Boolean=true,modifier:Modifier=Modifier,interactive:Boolean=false) {
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val motion=LocalMascotMotion.current
    val resumed=lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    val active=animate && motion && resumed
    var phase by remember {mutableFloatStateOf(0f)}
    val response=remember {Animatable(0f)}
    var greeting by remember {mutableStateOf(false)}
    var tapJob by remember {mutableStateOf<Job?>(null)}
    val scope=rememberCoroutineScope()
    val haptic=LocalHapticFeedback.current
    val interactions=remember {MutableInteractionSource()}
    val contour=remember {Path()}
    val mouth=remember {Path()}
    val x=remember {FloatArray(GoobShape.VERTICES)}
    val y=remember {FloatArray(GoobShape.VERTICES)}
    val color=Palette.rank(tier.level)
    val fill=remember(tier.level) {Brush.linearGradient(listOf(color,if(tier.level<3) Color(0xFFC6FF3D) else Color(0xFFFF4FB3)))}
    val ink=Color(0xFF172019)
    LaunchedEffect(active) {
        if(!active) {
            tapJob?.cancel();tapJob=null;greeting=false;phase=0f;response.snapTo(0f)
            return@LaunchedEffect
        }
        var last=0L
        while(isActive) {
            val now=withFrameNanos {it}
            if(last==0L) last=now
            val elapsed=now-last
            if(elapsed>=33_333_333L) {
                phase=(phase+elapsed/8_000_000_000f*(2*PI).toFloat())%(2*PI).toFloat()
                last=now
            }
        }
    }
    var target=modifier.size(184.dp).semantics {contentDescription=if(greeting) "Goob says hi" else "Goob looks ${tier.title}"}
    if(interactive) target=target.clickable(interactionSource=interactions,indication=null,role=Role.Button,onClickLabel="Say hi to Goob") {
        if(resumed && tapJob?.isActive!=true) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            tapJob=scope.launch {
                greeting=true
                val started=android.os.SystemClock.uptimeMillis()
                try {
                    if(motion && animate) {
                        response.animateTo(1f,tween(110,easing=FastOutSlowInEasing))
                        response.animateTo(0f,tween(360,easing=FastOutSlowInEasing))
                    }
                    delay((800L-(android.os.SystemClock.uptimeMillis()-started)).coerceAtLeast(0L))
                } finally {greeting=false}
            }
        }
    }
    Canvas(target.graphicsLayer {
        val r=response.value
        scaleX=1f+.08f*r;scaleY=1f-.06f*r
        rotationZ=sin(r*PI.toFloat())*5f
    }) {
        val time=phase
        val r=response.value
        val center=Offset(size.width/2,size.height/2)
        val radius=size.minDimension*.39f
        GoobShape.frame(time,tier.level,r,x,y)
        contour.reset()
        for(i in 0 until GoobShape.VERTICES) {
            val px=center.x+x[i]*radius;val py=center.y+y[i]*radius
            if(i==0) contour.moveTo(px,py) else contour.lineTo(px,py)
        }
        contour.close()
        drawCircle(color.copy(alpha=.035f),radius*1.16f,center)
        drawPath(contour,fill)
        drawPath(contour,Color.White.copy(alpha=.2f),style=Stroke(2.dp.toPx()))
        drawOval(Color.White.copy(alpha=.28f),Offset(center.x-radius*.6f,center.y-radius*.6f),Size(radius*.48f,radius*.2f))
        val blink=1f-.85f*max(0f,sin(time)).pow(80)
        for(side in -1..1 step 2) {
            val eyeBlink=if(side==1 && greeting) .18f else blink
            val eye=center+Offset(side*radius*.31f,-radius*.08f)
            drawOval(Color.White,eye-Offset(radius*.23f,radius*.26f*eyeBlink),Size(radius*.46f,radius*.52f*eyeBlink))
            if(eyeBlink>.5f) {
                val pupil=eye+Offset(sin(time)*radius*.035f,radius*.045f)
                drawCircle(ink,radius*(.13f-tier.level*.008f),pupil)
                drawCircle(Color.White,radius*.035f,pupil-Offset(radius*.04f,radius*.04f))
            }
        }
        mouth.reset()
        mouth.moveTo(center.x-radius*.18f,center.y+radius*.4f)
        mouth.quadraticTo(center.x,center.y+radius*(if(greeting) .76f else if(tier.level<4) .63f else .78f),center.x+radius*.18f,center.y+radius*.4f)
        drawPath(mouth,ink,style=Stroke(3.dp.toPx()))
        if(tier.level<3) for(side in -1..1 step 2) drawCircle(Color(0xFFFF4FB3).copy(alpha=.4f),radius*.075f,center+Offset(side*radius*.5f,radius*.27f))
    }
}
