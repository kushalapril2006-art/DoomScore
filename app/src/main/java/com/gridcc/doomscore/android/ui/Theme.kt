package com.gridcc.doomscore.android.ui

import androidx.compose.animation.core.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gridcc.doomscore.android.core.ScrollTier
import kotlin.math.*

object Palette {
    val Bg = Color(0xFF0B0E15); val Surface = Color(0xFF141923); val High = Color(0xFF222A38)
    val Lime = Color(0xFFC6FF3D); val Cyan = Color(0xFF3DE0FF); val Pink = Color(0xFFFF4FB3)
    val Violet = Color(0xFF8A5CFF); val Orange = Color(0xFFFF8A3D); val Text = Color(0xFFF4F4FA)
    val Dim = Color(0xFFB0B8CA); val Faint = Color(0xFF98A3B8)
    val Brand get() = Brush.linearGradient(listOf(Lime, Cyan))
    fun rank(level: Int) = Color(ScrollTier.color(level))
}
@Composable fun DoomTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(primary = Palette.Lime, secondary = Palette.Cyan, tertiary = Palette.Pink,
        background = Palette.Bg, surface = Palette.Surface, surfaceVariant = Palette.High, onPrimary = Palette.Bg, onSurface = Palette.Text), typography=DoomFonts.Type, shapes=androidx.compose.material3.Shapes(small=androidx.compose.foundation.shape.RoundedCornerShape(12.dp),medium=androidx.compose.foundation.shape.RoundedCornerShape(18.dp),large=androidx.compose.foundation.shape.RoundedCornerShape(24.dp)), content = content)
}

@Composable fun Goob(tier: ScrollTier, animate: Boolean = true, modifier: Modifier = Modifier) {
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateFlow.collectAsState()
    val phase=if(animate && lifecycleState.isAtLeast(Lifecycle.State.RESUMED)) {
        rememberInfiniteTransition(label="Goob idle").animateFloat(0f,(2*PI).toFloat(),infiniteRepeatable(tween(8000,easing=LinearEasing)),label="Goob phase")
    } else remember {mutableFloatStateOf(0f)}
    Canvas(modifier.size(184.dp).semantics { contentDescription = "Goob looks ${tier.title}" }) {
        val time=phase.value
        val center = Offset(size.width/2, size.height/2)
        val radius = size.minDimension * .41f
        val points = (0..72).map { i ->
            val angle = i/72f * 2f * PI.toFloat()
            val wobble = .025f + tier.level*.008f
            val k = 1 + wobble*(sin(3*angle+time)+.4f*sin(5*angle-time))
            val melt = if (tier.level >= 5) .13f * max(0f, sin(angle)).pow(6) * (.5f+.5f*sin(7*angle+time)) else 0f
            Offset(center.x + cos(angle)*radius*k, center.y + sin(angle)*radius*(k+melt))
        }
        drawCircle(Palette.rank(tier.level).copy(alpha=.04f), radius*1.22f, center)
        drawCircle(Palette.rank(tier.level).copy(alpha=.07f), radius*1.1f, center)
        val blob = Path().apply { moveTo(points[0].x, points[0].y); points.drop(1).forEach { lineTo(it.x, it.y) }; close() }
        drawPath(blob, Brush.linearGradient(listOf(Palette.rank(tier.level), if (tier.level < 3) Palette.Lime else Palette.Pink)))
        drawPath(blob, Color.White.copy(alpha=.2f), style=Stroke(2.dp.toPx()))
        drawOval(Color.White.copy(alpha=.28f), Offset(center.x-radius*.6f, center.y-radius*.6f), androidx.compose.ui.geometry.Size(radius*.48f, radius*.2f))
        val blink=1f-.85f*max(0f,sin(time)).pow(80)
        listOf(-1,1).forEach { side ->
            val eye = center + Offset(side*radius*.31f, -radius*.08f)
            drawOval(Color.White, eye-Offset(radius*.23f, radius*.26f*blink), androidx.compose.ui.geometry.Size(radius*.46f, radius*.52f*blink))
            val pupil = eye + Offset(sin(time)*radius*.035f, radius*.045f)
            if (blink > .5f) {
                drawCircle(Palette.Bg, radius*(.13f-tier.level*.008f), pupil)
                drawCircle(Color.White, radius*.035f, pupil-Offset(radius*.04f, radius*.04f))
            }
        }
        val mouth = Path().apply {
            moveTo(center.x-radius*.18f, center.y+radius*.4f)
            quadraticTo(center.x, center.y+radius*(if (tier.level < 4) .63f else .78f), center.x+radius*.18f, center.y+radius*.4f)
        }
        drawPath(mouth, Palette.Bg, style=Stroke(3.dp.toPx()))
        if (tier.level < 3) listOf(-1,1).forEach { drawCircle(Palette.Pink.copy(alpha=.4f), radius*.075f, center+Offset(it*radius*.5f,radius*.27f)) }
    }
}
