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
import org.pianopath.recording.FreePlayController
import java.util.UUID
import java.security.MessageDigest
import kotlin.math.*

enum class ScoreView { SHEET, FALLING, COMBINED }
enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class SheetDecoration { NONE, RAIN, LEAVES, CLOUDS, ORBIT }

data class PracticeUi(val score: Score? = null, val xml: String = "", val key: String = "", val category: String = "Score",
    val mode: PracticeMode = PracticeMode.RHYTHM, val hand: Hand = Hand.BOTH, val bpm: Double = 80.0,
    val startMeasure: Int = 1, val endMeasure: Int = 1, val startOffset: Double = 0.0, val endOffset: Double = 0.0,
    val loop: Boolean = false, val countIn: Boolean = true, val metronome: Boolean = true, val autoTempo: Boolean = false,
    val running: Boolean = false, val counting: Int = 0, val beat: Double = 0.0, val feedback: String = "Choose a score to begin",
    val matched: Set<Int> = emptySet(), val missed: Set<Int> = emptySet(), val expected: List<Int> = emptyList(),
    val loops: List<SessionResult> = emptyList(), val result: SessionResult? = null, val sections: List<SectionEntity> = emptyList(),
    val guidance: String = "", val hidden: Boolean = false, val previewSeconds: Int = 0, val zoom: Double = 1.0,
    val horizontal: Boolean = false, val simulated: Boolean = false, val sheetMargin: Int = 4,
    val take: List<PlayedNote> = emptyList(), val review: Boolean = false, val reviewBeat: Double = 0.0, val visualTolerance: Double = 180.0,
    val view: ScoreView = ScoreView.SHEET, val listening: Boolean = false,
    val transportBeat: Double = 0.0, val playbackHeld: Set<Int> = emptySet(),
    val cursorPreview: Double? = null, val fallingScale: Double = 1.0, val focusRemainingMs: Long = 0, val paused: Boolean = false, val performedRoll: Boolean = false,
    val selectingLoop: Boolean = false)
data class EarUi(val startOctave: Int = 4, val octaves: Int = 1, val voices: Int = 1, val target: Set<Int> = emptySet(),
    val fullKeyboard: Boolean = false, val triads: Boolean = true, val guesses: Set<Int> = emptySet(), val revealed: Boolean = false, val correct: Int = 0,
    val attempts: Int = 0, val streak: Int = 0, val bestStreak: Int = 0,val questionAttempts: Int = 0,
    val history: List<Set<Int>> = emptyList(),
    val feedback: String = "Choose a range and hear a question.")

data class NoteCapture(val pitch: Int, val channel: Int, val startedNs: Long, val durationMs: Long? = null)

data class AppSettings(val tolerance: Double = 180.0, val latency: Double = 0.0, val theme: ThemeMode = ThemeMode.SYSTEM, val simulator: Boolean = false, val sound: PianoSound = PianoSound.GRAND,
    val playedNoteCount: Int = 5, val noteGapMs: Int = 2000, val sheetMargin: Int = 4,
    val loopBreakSeconds: Int = 2, val accent: Int = 0xff1b554b.toInt(), val fallingGlow: Boolean = false, val keyboardFirst: Int = 21, val keyboardLast: Int = 108,
    val sheetDecoration: SheetDecoration = SheetDecoration.NONE, val reduceMotion: Boolean = false,
    val keepAwake: Boolean = true,val metronomeVolume: Float = .5f,val noteNaming: String = "LETTERS",val earAttempts: Int = 3)

