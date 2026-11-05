package org.pianopath.core

import java.util.ArrayDeque

/** Microseconds relative to the start of a take; array order breaks simultaneous-event ties. */
data class RecordedMidi(val atUs: Long,val kind: Int,val channel: Int,val a: Int,val b: Int) {
    fun event(now: Long): MidiEvent = when(kind) {
        0 -> MidiEvent.On(a,b,now,channel)
        1 -> MidiEvent.Off(a,b,now,channel)
        else -> MidiEvent.Control(a,b,now,channel)
    }
    companion object {
        fun from(e: MidiEvent,atUs: Long) = when(e) {
            is MidiEvent.On -> if(e.velocity==0)RecordedMidi(atUs,1,e.channel,e.pitch,0) else RecordedMidi(atUs,0,e.channel,e.pitch,e.velocity)
            is MidiEvent.Off -> RecordedMidi(atUs,1,e.channel,e.pitch,e.velocity)
            is MidiEvent.Control -> RecordedMidi(atUs,2,e.channel,e.controller,e.value)
        }
    }
}
data class MidiRecording(val id: String,val title: String,val createdAt: Long,val durationUs: Long,
    val events: List<RecordedMidi>,val finished: Boolean=true,val recovered: Boolean=false) {
    fun validate(): MidiRecording {
        require(id.matches(Regex("[a-f0-9-]{36}")) && title.length<=160)
        require(durationUs in 0..MAX_DURATION_US && events.size<=MAX_EVENTS*2+8192)
        var previous=0L
        events.forEach {e->require(e.atUs in previous..durationUs && e.kind in 0..2 && e.channel in 0..15 && e.a in 0..127 && e.b in 0..127);previous=e.atUs}
        return this
    }
    companion object {const val MAX_EVENTS=200_000;const val MAX_DURATION_US=7_200_000_000L}
}
data class RecordedKey(val pitch: Int,val velocity: Int,val channel: Int,val startUs: Long,val endUs: Long?)

/** Reconstructs physical holds and pedal-latched voices independently of the score engine. */
class RecordedMidiState {
    var limitReached=false;private set
    private var voices=0
    fun clear(){held.clear();latched.clear();controllers.clear();voices=0;limitReached=false}
    private val held=linkedMapOf<Pair<Int,Int>,ArrayDeque<RecordedMidi>>()
    private val latched=mutableListOf<RecordedMidi>()
    private val controllers=linkedMapOf<Pair<Int,Int>,Int>()
    val heldPitches get()=held.filterValues{it.isNotEmpty()}.keys.map{it.second}.toSet()
    val soundingPitches get()=heldPitches+latched.map{it.a}
    fun accept(e: RecordedMidi) {
        val key=e.channel to e.a
        when(e.kind) {
            0 -> if(e.b>0){if(voices>=4096){limitReached=true;return};held.getOrPut(key){ArrayDeque()}.addLast(e);voices++} else release(e)
            1 -> release(e)
            2 -> when(e.a) {
                64 -> {controllers[key]=e.b;if(e.b<64){voices-=latched.count{it.channel==e.channel};latched.removeAll{it.channel==e.channel}}}
                120,123 -> {voices-=held.filterKeys{it.first==e.channel}.values.sumOf{it.size}+latched.count{it.channel==e.channel};held.keys.removeAll{it.first==e.channel};latched.removeAll{it.channel==e.channel};controllers.remove(e.channel to 64)}
                121 -> {voices-=latched.count{it.channel==e.channel};controllers.keys.removeAll{it.first==e.channel};latched.removeAll{it.channel==e.channel}}
                else -> controllers[key]=e.b
            }
        }
    }
    private fun release(e: RecordedMidi) {
        val key=e.channel to e.a
        val on=held[key]?.pollFirst() ?: return
        if(held[key]?.isEmpty()==true)held.remove(key)
        if((controllers[e.channel to 64] ?: 0)>=64)latched.add(on) else voices--
    }
    fun restore(atUs: Long): List<RecordedMidi> = buildList {
        controllers.forEach{(k,v)->add(RecordedMidi(atUs,2,k.first,k.second,v))}
        // Release latched copies before re-creating held FIFO voices of the same pitch.
        latched.forEach{add(it.copy(atUs=atUs));add(RecordedMidi(atUs,1,it.channel,it.a,0))}
        held.values.forEach{queue->queue.forEach{add(it.copy(atUs=atUs))}}
    }
    fun close(atUs: Long): List<RecordedMidi> = buildList {
        held.forEach{(key,q)->repeat(q.size){add(RecordedMidi(atUs,1,key.first,key.second,0))}}
        controllers.keys.filter{it.second==64}.forEach{add(RecordedMidi(atUs,2,it.first,64,0))}
        (held.keys.map{it.first}+latched.map{it.channel}).distinct().forEach{add(RecordedMidi(atUs,2,it,123,0))}
    }
}

