package org.pianopath.recording

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.pianopath.audio.PianoAudio
import org.pianopath.core.*
import org.pianopath.data.RecordingRepository
import java.text.DateFormat
import java.util.Date
import java.util.UUID

data class RecorderUi(
    val recording: Boolean=false,val playing: Boolean=false,val saving: Boolean=false,
    val current: MidiRecording?=null,val positionSeconds: Double=0.0,val elapsedSeconds: Double=0.0,
    val eventCount: Int=0,val speed: Double=1.0,val held: Set<Int> = emptySet(),
    val sounding: Set<Int> = emptySet(),val status: String="Loading recordings…",
    val ready: Boolean=false,val error: String?=null,val unsaved: Boolean=false
)

/** All capture/transport methods run on the main thread; only private file IO uses the writer. */
class FreePlayController(context: Context,private val scope: CoroutineScope,private val audio: PianoAudio,
    private val onMessage: (String)->Unit,private val onSaved: (MidiRecording)->Unit = {}) {
    private val repository=RecordingRepository(context.applicationContext)
    private val mutable=MutableStateFlow(RecorderUi())
    val state: StateFlow<RecorderUi> = mutable.asStateFlow()
    private val library=MutableStateFlow<List<MidiRecording>>(emptyList())
    val recordings: StateFlow<List<MidiRecording>> = library.asStateFlow()
    private val capture=MidiCapture()
    private val live=RecordedMidiState()
    private var replay: MidiReplay?=null
    private var transport: Job?=null
    private var origin=0L
    private var playOrigin=0L
    private var playPosition=0.0
    private var selection=0L
    private var pendingCheckpoint: String?=null
    private var pendingSaves=0
    private var closed=false
    private val notifiedSaves=mutableSetOf<String>()
    // This short-lived writer outlives ViewModel cancellation long enough to commit queued finals.
    private val writerScope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val writes=Channel<suspend ()->Unit>(Channel.UNLIMITED)

    init {
        writerScope.launch {
            try {for(write in writes)write()} finally {writerScope.cancel()}
        }
        submit({repository.load()},{loaded->
            library.value=loaded.recordings
            val message=loaded.messages.take(3).joinToString(" ")
            mutable.value=mutable.value.copy(ready=true,status=message.ifBlank{"Ready"},error=message.takeIf{it.isNotBlank()})
            if(message.isNotBlank())onMessage(message)
        },{message->mutable.value=mutable.value.copy(ready=true,status=message,error=message)})
    }

    private fun <T> submit(work: ()->T,success: (T)->Unit={},failure: (String)->Unit={}) {
        if(closed)return
        writes.trySend {
            try {
                val result=work()
                withContext(Dispatchers.Main.immediate) {if(!closed)success(result)}
            } catch(e: Exception) {
                val message="Local recording storage: ${e.message ?: "operation failed"}"
                withContext(Dispatchers.Main.immediate) {
                    if(!closed) {
                        mutable.value=mutable.value.copy(status=message,error=message)
                        failure(message);onMessage(message)
                    }
                }
            }
        }
    }
    private fun upsert(value: MidiRecording) {
        library.value=(library.value.filterNot{it.id==value.id}+value.copy(events=emptyList())).sortedByDescending{it.createdAt}
    }
    private fun beginSave() {pendingSaves++;mutable.value=mutable.value.copy(saving=true)}
    private fun endSave() {pendingSaves=(pendingSaves-1).coerceAtLeast(0);mutable.value=mutable.value.copy(saving=pendingSaves>0)}

    /** Called for real MIDI/touch only, including while capture is idle, to prime a new take. */
    fun observe(e: MidiEvent) {
        if(closed)return
        live.accept(RecordedMidi.from(e,0))
        val accepted=capture.accept(e)
        if(live.limitReached)live.clear()
        if(!accepted && state.value.recording) {
            stop("Saved: recording size, duration, or voice limit reached")
            return
        }
        if(state.value.recording)mutable.value=state.value.copy(eventCount=capture.eventCount,
            held=live.heldPitches,sounding=live.soundingPitches)
    }

    fun start() {
        if(closed || !state.value.ready || state.value.recording || state.value.saving)return
        if(state.value.unsaved) {onMessage("Retry saving or delete the current recording before starting another.");return}
        pause();selection++
        val now=System.nanoTime();origin=now
        val date=System.currentTimeMillis()
        val title="Free play · ${DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(date))}"
        capture.begin(UUID.randomUUID().toString(),title,date,now)
        val draft=capture.snapshot(now)
        mutable.value=state.value.copy(recording=true,playing=false,current=draft,positionSeconds=0.0,elapsedSeconds=0.0,
            eventCount=capture.eventCount,held=live.heldPitches,sounding=live.soundingPitches,status="Recording",error=null)
        checkpoint(draft)
        transport=scope.launch {
            var checkpointAt=System.nanoTime()
            while(isActive && capture.recording) {
                delay(50)
                val time=System.nanoTime()
                val elapsed=(time-origin)/1e9
                mutable.value=state.value.copy(elapsedSeconds=elapsed,eventCount=capture.eventCount)
                if(elapsed>=MidiRecording.MAX_DURATION_US/1e6) {stop("Saved: two-hour recording limit reached");break}
                if(time-checkpointAt>=3_000_000_000L && pendingCheckpoint==null) {
                    checkpointAt=time;checkpoint(capture.snapshot(time))
                }
            }
        }
    }
    private fun checkpoint(value: MidiRecording) {
        if(pendingCheckpoint==value.id)return
        pendingCheckpoint=value.id
        fun clearPending() {if(pendingCheckpoint==value.id)pendingCheckpoint=null}
        submit({repository.checkpoint(value)},{clearPending()},{clearPending()})
    }
    fun stop(reason: String="Saved") {
        if(closed || !capture.recording)return
        transport?.cancel();transport=null
        val value=capture.finish(System.nanoTime())
        replay=MidiReplay(value)
        mutable.value=state.value.copy(recording=false,current=value,elapsedSeconds=value.durationUs/1e6,
            positionSeconds=0.0,eventCount=value.events.size,held=emptySet(),sounding=emptySet(),status="Saving…")
        saveFinal(value,reason)
    }
    private fun saveFinal(value: MidiRecording,reason: String) {
        beginSave()
        submit({repository.save(value)},{
            upsert(value);endSave()
            if(state.value.current?.id==value.id && !state.value.recording)mutable.value=state.value.copy(status=reason,error=null,unsaved=false)
            if(notifiedSaves.add(value.id))onSaved(value)
        },{message->
            endSave()
            if(state.value.current?.id==value.id)mutable.value=state.value.copy(unsaved=true,
                status="Save failed · Retry save to keep the complete recording",error=message)
        })
    }
    fun retrySave() {
        if(closed || state.value.recording || state.value.saving || !state.value.unsaved)return
        val value=state.value.current?.takeIf{it.finished} ?: return
        saveFinal(value,"Saved")
    }

    fun open(id: String) {
        if(closed || state.value.recording || state.value.unsaved || state.value.saving)return
        pause();val token=++selection
        mutable.value=state.value.copy(status="Opening…")
        submit({repository.read(id)},{value->
            if(selection==token && !state.value.recording) {
                pause()
                replay=MidiReplay(value)
                mutable.value=state.value.copy(current=value,positionSeconds=0.0,elapsedSeconds=value.durationUs/1e6,
                    eventCount=value.events.size,held=emptySet(),sounding=emptySet(),status=if(value.recovered)"Recovered recording" else "Ready",error=null)
            }
        })
    }
    fun rename(id: String,title: String) {
        if(closed || (state.value.recording && state.value.current?.id==id))return
        val clean=title.trim().take(160)
        if(clean.isEmpty()) {onMessage("Enter a recording name");return}
        beginSave()
        val inMemory=state.value.current?.takeIf{it.id==id && it.finished}
        submit({(inMemory ?: repository.read(id)).copy(title=clean).also(repository::save)},{value->
            upsert(value);endSave()
            if(state.value.current?.id==id)mutable.value=state.value.copy(current=state.value.current?.copy(title=clean),status="Renamed",error=null,unsaved=false)
        },{endSave()})
    }
    fun delete(id: String) {
        if(closed || (state.value.recording && state.value.current?.id==id))return
        if(state.value.current?.id==id) {pause();selection++}
        beginSave()
        submit({repository.delete(id)},{
            library.value=library.value.filterNot{it.id==id};endSave()
            if(state.value.current?.id==id) {
                pause()
                replay=null
                mutable.value=state.value.copy(current=null,positionSeconds=0.0,elapsedSeconds=0.0,eventCount=0,
                    held=emptySet(),sounding=emptySet(),status="Deleted",error=null,unsaved=false)
            }
        },{endSave()})
    }

    private fun positionNow(): Double = if(state.value.playing)
        (playPosition+(System.nanoTime()-playOrigin)/1e9*state.value.speed)
            .coerceIn(0.0,(state.value.current?.durationUs ?: 0)/1e6)
        else state.value.positionSeconds

    private fun restoreAt(seconds: Double,audible: Boolean) {
        val player=replay ?: return
        audio.silenceRecording()
        val us=(seconds*1e6).toLong().coerceIn(0,player.data.durationUs)
        val restored=player.seek(us)
        val boundary=player.advance(us)
        if(audible) {
            val now=System.nanoTime()
            restored.forEach{audio.acceptRecording(it.event(now))}
            boundary.forEach{audio.acceptRecording(it.event(now))}
        }
        mutable.value=state.value.copy(positionSeconds=us/1e6,held=player.state.heldPitches,sounding=player.state.soundingPitches)
    }
    private fun pause() {
        val position=positionNow()
        transport?.cancel();transport=null;audio.silenceRecording()
        if(state.value.playing)mutable.value=state.value.copy(playing=false,positionSeconds=position,status="Paused")
    }
    fun playPause() {
        if(closed || state.value.recording)return
        if(state.value.playing) {pause();return}
        val value=state.value.current ?: return
        if(replay?.data?.id!=value.id)replay=MidiReplay(value)
        if(!audio.start())return
        val from=if(state.value.positionSeconds>=value.durationUs/1e6)0.0 else state.value.positionSeconds
        restoreAt(from,true)
        playPosition=from;playOrigin=System.nanoTime()
        mutable.value=state.value.copy(playing=true,status="Playing",error=null)
        transport=scope.launch {
            while(isActive && state.value.playing) {
                val position=positionNow();val player=replay ?: break
                val now=System.nanoTime()
                player.advance((position*1e6).toLong()).forEach{audio.acceptRecording(it.event(now))}
                mutable.value=state.value.copy(positionSeconds=position,held=player.state.heldPitches,sounding=player.state.soundingPitches)
                if(position>=player.data.durationUs/1e6) {
                    audio.silenceRecording()
                    mutable.value=state.value.copy(playing=false,held=emptySet(),sounding=emptySet(),status="Finished")
                    break
                }
                delay(8)
            }
        }
    }
    fun seek(seconds: Double) {
        if(closed || state.value.recording || !seconds.isFinite())return
        val value=state.value.current ?: return
        val position=seconds.coerceIn(0.0,value.durationUs/1e6)
        restoreAt(position,state.value.playing)
        playPosition=position;playOrigin=System.nanoTime()
    }
    fun setSpeed(speed: Double) {
        if(closed || !speed.isFinite())return
        playPosition=positionNow();playOrigin=System.nanoTime()
        mutable.value=state.value.copy(speed=speed.coerceIn(.25,2.0),positionSeconds=playPosition)
    }
    fun interrupt(reason: String) {
        if(closed)return
        if(capture.recording)stop("Saved · $reason")
        else if(state.value.unsaved)retrySave()
        pause()
    }
    fun inputReset() {
        if(closed)return
        val now=System.nanoTime()
        repeat(16){observe(MidiEvent.Control(120,0,now,it))}
        capture.resetLive();live.clear()
        if(state.value.recording)mutable.value=state.value.copy(held=emptySet(),sounding=emptySet())
    }
    fun close() {
        if(closed)return
        interrupt("closed");closed=true;selection++;writes.close()
    }
}
