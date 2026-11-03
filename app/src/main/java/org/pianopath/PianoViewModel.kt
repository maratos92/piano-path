package org.pianopath

import android.app.Application
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.pianopath.core.*
import org.pianopath.data.*
import org.pianopath.midi.BleMidi
import org.pianopath.audio.PianoAudio
import org.pianopath.audio.PianoSound
import java.security.MessageDigest
import kotlin.math.*

enum class ScoreView { SHEET, FALLING, COMBINED }
enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class PracticeUi(val score: Score? = null, val xml: String = "", val key: String = "", val category: String = "Score",
    val mode: PracticeMode = PracticeMode.RHYTHM, val hand: Hand = Hand.BOTH, val bpm: Double = 80.0,
    val startMeasure: Int = 1, val endMeasure: Int = 1, val startOffset: Double = 0.0, val endOffset: Double = 0.0,
    val loop: Boolean = true, val countIn: Boolean = true, val metronome: Boolean = true, val autoTempo: Boolean = false,
    val running: Boolean = false, val counting: Int = 0, val beat: Double = 0.0, val feedback: String = "Choose a score to begin",
    val matched: Set<Int> = emptySet(), val missed: Set<Int> = emptySet(), val expected: List<Int> = emptyList(),
    val loops: List<SessionResult> = emptyList(), val result: SessionResult? = null, val sections: List<SectionEntity> = emptyList(),
    val guidance: String = "", val hidden: Boolean = false, val previewSeconds: Int = 0, val zoom: Double = 1.0,
    val horizontal: Boolean = false, val simulated: Boolean = false, val sheetMargin: Int = 4,
    val take: List<PlayedNote> = emptyList(), val review: Boolean = false, val reviewBeat: Double = 0.0, val visualTolerance: Double = 180.0,
    val view: ScoreView = ScoreView.FALLING, val listening: Boolean = false,
    val transportBeat: Double = 0.0, val playbackHeld: Set<Int> = emptySet(),
    val paused: Boolean = false)
data class EarUi(val octaves: Int = 1, val voices: Int = 1, val target: Set<Int> = emptySet(),
    val guesses: Set<Int> = emptySet(), val revealed: Boolean = false, val correct: Int = 0,
    val attempts: Int = 0, val feedback: String = "Choose a range and hear a question.")

data class AppSettings(val tolerance: Double = 180.0, val latency: Double = 0.0, val theme: ThemeMode = ThemeMode.SYSTEM, val simulator: Boolean = false, val sound: PianoSound = PianoSound.GRAND)

