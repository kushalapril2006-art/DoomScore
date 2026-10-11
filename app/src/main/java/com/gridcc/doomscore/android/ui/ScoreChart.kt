package com.gridcc.doomscore.android.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gridcc.doomscore.android.core.*
import java.text.NumberFormat

/** Full-width hit slots make even zero/tiny bars inspectable. Scroll works until a long press starts. */
@Composable fun ScoreChart(buckets:List<ScoreBucket>,tint:Color=Palette.Violet) {
    var selected by remember(buckets) {mutableStateOf<Int?>(null)}
    val maximum=(buckets.maxOfOrNull {it.reels} ?: 0L).coerceAtLeast(1)
    val fractions=buckets.map {animateFloatAsState(it.reels.toFloat()/maximum,tween(250,easing=FastOutSlowInEasing),label="chart bar")}
    val track=Palette.High;val highlight=Palette.Cyan
    Column(Modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(100.dp)
            .pointerInput(buckets) {
                detectTapGestures(onTap={selected=ScoreCharts.indexAt(it.x,size.width.toFloat(),buckets.size)})
            }.pointerInput(buckets) {
                detectDragGesturesAfterLongPress(onDragStart={selected=ScoreCharts.indexAt(it.x,size.width.toFloat(),buckets.size)}) {change,_->
                    selected=ScoreCharts.indexAt(change.position.x,size.width.toFloat(),buckets.size);change.consume()
                }
            }.onKeyEvent {event->
                if(event.type==KeyEventType.KeyDown && buckets.isNotEmpty() && event.key in listOf(Key.DirectionLeft,Key.DirectionRight)) {
                    selected=selected?.let {(it+if(event.key==Key.DirectionRight) 1 else -1).coerceIn(buckets.indices)} ?: 0;true
                } else false
            }.focusable().semantics {
                contentDescription="Reel counts chart. Hold or tap a bar to inspect its period."
                customActions=buckets.mapIndexed {index,bucket->CustomAccessibilityAction("${bucket.label}: ${bucket.reels} reels") {selected=index;true}}
            }) {
            val slot=size.width/buckets.size.coerceAtLeast(1)
            buckets.forEachIndexed {i,bucket->
                val height=if(bucket.reels==0L) 3.dp.toPx() else (size.height-6.dp.toPx())*fractions[i].value
                drawRoundRect(if(selected==i) highlight else if(bucket.reels>0) tint else track,
                    topLeft=Offset(slot*i+slot*.15f,size.height-height),size=Size(slot*.7f,height),cornerRadius=CornerRadius(3.dp.toPx()))
            }
        }
        val bucket=selected?.let {buckets.getOrNull(it)}
        Text(bucket?.let {"${it.label} · ${NumberFormat.getIntegerInstance().format(it.reels)} reels"} ?: "Hold a bar to inspect · drag to compare",
            modifier=Modifier.fillMaxWidth().heightIn(min=34.dp).padding(top=8.dp).semantics {liveRegion=LiveRegionMode.Polite},
            color=if(bucket==null) Palette.Dim else Palette.Text,fontSize=12.sp)
    }
}

@Composable fun ScoreProgressBar(label:String,count:Long,total:Long,tint:Color) {
    var show by remember(label,count,total) {mutableStateOf(false)}
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(28.dp).combinedClickable(onClick={show=!show},onLongClick={show=true})
            .semantics {contentDescription="$label: $count of $total reels"}) {
            LinearProgressIndicator(progress={if(total<=0) 0f else (count.toDouble()/total).toFloat().coerceIn(0f,1f)},
                modifier=Modifier.fillMaxWidth().height(5.dp).align(Alignment.Center),color=tint,trackColor=Palette.High)
        }
        if(show) Text("$label · ${NumberFormat.getIntegerInstance().format(count)} reels",color=Palette.Text,fontSize=12.sp)
    }
}
