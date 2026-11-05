package org.pianopath.core

import org.junit.Assert.*
import org.junit.Test

class PracticeVisualsTest {
    @Test fun loopBreakRespectsConfiguredSecondsAndSafeLimits() {
        assertEquals(0L,loopBreakMillis(0));assertEquals(2000L,loopBreakMillis(2))
        assertEquals(5000L,loopBreakMillis(5));assertEquals(0L,loopBreakMillis(-10))
        assertEquals(5000L,loopBreakMillis(Int.MAX_VALUE))
    }
    @Test fun cursorShowsHeldAndNewNotesOnlyForSelectedHand() {
        val score=Score("Cursor","Test",listOf(
            ScoreNote(0,60,0.0,2.0,1,1),
            ScoreNote(1,64,1.0,1.0,1,1),
            ScoreNote(2,48,0.0,3.0,1,2),
            ScoreNote(3,0,2.0,1.0,1,1,rest=true),
            ScoreNote(4,67,3.0,1.0,1,1)),listOf(Measure(1,0.0,4.0)))
        assertEquals(setOf(60,64),expectedAt(score,1.0,Hand.RIGHT).toSet())
        assertEquals(setOf(60,64,48),expectedAt(score,1.5,Hand.BOTH).toSet())
        assertEquals(listOf(48),expectedAt(score,2.5,Hand.LEFT))
        assertTrue(expectedAt(score,2.5,Hand.RIGHT).isEmpty())
        assertEquals(listOf(67),expectedAt(score,3.0,Hand.RIGHT))
        assertTrue(expectedAt(score,4.0,Hand.BOTH).isEmpty())
    }
}
