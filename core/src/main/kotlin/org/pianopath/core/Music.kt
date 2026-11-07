package org.pianopath.core

import kotlin.math.*

enum class Hand { LEFT, RIGHT, BOTH }
enum class PracticeMode { FREE, FOLLOW, WAIT, RHYTHM }
data class ScoreNote(val id: Int, val midi: Int, val beat: Double, val duration: Double,
    val measure: Int, val staff: Int = 1, val voice: String = "1", val finger: String = "",
    val rest: Boolean = false) {
    val hand get() = if (staff > 1) Hand.LEFT else Hand.RIGHT
}
/** Written grace notes have no ordinary beat duration; playback decides how much time they steal. */
data class GraceNote(val midi: Int,val beat: Double,val measure: Int,val staff: Int=1,
    val voice: String="1",val slashed: Boolean=true)
enum class OrnamentKind { TRILL, INVERTED_MORDENT }
data class OrnamentMark(val noteId: Int,val kind: OrnamentKind)
data class Measure(val number: Int, val start: Double, val length: Double, val label: String = number.toString())
data class TempoMark(val beat: Double, val bpm: Double)

data class Score(val title: String, val composer: String, val notes: List<ScoreNote>, val measures: List<Measure>,
    val tempo: Double = 80.0, val fifths: Int = 0, val beats: Int = 4, val beatType: Int = 4,
    val tempoMarks: List<TempoMark> = emptyList(), val expressiveTempo: Boolean = false,
    val graceNotes: List<GraceNote> = emptyList(), val ornaments: List<OrnamentMark> = emptyList()) {
    val endBeat get() = measures.lastOrNull()?.let { it.start + it.length } ?: 0.0
    fun tempoAt(beat: Double): Double = tempoMarks.lastOrNull { it.beat <= beat + 0.00001 }?.bpm ?: tempo
    /** Integrates explicit BPM changes between two score positions (quarter-note beats). */
    fun secondsBetween(start: Double, end: Double, scale: Double = 1.0): Double {
        if(end<=start)return 0.0
        var at=start;var bpm=tempoAt(at)*scale.coerceAtLeast(.01);var total=0.0
        for(mark in tempoMarks.filter {it.beat>start && it.beat<end}.sortedBy {it.beat}) {
            total+=(mark.beat-at)*60/bpm;at=mark.beat;bpm=mark.bpm*scale.coerceAtLeast(.01)
        }
        return total+(end-at)*60/bpm
    }
    /** Inverse of secondsBetween for a listening transport which follows discrete tempo markings. */
    fun beatAtSeconds(start: Double,seconds: Double,end: Double,scale: Double = 1.0): Double {
        var at=start;var remaining=seconds.coerceAtLeast(0.0);var bpm=tempoAt(at)*scale.coerceAtLeast(.01)
        for(mark in tempoMarks.filter {it.beat>start && it.beat<end}.sortedBy {it.beat}) {
            val length=(mark.beat-at)*60/bpm
            if(remaining<length)return (at+remaining*bpm/60).coerceAtMost(end)
            remaining-=length;at=mark.beat;bpm=mark.bpm*scale.coerceAtLeast(.01)
        }
        return (at+remaining*bpm/60).coerceAtMost(end)
    }
    fun selected(start: Double, end: Double, hand: Hand) = notes.filter {
        !it.rest && it.beat >= start - 0.0001 && it.beat < end - 0.0001 && (hand == Hand.BOTH || it.hand == hand)
    }
}
fun noteName(midi: Int): String = listOf("C", "C♯", "D", "E♭", "E", "F", "F♯", "G", "A♭", "A", "B♭", "B")[Math.floorMod(midi,12)] + (midi / 12 - 1)
fun beatNanos(beat: Double, bpm: Double): Long = (beat * 60_000_000_000.0 / bpm).toLong()
