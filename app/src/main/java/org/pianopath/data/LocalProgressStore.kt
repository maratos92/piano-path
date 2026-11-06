package org.pianopath.data

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import org.pianopath.core.PracticeEntry
import java.io.File

/** Local-only activity summaries. No schema change, network, original notation or raw audio. */
class LocalProgressStore(context: Context,scope: CoroutineScope,private val onError: (String)->Unit) {
    private val directory=File(context.filesDir,"progress").apply{mkdirs()}
    private val prefs=context.getSharedPreferences("practice_goals",0)
    private val mutex=Mutex()
    private val mutable=MutableStateFlow<List<PracticeEntry>>(emptyList())
    val entries: StateFlow<List<PracticeEntry>> = mutable.asStateFlow()
    private val goal=MutableStateFlow(prefs.getInt("minutes",10).coerceIn(5,30))
    val goalMinutes: StateFlow<Int> = goal.asStateFlow()
    private val loaded=CompletableDeferred<Unit>()
    init {scope.launch(start=CoroutineStart.UNDISPATCHED) {try{reload()}finally{loaded.complete(Unit)}}}
    fun setGoal(minutes: Int){goal.value=minutes.coerceIn(5,30);prefs.edit().putInt("minutes",goal.value).apply()}
    suspend fun add(entry: PracticeEntry) {
        loaded.await()
        withContext(NonCancellable+Dispatchers.IO) {mutex.withLock {
            try {
                validate(entry)
                val file=AtomicFile(File(directory,"${entry.id}.json"));val out=file.startWrite()
                try {out.write(encode(entry).toString().toByteArray());file.finishWrite(out)}catch(e: Exception){file.failWrite(out);throw e}
                mutable.value=(mutable.value.filterNot{it.id==entry.id}+entry).sortedByDescending{it.date}
            }catch(e: Exception){onError("Could not save local progress: ${e.message}")}
        }}
    }
    suspend fun reload() = withContext(Dispatchers.IO) {mutex.withLock {
        var unreadable=0
        val ids=directory.listFiles().orEmpty().map{it.name.removeSuffix(".bak")}.filter{it.matches(Regex("[a-f0-9-]{36}\\.json"))}.distinct()
        mutable.value=ids.mapNotNull {name->try {
            val file=AtomicFile(File(directory,name))
            file.openRead().use{stream->require(stream.channel.size()<128*1024);stream.bufferedReader().use{decode(JSONObject(it.readText())).also{e->require(name=="${e.id}.json");validate(e)}}}
        }catch(_: Exception){unreadable++;null}}.sortedByDescending{it.date}
        if(unreadable>0)onError("$unreadable progress record(s) could not be read; files were kept.")
    }}
    private fun validate(e: PracticeEntry) {
        require(e.id.matches(Regex("[a-f0-9-]{36}")) && e.title.length<=1000 && e.date>=0)
        require(e.activeMs in 0..86_400_000L && e.bpm.isFinite() && e.bpm>0 && e.bpm<=1000)
        require(e.startBeat.isFinite() && e.endBeat.isFinite() && e.startBeat>=0 && e.endBeat>=e.startBeat)
        require(e.tolerance.isFinite() && e.tolerance in 0.0..2000.0 && e.latency.isFinite() && kotlin.math.abs(e.latency)<=10000)
        require(listOf(e.pitch,e.rhythm,e.overall).all{it==null || it.isFinite() && it in 0.0..100.0})
        require(e.onTimeCorrect==null || e.onTimeCorrect in 0..e.expected)
        require(listOf(e.expected,e.missing,e.extra,e.correct,e.total).all{it>=0})
    }
    private fun encode(e: PracticeEntry)=JSONObject().put("version",1).put("id",e.id).put("date",e.date).put("title",e.title).put("category",e.category)
        .put("scoreKey",e.scoreKey).put("start",e.startBeat).put("end",e.endBeat).put("bpm",e.bpm).put("mode",e.mode).put("hand",e.hand)
        .put("tolerance",e.tolerance).put("latency",e.latency).put("source",e.source).put("activeMs",e.activeMs)
        .put("pitch",e.pitch ?: JSONObject.NULL).put("rhythm",e.rhythm ?: JSONObject.NULL).put("overall",e.overall ?: JSONObject.NULL)
        .put("expected",e.expected).put("missing",e.missing).put("extra",e.extra).put("weak",e.weakMeasure ?: JSONObject.NULL)
        .put("review",e.reviewFile ?: JSONObject.NULL).put("scoring",e.scoringVersion).put("correct",e.correct).put("total",e.total)
        .put("round",e.roundId).put("exercise",e.exercise).put("onTimeCorrect",e.onTimeCorrect ?: JSONObject.NULL)
    private fun decode(o: JSONObject): PracticeEntry {
        require(o.getInt("version")==1)
        fun optional(k: String)=if(o.isNull(k))null else o.getDouble(k)
        val id=o.getString("id");require(id.matches(Regex("[a-f0-9-]{36}")))
        return PracticeEntry(id,o.getLong("date"),o.getString("title"),o.getString("category"),o.optString("scoreKey"),o.optDouble("start",0.0),o.optDouble("end",0.0),o.getDouble("bpm"),o.optString("mode"),o.optString("hand","BOTH"),o.getDouble("tolerance"),o.getDouble("latency"),o.getString("source"),o.getLong("activeMs").coerceAtLeast(0),optional("pitch"),optional("rhythm"),optional("overall"),o.getInt("expected"),o.getInt("missing"),o.getInt("extra"),if(o.isNull("weak"))null else o.getInt("weak"),if(o.isNull("review"))null else o.getString("review"),o.getInt("scoring"),o.getInt("correct"),o.getInt("total"),o.optString("round"),o.optString("exercise"),if(o.isNull("onTimeCorrect"))null else o.getInt("onTimeCorrect"))
    }
}
