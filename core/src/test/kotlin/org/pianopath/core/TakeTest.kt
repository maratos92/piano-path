package org.pianopath.core

import org.junit.Assert.*
import org.junit.Test

class TakeTest {
    private val base=1_000_000_000L
    private fun time(ms: Int)=base+ms*1_000_000L
    private fun engine(mode: PracticeMode=PracticeMode.RHYTHM)=PerformanceEngine(Score("Take","Test",listOf(ScoreNote(0,60,0.0,1.0,1),ScoreNote(1,60,1.0,1.0,1)),listOf(Measure(1,0.0,4.0)),60.0),PracticeConfig(mode,bpm=60.0)).also{it.start(base)}
    @Test fun takeRetainsActualPitchVelocityTimingAndExtraNotes() {
        val e=engine();e.accept(MidiEvent.On(60,90,time(120),2));e.accept(MidiEvent.On(66,40,time(300),3));e.accept(MidiEvent.Off(60,0,time(720),2))
        val take=e.finish(time(1500)).take
        assertEquals(2,take.size);assertEquals(90,take[0].velocity);assertEquals(2,take[0].channel)
        assertEquals(.12,take[0].beat,.001);assertEquals(.6,take[0].duration!!,.001);assertEquals(0,take[0].expectedId)
        assertEquals(66,take[1].pitch);assertNull(take[1].expectedId);assertNull(take[1].duration)
    }
    @Test fun repeatedStrikesReleaseInOrderWithoutOverwritingEarlierTake() {
        val e=engine();e.accept(MidiEvent.On(60,70,time(0)));e.accept(MidiEvent.On(60,85,time(1000)))
        e.accept(MidiEvent.Off(60,0,time(1100)));e.accept(MidiEvent.Off(60,0,time(1300)))
        assertEquals(1.1,e.take[0].duration!!,.001);assertEquals(.3,e.take[1].duration!!,.001)
        assertEquals(listOf(0,1),e.take.map{it.expectedId})
    }
    @Test fun sustainDoesNotPretendThatFingerHeldTheKey() {
        val e=engine();e.accept(MidiEvent.Control(64,127,time(0)));e.accept(MidiEvent.On(60,80,time(0)));e.accept(MidiEvent.Off(60,0,time(250)))
        e.accept(MidiEvent.Control(64,0,time(2000)));assertEquals(.25,e.take.single().duration!!,.001)
    }
    @Test fun waitRecordsPitchWithoutInventingTimingAccuracy() {
        val e=engine(PracticeMode.WAIT);e.accept(MidiEvent.On(60,80,time(9000)))
        assertEquals(0.0,e.take.single().beat,.001);assertNull(e.take.single().onsetMs)
    }
    @Test fun freshLoopHasNoTakeOrHeldEventsFromPreviousLoop() {
        val first=engine();first.accept(MidiEvent.On(60,80,time(0)))
        val second=engine();assertTrue(second.take.isEmpty());second.accept(MidiEvent.Off(60,0,time(500)));assertTrue(second.take.isEmpty())
    }
}
