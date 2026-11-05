package org.pianopath.core

import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class EarExercisesTest {
    @Test fun fullKeyboardClustersStayWithinSelectedBoundsAndHaveDistinctPitches() {
        for(voices in 1..3)for(seed in 0..25) {
            val pitches=earExercise(21,108,voices,triads=false,random=Random(seed))
            assertEquals(voices,pitches.size)
            assertTrue(pitches.all{it in 21..108})
        }
        assertEquals(setOf(21,22,23),earExercise(21,23,3,triads=false,random=Random(0)))
    }
    @Test fun triadsAreMajorOrMinorAndRemainInsideRange() {
        for(seed in 0..100) {
            val notes=earExercise(21,108,3,triads=true,random=Random(seed)).sorted()
            assertTrue(notes.all{it in 21..108})
            assertTrue(notes[1]-notes[0] in listOf(3,4))
            assertEquals(7,notes[2]-notes[0])
        }
    }
    @Test fun avoidsImmediateRepeatWhenAlternativesAreAvailable() {
        val previous=setOf(60,64,67)
        for(seed in 0..25)assertNotEquals(previous,earExercise(60,72,3,triads=true,previous=previous,random=Random(seed)))
        // A range with exactly one possible result must still terminate.
        assertEquals(setOf(60,61),earExercise(60,61,2,triads=false,previous=setOf(60,61),random=Random(0)))
    }
}
