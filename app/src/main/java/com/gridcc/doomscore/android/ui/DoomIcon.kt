package com.gridcc.doomscore.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.gridcc.doomscore.android.core.SourceApp
import com.gridcc.doomscore.android.core.Trophy

enum class Mark { REELS, PODIUM, CHART, BATTLE, SETTINGS, STREAK, TROPHY, SHIELD, REPEAT, CLOCK, TARGET, MOON, LOCK, GLOBE, BED, LEAF, STAR, INSTAGRAM, PLAY, MUSIC, BOLT }
fun SourceApp.mark()=when(this) {SourceApp.INSTAGRAM->Mark.INSTAGRAM;SourceApp.YOUTUBE->Mark.PLAY;SourceApp.TIKTOK->Mark.MUSIC;SourceApp.SNAPCHAT->Mark.BOLT}
fun Trophy.mark()=when(this) {Trophy.ONE_MORE->Mark.MOON;Trophy.FOR_YOU->Mark.REELS;Trophy.FINAL_BOSS->Mark.STAR;Trophy.BED_ROT->Mark.BED;Trophy.CHRONIC->Mark.GLOBE;Trophy.OUTSCROLLED->Mark.BATTLE;Trophy.UNEMPLOYED->Mark.TROPHY;Trophy.GRASS->Mark.LEAF}

/** Original 24-unit line drawings. Parent controls provide accessible names. */
@Composable fun DoomIcon(mark:Mark,tint:Color=Palette.Dim,modifier:Modifier=Modifier) {
    Canvas(modifier.size(24.dp)) {
        val sx=size.width/24f;val sy=size.height/24f
        fun p(x:Float,y:Float)=Offset(x*sx,y*sy)
        val stroke=Stroke(1.65f*sx,cap=StrokeCap.Round,join=StrokeJoin.Round)
        fun line(x:Float,y:Float,x2:Float,y2:Float)=drawLine(tint,p(x,y),p(x2,y2),stroke.width,StrokeCap.Round)
        fun path(vararg points:Float,closed:Boolean=false) {
            val shape=Path().apply {moveTo(points[0]*sx,points[1]*sy);var i=2;while(i<points.size){lineTo(points[i]*sx,points[i+1]*sy);i+=2};if(closed)close()}
            drawPath(shape,tint,style=stroke)
        }
        fun circle(x:Float,y:Float,r:Float)=drawCircle(tint,r*sx,p(x,y),style=stroke)
        fun box(x:Float,y:Float,w:Float,h:Float,r:Float=3f)=drawRoundRect(tint,p(x,y),Size(w*sx,h*sy),androidx.compose.ui.geometry.CornerRadius(r*sx,r*sy),style=stroke)
        when(mark) {
            Mark.REELS -> {box(3f,3f,18f,18f,4f);line(3f,8f,21f,8f);line(8f,3f,5f,8f);line(15f,3f,12f,8f);path(10f,12f,15f,15f,10f,18f,closed=true)}
            Mark.PODIUM -> {box(3f,12f,6f,9f,1f);box(9f,7f,6f,14f,1f);box(15f,15f,6f,6f,1f);circle(12f,3f,1f)}
            Mark.CHART -> {line(4f,3f,4f,21f);line(4f,21f,21f,21f);line(9f,16f,9f,11f);line(14f,16f,14f,6f);line(19f,16f,19f,9f)}
            Mark.BATTLE -> {path(4f,3f,10f,7f,17f,17f);path(20f,3f,14f,7f,7f,17f);line(5f,14f,10f,19f);line(14f,19f,19f,14f);line(4f,21f,7f,18f);line(20f,21f,17f,18f)}
            Mark.SETTINGS -> {circle(12f,12f,7f);circle(12f,12f,2.5f);line(12f,2f,12f,5f);line(12f,19f,12f,22f);line(2f,12f,5f,12f);line(19f,12f,22f,12f);line(5f,5f,7f,7f);line(17f,17f,19f,19f);line(5f,19f,7f,17f);line(17f,7f,19f,5f)}
            Mark.STREAK,Mark.BOLT -> path(13f,2f,5f,13f,11f,13f,10f,22f,19f,10f,13f,10f,13f,2f)
            Mark.TROPHY -> {path(7f,3f,17f,3f,17f,9f,15f,13f,12f,15f,9f,13f,7f,9f,7f,3f);path(7f,5f,3f,5f,3f,9f,6f,12f,9f,12f);path(17f,5f,21f,5f,21f,9f,18f,12f,15f,12f);line(12f,15f,12f,21f);line(7f,21f,17f,21f)}
            Mark.SHIELD -> {path(12f,2f,20f,6f,19f,15f,12f,22f,5f,15f,4f,6f,12f,2f);path(8f,12f,11f,15f,16f,9f)}
            Mark.REPEAT -> {path(4f,8f,7f,5f,20f,5f,20f,10f);path(17f,2f,20f,5f,17f,8f);path(20f,16f,17f,19f,4f,19f,4f,14f);path(7f,16f,4f,19f,7f,22f)}
            Mark.CLOCK -> {circle(12f,12f,9f);path(12f,6f,12f,12f,16f,14f)}
            Mark.TARGET -> {circle(12f,12f,9f);circle(12f,12f,5f);circle(12f,12f,1f)}
            Mark.MOON -> {val moon=Path().apply {moveTo(16*sx,3*sy);cubicTo(1*sx,0f,0f,19*sy,12*sx,21*sy);cubicTo(17*sx,22*sy,21*sx,18*sy,21*sx,14*sy);cubicTo(10*sx,17*sy,7*sx,8*sy,16*sx,3*sy)};drawPath(moon,tint,style=stroke)}
            Mark.LOCK -> {box(5f,10f,14f,11f);path(8f,10f,8f,6f,10f,3f,14f,3f,16f,6f,16f,10f);line(12f,14f,12f,17f)}
            Mark.GLOBE -> {circle(12f,12f,9f);drawOval(tint,p(8f,3f),Size(8*sx,18*sy),style=stroke);line(3f,12f,21f,12f)}
            Mark.BED -> {line(3f,6f,3f,21f);line(21f,12f,21f,21f);path(3f,17f,21f,17f,21f,12f,3f,12f);box(5f,8f,6f,4f,1f)}
            Mark.LEAF -> {val leaf=Path().apply{moveTo(4*sx,20*sy);cubicTo(1*sx,8*sy,11*sx,2*sy,21*sx,3*sy);cubicTo(21*sx,17*sy,11*sx,22*sy,4*sx,20*sy)};drawPath(leaf,tint,style=stroke);line(4f,20f,16f,8f)}
            Mark.STAR -> path(12f,2f,15f,8f,22f,9f,17f,14f,18f,21f,12f,18f,6f,21f,7f,14f,2f,9f,9f,8f,12f,2f)
            Mark.INSTAGRAM -> {box(3f,3f,18f,18f,5f);circle(12f,12f,4f);drawCircle(tint,1*sx,p(17f,7f))}
            Mark.PLAY -> {box(2f,5f,20f,14f,4f);path(10f,9f,15f,12f,10f,15f,closed=true)}
            Mark.MUSIC -> {path(9f,17f,9f,4f,19f,2f,19f,15f);line(9f,8f,19f,6f);circle(6f,18f,3f);circle(16f,16f,3f)}
        }
    }
}
