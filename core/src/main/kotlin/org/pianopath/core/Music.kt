package org.pianopath.core

import kotlin.math.*

enum class Hand { LEFT, RIGHT, BOTH }
enum class PracticeMode { FREE, FOLLOW, WAIT, RHYTHM }
data class ScoreNote(val id: Int, val midi: Int, val beat: Double, val duration: Double,
    val measure: Int, val staff: Int = 1, val voice: String = "1", val finger: String = "",
    val rest: Boolean = false) {
    val hand get() = if (staff > 1) Hand.LEFT else Hand.RIGHT
}
data class Measure(val number: Int, val start: Double, val length: Double, val label: String = number.toString())
data class Score(val title: String, val composer: String, val notes: List<ScoreNote>, val measures: List<Measure>,
    val tempo: Double = 80.0, val fifths: Int = 0, val beats: Int = 4, val beatType: Int = 4) {
    val endBeat get() = measures.lastOrNull()?.let { it.start + it.length } ?: 0.0
    fun selected(start: Double, end: Double, hand: Hand) = notes.filter {
        !it.rest && it.beat >= start - 0.0001 && it.beat < end - 0.0001 && (hand == Hand.BOTH || it.hand == hand)
    }
}
fun noteName(midi: Int): String = listOf("C", "C♯", "D", "E♭", "E", "F", "F♯", "G", "A♭", "A", "B♭", "B")[Math.floorMod(midi,12)] + (midi / 12 - 1)
fun beatNanos(beat: Double, bpm: Double): Long = (beat * 60_000_000_000.0 / bpm).toLong()
