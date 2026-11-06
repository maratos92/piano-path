package org.pianopath.ui

import android.annotation.SuppressLint
import android.webkit.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONArray
import org.json.JSONObject
import org.pianopath.PracticeUi

@SuppressLint("SetJavaScriptEnabled")
@Composable fun Notation(p: PracticeUi,modifier: Modifier = Modifier,dark: Boolean = false,onSeek: (Double)->Unit = {},onLoop: (Double,Double)->Unit = {_,_->},onPreview: (Double?)->Unit = {},animate: Boolean = true,onError: (String)->Unit = {}) {
    var web by remember(p.xml) { mutableStateOf<WebView?>(null) }
    var renderFailed by remember(p.xml){mutableStateOf(false)}
    var ready by remember(p.xml) { mutableStateOf(false) }
    val currentError by rememberUpdatedState(onError)
    val currentSeek by rememberUpdatedState(onSeek)
    val currentLoop by rememberUpdatedState(onLoop)
    val currentPreview by rememberUpdatedState(onPreview)
    val accent="#%06x".format(MaterialTheme.colorScheme.primary.toArgb() and 0xffffff)
    if(renderFailed)Column(modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Score renderer stopped. Your score and practice data are kept.")
        OutlinedButton(onClick={renderFailed=false;ready=false}){Text("Retry score rendering")}
    } else key(p.xml) {
        AndroidView(modifier=modifier,factory={ context -> WebView(context).apply {
            val scoreView=this
            setBackgroundColor(android.graphics.Color.rgb(255,254,251))
            settings.javaScriptEnabled=true
            settings.blockNetworkLoads=true
            settings.allowFileAccess=false; settings.allowContentAccess=false
            settings.domStorageEnabled=false
            // Respect the device-width viewport instead of scaling a long SVG to fit.
            settings.useWideViewPort=true;settings.loadWithOverviewMode=false
            settings.setSupportZoom(true);settings.builtInZoomControls=true;settings.displayZoomControls=false
            webViewClient=object: WebViewClient() {
                override fun onRenderProcessGone(view: WebView,detail: RenderProcessGoneDetail): Boolean {
                    ready=false;web=null;renderFailed=true;currentError("Score renderer stopped; use Retry score rendering")
                    return true
                }
                override fun shouldOverrideUrlLoading(view: WebView,request: WebResourceRequest)=true
            }
            addJavascriptInterface(object {
                @JavascriptInterface fun score() = p.xml
                @JavascriptInterface fun ready() { post { if(web===scoreView)ready=true } }
                @JavascriptInterface fun seek(beat: Double) { if(beat.isFinite()) post { currentSeek(beat) } }
                @JavascriptInterface fun previewSeek(beat: Double) {if(beat.isFinite())post{currentPreview(beat)}}
                @JavascriptInterface fun previewEnd() {post{currentPreview(null)}}
                @JavascriptInterface fun setLoop(start: Double,end: Double) {if(start.isFinite() && end.isFinite() && end>start)post {currentLoop(start,end)} }
                @JavascriptInterface fun error(error: String) { post {if(web===scoreView){ready=false;renderFailed=true;currentError(error)}} }
            },"Android")
            loadUrl("file:///android_asset/notation/index.html"); web=this
        } },onRelease={ it.removeJavascriptInterface("Android");it.stopLoading();it.destroy();if(web===it){web=null;ready=false} })
    }
    val score=p.score
    val matched=remember(score,p.matched){JSONArray(score?.notes.orEmpty().filter{it.id in p.matched}.map{JSONArray(listOf(it.beat,it.midi))}).toString()}
    val missed=remember(score,p.missed){JSONArray(score?.notes.orEmpty().filter{it.id in p.missed}.map{JSONArray(listOf(it.beat,it.midi))}).toString()}
    val take=remember(p.take){JSONArray(p.take.map{n->JSONObject().put("id",n.id).put("pitch",n.pitch).put("beat",n.beat).put("duration",n.duration ?: JSONObject.NULL).put("expectedId",n.expectedId ?: JSONObject.NULL).put("onsetMs",n.onsetMs ?: JSONObject.NULL)}).toString()}
    val expected=remember(score){JSONArray(score?.notes.orEmpty().filter{!it.rest}.map{n->JSONObject().put("id",n.id).put("midi",n.midi).put("beat",n.beat).put("staff",n.staff)}).toString()}
    LaunchedEffect(ready,matched,missed,take,expected){
        if(ready)web?.evaluateJavascript("setPerformanceData($matched,$missed,$take,$expected)",null)
    }
    LaunchedEffect(ready,p.cursorPreview,p.transportBeat,p.beat,p.reviewBeat,p.review,p.running,p.listening,p.paused,p.bpm,p.visualTolerance,p.matched,p.missed,p.take,p.startMeasure,p.endMeasure,p.startOffset,p.endOffset,p.zoom,p.horizontal,p.sheetMargin,dark,p.hand,p.loop,p.selectingLoop,accent,animate) {
        if(!ready) return@LaunchedEffect
        if(score==null)return@LaunchedEffect
        val start=score.measures[p.startMeasure-1].start+p.startOffset
        val end=score.measures[p.endMeasure-1].start+p.endOffset
        val beat=p.cursorPreview ?: if(p.review && !p.running)p.reviewBeat else if(p.listening || p.running)p.transportBeat else p.beat
        web?.evaluateJavascript("progress($beat,performanceData.matched,performanceData.missed,$start,$end,${p.zoom},${p.horizontal},performanceData.take,performanceData.expected,${p.visualTolerance},${p.sheetMargin},$dark,${p.bpm},'${p.hand.name}',true,${p.selectingLoop},${!p.running && !p.listening},${score.endBeat},${p.cursorPreview==null && !p.paused && (p.running || p.listening || p.review)},'$accent',$animate)",null)
    }
}
