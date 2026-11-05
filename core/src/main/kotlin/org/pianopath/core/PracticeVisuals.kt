package org.pianopath.core

/** Visual preview only: never routes generated/reference notes into MIDI input or assessment. */
fun expectedAt(score: Score,beat: Double,hand: Hand=Hand.BOTH): List<Int> =
    if(!beat.isFinite())emptyList() else score.notes.filter { !it.rest && (hand==Hand.BOTH || it.hand==hand) && it.beat<=beat+0.000001 && it.beat+it.duration>beat+0.000001 }.map{it.midi}.distinct()
fun loopBreakMillis(seconds: Int): Long = seconds.coerceIn(0,5)*1000L
