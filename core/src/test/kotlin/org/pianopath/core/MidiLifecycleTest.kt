package org.pianopath.core

import org.junit.Assert.*
import org.junit.Test

class MidiLifecycleTest {
    private val start=1_000_000_000L
    private fun at(ms: Long)=start+ms*1_000_000
    private fun engine(): PerformanceEngine {
        val score=Score("Test","Test",listOf(ScoreNote(0,60,0.0,1.0,1)),listOf(Measure(1,0.0,4.0)),120.0)
        return PerformanceEngine(score,PracticeConfig(PracticeMode.RHYTHM,bpm=120.0,endBeat=4.0)).also{it.start(start)}
    }
    @Test fun channelResetDoesNotLoseAnotherChannelsPhysicalRelease() {
        for(controller in listOf(120,123)) {
            val e=engine()
            e.accept(MidiEvent.On(60,90,start,0))
            e.accept(MidiEvent.Control(controller,0,at(100),1))
            e.accept(MidiEvent.Off(60,0,at(500),0))
            val result=e.finish(at(600))
            assertEquals(0.0,requireNotNull(result.details.single().durationErrorMs),.001)
            assertEquals(1.0,requireNotNull(result.take.single().duration),.001)
        }
    }
    @Test fun channelResetClosesItsOwnPhysicallyHeldNotesAtResetTime() {
        for(controller in listOf(120,123)) {
            val e=engine()
            e.accept(MidiEvent.On(60,90,start,0))
            e.accept(MidiEvent.Control(controller,0,at(500),0))
            val result=e.finish(at(600))
            assertEquals(0.0,requireNotNull(result.details.single().durationErrorMs),.001)
            assertEquals(1.0,requireNotNull(result.take.single().duration),.001)
        }
    }
    @Test fun overlappingSamePitchStaysHeldUntilItsLastRelease() {
        val keyboard=KeyboardState()
        keyboard.accept(MidiEvent.On(60,50,start,0));keyboard.accept(MidiEvent.On(60,90,at(10),0))
        keyboard.accept(MidiEvent.Off(60,0,at(20),0))
        assertTrue(0 to 60 in keyboard.held);assertTrue(0 to 60 in keyboard.sounding)
        keyboard.accept(MidiEvent.Off(60,0,at(30),0))
        assertTrue(keyboard.held.isEmpty());assertTrue(keyboard.sounding.isEmpty())
    }
}
