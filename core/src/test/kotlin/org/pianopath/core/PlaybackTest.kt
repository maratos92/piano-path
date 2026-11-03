package org.pianopath.core

import org.junit.Assert.*
import org.junit.Test

class PlaybackTest {
    private fun score(vararg notes: ScoreNote)=Score("Test","Piano Path",notes.toList(),listOf(Measure(1,0.0,4.0)))
    @Test fun fullKeyboardHas88Keys52WhiteAnd36Black() {
        assertEquals(88,PianoLayout.keys.size);assertEquals(52,PianoLayout.whiteKeys.size);assertEquals(36,PianoLayout.blackKeys.size)
        assertEquals(21,PianoLayout.keys.first().pitch);assertEquals(108,PianoLayout.keys.last().pitch)
        assertEquals(0.0,PianoLayout.keys.first().left,.001);assertEquals(52.0,PianoLayout.keys.last().let{it.left+it.width},.001)
    }
    @Test fun everyKeyCanBeHitWithoutScrollingInFitMode() {
        for(key in PianoLayout.keys)assertEquals(key.pitch,PianoLayout.hit(key.left+key.width/2,if(key.black).2 else .9))
    }
    @Test fun blackKeysTakePriorityOnlyInUpperPartOfKeyboard() {
        val sharp=PianoLayout.key(61)!!
        assertEquals(61,PianoLayout.hit(sharp.left+.1,.2));assertEquals(60,PianoLayout.hit(sharp.left+.1,.9))
        assertNull(PianoLayout.hit(-.1,.5));assertNull(PianoLayout.hit(52.0,.5));assertNull(PianoLayout.hit(10.0,1.1))
    }
    @Test fun rollAndTouchCoordinatesRemainAlignedAtEveryZoomAndPan() {
        for(zoom in listOf(1.0,2.5,6.0))for(pan in listOf(0.0,.5,1.0))for(key in PianoLayout.keys) {
            val unit=key.left+key.width/2
            assertEquals(unit,PianoLayout.unitX(PianoLayout.screenX(unit,zoom,pan),zoom,pan),.000001)
        }
    }
    @Test fun playbackClipsReleasesAndOrdersOffBeforeRepeatedOn() {
        val s=score(ScoreNote(0,60,0.0,1.0,1),ScoreNote(1,60,1.0,3.0,1),ScoreNote(2,64,3.0,1.0,1))
        val plan=PlaybackPlan.create(s,0.0,2.0)
        assertEquals(4,plan.size);assertFalse(plan[1].down);assertTrue(plan[2].down);assertEquals(2.0,plan.last().beat,.001)
    }
    @Test fun playbackHonorsPassageAndHandAndSeparatesTouchChannel() {
        val s=score(ScoreNote(0,48,1.0,1.0,1,2),ScoreNote(1,60,1.0,1.0,1,1),ScoreNote(2,50,3.0,1.0,1,2))
        val plan=PlaybackPlan.create(s,1.0,2.0,Hand.LEFT)
        assertEquals(listOf(48,48),plan.map{it.pitch});assertTrue(plan.all{it.channel==2})
    }
    @Test fun barsReachStrikeLineAtOnsetAndLeaveAtRelease() {
        val roll=PianoRoll(score(ScoreNote(0,60,2.0,1.0,1)),0.0,4.0)
        assertEquals(.75,roll.visible(0.0,8.0).single().bottom,.001)
        assertEquals(1.0,roll.visible(2.0,8.0).single().bottom,.001)
        assertEquals(.875,roll.visible(2.0,8.0).single().top,.001)
        assertTrue(roll.visible(3.0,8.0).isEmpty())
    }
    @Test fun longTiesRemainVisibleAndRestNotesDoNotMakeBars() {
        val roll=PianoRoll(score(ScoreNote(0,60,0.0,4.0,1),ScoreNote(1,0,1.0,1.0,1,rest=true)),0.0,4.0)
        assertEquals(1,roll.visible(3.0,2.0).size)
    }
    @Test fun barsExcludeUnselectedMeasuresAndHand() {
        val s=score(ScoreNote(0,48,0.0,4.0,1,2),ScoreNote(1,60,1.0,3.0,1,1),ScoreNote(2,62,3.0,1.0,1,1))
        val bars=PianoRoll(s,1.0,2.0,Hand.RIGHT).visible(1.0,8.0)
        assertEquals(listOf(60),bars.map{it.note.midi});assertEquals(.875,bars.single().top,.001)
    }
}
