package org.pianopath.core

import org.junit.Assert.*
import org.junit.Test

class PlayedNotationTest {
    @Test fun simultaneousAttacksFormChordButRepeatedNotesRemainDistinct() {
        val c=PlayedAttack(60,0,0);val e=PlayedAttack(64,0,30_000_000)
        val repeatedC=PlayedAttack(60,0,40_000_000);val g=PlayedAttack(67,0,70_000_000)
        val groups=groupPlayedAttacks(listOf(c,e,repeatedC,g))
        assertEquals(listOf(listOf(c,e),listOf(repeatedC,g)),groups)
        assertEquals(listOf(c,e,repeatedC,g),groups.flatten())
    }
    @Test fun chordWindowIsMeasuredFromFirstAttackRatherThanSliding() {
        val a=PlayedAttack(60,0,0);val b=PlayedAttack(64,0,50_000_000);val c=PlayedAttack(67,0,100_000_000)
        assertEquals(listOf(listOf(a,b),listOf(c)),groupPlayedAttacks(listOf(a,b,c)))
    }
    @Test fun extremeKeyboardNotesFoldByWholeOctavesWithoutChangingPitchClass() {
        for(pitch in 21..108) {
            val written=pitchForNotation(pitch)
            assertTrue(written.writtenPitch in 36..84)
            assertEquals(pitch,written.writtenPitch+written.octaves*12)
        }
        assertEquals(0,pitchForNotation(60).octaves)
        assertTrue(pitchForNotation(21).octaves<0)
        assertTrue(pitchForNotation(108).octaves>0)
    }
    @Test fun ledgerLinesAppearOnAlternateStepsBeyondTheStaff() {
        assertTrue(ledgerStepsFromStaffBottom(0).isEmpty())
        assertTrue(ledgerStepsFromStaffBottom(-1).isEmpty())
        assertEquals(listOf(-2,-4),ledgerStepsFromStaffBottom(-5))
        assertTrue(ledgerStepsFromStaffBottom(9).isEmpty())
        assertEquals(listOf(10,12),ledgerStepsFromStaffBottom(12))
    }
}
