package com.gridcc.doomscore.android.ui

import android.annotation.SuppressLint
import android.net.Uri
import android.net.http.SslError
import android.webkit.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.gridcc.doomscore.android.BuildConfig

/** A one-use challenge for optional online accounts. No bridge exposes app data to JavaScript. */
@SuppressLint("SetJavaScriptEnabled")
@Composable fun BattleChallenge(onCancel: () -> Unit, onToken: (String) -> Unit) {
    val endpoint = remember { Uri.parse(BuildConfig.CAPTCHA_URL) }
    val origin = remember { Uri.Builder().scheme("https").authority(endpoint.encodedAuthority).build() }
    var failure by remember { mutableStateOf(false) }
    var delivered by remember { mutableStateOf(false) }
    var view by remember { mutableStateOf<WebView?>(null) }
    var channel by remember { mutableStateOf<WebMessagePort?>(null) }
    fun trusted(url: Uri) = url.scheme == "https" && url.encodedAuthority == endpoint.encodedAuthority &&
        url.encodedPath == endpoint.encodedPath && url.query == null && url.fragment == null
    DisposableEffect(Unit) { onDispose { channel?.close(); view?.apply { stopLoading(); destroy() } } }
    AlertDialog(onDismissRequest=onCancel, confirmButton={}, dismissButton={TextButton(onClick=onCancel){Text("Cancel")}},
        title={Text("Quick security check")}, text={
            Column {
                Text("Complete this once to create your battle account.")
                if(failure) Text("Could not load the check. Close it and try again.",color=Palette.Pink)
                AndroidView(modifier=Modifier.fillMaxWidth().height(330.dp), factory={ context ->
                    WebView(context).apply {
                        view=this
                        settings.apply {
                            javaScriptEnabled=true
                            domStorageEnabled=false
                            allowFileAccess=false
                            allowContentAccess=false
                            mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            javaScriptCanOpenWindowsAutomatically=false
                            setSupportMultipleWindows(false)
                            cacheMode=WebSettings.LOAD_NO_CACHE
                        }
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this,false)
                        webViewClient=object: WebViewClient() {
                            override fun shouldOverrideUrlLoading(v:WebView, request:WebResourceRequest):Boolean =
                                request.isForMainFrame && !trusted(request.url)
                            override fun onReceivedSslError(v:WebView, handler:SslErrorHandler, error:SslError) { handler.cancel();failure=true }
                            override fun onReceivedError(v:WebView, request:WebResourceRequest, error:WebResourceError) {if(request.isForMainFrame) failure=true}
                            override fun onPageFinished(v:WebView, url:String) {
                                if(!trusted(Uri.parse(url))) {failure=true;return}
                                channel?.close()
                                val ports=v.createWebMessageChannel()
                                channel=ports[0]
                                ports[0].setWebMessageCallback(object:WebMessagePort.WebMessageCallback() {
                                    override fun onMessage(port:WebMessagePort, message:WebMessage) {
                                        val token=message.data.orEmpty()
                                        if(!delivered && token.length in 20..4096 && Regex("[A-Za-z0-9._-]+").matches(token)) {
                                            delivered=true
                                            ports[0].close()
                                            onToken(token)
                                        }
                                    }
                                })
                                v.postWebMessage(WebMessage("doomscore-connect",arrayOf(ports[1])),origin)
                            }
                        }
                        loadUrl(endpoint.toString())
                    }
                })
            }
        })
}
