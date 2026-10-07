package com.gridcc.doomscore.android

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.*
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class ChallengeChannelTest {
    @SuppressLint("SetJavaScriptEnabled")
    @Test fun hostedChallengeReceivesNativeChannelAndReturnsTokenOnce() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val script=instrumentation.context.assets.open("battle-challenge.js").bufferedReader().use{it.readText()}
        val finished=CountDownLatch(1)
        val received=AtomicReference<String>()
        var view:WebView?=null
        var channel:WebMessagePort?=null
        try {
            instrumentation.runOnMainSync {
                view=WebView(instrumentation.targetContext).apply {
                    settings.javaScriptEnabled=true;settings.allowFileAccess=false;settings.allowContentAccess=false
                    webViewClient=object:WebViewClient() {
                        override fun onPageFinished(v:WebView,url:String) {
                            val ports=v.createWebMessageChannel();channel=ports[0]
                            ports[0].setWebMessageCallback(object:WebMessagePort.WebMessageCallback() {
                                override fun onMessage(port:WebMessagePort,message:WebMessage) {received.set(message.data);finished.countDown()}
                            })
                            val setup="window.turnstile={render:(element,options)=>options.callback('synthetic.challenge.token')};"+script+";window.doomscoreChallengeReady();"
                            v.evaluateJavascript(setup) {
                                v.postWebMessage(WebMessage("doomscore-connect",arrayOf(ports[1])),Uri.parse("https://challenge.example"))
                            }
                        }
                    }
                    loadDataWithBaseURL("https://challenge.example/battle-challenge.html","<html><body><div id='challenge' data-sitekey='synthetic-public-key'></div><p id='status'></p></body></html>","text/html","utf-8",null)
                }
            }
            assertTrue("Native channel must reach the hosted page script",finished.await(15,TimeUnit.SECONDS))
            assertEquals("synthetic.challenge.token",received.get())
        } finally {instrumentation.runOnMainSync {channel?.close();view?.destroy()}}
    }
}
