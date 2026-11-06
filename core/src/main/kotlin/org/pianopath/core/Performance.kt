package org.pianopath.core

import kotlin.math.*

data class PracticeConfig(val mode: PracticeMode = PracticeMode.FOLLOW, val hand: Hand = Hand.BOTH,
    val bpm: Double = 80.0, val toleranceMs: Double = 180.0, val chordSpreadMs: Double = 140.0,
    val latencyMs: Double = 0.0, val startBeat: Double = 0.0, val endBeat: Double = Double.MAX_VALUE)
data class NoteResult(val note: ScoreNote, var onsetMs: Double? = null, var durationErrorMs: Double? = null,
    var missing: Boolean = false, var channel: Int = -1)
/** Physical key presses, including extras. Sustained sound is deliberately separate from key duration. */
data class PlayedNote(val id: Int, val pitch: Int, val velocity: Int, val channel: Int,
    val beat: Double, val duration: Double? = null, val expectedId: Int? = null, val onsetMs: Double? = null)
data class SessionResult(val expected: Int, val correct: Int, val missing: Int, val extra: Int,
    val pitch: Double, val rhythm: Double?, val overall: Double, val meanOnsetMs: Double?,
    val meanDurationMs: Double?, val early: Int, val late: Int, val completeChords: Int, val totalChords: Int,
    val leftPitch: Double?, val rightPitch: Double?, val tempoConsistency: Double?, val details: List<NoteResult>, val take: List<PlayedNote> = emptyList())

/** Bounded, polyphonic onset matcher. One note-on consumes at most one score note.
 * FOLLOW adjusts its phase only after finishing the current onset group. It never searches beyond
 * the next group; pauses cannot skip unplayed measures. RHYTHM stays on the metronome clock. WAIT uses pitch completion, without rhythm marks.
 */
