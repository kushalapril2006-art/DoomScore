package com.gridcc.doomscore.android.ui

import android.content.Context
import android.content.Intent
import android.graphics.*
import androidx.core.content.FileProvider
import com.gridcc.doomscore.android.core.DayStats
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object RecapShare {
    suspend fun share(context: Context, days: List<DayStats>): Boolean = try {
        val intent=withContext(Dispatchers.IO) { prepare(context,days) }
        withContext(Dispatchers.Main) { context.startActivity(Intent.createChooser(intent,"Share your Wrapped")) }
        true
    } catch(e:Exception) { if(e is kotlinx.coroutines.CancellationException) throw e;false }
    private fun prepare(context: Context, days: List<DayStats>): Intent {
        val count = days.sumOf {it.total}
        val bitmap = Bitmap.createBitmap(1080,1350,Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(Color.parseColor("#0B0E15"))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val display=Typeface.create(context.resources.getFont(com.gridcc.doomscore.android.R.font.space_grotesk),Typeface.BOLD)
        val body=context.resources.getFont(com.gridcc.doomscore.android.R.font.dm_sans)
        paint.shader = LinearGradient(0f,0f,1080f,1350f,intArrayOf(Color.parseColor("#C6FF3D"),Color.parseColor("#3DE0FF")),null,Shader.TileMode.CLAMP)
        canvas.drawRoundRect(60f,60f,1020f,1290f,64f,64f,paint)
        paint.shader=null;paint.color=Color.parseColor("#141923");canvas.drawRoundRect(65f,65f,1015f,1285f,60f,60f,paint)
        fun text(value: String,y:Float,size:Float,color:String="#F4F4FA",bold:Boolean=false) {
            paint.color=Color.parseColor(color);paint.textSize=size;paint.textAlign=Paint.Align.CENTER
            paint.typeface=if(bold) display else body
            canvas.drawText(value,540f,y,paint)
        }
        text("doomscore",190f,58f,"#C6FF3D",true)
        text("MY LAST ${days.size} DAYS, WRAPPED",300f,30f,"#B0B8CA")
        text(count.toString(),580f,190f,"#C6FF3D",true)
        text("reels watched",665f,44f)
        text("${duration(days.sumOf{it.watchMs})} in reel feeds",810f,38f,"#3DE0FF")
        text("${days.sumOf{it.ads}} ads skipped · ${days.sumOf{it.repeats}} rewatches",900f,30f,"#B0B8CA")
        text("personal best: ${days.maxOfOrNull {it.total} ?: 0} reels / day",1050f,30f,"#B0B8CA")
        text("can your thumb beat this?",1180f,44f,"#FF4FB3",true)
        val directory = File(context.cacheDir,"recaps").apply{mkdirs()}
        val file = File(directory,"doomscore-wrapped.png")
        try {file.outputStream().use {check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))}} finally {bitmap.recycle()}
        val uri = FileProvider.getUriForFile(context,"${context.packageName}.files",file)
        return Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM,uri)
            .putExtra(Intent.EXTRA_TEXT,"$count reels in ${days.size} days. Can your thumb beat this? #doomscore").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
