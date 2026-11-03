package org.pianopath.ui

import android.annotation.SuppressLint
import android.webkit.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONArray
import org.json.JSONObject
import org.pianopath.PracticeUi

@SuppressLint("SetJavaScriptEnabled")
@Composable fun Notation(p: PracticeUi,modifier: Modifier = Modifier,dark: Boolean = false,onSeek: (Double)->Unit = {},onError: (String)->Unit = {}) {
    var web by remember(p.xml) { mutableStateOf<WebView?>(null) }
    var ready by remember(p.xml) { mutableStateOf(false) }
    val currentError by rememberUpdatedState(onError)
    val currentSeek by rememberUpdatedState(onSeek)
    key(p.xml) {
        AndroidView(modifier=modifier,factory={ context -> WebView(context).apply {
            setBackgroundColor(android.graphics.Color.rgb(255,254,251))
            settings.javaScriptEnabled=true
            settings.allowFileAccess=false; settings.allowContentAccess=false
            settings.domStorageEnabled=false
            settings.setSupportZoom(true);settings.builtInZoomControls=true;settings.displayZoomControls=false
            webViewClient=object: WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView,request: WebResourceRequest)=true
            }
            addJavascriptInterface(object {
                @JavascriptInterface fun score() = p.xml
                @JavascriptInterface fun ready() { post { ready=true } }
                @JavascriptInterface fun seek(beat: Double) { if(beat.isFinite()) post { currentSeek(beat) } }
                @JavascriptInterface fun error(error: String) { post { currentError(error) } }
            },"Android")
            loadUrl("file:///android_asset/notation/index.html"); web=this
        } },onRelease={ it.removeJavascriptInterface("Android");it.stopLoading();it.destroy();web=null })
    }
    LaunchedEffect(ready,p.transportBeat,p.beat,p.reviewBeat,p.review,p.running,p.matched,p.missed,p.take,p.startMeasure,p.endMeasure,p.startOffset,p.endOffset,p.zoom,p.horizontal,p.sheetMargin,dark,p.hand) {
        if(!ready) return@LaunchedEffect
        val score=p.score ?: return@LaunchedEffect
        fun json(ids: Set<Int>)=JSONArray(score.notes.filter { it.id in ids }.map { JSONArray(listOf(it.beat,it.midi)) }).toString()
        val start=score.measures[p.startMeasure-1].start+p.startOffset
        val end=score.measures[p.endMeasure-1].start+p.endOffset
        val take=JSONArray(p.take.map{n->JSONObject().put("id",n.id).put("pitch",n.pitch).put("beat",n.beat).put("duration",n.duration ?: JSONObject.NULL).put("expectedId",n.expectedId ?: JSONObject.NULL).put("onsetMs",n.onsetMs ?: JSONObject.NULL)})
        val expected=JSONArray(score.notes.filter{!it.rest}.map{n->JSONObject().put("id",n.id).put("midi",n.midi).put("beat",n.beat).put("staff",n.staff)})
        val beat=if(p.review && !p.running)p.reviewBeat else if(p.listening || p.running)p.transportBeat else p.beat
        web?.evaluateJavascript("progress($beat,${json(p.matched)},${json(p.missed)},$start,$end,${p.zoom},${p.horizontal},$take,$expected,${p.visualTolerance},${p.sheetMargin},$dark,${p.bpm},'${p.hand.name}',true)",null)
    }
}