/** Main-thread capture. Listener sees only real MIDI/touch, never demonstration/replay output. */
class MidiCapture {
    private val live=RecordedMidiState()
    private var draft: MidiRecording?=null
    private var origin=0L
    private val events=mutableListOf<RecordedMidi>()
    val recording get()=draft!=null
    val eventCount get()=events.size
    fun begin(id: String,title: String,date: Long,now: Long) {
        check(!recording);origin=now;events.clear()
        draft=MidiRecording(id,title,date,0,emptyList(),finished=false)
        // A note already sounding at the start is represented from t=0, including pedal state.
        events.addAll(live.restore(0))
    }
    /** False means the explicit duration/event bound has been reached; caller must save/stop. */
    fun accept(e: MidiEvent): Boolean {
        val relative=if(recording)((e.timestamp-origin)/1000).coerceAtLeast(events.lastOrNull()?.atUs ?: 0) else 0
        val value=RecordedMidi.from(e,relative)
        live.accept(value)
        if(live.limitReached){live.clear();return false}
        if(!recording)return true
        if(relative>MidiRecording.MAX_DURATION_US || events.size>=MidiRecording.MAX_EVENTS)return false
        events.add(value);return true
    }
    fun resetLive(){live.clear()}
    fun snapshot(now: Long): MidiRecording {
        val p=checkNotNull(draft)
        val until=((now-origin)/1000).coerceIn(0,MidiRecording.MAX_DURATION_US).coerceAtLeast(events.lastOrNull()?.atUs ?: 0)
        return p.copy(durationUs=until,events=events.toList())
    }
    fun finish(now: Long): MidiRecording {
        val value=closeRecording(snapshot(now));draft=null;events.clear();return value
    }
}
fun closeRecording(data: MidiRecording): MidiRecording {
    val state=RecordedMidiState();data.events.forEach(state::accept)
    return data.copy(events=data.events+state.close(data.durationUs),finished=true).validate()
}
fun recordingKeys(data: MidiRecording): List<RecordedKey> {
    val keys=mutableListOf<RecordedKey>();val active=mutableMapOf<Pair<Int,Int>,ArrayDeque<Int>>()
    fun release(channel: Int,pitch: Int,at: Long) {active[channel to pitch]?.pollFirst()?.let{keys[it]=keys[it].copy(endUs=at)}}
    data.events.forEach {e->when(e.kind) {
        0 -> if(e.b>0){val i=keys.size;keys.add(RecordedKey(e.a,e.b,e.channel,e.atUs,null));active.getOrPut(e.channel to e.a){ArrayDeque()}.addLast(i)}else release(e.channel,e.a,e.atUs)
        1 -> release(e.channel,e.a,e.atUs)
        2 -> if(e.a==120 || e.a==123)active.filterKeys{it.first==e.channel}.values.forEach{q->while(q.isNotEmpty())q.pollFirst()?.let{keys[it]=keys[it].copy(endUs=e.atUs)}}
    }}
    return keys.map{if(it.endUs==null)it.copy(endUs=data.durationUs)else it}
}
class MidiReplay(val data: MidiRecording) {
    var next=0;private set
    var state=RecordedMidiState();private set
    fun seek(positionUs: Long): List<RecordedMidi> {
        val position=positionUs.coerceIn(0,data.durationUs)
        state=RecordedMidiState();next=0
        while(next<data.events.size && data.events[next].atUs<position)state.accept(data.events[next++])
        return state.restore(position)
    }
    fun advance(positionUs: Long): List<RecordedMidi> = buildList {
        while(next<data.events.size && data.events[next].atUs<=positionUs){val e=data.events[next++];state.accept(e);add(e)}
    }
}
