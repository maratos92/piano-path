package org.pianopath.core

import org.junit.Assert.*
import org.junit.Test

class PracticeMetricsTest {
    @Test fun extraNotesAndMissingNotesCountAgainstTheRightPercentages() {
        val score=Score("Test","Test",listOf(
            ScoreNote(0,60,0.0,1.0,1),ScoreNote(1,62,1.0,1.0,1),ScoreNote(2,64,2.0,1.0,1)),
            listOf(Measure(1,0.0,4.0)),120.0)
        val start=1_000_000_000L
        val engine=PerformanceEngine(score,PracticeConfig(PracticeMode.RHYTHM,bpm=120.0,toleranceMs=100.0,endBeat=4.0))
        engine.start(start)
        engine.accept(MidiEvent.On(60,80,start));engine.accept(MidiEvent.On(70,80,start+300_000_000))
        engine.accept(MidiEvent.On(62,80,start+600_000_000)) // Exactly 100 ms late is within tolerance.
        val result=engine.finish(start+2_000_000_000)
        assertEquals(2,result.correct);assertEquals(1,result.missing);assertEquals(1,result.extra)
        val metrics=PracticeMetrics.from(result,PracticeMode.RHYTHM,100.0)
        assertEquals(50.0,requireNotNull(metrics.accuracy),.001)
        assertEquals(200.0/3,requireNotNull(metrics.onTime),.001)
        assertEquals(2,metrics.onTimeCorrect)
        assertNull(PracticeMetrics.from(result,PracticeMode.WAIT,100.0).onTime)
        assertNull(PracticeMetrics.from(result,PracticeMode.FREE,100.0).onTime)
    }
}