class PianoViewModel(application: Application): AndroidViewModel(application) {
    val midi = BleMidi(application)
    private val database = PianoDatabase.create(application)
    private val dao = database.dao()
    val library = loadLibrary(application)
    val history = dao.history().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val recentKeys = MutableStateFlow(preferencesRecent(application))
    val imports = dao.imports().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    private val preferences = application.getSharedPreferences("settings",0)
    private val mutableFavourites=MutableStateFlow(preferences.getStringSet("favourites",emptySet()).orEmpty().toSet())
    val favourites: StateFlow<Set<String>> = mutableFavourites.asStateFlow()
    fun toggleFavourite(key: String) {
        if(key.isBlank())return
        mutableFavourites.value=if(key in mutableFavourites.value)mutableFavourites.value-key else mutableFavourites.value+key
        preferences.edit().putStringSet("favourites",mutableFavourites.value.toSet()).apply()
    }
    private var scoreSelection=0L
    private val storedKeyboard=storedKeyboard(preferences)
    val settings = MutableStateFlow(AppSettings(preferences.getFloat("tolerance",180f).toDouble(),preferences.getFloat("latency",0f).toDouble(),runCatching { ThemeMode.valueOf(preferences.getString("theme",if(preferences.getBoolean("dark",false))"DARK" else "SYSTEM")!!) }.getOrDefault(ThemeMode.SYSTEM),preferences.getBoolean("simulator",false),runCatching {PianoSound.valueOf(preferences.getString("sound","GRAND")!!)}.getOrDefault(PianoSound.GRAND),
        preferences.getInt("playedNoteCount",5).coerceIn(1,32),preferences.getInt("noteGapMs",2000).coerceIn(500,10000),preferences.getInt("sheetMargin",4).coerceIn(0,32),preferences.getInt("loopBreakSeconds",2).coerceIn(0,5),preferences.getInt("accent",0xff1b554b.toInt()),preferences.getBoolean("fallingGlow",false),storedKeyboard.first,storedKeyboard.last,runCatching{SheetDecoration.valueOf(preferences.getString("sheetDecoration","NONE")!!)}.getOrDefault(SheetDecoration.NONE),preferences.getBoolean("reduceMotion",false),
        preferences.getBoolean("keepAwake",true),preferences.getFloat("metronomeVolume",.5f).takeIf{it.isFinite()}?.coerceIn(0f,1f) ?: .5f,
        preferences.getString("noteNaming","LETTERS").takeIf{it in listOf("LETTERS","SOLFEGE")} ?: "LETTERS",preferences.getInt("earAttemptLimit",3).coerceIn(1,10)))
    val ui = MutableStateFlow(PracticeUi())
    val message = MutableStateFlow<String?>(null)
    /** Keeps a rolling sequence of physical MIDI and touch key presses, with their key-down durations. */
    val playedSequence = MutableStateFlow<List<NoteCapture>>(emptyList())
    val playedIdle = MutableStateFlow(false)
    private var idleMarkerJob: Job? = null
    private val activeKeyStarts=mutableMapOf<Pair<Int,Int>,java.util.ArrayDeque<Long>>()
    private fun captureKey(event: MidiEvent) {
        when(event) {
            is MidiEvent.On -> {
                if(event.velocity<=0){captureKey(MidiEvent.Off(event.pitch,0,event.timestamp,event.channel));return}
                if(activeKeyStarts.values.sumOf{it.size}>=4096){clearPlayedSequence();message.value="Recent-note monitor reset after excessive held input."}
                idleMarkerJob?.cancel();playedIdle.value=false
                val key=event.channel to event.pitch
                activeKeyStarts.getOrPut(key){java.util.ArrayDeque()}.addLast(event.timestamp)
                playedSequence.value=(playedSequence.value+NoteCapture(event.pitch,event.channel,event.timestamp)).takeLast(128)
            }
            is MidiEvent.Off -> {
                val key=event.channel to event.pitch
                val start=activeKeyStarts[key]?.pollFirst() ?: return
                if(activeKeyStarts[key]?.isEmpty()==true)activeKeyStarts.remove(key)
                val length=((event.timestamp-start)/1_000_000).coerceIn(0L,12000L)
                val old=playedSequence.value.toMutableList()
                val i=old.indexOfFirst {it.channel==event.channel && it.pitch==event.pitch && it.startedNs==start && it.durationMs==null}
                if(i>=0){old[i]=old[i].copy(durationMs=length);playedSequence.value=old}
                if(activeKeyStarts.isEmpty()) {
                    idleMarkerJob?.cancel()
                    idleMarkerJob=viewModelScope.launch {
                        delay(settings.value.noteGapMs.toLong())
                        if(activeKeyStarts.isEmpty())playedIdle.value=true
                    }
                }
            }
            is MidiEvent.Control -> if(event.controller==120 || event.controller==123) {
                activeKeyStarts.keys.filter{it.first==event.channel}.toList().forEach{key->
                    while(activeKeyStarts.containsKey(key))captureKey(MidiEvent.Off(key.second,0,event.timestamp,event.channel))
                }
            }
        }
    }
    fun clearPlayedSequence(){idleMarkerJob?.cancel();playedIdle.value=false;activeKeyStarts.clear();playedSequence.value=emptyList()}
    val ear = MutableStateFlow(EarUi(correct=preferences.getInt("earCorrect",0),
        attempts=preferences.getInt("earAttempts",0),streak=preferences.getInt("earStreak",0),
        bestStreak=preferences.getInt("earBestStreak",0)))
    private var earPlayback: Job?=null
    private val earHeld = mutableSetOf<Int>()
    private val earAdvanceGuesses = mutableSetOf<Int>()
    val earReferencePlaying=MutableStateFlow(false)
    val earPianoOutput=MutableStateFlow(false)
    fun setEarPianoOutput(on: Boolean){earPlayback?.cancel();midi.stopReference();earPianoOutput.value=on && midi.state.value.canSend}
    val screen = MutableStateFlow("Home")
    val calibrating = MutableStateFlow(false)
    private val audio=PianoAudio(application) { reason ->
        handleAudioInterruption(reason)
    }
    private val persistenceJob=SupervisorJob()
    private val persistenceScope=CoroutineScope(persistenceJob+Dispatchers.Main.immediate)
    val progress=LocalProgressStore(application,viewModelScope){message.value=it}
    private val recordingSummaryIds=mutableSetOf<String>()
    val recorder=FreePlayController(application,viewModelScope,audio,onMessage={message.value=it},onSaved=::recordingSummary)
    private fun recordingSummary(take: MidiRecording) {
        if(recordingSummaryIds.add(take.id))persistenceScope.launch {
            progress.add(PracticeEntry(take.id,take.createdAt,take.title,"Free play",activeMs=take.durationUs/1000,source="MIDI / TOUCH"))
        }
    }
    private val activityClock=PracticeClock()
    private val earClock=PracticeClock()
    private var earRound=UUID.randomUUID().toString()
    private var earQuestions=0
    private var loopContext: PracticeUi?=null
    private var loopTolerance=180.0
    private var loopLatency=0.0
    private var foreground=true
    private var playback: Job?=null
    private var playbackGeneration=0
    private var playbackSeek: Double? = null
    private var playbackPauseAdjustment = 0L
    private var playbackRestore = false
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
    private var visualTickNs=0L
    private var progressPublishedNs=0L
    private var progressDirty=false
    private data class SightSettings(val level: Int,val fifths: Int,val beats: Int,val hand: Hand,val preview: Int,val bpm: Double)
    private var sightSettings: SightSettings?=null
    private var practiceGeneration = 0
    private var finishing = false
    private var simulationUsed = false
    init {
        viewModelScope.launch {recorder.recordings.collect {takes->takes.forEach(::recordingSummary)}}
        viewModelScope.launch {
            var previous=screen.value
            screen.collect {current->
                if(previous!=current && current!="Practice")scoreSelection++
                if(previous=="Practice" && current!="Practice"){stop();stopListening()}
                if(previous=="Ear training" && current!="Ear training"){earPlayback?.cancel();midi.stopReference()}
                if(previous=="Recorder" && current!="Recorder")recorder.interrupt("Saved when leaving recorder")
                if(current!="Ear training")earClock.pause(System.nanoTime()) else if(foreground)earClock.resume(System.nanoTime())
                previous=current
            }
        }
        audio.selectSound(settings.value.sound)
        audio.setMetronomeVolume(settings.value.metronomeVolume)
        midi.onEvent = input@{ event ->
            if(earReferencePlaying.value)return@input
            captureKey(event)
            recorder.observe(event)
            if(screen.value=="Ear training")when(event) {
                is MidiEvent.On -> if(event.velocity>0)earGuess(event.pitch) else earRelease(event.pitch)
                is MidiEvent.Off -> earRelease(event.pitch)
                is MidiEvent.Control -> if(event.controller==120 || event.controller==123)earHeld.clear()
            }
            if(calibrating.value && event is MidiEvent.On && calibrationClicks.size >= 5) {
                val nearest = calibrationClicks.minByOrNull { abs(event.timestamp-it) }
                nearest?.let { val ms = (event.timestamp-it)/1e6; if(abs(ms) < 350) calibrationSamples.add(ms) }
            }
            if(ui.value.running && !finishing && event.timestamp >= startTime) {
                val previousMatched=engine?.matchedIds?.size
                engine?.accept(event)
                progressDirty=true
                updateProgress(force=previousMatched!=engine?.matchedIds?.size)
            }
        }
        midi.onInputReset = {
            recorder.inputReset();recorder.interrupt("Saved after MIDI input reset");stopListening();clearPlayedSequence();earPlayback?.cancel();midi.stopReference();audio.silence();if(ui.value.running)stop("MIDI input reset; restart practice")
        }
        midi.onDisconnect = {
            earPlayback?.cancel();midi.stopReference();earPianoOutput.value=false
            recorder.inputReset();recorder.interrupt("Saved after piano disconnected");clearPlayedSequence()
            if(ui.value.running) stop("Piano disconnected. Session stopped; reconnect before restarting.")
        }
    }
    private fun handleAudioInterruption(reason: String) {
        stopListening();if(ui.value.running)stop(reason)
        recorder.inputReset();recorder.interrupt(reason);clearPlayedSequence();midi.clearKeys();message.value=reason
    }
    fun openRecorder() {stopListening();if(ui.value.running)stop();screen.value="Recorder"}
    fun recordFreePlay() {stopListening();if(ui.value.running)stop();earPlayback?.cancel();audio.silence();recorder.start()}
    fun panic() {if(recorder.state.value.playing)recorder.interrupt("Playback stopped");recorder.inputReset();midi.clearKeys();clearPlayedSequence();audio.silence()}
    fun foreground() {org.pianopath.reminders.PracticeReminders.schedule(getApplication());foreground=true;if(screen.value=="Ear training")earClock.resume(System.nanoTime())}
    /** Ear training uses outgoing audio only; questions never feed generated MIDI into the scorer. */
    fun earQuestion(octaves: Int,voices: Int) = earQuestion(4,octaves,voices)
    /** Choose any C1..C7 start and 1..3 octaves within the 88-key C8 limit. */
    fun earQuestion(startOctave: Int,octaves: Int,voices: Int,fullKeyboard: Boolean=false,triads: Boolean=true) {
        val previous=ear.value
        if(previous.target.isNotEmpty() && !previous.revealed)return
        val from=startOctave.coerceIn(1,7)
        val span=octaves.coerceIn(1,8-from)
        val count=voices.coerceIn(1,3)
        if(earQuestions>=10 || previous.startOctave!=from || previous.octaves!=span || previous.voices!=count || previous.fullKeyboard!=fullKeyboard || previous.triads!=triads){earRound=UUID.randomUUID().toString();earQuestions=0}
        earClock.begin(System.nanoTime())
        val first=(from+1)*12
        val low=maxOf(settings.value.keyboardFirst,if(fullKeyboard)0 else first)
        val high=minOf(settings.value.keyboardLast,if(fullKeyboard)127 else first+span*12)
        if(high<low || high-low<count-1 || count==3 && triads && high-low<7){message.value="This ear exercise does not fit your keyboard range. Choose a wider range or fewer notes.";return}
        val pitches=earExercise(low,high,count,triads,previous.target)
        earHeld.clear();earAdvanceGuesses.clear()
        ear.value=previous.copy(startOctave=from,octaves=span,voices=count,fullKeyboard=fullKeyboard,triads=triads,target=pitches,guesses=emptySet(),revealed=false,questionAttempts=0,history=emptyList(),
            feedback="Listen and find the notes.")
        earReplay()
    }
    fun earReplay() {
        val pitches=ear.value.target
        if(pitches.isEmpty())return
        val previousPlayback=earPlayback
        previousPlayback?.cancel()
        earPlayback=viewModelScope.launch {
            previousPlayback?.join()
            val piano=earPianoOutput.value && midi.state.value.canSend
            var external=piano
            recorder.inputReset();clearPlayedSequence();earReferencePlaying.value=true
            try {
                if(piano) {
                    external=pitches.sorted().all{midi.sendReference(it,80,true)}
                    if(!external){midi.stopReference();message.value="Piano output unavailable; using phone sound";earPianoOutput.value=false}
                }
                if(!external) {
                    if(!audio.start())return@launch
                    pitches.forEach {audio.accept(MidiEvent.On(it,80,System.nanoTime(),14))}
                }
                delay(900)
            } finally {
                if(piano)midi.stopReference()
                pitches.forEach{audio.accept(MidiEvent.Off(it,0,System.nanoTime(),14))}
                // A receiving instrument can echo reference messages. Listen first, then answer.
                recorder.inputReset();midi.clearKeys();earReferencePlaying.value=false
            }
        }
    }
    fun earGuess(pitch: Int) {
        val p=ear.value
        if(earReferencePlaying.value || p.target.isEmpty() || pitch !in (if(p.fullKeyboard)settings.value.keyboardFirst..settings.value.keyboardLast else (p.startOctave+1)*12..minOf(108,(p.startOctave+1)*12+p.octaves*12)))return
        val wasHeld=pitch in earHeld
        earHeld.add(pitch)
        if(p.revealed) {
            // A held final answer cannot also answer the next question. Replay it after release.
            if(wasHeld)return
            if(pitch !in p.target) {earAdvanceGuesses.clear();return}
            earAdvanceGuesses.add(pitch)
            if(earAdvanceGuesses.containsAll(p.target))earQuestion(p.startOctave,p.octaves,p.voices,p.fullKeyboard,p.triads)
            return
        }
        if(pitch in p.guesses)return
        val guesses=p.guesses+pitch
        ear.value=p.copy(guesses=guesses)
        if(guesses.size>=p.voices)earCheck()
    }
    fun earRelease(pitch: Int) {earHeld.remove(pitch)}
    fun earClear() {ear.value=ear.value.copy(guesses=emptySet());earAdvanceGuesses.clear()}
    fun earCheck() {
        val p=ear.value
        if(earReferencePlaying.value || p.target.isEmpty() || p.revealed || p.guesses.size!=p.voices)return
        val right=p.guesses==p.target
        val streak=if(right)p.streak+1 else 0
        val used=p.questionAttempts+1
        val revealed=right || used>=settings.value.earAttempts
        val updated=p.copy(revealed=revealed,questionAttempts=used,guesses=if(revealed)p.guesses else emptySet(),history=p.history+listOf(p.guesses.toSet()),attempts=p.attempts+1,correct=p.correct+if(right)1 else 0,
            streak=streak,bestStreak=maxOf(p.bestStreak,streak),
            feedback=if(right)"Correct! +10 points" else if(revealed)"Attempts used · answer revealed" else "Try again · ${settings.value.earAttempts-used} attempt(s) left")
        earAdvanceGuesses.clear()
        ear.value=updated
        val active=earClock.millis(System.nanoTime()).coerceAtMost(300_000)
        if(revealed){earClock.pause(System.nanoTime());earQuestions++}else earClock.begin(System.nanoTime())
        val entry=PracticeEntry(UUID.randomUUID().toString(),System.currentTimeMillis(),"${if(p.fullKeyboard)"${noteName(settings.value.keyboardFirst)}–${noteName(settings.value.keyboardLast)}" else "C${p.startOctave}–C${p.startOctave+p.octaves}"} · ${if(p.voices==3&&p.triads)"triads" else "${p.voices} note(s)"}","Ear training",
            source=if(midi.state.value.connected)"MIDI / TOUCH" else "TOUCH",activeMs=active,correct=if(right)1 else 0,total=1,roundId=earRound,
            exercise="${p.startOctave}:${p.octaves}:${p.voices}:${p.fullKeyboard}:${p.triads}:${settings.value.keyboardFirst}:${settings.value.keyboardLast}")
        persistenceScope.launch{progress.add(entry)}
        preferences.edit().putInt("earCorrect",updated.correct).putInt("earAttempts",updated.attempts)
            .putInt("earStreak",updated.streak).putInt("earBestStreak",updated.bestStreak).apply()
    }
    fun setSettings(change: (AppSettings)->AppSettings) {
        val previous=settings.value
        val raw=change(previous)
        val updated=raw.copy(metronomeVolume=raw.metronomeVolume.takeIf{it.isFinite()}?.coerceIn(0f,1f) ?: .5f,
            earAttempts=raw.earAttempts.coerceIn(1,10),noteNaming=raw.noteNaming.takeIf{it in listOf("LETTERS","SOLFEGE")} ?: "LETTERS")
        require(updated.keyboardFirst in 0..127 && updated.keyboardLast in updated.keyboardFirst..127)
        if(previous.keyboardFirst!=updated.keyboardFirst || previous.keyboardLast!=updated.keyboardLast){
            stop("Keyboard range changed");stopListening();recorder.interrupt("Keyboard range changed");earPlayback?.cancel();midi.stopReference();panic()
            earRound=UUID.randomUUID().toString();earQuestions=0
            ear.value=ear.value.copy(target=emptySet(),guesses=emptySet(),history=emptyList(),questionAttempts=0,revealed=false,feedback="Keyboard range changed; start a new question")
        }
        settings.value = updated
        val s = settings.value
        if(previous.simulator && !s.simulator){audio.releaseTouch();midi.clearKeys()}
        if(previous.sound!=s.sound){recorder.interrupt("Playback paused for sound change");audio.selectSound(s.sound)}
        if(previous.sheetMargin!=s.sheetMargin)ui.value=ui.value.copy(sheetMargin=s.sheetMargin)
        audio.setMetronomeVolume(s.metronomeVolume)
        preferences.edit().putFloat("tolerance",s.tolerance.toFloat()).putFloat("latency",s.latency.toFloat()).putString("theme",s.theme.name).putBoolean("simulator",s.simulator).putString("sound",s.sound.name)
            .putInt("playedNoteCount",s.playedNoteCount.coerceIn(1,32)).putInt("noteGapMs",s.noteGapMs.coerceIn(500,10000)).putInt("sheetMargin",s.sheetMargin).putInt("loopBreakSeconds",s.loopBreakSeconds.coerceIn(0,5)).putInt("accent",s.accent).putBoolean("fallingGlow",s.fallingGlow).putInt("keyboardFirst",s.keyboardFirst).putInt("keyboardLast",s.keyboardLast).putString("sheetDecoration",s.sheetDecoration.name).putBoolean("reduceMotion",s.reduceMotion)
            .putBoolean("keepAwake",s.keepAwake).putFloat("metronomeVolume",s.metronomeVolume).putString("noteNaming",s.noteNaming).putInt("earAttemptLimit",s.earAttempts).apply()
    }
    fun zoom(delta: Double) { ui.value=ui.value.copy(zoom=(ui.value.zoom+delta).coerceIn(.4,2.5)) }
    fun edit(change: (PracticeUi)->PracticeUi) { if(!ui.value.running && !ui.value.listening) ui.value = change(ui.value) }
    fun review() { ui.value=ui.value.copy(review=true,view=ScoreView.SHEET,reviewBeat=ui.value.beat) }
    fun seekReview(beat: Double) { ui.value=ui.value.copy(reviewBeat=beat) }
    fun previewCursor(beat: Double?) {ui.value=ui.value.copy(cursorPreview=beat?.takeIf{it.isFinite()})}
    fun scaleFalling(delta: Double){ui.value=ui.value.copy(fallingScale=(ui.value.fallingScale+delta).coerceIn(.4,3.0))}
    fun setView(view: ScoreView) { ui.value=ui.value.copy(view=view) }
    /** Supports paused practice (without measuring the pause as lateness) and paused listening. */
    fun togglePause() {
        val p=ui.value
        if(!p.running && !p.listening) return
        val now=System.nanoTime()
        visualTickNs=0L
        if(p.paused) {
            if(p.running) { startTime += (now-pausedAt).coerceAtLeast(0L); engine?.resume(now); activityClock.resume(now); lastClick=Long.MIN_VALUE }
            if(p.listening) {playbackPauseAdjustment+=(now-pausedAt).coerceAtLeast(0L);playbackRestore=true}
            ui.value=p.copy(paused=false,feedback=if(p.listening)"Listening" else "Resumed")
        } else {
            pausedAt=now
            if(p.running) {engine?.pause(now);activityClock.pause(now)} else audio.releasePlayback()
            tone?.stopTone()
            ui.value=p.copy(paused=true,playbackHeld=emptySet(),feedback="Paused")
        }
    }
    /**
     * Draggable score cursor / whole-piece slider.
     * Repositioning during assessment ends the current take; selected A/B bounds stay unchanged.
     */
    fun seek(beat: Double) {
        val before=ui.value;val score=before.score ?: return
        if(!beat.isFinite() || score.endBeat<=0.0)return
        visualTickNs=0L
        if(before.running)stop("Practice stopped for cursor move")
        val current=ui.value
        val target=if(current.listening) {
            val (a,b)=bounds(current);beat.coerceIn(a,(b-.001).coerceAtLeast(a))
        } else beat.coerceIn(0.0,score.endBeat)
        if(current.listening)playbackSeek=target
        ui.value=current.copy(beat=target,transportBeat=target,reviewBeat=target,review=false,cursorPreview=null,expected=expectedAt(score,target,current.hand),playbackHeld=emptySet())
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
            p.copy(startMeasure=from.number,endMeasure=through.number,startOffset=a-from.start,endOffset=b-through.start,loop=true,selectingLoop=true)
        }
    }
    fun toggleLoopSelection() {
        edit {p->
            val score=p.score ?: return@edit p
            if(p.selectingLoop)p.copy(startMeasure=1,startOffset=0.0,endMeasure=score.measures.size,
                endOffset=score.measures.last().length,loop=false,selectingLoop=false)
            else {
                val cursor=(p.cursorPreview ?: if(p.review)p.reviewBeat else p.transportBeat).coerceIn(0.0,score.endBeat)
                val a=(cursor-2.0).coerceAtLeast(0.0).coerceAtMost((score.endBeat-.25).coerceAtLeast(0.0))
                val b=(cursor+2.0).coerceAtMost(score.endBeat).coerceAtLeast(a+.25)
                val from=score.measures.last{it.start<=a+.0001};val through=score.measures.last{it.start<b-.0001}
                p.copy(startMeasure=from.number,startOffset=a-from.start,endMeasure=through.number,endOffset=b-through.start,
                    loop=true,selectingLoop=true)
            }
        }
    }
    fun enableTouch() {setSettings {it.copy(simulator=true)};audio.start()}
    fun playWithoutPiano() = viewModelScope.launch {
        val token=++scoreSelection
        stopListening();if(ui.value.running)stop()
        enableTouch()
        if(ui.value.score!=null) {
            edit{it.copy(mode=PracticeMode.FREE,view=ScoreView.SHEET)}
            screen.value="Practice";return@launch
        }
        val exercise=Exercises.scale(root=0,kind=ScaleKind.MAJOR,octaves=1,hand=Hand.BOTH,descending=true)
        if(!load(exercise.xml,"touch-free-play","Scales","",selectionToken=token))return@launch
        edit{it.copy(mode=PracticeMode.FREE,view=ScoreView.SHEET)}
    }
    fun open(piece: LibraryPiece) = viewModelScope.launch {
        val token=++scoreSelection
        try {
            val xml = withContext(Dispatchers.IO) { getApplication<Application>().assets.open("scores/${piece.asset}").bufferedReader().use { it.readText() } }
            load(xml,piece.key,"Score", "${piece.license} · ${piece.attribution}",selectionToken=token)
        } catch(e: Exception){if(e is CancellationException)throw e;if(token==scoreSelection)message.value="Could not open score: ${e.message}"}
    }
    fun openImport(piece: ImportEntity) = viewModelScope.launch {
        val token=++scoreSelection
        try {
            val xml=withContext(Dispatchers.IO) { java.io.File(getApplication<Application>().filesDir,"scores/${piece.fileName}").readText() }
            load(xml,piece.key,"Imported score","User imported edition. Redistribution rights remain with its owner.",selectionToken=token)
        } catch(e: Exception){if(e is CancellationException)throw e;if(token==scoreSelection)message.value="Could not open imported score: ${e.message}"}
    }
    fun import(uri: Uri) = viewModelScope.launch {
        val token=++scoreSelection
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
            load(xml,key,"Imported score","Imported privately on this device.",selectionToken=token)
        } catch(e: Exception){if(e is CancellationException)throw e;if(token==scoreSelection)message.value="Import failed: ${e.message}"}
    }
    private fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray {
        val out=java.io.ByteArrayOutputStream(); val buffer=ByteArray(8192)
        var n=read(buffer); while(n>0) { require(out.size()+n<=limit) { "Score exceeds 12 MB" }; out.write(buffer,0,n); n=read(buffer) }; return out.toByteArray()
    }
    private suspend fun load(xml: String,key: String,category: String,guidance: String,preview: Int = 0,selectionToken: Long): Boolean {
        val score = withContext(Dispatchers.Default) { MusicXml.parse(xml) }
        val sections=dao.sections(key)
        if(selectionToken!=scoreSelection)return false
        stopListening();if(ui.value.running)stop()
        recorder.interrupt("Score opened")
        ui.value = PracticeUi(score=score,xml=xml,key=key,category=category,bpm=score.tempo,endMeasure=score.measures.size,sheetMargin=settings.value.sheetMargin,
            endOffset=score.measures.last().length,sections=sections,guidance=guidance,previewSeconds=preview,
            hidden=category=="Sight reading",feedback="Ready · ${score.notes.count { !it.rest }} notes",expected=score.notes.filter { !it.rest && it.beat==0.0 }.map { it.midi })
        if(category=="Imported score") {
            recentKeys.value=(listOf(key)+recentKeys.value).distinct().take(8)
            preferences.edit().putString("recentScores",recentKeys.value.joinToString("\n")).apply()
        }
        screen.value="Practice"
        return true
    }
    fun scale(root: Int,kind: ScaleKind,octaves: Int,hand: Hand,descending: Boolean) = viewModelScope.launch {
        val token=++scoreSelection
        val ex = Exercises.scale(root,kind,octaves,hand,descending)
        if(!load(ex.xml,"scale-$root-$kind-$octaves-$hand-$descending","Scales",ex.guidance,selectionToken=token))return@launch
        edit { it.copy(hand=hand,mode=PracticeMode.RHYTHM) }
    }
    fun sight(level: Int,fifths: Int,beats: Int,hand: Hand,preview: Int,bpm: Double = 80.0) = viewModelScope.launch {
        sightSettings=SightSettings(level,fifths,beats,hand,preview.coerceIn(0,60),bpm)
        val token=++scoreSelection
        val ex = Exercises.sight(level,fifths,beats,hand)
        if(!load(ex.xml,"sight-${System.currentTimeMillis()}","Sight reading",ex.guidance,preview.coerceIn(0,60),selectionToken=token))return@launch
        edit { it.copy(hand=hand,mode=PracticeMode.RHYTHM,loop=false,bpm=bpm.coerceIn(20.0,200.0),view=ScoreView.SHEET) }
    }
    fun nextSight() {
        val previous=sightSettings ?: return
        val p=ui.value
        sight(previous.level,previous.fifths,previous.beats,p.hand,previous.preview,p.bpm)
    }
    private fun bounds(p: PracticeUi): Pair<Double,Double> {
        val score = requireNotNull(p.score)
        return score.measures[p.startMeasure-1].start+p.startOffset to score.measures[p.endMeasure-1].start+p.endOffset
    }
    fun saveSection(name: String) = viewModelScope.launch {
        val p=ui.value; if(p.score==null) return@launch
        val (a,b)=bounds(p); dao.saveSection(SectionEntity(scoreKey=p.key,name=name.ifBlank { "Measures ${p.startMeasure}–${p.endMeasure}" },startBeat=a,endBeat=b,bpm=p.bpm))
        val sections=dao.sections(p.key)
        if(ui.value.key==p.key){ui.value=ui.value.copy(sections=sections);message.value="Loop saved on this device"}
    }
    fun useSection(s: SectionEntity) { edit { p ->
        val ms=p.score?.measures ?: return@edit p
        if(s.scoreKey!=p.key || !s.startBeat.isFinite() || !s.endBeat.isFinite() || s.startBeat<0 || s.endBeat<=s.startBeat || s.endBeat>p.score.endBeat || !s.bpm.isFinite() || s.bpm !in 20.0..400.0)return@edit p
        val a=ms.last { it.start<=s.startBeat+0.0001 }; val b=ms.last { it.start<s.endBeat-0.0001 }
        p.copy(startMeasure=a.number,endMeasure=b.number,startOffset=s.startBeat-a.start,endOffset=s.endBeat-b.start,bpm=s.bpm)
    } }
    fun chooseMeasure(number: Int) { edit { p ->
        val m=p.score?.measures?.getOrNull(number-1) ?: return@edit p
        p.copy(startMeasure=number,endMeasure=number,startOffset=0.0,endOffset=m.length)
    } }
    fun start() {
        scoreSelection++
        recorder.interrupt("Saved before practice")
        if (ui.value.running) {
            return
        }
        stopListening()
        cancelAuxiliaryPlayback()
        val p=ui.value
        if(p.score==null) return
        val (a,b)=bounds(p)
        if(a>=b) { message.value="End of passage must follow its start"; return }
        if(p.mode != PracticeMode.FREE && p.score.selected(a,b,p.hand).isEmpty()) { message.value="No notes for this hand in the selected section"; return }
        tone = tone ?: try { ToneGenerator(AudioManager.STREAM_MUSIC,65) } catch(_: Exception) { null }
        simulationUsed=false; engine=null; progressDirty=false; progressPublishedNs=0L
        ui.value=p.copy(beat=a,transportBeat=a,reviewBeat=a,matched=emptySet(),missed=emptySet(),expected=emptyList(),counting=0,cursorPreview=null,playbackHeld=emptySet(),running=true,paused=false,result=null,visualTolerance=settings.value.tolerance,review=false,take=emptyList(),loops=emptyList(),hidden=p.category=="Sight reading" && p.previewSeconds==0,simulated=false)
        ticker=viewModelScope.launch {
            if(p.category=="Sight reading" && p.previewSeconds>0) {
                repeat(p.previewSeconds) { remaining -> ui.value=ui.value.copy(counting=p.previewSeconds-remaining,hidden=false,feedback="Preview · ${p.previewSeconds-remaining} seconds"); waitPracticeTime(1000) }
            }
            beginLoop(skipCountIn=p.category=="Sight reading" && p.previewSeconds>0)
            while(isActive && ui.value.running) {
                if(ui.value.paused) { delay(30); continue }
                val now=System.nanoTime()
                val current=ui.value
                if(now<startTime) {
                    val remaining=ceil((startTime-now)/1e9).toInt().coerceIn(1,4)
                    ui.value=current.copy(counting=remaining,feedback="Count in · $remaining",transportBeat=current.beat)
                } else {
                    if(current.counting>0 || current.hidden && current.previewSeconds>=0) ui.value=current.copy(counting=0,hidden=false)
                    engine?.tick(now)
                    if(current.mode!=PracticeMode.WAIT || progressDirty)updateProgress()
                    val e=engine
                    if(e!=null && current.mode!=PracticeMode.FREE && !finishing) {
                        val (_,end)=bounds(current)
                        val done = if(current.mode==PracticeMode.WAIT) e.finished && midi.state.value.held.isEmpty()
                            else e.hasAnchor && (current.mode!=PracticeMode.FOLLOW || e.finished) && e.clockBeat(now)>=end+current.bpm/60.0*0.5
                        if(done) completeLoop(now)
                    }
                }
                if(ui.value.metronome && ui.value.mode!=PracticeMode.WAIT) {
                    val elapsedBeat=floor((now-startTime)/1e9*(if(now<startTime)1.0 else ui.value.bpm/60)).toLong()
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
    /** Preview and between-loop delays stop counting while practice is paused. */
    private suspend fun waitPracticeTime(milliseconds: Long) {
        var remaining=milliseconds*1_000_000L
        var previous=System.nanoTime()
        var wasPaused=ui.value.paused
        while(remaining>0 || ui.value.paused) {
            delay(20)
            val now=System.nanoTime()
            if(!wasPaused && !ui.value.paused)remaining-=now-previous
            previous=now;wasPaused=ui.value.paused
        }
    }
    private fun beginLoop(skipCountIn: Boolean=false) {
        val p=ui.value; val score=p.score ?: return
        practiceGeneration++
        val(a,b)=bounds(p)
        midi.clearKeys(); audio.silence(); finishing=false; lastClick=Long.MIN_VALUE;visualTickNs=0L
        startTime=System.nanoTime()+if(p.countIn && !skipCountIn) 4_000_000_000L else 0L
        activityClock.begin(startTime);loopContext=p;loopTolerance=settings.value.tolerance;loopLatency=settings.value.latency
        engine=PerformanceEngine(score,PracticeConfig(p.mode,p.hand,p.bpm,settings.value.tolerance,latencyMs=settings.value.latency,startBeat=a,endBeat=b)).also { it.start(startTime) }
        ui.value=p.copy(paused=false,take=emptyList(),review=false,matched=emptySet(),missed=emptySet(),beat=a,transportBeat=a,expected=score.selected(a,b,p.hand).filter { it.beat==a }.map { it.midi },feedback="Ready",counting=if(p.countIn && !skipCountIn) 4 else 0,hidden=p.category=="Sight reading" && p.previewSeconds==0)
    }
    private fun updateProgress(force: Boolean=false) {
        val e=engine ?: return; val p=ui.value
        val now=System.nanoTime()
        if(!force && now-progressPublishedNs<33_000_000L)return
        progressPublishedNs=now;progressDirty=false
        if(p.mode==PracticeMode.FREE) { ui.value=p.copy(expected=emptyList(),feedback=midi.state.value.held.keys.sorted().joinToString { noteName(it) }.ifEmpty { "Free play · play any notes" }); return }
        val (a,b)=bounds(p)
        val target=e.clockBeat(now)
        val visual=when {
            p.paused->p.transportBeat
            p.mode==PracticeMode.WAIT || !e.hasAnchor->{visualTickNs=0L;e.currentBeat}
            p.mode==PracticeMode.RHYTHM->target
            visualTickNs==0L->target
            else->{
                val step=(now-visualTickNs).coerceAtLeast(0)/1e9*p.bpm/60
                val nominal=p.transportBeat+step
                nominal+(target-nominal).coerceIn(-.25*step,.25*step)
            }
        }.coerceIn(a,b)
        visualTickNs=if(p.paused || !e.hasAnchor)0L else now
        ui.value=p.copy(take=e.liveTake(),matched=e.matchedIds,missed=e.missedIds,beat=e.currentBeat,transportBeat=visual,feedback=e.lastFeedback,
            expected=e.expectedPitches)
    }
    private suspend fun completeLoop(now: Long) {
        val generation=practiceGeneration
        finishing=true;simulator?.cancel();simulator=null
        val result=engine?.finish(now) ?: return
        engine=null
        val p=ui.value
        val elapsed=activityClock.millis(now);activityClock.pause(now)
        val context=loopContext ?: p;val simulated=simulationUsed;val tolerance=loopTolerance;val latency=loopLatency
        // Storage must not keep the transport alive or delay the end-of-passage feedback.
        persistenceScope.launch {persist(result,context,simulated,elapsed,tolerance,latency)}
        val remaining=(p.focusRemainingMs-elapsed).coerceAtLeast(0)
        val repeat=p.loop && (p.focusRemainingMs==0L || remaining>0)
        ui.value=p.copy(loops=(p.loops+result).takeLast(100),result=result,take=result.take,focusRemainingMs=remaining,
            running=repeat,paused=false,counting=0,expected=emptyList(),playbackHeld=emptySet(),
            matched=result.details.filter{it.onsetMs!=null}.map{it.note.id}.toSet(),
            missed=result.details.filter{it.missing}.map{it.note.id}.toSet(),
            feedback=if(repeat)"Rest · ${settings.value.loopBreakSeconds}s before count-in" else "Session complete")
        midi.clearKeys();audio.silence();tone?.stopTone()
        if(repeat) {
            if(p.autoTempo && result.overall>=90)ui.value=ui.value.copy(bpm=(p.bpm+4).coerceAtMost(240.0))
            waitPracticeTime(loopBreakMillis(settings.value.loopBreakSeconds))
            if(generation==practiceGeneration && ui.value.running)beginLoop()
        }
    }
    private suspend fun persist(r: SessionResult,p: PracticeUi = ui.value,simulated: Boolean = simulationUsed,activeMs: Long=0,tolerance: Double=loopTolerance,latency: Double=loopLatency) = withContext(NonCancellable) {
        if(p.mode==PracticeMode.FREE || activeMs<=0 && r.take.isEmpty()) return@withContext
        val reviewFile=withContext(Dispatchers.IO){runCatching{TakeArchive.save(getApplication(),p,r)}.getOrNull()}
        runCatching { dao.saveSession(SessionEntity(title=p.score?.title ?: "Practice",date=System.currentTimeMillis(),pitch=r.pitch,rhythm=r.rhythm,overall=r.overall,
            correct=r.correct,expected=r.expected,extra=r.extra,missing=r.missing,onsetMs=r.meanOnsetMs,durationMs=r.meanDurationMs,mode="${p.mode} · ${p.hand}",bpm=p.bpm,category=p.category,simulated=simulated,reviewFile=reviewFile)) }.onFailure { message.value="Session history could not be saved; retaining local progress." }
        val (a,b)=bounds(p)
        val penalties=r.details.groupBy{it.note.measure}.mapValues{(_,notes)->notes.sumOf{n->
            (if(n.missing)2 else 0)+(if(kotlin.math.abs(n.onsetMs ?: 0.0)>tolerance)1 else 0)+(if(kotlin.math.abs(n.durationErrorMs ?: 0.0)>tolerance)1 else 0)}}
        val weak=penalties.filterValues{it>0}.maxByOrNull{it.value}?.key
        val fingerprint=MessageDigest.getInstance("SHA-256").digest(p.xml.toByteArray()).joinToString(""){"%02x".format(it)}
        val metrics=PracticeMetrics.from(r,p.mode,tolerance)
        progress.add(PracticeEntry(UUID.randomUUID().toString(),System.currentTimeMillis(),p.score?.title ?: "Practice",p.category,
            p.key,a,b,p.bpm,p.mode.name,p.hand.name,tolerance,latency,if(simulated)"TOUCH / TEST" else "MIDI",activeMs.coerceIn(0,86_400_000),
            r.pitch,r.rhythm,r.overall,r.expected,r.missing,r.extra,weak,reviewFile,scoringVersion=2,correct=r.correct,exercise=fingerprint,onTimeCorrect=metrics.onTimeCorrect))

    }
    fun practiseEntry(entry: PracticeEntry) = viewModelScope.launch {
        val token=++scoreSelection
        try {
            val xml=withContext(Dispatchers.IO){TakeArchive.originalXml(getApplication(),requireNotNull(entry.reviewFile))}
            if(!load(xml,entry.scoreKey,entry.category,"",selectionToken=token))return@launch
            val score=requireNotNull(ui.value.score)
            val measure=entry.weakMeasure?.let{n->score.measures.find{it.number==n}}
            val a=maxOf(entry.startBeat,measure?.start ?: entry.startBeat)
            val b=minOf(entry.endBeat,measure?.let{it.start+it.length} ?: entry.endBeat)
            setPassage(a,b)
            edit{it.copy(bpm=entry.bpm,mode=runCatching{PracticeMode.valueOf(entry.mode)}.getOrDefault(PracticeMode.RHYTHM),hand=runCatching{Hand.valueOf(entry.hand)}.getOrDefault(Hand.BOTH),loop=true,countIn=true,focusRemainingMs=120_000)}
            message.value="Two-minute focus · finish a repetition when ready"
        }catch(e: Exception){if(e is CancellationException)throw e;if(token==scoreSelection)message.value="Could not reopen this passage: ${e.message}"}
    }
    fun showPerformedRoll(on: Boolean) {
        val p=ui.value;if(!p.review || p.take.isEmpty())return
        ui.value=p.copy(performedRoll=on,view=if(on)ScoreView.FALLING else ScoreView.SHEET)
    }
    fun openReview(session: SessionEntity,showRoll: Boolean = false) = viewModelScope.launch {
        val token=++scoreSelection
        stopListening();if(ui.value.running)stop()
        try {
            val archive=withContext(Dispatchers.IO){TakeArchive.load(getApplication(),session)}
            if(token!=scoreSelection)return@launch
            stopListening();if(ui.value.running)stop();recorder.interrupt("Review opened")
            ui.value=archive.copy(performedRoll=showRoll,view=if(showRoll)ScoreView.FALLING else ScoreView.SHEET)
            screen.value="Practice"
        }
        catch(e: Exception){if(e is CancellationException)throw e;if(token==scoreSelection)message.value="Could not open visual review: ${e.message}"}
    }
    fun stop(reason: String = "Practice stopped",saveResult: Boolean = false) {
        val snapshot=ui.value
        val now=System.nanoTime()
        practiceGeneration++
        ticker?.cancel();ticker=null
        cancelAuxiliaryPlayback()
        stopListening()
        val r=if(saveResult && snapshot.running && !finishing && snapshot.mode!=PracticeMode.FREE)engine?.finish(now) else null
        val simulated=simulationUsed
        val elapsed=activityClock.millis(now);activityClock.pause(now)
        val savedContext=loopContext ?: snapshot
        val tolerance=loopTolerance;val latency=loopLatency
        engine=null;finishing=false;progressDirty=false;visualTickNs=0L
        if(r!=null)persistenceScope.launch {persist(r,savedContext,simulated,elapsed,tolerance,latency)}
        ui.value=ui.value.copy(running=false,listening=false,paused=false,counting=0,hidden=false,
            take=if(saveResult)r?.take ?: snapshot.take else emptyList(),
            result=if(saveResult)r ?: snapshot.result else null,
            matched=if(saveResult)r?.details?.filter{it.onsetMs!=null}?.map{it.note.id}?.toSet() ?: snapshot.matched else emptySet(),
            missed=if(saveResult)r?.details?.filter{it.missing}?.map{it.note.id}?.toSet() ?: snapshot.missed else emptySet(),
            expected=emptyList(),playbackHeld=emptySet(),cursorPreview=null,review=false,performedRoll=false,
            loops=if(saveResult)snapshot.loops else emptyList(),feedback=reason)
        midi.clearKeys();audio.silence();tone?.stopTone()
    }
    private fun cancelAuxiliaryPlayback() {
        simulator?.cancel();simulator=null
        calibration?.cancel();calibration=null;calibrating.value=false
        earPlayback?.cancel();earPlayback=null;midi.stopReference()
        audio.silence();tone?.stopTone()
    }
    fun simulateNext() {
        if(!settings.value.simulator || !audio.start()) return
        simulationUsed=true; ui.value=ui.value.copy(simulated=true)
        val p=ui.value
        val pitches=p.expected.ifEmpty { listOf(60,64,67) }
        simulator?.cancel()
        simulator=viewModelScope.launch {
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
        // Always deliver key-up: playback may finish while a finger is still down.
        if(down && !audio.start())return
        if(!ui.value.listening && down) { simulationUsed=true; ui.value=ui.value.copy(simulated=true) }
        touchEvent(if(down) MidiEvent.On(pitch,80,System.nanoTime()) else MidiEvent.Off(pitch,0,System.nanoTime()))
    }
    private fun touchEvent(event: MidiEvent) {audio.accept(event);midi.simulate(event)}
    fun listen() {
        scoreSelection++
        recorder.interrupt("Saved before listening")
        if (ui.value.listening) {
            stopListening()
            return
        }
        if (ui.value.running) {
            stop("Stopped for listening")
        }
        cancelAuxiliaryPlayback()
        val p=ui.value
        val score=p.score ?: return
        val(a,b)=bounds(p)
        if(a>=b){message.value="End of passage must follow its start";return}
        if(!audio.start())return
        val plan=PlaybackPlan.create(score,a,b,p.hand)
        val tempoScale=p.bpm/score.tempo
        if(plan.isEmpty()){message.value="No notes for this hand in the passage";return}
        val firstBeat=p.transportBeat.takeIf {it>=a && it<b} ?: a
        playbackSeek=null;playbackPauseAdjustment=0L;playbackRestore=false
        lastClick=Long.MIN_VALUE
        if(p.metronome) tone=tone ?: try {ToneGenerator(AudioManager.STREAM_MUSIC,65)} catch(_: Exception){null}
        ui.value=p.copy(listening=true,paused=false,hidden=false,review=false,result=null,matched=emptySet(),missed=emptySet(),beat=firstBeat,transportBeat=firstBeat,feedback="Listening · no assessment",playbackHeld=emptySet())
        val generation=++playbackGeneration
        playback=viewModelScope.launch {
            try {
                var begin=firstBeat
                do {
                    playbackPauseAdjustment=0L
                    var origin=System.nanoTime()-(score.secondsBetween(a,begin,tempoScale)*1e9).toLong()
                    var next=plan.indexOfFirst {it.beat>=begin-.0001}.let {if(it<0)plan.size else it}
                    val held=mutableMapOf<Int,Int>()
                    fun restoreHeld(): Boolean {
                        audio.releasePlayback();held.clear()
                        val sounding=PlaybackPlan.activeBefore(plan,next)
                        if(sounding.isNotEmpty() && !audio.start())return false
                        sounding.forEach {event ->
                            audio.accept(MidiEvent.On(event.pitch,event.velocity,System.nanoTime(),event.channel))
                            held[event.pitch]=(held[event.pitch] ?: 0)+1
                        }
                        return true
                    }
                    if(!restoreHeld())return@launch
                    begin=a // Only the first pass starts at the scrubber; repetitions use the passage start.
                    while(isActive) {
                        if(ui.value.paused) {
                            delay(30);continue
                        }
                        origin+=playbackPauseAdjustment;playbackPauseAdjustment=0L
                        val requested=playbackSeek
                        if(requested!=null) {
                            playbackSeek=null
                            val destination=requested.coerceIn(a,b)
                            next=plan.indexOfFirst { it.beat>=destination-.0001 }.let { if(it<0)plan.size else it }
                            origin=System.nanoTime()-(score.secondsBetween(a,destination,tempoScale)*1e9).toLong()
                            playbackRestore=true;lastClick=Long.MIN_VALUE
                        }
                        if(playbackRestore) {playbackRestore=false;if(!restoreHeld())return@launch}
                        val now=System.nanoTime()
                        val beat=score.beatAtSeconds(a,(now-origin)/1e9,b,tempoScale)
                        while(next<plan.size && plan[next].beat<=beat) {
                            val event=plan[next++]
                            // Audio output may have idled during a rest or long pause.
                            if(event.down && !audio.start())return@launch
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
                        if (beat>=b) {
                            break
                        }
                        delay(12)
                    }
                    audio.releasePlayback()
                    if(isActive && ui.value.loop){
                        ui.value=ui.value.copy(playbackHeld=emptySet(),feedback="Rest · ${settings.value.loopBreakSeconds}s")
                        waitPracticeTime(loopBreakMillis(settings.value.loopBreakSeconds))
                    }
                } while(isActive && ui.value.loop)
                ui.value=ui.value.copy(listening=false,paused=false,playbackHeld=emptySet(),feedback="Playback complete · no assessment")
            } finally {if(generation==playbackGeneration)audio.releasePlayback()}
        }
    }
    fun stopListening() {
        playbackGeneration++;playback?.cancel();playback=null;playbackSeek=null;playbackPauseAdjustment=0L;playbackRestore=false;audio.releasePlayback();tone?.stopTone()
        if(ui.value.listening)ui.value=ui.value.copy(listening=false,paused=false,playbackHeld=emptySet(),feedback="Playback stopped")
    }
    fun background() {
        foreground=false;earClock.pause(System.nanoTime());recorder.inputReset();recorder.interrupt("Saved when app left foreground");clearPlayedSequence()
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
    override fun onCleared() { playback?.cancel();earPlayback?.cancel();idleMarkerJob?.cancel();recorder.close();audio.stop();midi.close(); tone?.release();persistenceJob.invokeOnCompletion{database.close()};persistenceJob.complete();super.onCleared() }
}

private fun preferencesRecent(context: android.content.Context): List<String> = context.getSharedPreferences("settings",0).getString("recentScores","").orEmpty().split("\n").filter{it.isNotBlank()}.take(8)

private fun storedKeyboard(prefs: android.content.SharedPreferences): IntRange {val first=prefs.getInt("keyboardFirst",21);val last=prefs.getInt("keyboardLast",108);return if(first in 0..127 && last in first..127)first..last else 21..108}
