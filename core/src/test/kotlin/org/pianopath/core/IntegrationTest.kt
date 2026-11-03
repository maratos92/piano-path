package org.pianopath.core

import org.junit.Assert.*
import org.junit.Test

class IntegrationTest {
    private fun replay(score: Score,start: Double=0.0,end: Double=score.endBeat): SessionResult {
        val origin=2_000_000_000L
        val engine=PerformanceEngine(score,PracticeConfig(mode=PracticeMode.RHYTHM,bpm=72.0,startBeat=start,endBeat=end))
        engine.start(origin)
        val events=score.selected(start,end,Hand.BOTH).flatMap { n ->
            listOf(MidiEvent.On(n.midi,80,origin+beatNanos(n.beat-start,72.0)),MidiEvent.Off(n.midi,0,origin+beatNanos(minOf(n.beat+n.duration,end)-start,72.0)))
        }.sortedWith(compareBy({it.timestamp},{if(it is MidiEvent.Off)0 else 1}))
        events.forEach(engine::accept)
        return engine.finish(origin+beatNanos(end-start,72.0)+600_000_000)
    }
    @Test fun completeScaleXmlMidiReplayScoresPerfectly() {
        val s=MusicXml.parse(Exercises.scale(octaves=2).xml);val r=replay(s)
        assertEquals(100.0,r.pitch,.001);assertEquals(100.0,r.rhythm!!,.001);assertEquals(r.totalChords,r.completeChords)
    }
    @Test fun sightReadingXmlWithRestsAndChordsReplaysCorrectly() {
        val s=MusicXml.parse(Exercises.sight(level=3,seed=8713).xml);val r=replay(s)
        assertEquals(100.0,r.pitch,.001);assertEquals(100.0,r.rhythm!!,.001);assertEquals(0,r.missing)
    }
    @Test fun partialBeatLoopWithClippedReleasesResetsForNextRepetition() {
        val s=MusicXml.parse(Exercises.sight(level=2,seed=32).xml)
        val a=replay(s,1.5,5.75);val b=replay(s,1.5,5.75)
        assertEquals(100.0,a.overall,.001);assertEquals(a.overall,b.overall,.001);assertEquals(a.correct,b.correct)
    }
}
