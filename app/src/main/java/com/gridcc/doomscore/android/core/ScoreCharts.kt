package com.gridcc.doomscore.android.core

import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.floor

data class ScoreBucket(val label:String,val reels:Long) {
    init {require(label.isNotBlank() && reels>=0)}
}
object ScoreCharts {
    private fun date()=DateTimeFormatter.ofPattern("d MMM yyyy",Locale.getDefault())
    fun hourly(day:DayStats)=day.hourly.mapIndexed {hour,n->ScoreBucket("${day.day.format(date())} · %02d:00–%02d:59".format(hour,hour),n.toLong())}
    fun daily(days:List<DayStats>)=days.map {ScoreBucket(it.day.format(date()),it.total.toLong())}
    fun monthly(days:List<DayStats>)=days.groupBy {YearMonth.from(it.day)}.toSortedMap().map {(month,rows)->
        ScoreBucket(month.format(DateTimeFormatter.ofPattern("MMMM yyyy",Locale.getDefault())),rows.sumOf {it.total.toLong()})
    }
    fun hoursAcross(days:List<DayStats>,period:String)=(0..23).map {h->ScoreBucket("$period · %02d:00–%02d:59".format(h,h),days.sumOf {it.hourly[h].toLong()})}
    fun indexAt(x:Float,width:Float,count:Int):Int? {
        if(count<=0 || !x.isFinite() || !width.isFinite() || width<=0) return null
        return floor(x/width*count).toInt().coerceIn(0,count-1)
    }
}
