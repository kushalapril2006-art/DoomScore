package com.gridcc.doomscore.android

import android.accessibilityservice.AccessibilityServiceInfo
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import android.app.UiAutomation
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/** Test-only window reader: unlike the shell dump, keeps the counter service connected. */
class UiProbe {
    @Test fun exportWindows() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
            check(android.os.Build.MODEL.startsWith("sdk_gphone")) {"Synthetic emulator only"}
            val automation=instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
            val info=automation.serviceInfo
            info.flags=info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            automation.serviceInfo=info
            Thread.sleep(100)
            val watch=InstrumentationRegistry.getArguments().getString("watch")=="true"
            val stop=java.io.File(instrumentation.targetContext.cacheDir,"island-probe-stop")
            do {
            val roots=automation.windows.mapNotNull {it.root}.ifEmpty {listOfNotNull(automation.rootInActiveWindow)}
            val file=java.io.File(instrumentation.targetContext.cacheDir,"island-ui.xml")
            var remaining=10000
            val temporary=java.io.File(file.parentFile,"island-ui.tmp")
            temporary.outputStream().use {stream ->
                val xml=android.util.Xml.newSerializer().apply {setOutput(stream,"UTF-8");startDocument("UTF-8",true);startTag("","hierarchy");attribute("","capture-ms",android.os.SystemClock.elapsedRealtime().toString());attribute("","watch",watch.toString())}
                @Suppress("DEPRECATION")
                fun node(n:AccessibilityNodeInfo,depth:Int) {
                    try {
                        if(remaining--<=0 || depth>100 || !n.isVisibleToUser) return
                        val rect=Rect();n.getBoundsInScreen(rect)
                        xml.startTag("","node")
                        for((key,value) in mapOf("text" to n.text?.toString().orEmpty(),"content-desc" to n.contentDescription?.toString().orEmpty(),
                            "resource-id" to n.viewIdResourceName.orEmpty(),"package" to n.packageName?.toString().orEmpty(),
                            "class" to n.className?.toString().orEmpty(),"bounds" to "[${rect.left},${rect.top}][${rect.right},${rect.bottom}]",
                            "checkable" to n.isCheckable.toString(),"checked" to n.isChecked.toString(),"selected" to n.isSelected.toString(),"clickable" to n.isClickable.toString())) xml.attribute("",key,value.take(8192))
                        for(i in 0 until n.childCount.coerceAtMost(remaining.coerceAtLeast(0))) n.getChild(i)?.let {node(it,depth+1)}
                        xml.endTag("","node")
                    } finally {n.recycle()}
                }
                roots.forEach {node(it,0)}
                xml.endTag("","hierarchy");xml.endDocument();xml.flush()
            }
            check(temporary.renameTo(file)) {"Unable to export snapshot"}
            if(watch) Thread.sleep(500)
            } while(watch && !stop.exists())
    }
}