class PerformanceEngine(val score: Score, val config: PracticeConfig) {
    val results = score.selected(config.startBeat, config.endBeat,config.hand).sortedWith(compareBy({ it.beat },{ it.midi })).map { NoteResult(it) }
    private val groups = results.groupBy { it.note.beat }.values.toList()
    private var group = 0
    private var origin = 0L
    private var pausedAt: Long? = null
    private var started = false
    private var anchored = false
    private var phaseMs = 0.0
    private var extra = 0
    private val active = mutableMapOf<Pair<Int,Int>,java.util.ArrayDeque<Pair<NoteResult,Long>>>()
    private var lastNanos = Long.MIN_VALUE
    var lastFeedback: String = "Ready"
        private set
    val currentBeat get() = groups.getOrNull(group)?.firstOrNull()?.note?.beat ?: min(score.endBeat,config.endBeat)
    val hasAnchor get() = anchored
    val finished get() = group >= groups.size
    private val matched = linkedSetOf<Int>()
    private val missed = linkedSetOf<Int>()
    private var matchedSnapshot: Set<Int> = emptySet()
    private var missedSnapshot: Set<Int> = emptySet()
    val matchedIds: Set<Int> get() {
        if(matchedSnapshot.size != matched.size) matchedSnapshot = matched.toSet()
        return matchedSnapshot
    }
    val missedIds: Set<Int> get() {
        if(missedSnapshot.size != missed.size) missedSnapshot = missed.toSet()
        return missedSnapshot
    }
    val expectedPitches get() = groups.getOrNull(group).orEmpty().filter { !it.missing && it.onsetMs == null }.map { it.note.midi }
    private var acceptedMatch: NoteResult? = null
    private fun markMissing(notes: List<NoteResult>) {
        notes.filter { it.onsetMs == null }.forEach { it.missing = true; missed.add(it.note.id) }
    }
    fun start(now: Long) { origin = now; started = true; pausedAt = null; anchored = config.mode == PracticeMode.RHYTHM }
    /** Freezes the musical clock without changing note-matching results. */
    fun pause(now: Long) { if(started && pausedAt == null) pausedAt = now }
    /** Exclude paused wall time from both onset timing and still-held key durations. */
    fun resume(now: Long) { pausedAt?.let { paused ->
        val delay = (now-paused).coerceAtLeast(0L)
        origin += delay
        active.values.forEach { queue ->
            val shifted = queue.map { it.first to it.second+delay }
            queue.clear(); queue.addAll(shifted)
        }
        recordingActive.values.forEach { queue ->
            val shifted = queue.map { it.first to it.second+delay }
            queue.clear(); queue.addAll(shifted)
        }
        pausedAt = null
    } }
    private fun elapsedMs(now: Long) = ((pausedAt?.let { min(now,it) } ?: now) - origin) / 1e6 - config.latencyMs
    private fun expectedMs(note: ScoreNote) = (note.beat - config.startBeat) * 60000.0 / config.bpm + phaseMs
    fun clockBeat(now: Long) = config.startBeat + (elapsedMs(now) - phaseMs) * config.bpm / 60000.0
    private val recorded = mutableListOf<PlayedNote>()
    private val recordingActive = mutableMapOf<Pair<Int,Int>,java.util.ArrayDeque<Pair<Int,Long>>>()
    private var takeRevision = 0L
    private var liveRevision = -1L
    private var liveLimit = 0
    private var liveSnapshot: List<PlayedNote> = emptyList()
    val take: List<PlayedNote> get() = recorded.toList()
    /** Rendering needs only recent notes; retain the complete take for the finished review. */
    fun liveTake(limit: Int = 256): List<PlayedNote> {
        require(limit > 0)
        if(liveRevision != takeRevision || liveLimit != limit) {
            liveSnapshot = recorded.takeLast(limit)
            liveRevision = takeRevision; liveLimit = limit
        }
        return liveSnapshot
    }
    fun accept(event: MidiEvent) {
        if(!started || config.mode == PracticeMode.FREE)return
        // New attacks during a pause are not scored, but releases must still close
        // pre-pause notes. Their duration ends at the frozen musical clock.
        val incoming=if(event is MidiEvent.On && event.velocity==0)MidiEvent.Off(event.pitch,0,event.timestamp,event.channel) else event
        val e = pausedAt?.let { paused -> when(incoming) {
            is MidiEvent.On -> return
            is MidiEvent.Off -> incoming.copy(timestamp=min(incoming.timestamp,paused))
            is MidiEvent.Control -> incoming.copy(timestamp=min(incoming.timestamp,paused))
        } } ?: incoming
        acceptedMatch=null
        val clockBefore=if(config.mode==PracticeMode.WAIT || !anchored)currentBeat else clockBeat(e.timestamp)
        val releaseIsExtra=e is MidiEvent.Off && recordingActive[e.channel to e.pitch]?.peekFirst()?.first?.let{recorded[it].expectedId==null}==true
        if(!releaseIsExtra)acceptScored(e)
        when(e) {
            is MidiEvent.On -> if(recorded.size<20000) {
                val match=acceptedMatch
                val beat=match?.let {it.note.beat + if(config.mode==PracticeMode.WAIT)0.0 else (it.onsetMs ?: 0.0)*config.bpm/60000.0} ?: clockBefore
                val index=recorded.size
                recorded.add(PlayedNote(index,e.pitch,e.velocity,e.channel,beat,expectedId=match?.note?.id,onsetMs=if(config.mode==PracticeMode.WAIT)null else match?.onsetMs)); takeRevision++
                recordingActive.getOrPut(e.channel to e.pitch){java.util.ArrayDeque()}.add(index to e.timestamp)
            }
            is MidiEvent.Off -> recordingActive[e.channel to e.pitch]?.pollFirst()?.let { (index,time) ->
                recorded[index]=recorded[index].copy(duration=((e.timestamp-time)/1e9*config.bpm/60.0).coerceAtLeast(0.0)); takeRevision++
            }
            is MidiEvent.Control -> if(e.controller in listOf(120,123))recordingActive.keys.filter{it.first==e.channel}.toList().forEach{key->
                recordingActive.remove(key)?.forEach{(index,time)->recorded[index]=recorded[index].copy(duration=((e.timestamp-time)/1e9*config.bpm/60.0).coerceAtLeast(0.0)); takeRevision++}
            }
        }
    }
    private fun acceptScored(e: MidiEvent) {
        if(!started || config.mode == PracticeMode.FREE) return
        // A driver may deliver buffered timestamps out of order across packets. Do not move time backwards.
        lastNanos = max(lastNanos,e.timestamp)
        if(e is MidiEvent.Control && e.controller in listOf(120,123)) {
            active.keys.filter{it.first==e.channel}.toList().forEach{key->
                while(active[key]?.isNotEmpty()==true)acceptScored(MidiEvent.Off(key.second,0,e.timestamp,key.first))
            }
            return
        }
        if(e is MidiEvent.Off) {
            val queue = active[e.channel to e.pitch]
            val played = queue?.pollFirst() ?: return
            if(queue.isEmpty()) active.remove(e.channel to e.pitch)
            if(config.mode != PracticeMode.WAIT) {
                val expectedDuration = min(played.first.note.duration,config.endBeat - played.first.note.beat) * 60000.0 / config.bpm
                played.first.durationErrorMs = (e.timestamp - played.second) / 1e6 - expectedDuration
            }
            return
        }
        if(e !is MidiEvent.On) return
        if(!anchored && config.mode == PracticeMode.FOLLOW) {
            // Anchor only on a pitch from the first group; an accidental extra note cannot start transport.
            if(groups.firstOrNull()?.none { it.note.midi == e.pitch } != false) { extra++; lastFeedback = "Extra ${noteName(e.pitch)}"; return }
            origin = e.timestamp - beatNanos(groups.first().first().note.beat - config.startBeat,config.bpm) - (config.latencyMs * 1e6).toLong()
            anchored = true
        }
        if(anchored && config.mode != PracticeMode.WAIT) tick(e.timestamp)
        val current = groups.getOrNull(group)
        if(current == null) { extra++; lastFeedback = "Extra ${noteName(e.pitch)}"; return }
        val candidates = if(config.mode == PracticeMode.WAIT) current else current + (groups.getOrNull(group+1) ?: emptyList())
        val window = max(450.0,config.toleranceMs + config.chordSpreadMs)
        val match = candidates.filter { !it.missing && it.onsetMs == null && it.note.midi == e.pitch }
            .filter {
                config.mode == PracticeMode.WAIT ||
                config.mode == PracticeMode.FOLLOW && elapsedMs(e.timestamp) >= expectedMs(it.note) - window ||
                abs(elapsedMs(e.timestamp) - expectedMs(it.note)) <= window
            }
            .minByOrNull {
                // In follow mode prefer the current onset over a repeated pitch in the next onset.
                if(config.mode == PracticeMode.WAIT) 0.0
                else if(config.mode == PracticeMode.FOLLOW) if(it in current) 0.0 else 1.0
                else abs(elapsedMs(e.timestamp) - expectedMs(it.note))
            }
        if(match == null) { extra++; lastFeedback = "Extra ${noteName(e.pitch)}"; return }
        val deviation = if(config.mode == PracticeMode.WAIT) 0.0 else elapsedMs(e.timestamp) - expectedMs(match.note)
        if(config.mode == PracticeMode.FOLLOW && match !in current) {
            markMissing(current)
            advance()
        }
        match.onsetMs = deviation; match.channel = e.channel
        matched.add(match.note.id); acceptedMatch = match
        // Re-anchor after a long expressive pause. Never advance unseen measures on a timer.
        if(config.mode == PracticeMode.FOLLOW && abs(deviation) > window) phaseMs += deviation
        active.getOrPut(e.channel to e.pitch) { java.util.ArrayDeque() }.add(match to e.timestamp)
        lastFeedback = when {
            config.mode == PracticeMode.WAIT -> "Correct ${noteName(e.pitch)}"
            deviation < -config.toleranceMs -> "Early ${abs(deviation).toInt()} ms"
            deviation > config.toleranceMs -> "Late ${deviation.toInt()} ms"
            else -> "On time · ${noteName(e.pitch)}"
        }
        advance()
    }
    private fun advance() {
        while(group < groups.size && groups[group].all { it.onsetMs != null || it.missing }) {
            if(config.mode == PracticeMode.FOLLOW) {
                val played = groups[group].mapNotNull { it.onsetMs }.filter { abs(it) <= max(450.0,config.toleranceMs + config.chordSpreadMs) }.sorted()
                if(played.isNotEmpty()) phaseMs += (played[played.size/2] * 0.35).coerceIn(-120.0,120.0)
            }
            group++
        }
    }
    fun tick(now: Long) {
        if(!started || pausedAt != null || !anchored || config.mode in listOf(PracticeMode.WAIT,PracticeMode.FREE,PracticeMode.FOLLOW)) return
        val expiry = max(450.0,config.toleranceMs + config.chordSpreadMs)
        while(group < groups.size) {
            val g = groups[group]
            if(elapsedMs(now) <= expectedMs(g.first().note) + expiry) break
            markMissing(g)
            advance()
        }
    }
    fun finish(now: Long): SessionResult {
        tick(now)
        markMissing(results)
        if(config.mode != PracticeMode.WAIT) active.values.flatMap { it.toList() }.forEach { (r,_) ->
            // No key release was received. Do not silently treat this as an accurate duration.
            r.durationErrorMs = max(config.toleranceMs * 2, r.note.duration * 60000.0 / config.bpm)
        }
        active.clear()
        return summary()
    }
    fun summary(): SessionResult {
        val played = results.filter { it.onsetMs != null }
        val correct = played.size
        val missing = results.count { it.missing }
        val pitch = if(results.isEmpty() && extra == 0) 100.0 else 100.0 * correct / (results.size + extra).coerceAtLeast(1)
        val onsets = played.mapNotNull { it.onsetMs }
        val durations = played.mapNotNull { it.durationErrorMs }
        val rhythm = if(config.mode == PracticeMode.WAIT || config.mode == PracticeMode.FREE) null else {
            val onsetPoints = onsets.sumOf { (1.0 - max(0.0,abs(it) - config.toleranceMs) / (config.toleranceMs*2)).coerceIn(0.0,1.0) }
            val durationPoints = played.sumOf { r -> r.durationErrorMs?.let { (1.0 - max(0.0,abs(it) - config.toleranceMs) / (config.toleranceMs*3)).coerceIn(0.0,1.0) } ?: 0.0 }
            100.0 * (onsetPoints * 0.7 + durationPoints * 0.3) / results.size.coerceAtLeast(1)
        }
        fun hand(h: Hand): Double? { val subset = results.filter { it.note.hand == h }; return if(subset.isEmpty()) null else 100.0 * subset.count { it.onsetMs != null } / subset.size }
        val chords = groups.filter { it.size > 1 }
        val sd = if(onsets.size < 2) null else { val mean = onsets.average(); sqrt(onsets.sumOf { (it-mean).pow(2) } / onsets.size) }
        return SessionResult(results.size,correct,missing,extra,pitch,rhythm,if(rhythm == null) pitch else pitch*0.6+rhythm*0.4,
            onsets.takeIf { it.isNotEmpty() }?.map { abs(it) }?.average(),durations.takeIf { it.isNotEmpty() }?.map { abs(it) }?.average(),
            onsets.count { it < -config.toleranceMs },onsets.count { it > config.toleranceMs },
            chords.count { chord -> chord.all { it.onsetMs != null } },chords.size,hand(Hand.LEFT),hand(Hand.RIGHT),sd,results.map { it.copy() },take)
    }
}