class PianoViewModel(application: Application): AndroidViewModel(application) {
    val midi = BleMidi(application)
    private val database = PianoDatabase.create(application)
    private val dao = database.dao()
    val library = loadLibrary(application)
    val history = dao.history().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val imports = dao.imports().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    private val preferences = application.getSharedPreferences("settings",0)
    val settings = MutableStateFlow(AppSettings(preferences.getFloat("tolerance",180f).toDouble(),preferences.getFloat("latency",0f).toDouble(),runCatching { ThemeMode.valueOf(preferences.getString("theme",if(preferences.getBoolean("dark",false))"DARK" else "SYSTEM")!!) }.getOrDefault(ThemeMode.SYSTEM),preferences.getBoolean("simulator",false),runCatching {PianoSound.valueOf(preferences.getString("sound","GRAND")!!)}.getOrDefault(PianoSound.GRAND)))
    val ui = MutableStateFlow(PracticeUi())
    val message = MutableStateFlow<String?>(null)
    val ear = MutableStateFlow(EarUi())
    private var earPlayback: Job?=null
    val screen = MutableStateFlow("Home")
    val calibrating = MutableStateFlow(false)
    private val audio=PianoAudio(application) { reason ->
        stopListening();if(ui.value.running)stop(reason);midi.clearKeys();message.value=reason
    }
    private var playback: Job?=null
    private var playbackGeneration=0
    private var playbackSeek: Double? = null
    private var pausedAt = 0L
    private var engine: PerformanceEngine? = null
    private var ticker: Job? = null
    private var simulator: Job? = null
    private var calibration: Job? = null
    private var calibrationClicks = mutableListOf<Long>()
    private val calibrationSamples = mutableListOf<Double>()
    private var tone: ToneGenerator? = null
    private var startTime = 0L
    private var lastClick = Long.MIN_VALUE
    private var finishing = false
    private var simulationUsed = false
    init {
        audio.selectSound(settings.value.sound)
        midi.onEvent = { event ->
            if(screen.value=="Ear training" && event is MidiEvent.On) earGuess(event.pitch)
            if(calibrating.value && event is MidiEvent.On && calibrationClicks.size >= 5) {
                val nearest = calibrationClicks.minByOrNull { abs(event.timestamp-it) }
                nearest?.let { val ms = (event.timestamp-it)/1e6; if(abs(ms) < 350) calibrationSamples.add(ms) }
            }
            if(ui.value.running && !ui.value.paused && event.timestamp >= startTime) {
                engine?.accept(event)
                updateProgress()
            }
        }
        midi.onDisconnect = {
            if(ui.value.running) stop("Piano disconnected. Session stopped; reconnect before restarting.")
        }
    }
    /** Ear training uses outgoing audio only; questions never feed generated MIDI into the scorer. */
    fun earQuestion(octaves: Int,voices: Int) {
        val count=voices.coerceIn(1,3)
        val span=octaves.coerceIn(1,3)
        val pitches=(60..(60+span*12)).shuffled().take(count).toSet()
        val previous=ear.value
        ear.value=previous.copy(octaves=span,voices=count,target=pitches,guesses=emptySet(),revealed=false,feedback="Listen, then select the notes you heard.")
        earReplay()
    }
    fun earReplay() {
        val pitches=ear.value.target
        if(pitches.isEmpty())return
        if(!audio.start())return
        earPlayback?.cancel();audio.releasePlayback()
        earPlayback=viewModelScope.launch {
            try {
                pitches.sorted().forEach { audio.accept(MidiEvent.On(it,80,System.nanoTime(),14)) }
                delay(900)
            } finally { pitches.forEach { audio.accept(MidiEvent.Off(it,0,System.nanoTime(),14)) } }
        }
    }
    fun earGuess(pitch: Int) {
        val p=ear.value
        if(p.target.isEmpty() || p.revealed || pitch !in 60..(60+p.octaves*12))return
        ear.value=p.copy(guesses=if(pitch in p.guesses)p.guesses-pitch else p.guesses+pitch)
    }
    fun earClear() {ear.value=ear.value.copy(guesses=emptySet())}
    fun earCheck() {
        val p=ear.value
        if(p.target.isEmpty() || p.revealed || p.guesses.isEmpty())return
        val right=p.guesses==p.target
        ear.value=p.copy(revealed=true,attempts=p.attempts+1,correct=p.correct+if(right)1 else 0,
            feedback=if(right)"Correct! You identified every pitch." else "Not quite. Compare your answer with the revealed notes.")
    }
    fun setSettings(change: (AppSettings)->AppSettings) {
        val previous=settings.value
        settings.value = change(previous)
        val s = settings.value
        if(previous.simulator && !s.simulator){audio.releaseTouch();midi.clearKeys()}
        if(previous.sound!=s.sound)audio.selectSound(s.sound)
        preferences.edit().putFloat("tolerance",s.tolerance.toFloat()).putFloat("latency",s.latency.toFloat()).putString("theme",s.theme.name).putBoolean("simulator",s.simulator).putString("sound",s.sound.name).apply()
    }
    fun zoom(delta: Double) { ui.value=ui.value.copy(zoom=(ui.value.zoom+delta).coerceIn(.4,2.5)) }
    fun edit(change: (PracticeUi)->PracticeUi) { if(!ui.value.running && !ui.value.listening) ui.value = change(ui.value) }
    fun review() { ui.value=ui.value.copy(review=true,view=ScoreView.SHEET,reviewBeat=ui.value.beat) }
    fun seekReview(beat: Double) { ui.value=ui.value.copy(reviewBeat=beat) }
    fun setView(view: ScoreView) { ui.value=ui.value.copy(view=view) }
    /** Supports paused practice (without measuring the pause as lateness) and paused listening. */
    fun togglePause() {
        val p=ui.value
        if(!p.running && !p.listening) return
        val now=System.nanoTime()
        if(p.paused) {
            if(p.running) { startTime += (now-pausedAt).coerceAtLeast(0L); engine?.resume(now); lastClick=Long.MIN_VALUE }
            ui.value=p.copy(paused=false,feedback=if(p.listening)"Listening" else "Resumed")
        } else {
            pausedAt=now
            if(p.running) engine?.pause(now) else audio.releasePlayback()
            tone?.stopTone()
            ui.value=p.copy(paused=true,playbackHeld=emptySet(),feedback="Paused")
        }
    }
    /** Whole-score scrubbing; a running scored take cannot be arbitrarily re-timed. */
    fun seek(beat: Double) {
        val p=ui.value; val score=p.score ?: return
        if(!beat.isFinite() || p.running)return
        val target=if(p.listening) {
            val(a,b)=bounds(p);beat.coerceIn(a,b-.001)
        } else beat.coerceIn(0.0,score.endBeat)
        if(p.listening) playbackSeek=target
        ui.value=p.copy(beat=target,transportBeat=target,reviewBeat=target)
    }
    /** Quarter-beat precision selection for the loop handles; same bounds as measure controls. */
    fun setPassage(start: Double,end: Double) {
        edit { p ->
            val score=p.score ?: return@edit p
            val measures=score.measures
            val full=score.endBeat
            val a=(kotlin.math.round(start*4)/4).coerceIn(0.0,(full-.25).coerceAtLeast(0.0))
            val b=(kotlin.math.round(end*4)/4).coerceIn(a+.25,full)
            val from=measures.last { it.start<=a+.0001 }
            val through=measures.last { it.start<b-.0001 }
            p.copy(startMeasure=from.number,endMeasure=through.number,startOffset=a-from.start,endOffset=b-through.start)
        }
    }
    fun enableTouch() {setSettings {it.copy(simulator=true)};audio.start()}
    fun playWithoutPiano() = viewModelScope.launch {
        stopListening();if(ui.value.running)stop()
        enableTouch()
        if(ui.value.score!=null) {
            edit{it.copy(mode=PracticeMode.FREE,view=ScoreView.FALLING)}
            screen.value="Practice";return@launch
        }
        val piece=library.first()
        val xml=withContext(Dispatchers.IO) {getApplication<Application>().assets.open("scores/${piece.asset}").bufferedReader().use{it.readText()}}
        load(xml,piece.key,"Score","Touch the keys to play. Zoom for larger keys; use the range slider to move across all 88 notes.")
        edit{it.copy(mode=PracticeMode.FREE,view=ScoreView.FALLING)}
    }
    fun open(piece: LibraryPiece) = viewModelScope.launch {
        try {
            val xml = withContext(Dispatchers.IO) { getApplication<Application>().assets.open("scores/${piece.asset}").bufferedReader().use { it.readText() } }
            load(xml,piece.key,"Score", "${piece.license} · ${piece.attribution}")
        } catch(e: Exception) { message.value = "Could not open score: ${e.message}" }
    }
    fun openImport(piece: ImportEntity) = viewModelScope.launch {
        try {
            val xml=withContext(Dispatchers.IO) { java.io.File(getApplication<Application>().filesDir,"scores/${piece.fileName}").readText() }
            load(xml,piece.key,"Imported score","User imported edition. Redistribution rights remain with its owner.")
        } catch(e: Exception) { message.value="Could not open imported score: ${e.message}" }
    }
    fun import(uri: Uri) = viewModelScope.launch {
        try {
            val xml = withContext(Dispatchers.IO) {
                val bytes = getApplication<Application>().contentResolver.openInputStream(uri)?.use { stream -> stream.readBytesLimited(MusicXml.MAX_BYTES) } ?: error("Cannot read file")
                MusicXml.unpack(bytes)
            }
            val score = withContext(Dispatchers.Default) { MusicXml.parse(xml) }
            val key = MessageDigest.getInstance("SHA-256").digest(xml.toByteArray()).joinToString("") { "%02x".format(it) }
            withContext(Dispatchers.IO) {
                val directory=java.io.File(getApplication<Application>().filesDir,"scores");directory.mkdirs()
                val temporary=java.io.File(directory,"$key.tmp");temporary.writeText(xml)
                val destination=java.io.File(directory,"$key.musicxml")
                if(!temporary.renameTo(destination)) { destination.writeText(xml);temporary.delete() }
            }
            dao.saveImport(ImportEntity(key,score.title,score.composer,"$key.musicxml"))
            load(xml,key,"Imported score","Imported privately on this device.")
        } catch(e: Exception) { message.value = "Import failed: ${e.message}" }
    }
    private fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray {
        val out=java.io.ByteArrayOutputStream(); val buffer=ByteArray(8192)
        var n=read(buffer); while(n>0) { require(out.size()+n<=limit) { "Score exceeds 12 MB" }; out.write(buffer,0,n); n=read(buffer) }; return out.toByteArray()
    }
    private suspend fun load(xml: String,key: String,category: String,guidance: String,preview: Int = 0) {
        stopListening()
        if(ui.value.running) stop()
        val score = withContext(Dispatchers.Default) { MusicXml.parse(xml) }
        ui.value = PracticeUi(score=score,xml=xml,key=key,category=category,bpm=score.tempo,endMeasure=score.measures.size,
            endOffset=score.measures.last().length,sections=dao.sections(key),guidance=guidance,previewSeconds=preview,
            hidden=category=="Sight reading",feedback="Ready · ${score.notes.count { !it.rest }} notes",expected=score.notes.filter { !it.rest && it.beat==0.0 }.map { it.midi })
        screen.value="Practice"
    }
    fun scale(root: Int,kind: ScaleKind,octaves: Int,hand: Hand,descending: Boolean) = viewModelScope.launch {
        val ex = Exercises.scale(root,kind,octaves,hand,descending)
        load(ex.xml,"scale-$root-$kind-$octaves-$hand-$descending","Scales",ex.guidance)
        edit { it.copy(hand=hand,mode=PracticeMode.RHYTHM) }
    }
    fun sight(level: Int,fifths: Int,beats: Int,hand: Hand,preview: Int) = viewModelScope.launch {
        val ex = Exercises.sight(level,fifths,beats,hand)
        load(ex.xml,"sight-${System.currentTimeMillis()}","Sight reading",ex.guidance,preview)
        edit { it.copy(hand=hand,mode=PracticeMode.RHYTHM,loop=false) }
    }
    private fun bounds(p: PracticeUi): Pair<Double,Double> {
        val score = requireNotNull(p.score)
        return score.measures[p.startMeasure-1].start+p.startOffset to score.measures[p.endMeasure-1].start+p.endOffset
    }
    fun saveSection(name: String) = viewModelScope.launch {
        val p=ui.value; if(p.score==null) return@launch
        val (a,b)=bounds(p); dao.saveSection(SectionEntity(scoreKey=p.key,name=name.ifBlank { "Measures ${p.startMeasure}–${p.endMeasure}" },startBeat=a,endBeat=b,bpm=p.bpm))
        ui.value=ui.value.copy(sections=dao.sections(p.key)); message.value="Section saved on this device"
    }
    fun useSection(s: SectionEntity) { edit { p ->
        val ms=p.score?.measures ?: return@edit p
        val a=ms.last { it.start<=s.startBeat+0.0001 }; val b=ms.last { it.start<s.endBeat-0.0001 }
        p.copy(startMeasure=a.number,endMeasure=b.number,startOffset=s.startBeat-a.start,endOffset=s.endBeat-b.start,bpm=s.bpm)
    } }
    fun chooseMeasure(number: Int) { edit { p ->
        val m=p.score?.measures?.getOrNull(number-1) ?: return@edit p
        p.copy(startMeasure=number,endMeasure=number,startOffset=0.0,endOffset=m.length)
    } }
    fun start() {
        if(ui.value.running) return
        stopListening()
        val p=ui.value
        if(p.score==null) return
        val (a,b)=bounds(p)
        if(a>=b) { message.value="End of passage must follow its start"; return }
        if(p.mode != PracticeMode.FREE && p.score.selected(a,b,p.hand).isEmpty()) { message.value="No notes for this hand in the selected section"; return }
        tone = tone ?: try { ToneGenerator(AudioManager.STREAM_MUSIC,65) } catch(_: Exception) { null }
        simulationUsed=false
        ui.value=p.copy(running=true,paused=false,result=null,visualTolerance=settings.value.tolerance,review=false,take=emptyList(),loops=emptyList(),hidden=p.category=="Sight reading" && p.previewSeconds==0,simulated=false)
        ticker=viewModelScope.launch {
            if(p.category=="Sight reading" && p.previewSeconds>0) {
                repeat(p.previewSeconds) { remaining -> ui.value=ui.value.copy(feedback="Preview · ${p.previewSeconds-remaining} seconds"); delay(1000) }
            }
            beginLoop()
            while(isActive && ui.value.running) {
                if(ui.value.paused) { delay(30); continue }
                val now=System.nanoTime()
                val current=ui.value
                if(now<startTime) {
                    val remaining=ceil((startTime-now)/1e9*current.bpm/60).toInt()
                    ui.value=current.copy(counting=remaining,feedback="Count in · $remaining",transportBeat=engine?.clockBeat(now) ?: current.beat)
                } else {
                    if(current.counting>0 || current.hidden && current.previewSeconds>=0) ui.value=current.copy(counting=0,hidden=false)
                    engine?.tick(now); updateProgress()
                    val e=engine
                    if(e!=null && current.mode!=PracticeMode.FREE && !finishing) {
                        val (_,end)=bounds(current)
                        val done = if(current.mode==PracticeMode.WAIT) e.finished && midi.state.value.held.isEmpty()
                            else e.hasAnchor && (current.mode!=PracticeMode.FOLLOW || e.finished) && e.clockBeat(now)>=end+current.bpm/60.0*0.5
                        if(done) completeLoop(now)
                    }
                }
                if(ui.value.metronome && ui.value.mode!=PracticeMode.WAIT) {
                    val elapsedBeat=floor((now-startTime)/1e9*ui.value.bpm/60).toLong()
                    if(elapsedBeat != lastClick && (now<startTime || ui.value.mode==PracticeMode.RHYTHM || engine?.hasAnchor==true)) {
                        lastClick=elapsedBeat
                        val measureBeats=(ui.value.score?.beats ?: 4)*4.0/(ui.value.score?.beatType ?: 4)
                        audio.metronomeClick(Math.floorMod(elapsedBeat,measureBeats.toLong().coerceAtLeast(1L))==0L)
                    }
                }
                delay(12)
            }
        }
    }
    private fun beginLoop() {
        val p=ui.value; val score=p.score ?: return
        val(a,b)=bounds(p)
        midi.clearKeys(); audio.silence(); finishing=false; lastClick=Long.MIN_VALUE
        startTime=System.nanoTime()+if(p.countIn) beatNanos(score.beats*4.0/score.beatType,p.bpm) else 0L
        engine=PerformanceEngine(score,PracticeConfig(p.mode,p.hand,p.bpm,settings.value.tolerance,latencyMs=settings.value.latency,startBeat=a,endBeat=b)).also { it.start(startTime) }
        ui.value=p.copy(paused=false,take=emptyList(),review=false,matched=emptySet(),missed=emptySet(),beat=a,transportBeat=a,expected=score.selected(a,b,p.hand).filter { it.beat==a }.map { it.midi },feedback="Ready",counting=if(p.countIn) score.beats else 0,hidden=p.category=="Sight reading" && p.previewSeconds==0)
    }
    private fun updateProgress() {
        val e=engine ?: return; val p=ui.value
        if(p.mode==PracticeMode.FREE) { ui.value=p.copy(expected=emptyList(),feedback=midi.state.value.held.keys.sorted().joinToString { noteName(it) }.ifEmpty { "Free play · play any notes" }); return }
        ui.value=p.copy(take=e.take,matched=e.matchedIds,missed=e.missedIds,beat=e.currentBeat,transportBeat=(if(p.mode==PracticeMode.WAIT || !e.hasAnchor)e.currentBeat else e.clockBeat(System.nanoTime())).coerceAtLeast(p.transportBeat),feedback=e.lastFeedback,
            expected=e.results.filter { !it.missing && it.onsetMs==null && abs(it.note.beat-e.currentBeat)<0.001 }.map { it.note.midi })
    }
    private suspend fun completeLoop(now: Long) {
        finishing=true; simulator?.cancel()
        val result=engine?.finish(now) ?: return
        persist(result)
        val p=ui.value
        ui.value=p.copy(loops=p.loops+result,result=result,take=result.take)
        if(p.loop) {
            if(p.autoTempo && result.overall>=90) ui.value=ui.value.copy(bpm=(p.bpm+4).coerceAtMost(240.0))
            delay(600); if(ui.value.running) beginLoop()
        } else { ui.value=ui.value.copy(running=false,paused=false,feedback="Session complete"); engine=null; midi.clearKeys();audio.silence() }
    }
    private suspend fun persist(r: SessionResult,p: PracticeUi = ui.value,simulated: Boolean = simulationUsed) {
        if(p.mode==PracticeMode.FREE) return
        val reviewFile=withContext(Dispatchers.IO){runCatching{TakeArchive.save(getApplication(),p,r)}.getOrNull()}
        dao.saveSession(SessionEntity(title=p.score?.title ?: "Practice",date=System.currentTimeMillis(),pitch=r.pitch,rhythm=r.rhythm,overall=r.overall,
            correct=r.correct,expected=r.expected,extra=r.extra,missing=r.missing,onsetMs=r.meanOnsetMs,durationMs=r.meanDurationMs,mode="${p.mode} · ${p.hand}",bpm=p.bpm,category=p.category,simulated=simulated,reviewFile=reviewFile))
    }
    fun openReview(session: SessionEntity) = viewModelScope.launch {
        stopListening();if(ui.value.running)stop()
        try {ui.value=withContext(Dispatchers.IO){TakeArchive.load(getApplication(),session)};screen.value="Practice"}
        catch(e: Exception){message.value="Could not open visual review: ${e.message}"}
    }
    fun stop(reason: String = "Practice finished") {
        if(!ui.value.running) return
        ticker?.cancel(); simulator?.cancel()
        val r=if(!finishing && ui.value.mode!=PracticeMode.FREE) engine?.finish(System.nanoTime()) else null
        val snapshot=ui.value;val simulated=simulationUsed
        if(r!=null && ui.value.mode!=PracticeMode.FREE) viewModelScope.launch { persist(r,snapshot,simulated) }
        ui.value=ui.value.copy(running=false,paused=false,counting=0,hidden=false,take=r?.take ?: ui.value.take,result=r ?: ui.value.result,feedback=reason)
        engine=null; midi.clearKeys();audio.silence()
    }
    fun simulateNext() {
        if(!settings.value.simulator || !audio.start()) return
        simulationUsed=true; ui.value=ui.value.copy(simulated=true)
        val p=ui.value
        val pitches=p.expected.ifEmpty { listOf(60,64,67) }
        viewModelScope.launch {
            for((i,pitch) in pitches.withIndex()) {
                touchEvent(MidiEvent.On(pitch,76,System.nanoTime())); if(i<pitches.lastIndex) delay(25)
            }
            delay(350)
            pitches.forEach { touchEvent(MidiEvent.Off(it,0,System.nanoTime())) }
        }
    }
    fun simulatePerformance() {
        if(!settings.value.simulator || !ui.value.running || !audio.start()) return
        simulationUsed=true; ui.value=ui.value.copy(simulated=true)
        simulator?.cancel()
        simulator=viewModelScope.launch {
            val p=ui.value; val score=p.score ?: return@launch; val(a,b)=bounds(p)
            val origin=startTime.coerceAtLeast(System.nanoTime())
            val events=score.selected(a,b,p.hand).flatMap { n -> listOf(
                (origin+beatNanos(n.beat-a,p.bpm)) to MidiEvent.On(n.midi,80,origin+beatNanos(n.beat-a,p.bpm)),
                (origin+beatNanos(min(n.beat+n.duration,b)-a,p.bpm)) to MidiEvent.Off(n.midi,0,origin+beatNanos(min(n.beat+n.duration,b)-a,p.bpm))) }.sortedWith(compareBy({it.first},{if(it.second is MidiEvent.Off) 0 else 1}))
            for((time,event) in events) {
                while(System.nanoTime()<time) delay(((time-System.nanoTime())/1_000_000).coerceIn(1,20))
                touchEvent(event)
            }
        }
    }
    fun key(pitch: Int, down: Boolean) {
        if(!(settings.value.simulator || ui.value.listening) || down && !audio.start()) return
        if(!ui.value.listening) { simulationUsed=true; ui.value=ui.value.copy(simulated=true) }
        touchEvent(if(down) MidiEvent.On(pitch,80,System.nanoTime()) else MidiEvent.Off(pitch,0,System.nanoTime()))
    }
    private fun touchEvent(event: MidiEvent) {audio.accept(event);midi.simulate(event)}
    fun listen() {
        if(ui.value.listening){stopListening();return}
        if(ui.value.running)stop("Stopped for listening")
        val p=ui.value;val score=p.score ?: return
        val(a,b)=bounds(p)
        if(a>=b){message.value="End of passage must follow its start";return}
        if(!audio.start())return
        // A stopped score may be scrubbed before pressing Listen. Honor that playhead.
        val fromBeat=p.transportBeat.coerceIn(a,b-.001)
        val plan=PlaybackPlan.create(score,a,b,p.hand)
        val tempoScale=p.bpm/score.tempo
        if(plan.isEmpty()){message.value="No notes for this hand in the passage";return}
        playbackSeek=null
        lastClick=Long.MIN_VALUE
        if(p.metronome) tone=tone ?: try {ToneGenerator(AudioManager.STREAM_MUSIC,65)} catch(_: Exception){null}
        ui.value=p.copy(listening=true,paused=false,hidden=false,review=false,result=null,matched=emptySet(),missed=emptySet(),beat=fromBeat,transportBeat=fromBeat,feedback="Listening",playbackHeld=emptySet())
        val generation=++playbackGeneration
        playback=viewModelScope.launch {
            try {
                do {
                    var origin=System.nanoTime()-(score.secondsBetween(a,fromBeat,tempoScale)*1e9).toLong()
                    var next=plan.indexOfFirst { it.beat>=fromBeat-.0001 }.let { if(it<0)plan.size else it }
                    var suspendedAt: Long?=null
                    val held=mutableMapOf<Int,Int>()
                    while(isActive) {
                        if(ui.value.paused) {
                            if(suspendedAt==null)suspendedAt=System.nanoTime()
                            delay(30);continue
                        }
                        suspendedAt?.let {origin+=(System.nanoTime()-it);suspendedAt=null}
                        val requested=playbackSeek
                        if(requested!=null) {
                            playbackSeek=null
                            audio.releasePlayback();held.clear()
                            next=plan.indexOfFirst { it.beat>=requested-.0001 }.let { if(it<0)plan.size else it }
                            origin=System.nanoTime()-(score.secondsBetween(a,requested,tempoScale)*1e9).toLong()
                        }
                        val now=System.nanoTime()
                        val beat=score.beatAtSeconds(a,(now-origin)/1e9,b,tempoScale)
                        while(next<plan.size && plan[next].beat<=beat) {
                            val event=plan[next++]
                            audio.accept(if(event.down)MidiEvent.On(event.pitch,event.velocity,now,event.channel) else MidiEvent.Off(event.pitch,0,now,event.channel))
                            if(event.down)held[event.pitch]=(held[event.pitch] ?: 0)+1
                            else if((held[event.pitch] ?: 0)<=1)held.remove(event.pitch) else held[event.pitch]=held.getValue(event.pitch)-1
                        }
                        ui.value=ui.value.copy(beat=beat.coerceAtMost(b),transportBeat=beat.coerceAtMost(b),playbackHeld=held.keys.toSet())
                        if(p.metronome && beat<b) {
                            val click=floor(beat).toLong()
                            if(click!=lastClick && click>=floor(a).toLong()) {
                                lastClick=click
                                val downbeat=score.measures.any {abs(it.start-click.toDouble())<.0001}
                                audio.metronomeClick(downbeat)
                            }
                        }
                        if(beat>=b)break
                        delay(12)
                    }
                    audio.releasePlayback()
                } while(isActive && ui.value.loop)
                ui.value=ui.value.copy(listening=false,paused=false,playbackHeld=emptySet(),feedback="Playback complete · no assessment")
            } finally {if(generation==playbackGeneration)audio.releasePlayback()}
        }
    }
    fun stopListening() {
        playbackGeneration++;playback?.cancel();playback=null;playbackSeek=null;audio.releasePlayback();tone?.stopTone()
        if(ui.value.listening)ui.value=ui.value.copy(listening=false,paused=false,playbackHeld=emptySet(),feedback="Playback stopped")
    }
    fun background() {
        stopListening();if(ui.value.running)stop("Practice stopped while app is in the background")
        simulator?.cancel();earPlayback?.cancel();calibration?.cancel();calibrating.value=false;tone?.stopTone();midi.clearKeys();audio.stop()
    }
    fun pedal(down: Boolean) { if(settings.value.simulator) touchEvent(MidiEvent.Control(64,if(down) 127 else 0,System.nanoTime())) }
    fun calibrate() {
        if(calibrating.value) return
        stopListening()
        if(ui.value.running)stop("Stopped for latency calibration")
        calibrating.value=true; calibrationSamples.clear(); calibrationClicks.clear()
        tone=tone ?: try { ToneGenerator(AudioManager.STREAM_MUSIC,65) } catch(_: Exception) { null }
        calibration=viewModelScope.launch {
            repeat(12) { calibrationClicks.add(System.nanoTime()); tone?.startTone(ToneGenerator.TONE_PROP_BEEP,50); delay(750) }
            delay(400)
            if(calibrationSamples.size>=4) {
                val median=calibrationSamples.sorted()[calibrationSamples.size/2].coerceIn(-300.0,300.0)
                setSettings { it.copy(latency=median) }; message.value="Calibration saved: ${median.toInt()} ms. This includes your tapping offset."
            } else message.value="Not enough MIDI taps to calibrate. Connect the piano and tap with each click."
            calibrating.value=false
        }
    }
    override fun onCleared() { playback?.cancel();earPlayback?.cancel();audio.stop();midi.close(); tone?.release(); database.close(); super.onCleared() }
}
