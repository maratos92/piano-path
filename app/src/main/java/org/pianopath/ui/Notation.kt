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
    var bridge by remember(p.xml) { mutableStateOf<NotationBridge?>(null) }
    val currentError by rememberUpdatedState(onError)
    val currentSeek by rememberUpdatedState(onSeek)
    val currentLoop by rememberUpdatedState(onLoop)
    val currentPreview by rememberUpdatedState(onPreview)
    val reduced by rememberUpdatedState(!animate)
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
                    ready=false;bridge?.close();bridge=null;web=null;renderFailed=true;currentError("Score renderer stopped; use Retry score rendering")
                    return true
                }
                override fun shouldOverrideUrlLoading(view: WebView,request: WebResourceRequest)=true
            }
            addJavascriptInterface(object {
                @JavascriptInterface fun reducedMotion()=reduced
                @JavascriptInterface fun score() = p.xml
                @JavascriptInterface fun ready() { post { if(web===scoreView)ready=true } }
                @JavascriptInterface fun seek(beat: Double) { if(beat.isFinite()) post { currentSeek(beat) } }
                @JavascriptInterface fun previewSeek(beat: Double) {if(beat.isFinite())post{currentPreview(beat)}}
                @JavascriptInterface fun previewEnd() {post{currentPreview(null)}}
                @JavascriptInterface fun setLoop(start: Double,end: Double) {if(start.isFinite() && end.isFinite() && end>start)post {currentLoop(start,end)} }
                @JavascriptInterface fun error(error: String) { post {if(web===scoreView){ready=false;renderFailed=true;currentError(error)}} }
            },"Android")
            bridge=NotationBridge(this);loadUrl("file:///android_asset/notation/index.html"); web=this
        } },onRelease={ if(web===it){bridge?.close();bridge=null;web=null;ready=false};it.removeJavascriptInterface("Android");it.stopLoading();it.destroy() })
    }
    // Only one WebView command may be outstanding. New snapshots replace the pending
    // snapshot instead of queuing every intermediate key release and timer tick.
    // Read readiness during composition so the asynchronous ready callback invalidates
    // this scope even when an idle score has no transport/MIDI updates.
    val rendererReady=ready
    val rendererBridge=bridge
    SideEffect { if(rendererReady)rendererBridge?.submit(NotationPacket(p,dark,accent,animate)) }
}

private data class NotationPacket(val p: PracticeUi,val dark: Boolean,val accent: String,val animate: Boolean)

private class NotationBridge(private val view: WebView) {
    private var pending: NotationPacket?=null
    private var busy=false
    private var closed=false
    private var lastScore: org.pianopath.core.Score?=null
    private var notesById=emptyMap<Int,org.pianopath.core.ScoreNote>()
    private var lastMatched: Set<Int>?=null
    private var lastMissed: Set<Int>?=null
    private var lastTake: List<org.pianopath.core.PlayedNote>?=null
    private var lastReview=false
    private var matched="[]"
    private var missed="[]"
    private var take="[]"
    private var expected="[]"
    fun close(){closed=true;pending=null}
    fun submit(packet: NotationPacket){if(closed)return;pending=packet;if(!busy)drain()}
    private fun drain(){
        val packet=pending ?: return
        pending=null
        val p=packet.p;val score=p.score ?: return
        var changed=false
        var matchedCode="performanceData.matched";var missedCode="performanceData.missed";var takeCode="performanceData.take";var expectedCode="performanceData.expected"
        if(lastScore!==score){
            lastScore=score;notesById=score.notes.associateBy{it.id}
            expected=JSONArray(score.notes.filter{!it.rest}.map{n->JSONObject().put("id",n.id).put("midi",n.midi).put("beat",n.beat).put("staff",n.staff)}).toString()
            lastMatched=null;lastMissed=null;lastTake=null;expectedCode=expected;changed=true
        }
        if(lastMatched!==p.matched){lastMatched=p.matched;matched=JSONArray(p.matched.mapNotNull{notesById[it]}.map{JSONArray(listOf(it.beat,it.midi))}).toString();matchedCode=matched;changed=true}
        if(lastMissed!==p.missed){lastMissed=p.missed;missed=JSONArray(p.missed.mapNotNull{notesById[it]}.map{JSONArray(listOf(it.beat,it.midi))}).toString();missedCode=missed;changed=true}
        if(lastTake!==p.take || lastReview!=p.review){
            lastTake=p.take;lastReview=p.review
            // Assessment/history retain the full take. Bound only the live SVG workload;
            // Review restores all events after transport has stopped.
            val visible=if(p.review)p.take else p.take.takeLast(256)
            take=JSONArray(visible.map{n->JSONObject().put("id",n.id).put("pitch",n.pitch).put("beat",n.beat).put("duration",n.duration ?: JSONObject.NULL).put("expectedId",n.expectedId ?: JSONObject.NULL).put("onsetMs",n.onsetMs ?: JSONObject.NULL)}).toString();takeCode=take;changed=true
        }
        val start=score.measures[p.startMeasure-1].start+p.startOffset
        val end=score.measures[p.endMeasure-1].start+p.endOffset
        val beat=p.cursorPreview ?: if(p.review && !p.running)p.reviewBeat else if(p.listening || p.running)p.transportBeat else p.beat
        val moving=!p.paused && !p.hidden && p.counting==0 && !p.feedback.startsWith("Preview ·") && !p.feedback.startsWith("Rest ·") && p.cursorPreview==null && (p.running || p.listening)
        val rate=when {
            !moving->0.0
            p.listening->score.tempoAt(beat)*p.bpm/score.tempo/60
            p.mode!=org.pianopath.core.PracticeMode.WAIT->p.bpm/60
            else->0.0
        }
        val follow=p.cursorPreview==null && !p.paused && (p.running || p.listening || p.review)
        val data=if(changed)"setPerformanceData($matchedCode,$missedCode,$takeCode,$expectedCode);" else ""
        // Cancel autonomous motion before any overlay work on stop/pause/countdown.
        val halt=if(!moving)"stopCursorMotion();" else ""
        val script=halt+data+"progress($beat,performanceData.matched,performanceData.missed,$start,$end,${p.zoom},${p.horizontal},performanceData.take,performanceData.expected,${p.visualTolerance},${p.sheetMargin},${packet.dark},${p.bpm},'${p.hand.name}',true,${p.selectingLoop},${!p.running && !p.listening},${score.endBeat},$follow,'${packet.accent}',${packet.animate},$rate)"
        busy=true
        view.evaluateJavascript(script){
            if(!closed){busy=false;drain()}
        }
    }
}
