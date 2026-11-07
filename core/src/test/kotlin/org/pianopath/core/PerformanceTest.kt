package org.pianopath.core

import org.junit.Assert.*
import org.junit.Test

class PerformanceTest {
    private val base=1_000_000_000L
    private fun at(ms: Int)=base+ms*1_000_000L
    private fun score(vararg notes: ScoreNote)=Score("Test","Test",notes.toList(),listOf(Measure(1,0.0,4.0)),120.0)
    private fun engine(s: Score,mode: PracticeMode=PracticeMode.RHYTHM,start: Double=0.0,end: Double=4.0,hand: Hand=Hand.BOTH,bpm: Double=120.0)=PerformanceEngine(s,PracticeConfig(mode,hand,bpm,toleranceMs=100.0,startBeat=start,endBeat=end)).also{it.start(base)}
    @Test fun pauseDoesNotTurnElapsedSilenceIntoMissedNotes() {
        val e=engine(score(ScoreNote(0,60,0.0,1.0,1),ScoreNote(1,62,1.0,1.0,1)))
        e.accept(MidiEvent.On(60,80,base))
        e.pause(at(150))
        e.accept(MidiEvent.On(62,80,at(800))) // ignored during pause
        e.tick(at(800))
        assertEquals(0,e.summary().missing)
        e.resume(at(1150))
        assertEquals(1.0,e.clockBeat(at(1500)),.01)
        e.accept(MidiEvent.On(62,80,at(1500)))
        val result=e.finish(at(1550))
        assertEquals(2,result.correct)
        assertEquals(0.0,result.meanOnsetMs!!,.01)
    }
    @Test fun heldNotesAndRecordedExtrasExcludeMultiplePauses() {
        val e=engine(score(ScoreNote(0,60,0.0,1.0,1),ScoreNote(1,62,1.0,1.0,1)))
        e.accept(MidiEvent.On(60,80,base))
        e.accept(MidiEvent.On(61,60,at(100)))
        e.pause(at(150))
        assertEquals(.3,e.clockBeat(at(1100)),.001)
        e.resume(at(1150))
        e.pause(at(1250))
        assertEquals(.5,e.clockBeat(at(3200)),.001)
        e.resume(at(3250))
        e.accept(MidiEvent.Off(60,0,at(3500)))
        e.accept(MidiEvent.Off(61,0,at(3500)))
        e.accept(MidiEvent.On(62,80,at(3500)))
        e.accept(MidiEvent.Off(62,0,at(4000)))
        val result=e.finish(at(4100))
        assertEquals(0.0,result.meanDurationMs!!,.001)
        assertEquals(0.0,result.meanOnsetMs!!,.001)
        assertEquals(1.0,result.take.first {it.pitch==60}.duration!!,.001)
        assertEquals(.8,result.take.first {it.pitch==61}.duration!!,.001)
    }
    @Test fun releaseDuringPauseClosesTheNoteBeforeARepeatedStrike() {
        val e=engine(score(ScoreNote(0,60,0.0,1.0,1),ScoreNote(1,60,1.0,1.0,1)))
        e.accept(MidiEvent.On(60,80,base))
        e.pause(at(500))
        e.accept(MidiEvent.Off(60,0,at(900)))
        e.accept(MidiEvent.On(60,80,at(1000))) // an unscored paused attack
        e.accept(MidiEvent.Off(60,0,at(1100)))
        assertEquals(1.0,e.take.single().duration!!,.001)
        assertEquals(0.0,e.summary().details.first().durationErrorMs!!,.001)
        e.resume(at(1500))
        e.accept(MidiEvent.On(60,80,at(1500)))
        e.accept(MidiEvent.Off(60,0,at(2000)))
        val result=e.finish(at(2100))
        assertEquals(2,result.correct)
        assertEquals(0,result.extra)
        assertEquals(100.0,result.rhythm!!,.001)
        assertEquals(listOf(1.0,1.0),result.take.map{it.duration})
    }
    @Test fun releaseDuringPauseUsesFrozenTimeEvenWhenFinishingPaused() {
        val e=engine(score(ScoreNote(0,60,0.0,1.0,1)))
        e.accept(MidiEvent.On(60,80,base))
        e.pause(at(250))
        e.accept(MidiEvent.Off(60,0,at(4000)))
        val result=e.finish(at(5000))
        assertEquals(-250.0,result.details.single().durationErrorMs!!,.001)
        assertEquals(.5,result.take.single().duration!!,.001)
    }
    @Test fun simultaneousChordIsMatchedIndividually() {
        val e=engine(score(ScoreNote(0,60,0.0,1.0,1),ScoreNote(1,64,0.0,1.0,1),ScoreNote(2,67,0.0,1.0,1)))
        listOf(60,64,67).forEach{e.accept(MidiEvent.On(it,80,base))};listOf(60,64,67).forEach{e.accept(MidiEvent.Off(it,0,at(500)))}
        val r=e.finish(at(600));assertEquals(100.0,r.pitch,.01);assertEquals(100.0,r.rhythm!!,.01);assertEquals(1,r.completeChords)
    }
    @Test fun arpeggiatedChordAllowsSpread() {
        val e=engine(score(ScoreNote(0,60,0.0,1.0,1),ScoreNote(1,64,0.0,1.0,1),ScoreNote(2,67,0.0,1.0,1)))
        listOf(60,64,67).forEachIndexed{i,p->e.accept(MidiEvent.On(p,80,at(i*60)))}
        val r=e.finish(at(650));assertEquals(3,r.correct);assertEquals(0,r.extra);assertEquals(60.0,r.meanOnsetMs!!,.01)
    }
    @Test fun missingAndExtraReducePitch() {
        val e=engine(score(ScoreNote(0,60,0.0,1.0,1),ScoreNote(1,64,0.0,1.0,1)))
        e.accept(MidiEvent.On(60,80,base));e.accept(MidiEvent.On(61,80,base));val r=e.finish(at(800))
        assertEquals(1,r.missing);assertEquals(1,r.extra);assertEquals(100.0/3,r.pitch,.01);assertEquals(0,r.completeChords)
    }
    @Test fun repeatedPitchUsesDistinctOnsetsAndReleases() {
        val e=engine(score(ScoreNote(0,60,0.0,1.0,1),ScoreNote(1,60,1.0,1.0,1)))
        e.accept(MidiEvent.On(60,80,base));e.accept(MidiEvent.Off(60,0,at(480)));e.accept(MidiEvent.On(60,72,at(510)));e.accept(MidiEvent.Off(60,0,at(1000)))
        val r=e.finish(at(1200));assertEquals(2,r.correct);assertEquals(-20.0,r.details[0].durationErrorMs!!,.01);assertEquals(-10.0,r.details[1].durationErrorMs!!,.01)
    }
    @Test fun earlyLateAndReleaseErrorsAreMeasuredInMilliseconds() {
        val e=engine(score(ScoreNote(0,60,1.0,1.0,1),ScoreNote(1,62,2.0,1.0,1)))
        e.accept(MidiEvent.On(60,80,at(350)));e.accept(MidiEvent.Off(60,0,at(600)));e.accept(MidiEvent.On(62,80,at(1200)));e.accept(MidiEvent.Off(62,0,at(1800)))
        val r=e.finish(at(2000));assertEquals(1,r.early);assertEquals(1,r.late);assertEquals(175.0,r.meanOnsetMs!!,.01);assertEquals(-250.0,r.details[0].durationErrorMs!!,.01)
    }
    @Test fun waitModeHasNoRhythmScoreAndRequiresCompleteChord() {
        val e=engine(score(ScoreNote(0,60,0.0,1.0,1),ScoreNote(1,64,0.0,1.0,1),ScoreNote(2,67,1.0,1.0,1)),PracticeMode.WAIT)
        e.accept(MidiEvent.On(60,80,at(4000)));assertEquals(0.0,e.currentBeat,.01)
        e.accept(MidiEvent.On(64,80,at(8000)));assertEquals(1.0,e.currentBeat,.01)
        e.accept(MidiEvent.On(67,80,at(9000)));assertNull(e.finish(at(9500)).rhythm)
    }
    @Test fun loopBoundsExcludeEndAndResetState() {
        val s=score(ScoreNote(0,60,0.0,1.0,1),ScoreNote(1,62,1.0,1.0,1),ScoreNote(2,64,2.0,1.0,1))
        val e=engine(s,start=1.0,end=2.0);assertEquals(listOf(62),e.results.map{it.note.midi});e.accept(MidiEvent.On(62,80,base));assertEquals(1,e.summary().correct)
        val next=engine(s,start=1.0,end=2.0);assertEquals(0,next.summary().correct);assertEquals(1.0,next.currentBeat,.01)
    }
    @Test fun slowerTempoChangesExpectedOnset() {
        val e=engine(score(ScoreNote(0,60,1.0,1.0,1)),bpm=60.0);e.accept(MidiEvent.On(60,80,at(1000)));e.accept(MidiEvent.Off(60,0,at(2000)));val r=e.finish(at(2100));assertEquals(0.0,r.meanOnsetMs!!,.01);assertEquals(0.0,r.meanDurationMs!!,.01)
    }
    @Test fun leftHandSelectionUsesStaff() {
        val e=engine(score(ScoreNote(0,60,0.0,1.0,1,1),ScoreNote(1,48,0.0,1.0,1,2)),hand=Hand.LEFT);assertEquals(listOf(48),e.results.map{it.note.midi})
    }
    @Test fun followAnchorsOnlyOnExpectedPitchAndDoesNotSkipFarAhead() {
        val e=engine(score(ScoreNote(0,60,0.0,1.0,1),ScoreNote(1,62,1.0,1.0,1),ScoreNote(2,64,2.0,1.0,1)),PracticeMode.FOLLOW)
        e.accept(MidiEvent.On(64,80,at(3000)));assertFalse(e.hasAnchor);assertEquals(0.0,e.currentBeat,.01)
        e.accept(MidiEvent.On(60,80,at(4000)));assertTrue(e.hasAnchor);assertEquals(0.0,e.summary().details[0].onsetMs!!,.01)
    }
    @Test fun noReleaseIsPenalizedInsteadOfIgnored() {
        val e=engine(score(ScoreNote(0,60,0.0,1.0,1)));e.accept(MidiEvent.On(60,80,base));val r=e.finish(at(800));assertNotNull(r.meanDurationMs);assertTrue(r.rhythm!!<100)
    }
    @Test fun restsCountExtraEvents() {
        val e=engine(score(ScoreNote(0,60,2.0,1.0,1)));e.accept(MidiEvent.On(60,80,base));e.accept(MidiEvent.On(60,80,at(1000)));assertEquals(1,e.summary().extra);assertEquals(1,e.summary().correct)
    }
    @Test fun latencyCompensationIsApplied() {
        val s=score(ScoreNote(0,60,0.0,1.0,1));val e=PerformanceEngine(s,PracticeConfig(mode=PracticeMode.RHYTHM,bpm=120.0,latencyMs=50.0));e.start(base);e.accept(MidiEvent.On(60,80,at(50)));assertEquals(0.0,e.summary().details[0].onsetMs!!,.01)
    }
    @Test fun sustainDoesNotReplacePhysicalReleaseTiming() {
        val e=engine(score(ScoreNote(0,60,0.0,1.0,1)));e.accept(MidiEvent.On(60,80,base));e.accept(MidiEvent.Control(64,127,at(100)));e.accept(MidiEvent.Off(60,0,at(500)));e.accept(MidiEvent.Control(64,0,at(2000)));assertEquals(0.0,e.finish(at(2100)).meanDurationMs!!,.01)
    }
    @Test fun anchoredFollowExpiresMissingNotesAndFinishesWithoutAFinalAttack() {
        val e=engine(score(ScoreNote(0,60,0.0,1.0,1),ScoreNote(1,62,1.0,1.0,1),ScoreNote(2,64,3.75,.25,1)),PracticeMode.FOLLOW)
        e.accept(MidiEvent.On(60,80,base))
        e.tick(at(951))
        assertEquals(setOf(1),e.missedIds)
        assertEquals(3.75,e.currentBeat,.001)
        e.tick(at(2000))
        assertEquals(4.0,e.clockBeat(at(2000)),.001)
        // The selected end is authoritative even if the last note is inside its grace window.
        val result=e.finish(at(2000))
        assertEquals(2,result.missing)
        assertEquals(setOf(1,2),e.missedIds)
        assertEquals(1,result.correct)
    }
    @Test fun timedPassageEndRejectsNewAttacksButAcceptsFinalReleases() {
        for(mode in listOf(PracticeMode.RHYTHM,PracticeMode.FOLLOW)) {
            val e=engine(score(ScoreNote(0,60,0.0,1.0,1)),mode,end=1.0)
            e.accept(MidiEvent.On(60,80,base))
            e.tick(at(500))
            e.accept(MidiEvent.On(61,80,at(500)))
            e.accept(MidiEvent.On(62,80,at(550)))
            e.accept(MidiEvent.On(60,0,at(500))) // velocity-zero attack is still a release
            val result=e.finish(at(550))
            assertEquals(0,result.extra)
            assertEquals(1,result.take.size)
            assertEquals(0.0,result.details.single().durationErrorMs!!,.001)
            assertEquals(1.0,result.take.single().duration!!,.001)
        }
    }
    @Test fun followRunsAcrossRestsWithoutStartingFarFutureNotesEarly() {
        val s=score(ScoreNote(0,60,0.0,.5,1),ScoreNote(1,64,6.0,1.0,2)).copy(measures=listOf(Measure(1,0.0,4.0),Measure(2,4.0,4.0)))
        val e=engine(s,PracticeMode.FOLLOW,end=8.0)
        e.accept(MidiEvent.On(60,80,base))
        e.tick(at(1500))
        assertEquals(3.0,e.clockBeat(at(1500)),.001)
        assertEquals(0,e.summary().missing)
        e.accept(MidiEvent.On(64,80,at(1500)))
        assertEquals(1,e.summary().extra)
        e.accept(MidiEvent.On(64,80,at(3000)))
        assertEquals(2,e.summary().correct)
        assertTrue(e.finished)
    }
    @Test fun explicitFollowPauseDoesNotExpireOrSkipUnplayedMeasures() {
        val e=engine(score(ScoreNote(0,60,0.0,1.0,1),ScoreNote(1,62,1.0,1.0,1),ScoreNote(2,64,2.0,1.0,1)),PracticeMode.FOLLOW)
        e.accept(MidiEvent.On(60,80,base))
        e.pause(at(100))
        e.tick(at(20_100))
        e.resume(at(20_100))
        e.accept(MidiEvent.On(62,80,at(20_500)))
        assertEquals(2,e.summary().correct)
        assertEquals(0,e.summary().missing)
        assertEquals(2.0,e.currentBeat,.001)
    }
    @Test fun followPhaseCorrectedPlayedOnsetsShareTheCursorClock() {
        val e=engine(score(ScoreNote(0,60,0.0,1.0,1),ScoreNote(1,62,1.0,1.0,1),ScoreNote(2,64,2.0,1.0,1)),PracticeMode.FOLLOW)
        e.accept(MidiEvent.On(60,80,base))
        e.accept(MidiEvent.On(61,80,at(400)))
        assertEquals(e.clockBeat(at(400)),e.take.last().beat,.001)
        e.accept(MidiEvent.On(62,80,at(700)))
        assertEquals(200.0,e.summary().details[1].onsetMs!!,.001)
        assertEquals(e.clockBeat(at(700)),e.take.last().beat,.001)
        assertEquals(1.26,e.take.last().beat,.001)
        e.accept(MidiEvent.On(64,80,at(1200)))
        assertEquals(e.clockBeat(at(1200)),e.take.last().beat,.001)
        assertTrue(e.clockBeat(at(1200))>=2.0)
    }
    @Test fun followOverlappingRepeatedPitchesKeepDistinctMatchesAndKeyDurations() {
        val e=engine(score(ScoreNote(0,60,0.0,2.0,1),ScoreNote(1,60,1.0,1.0,1),ScoreNote(2,64,1.0,1.0,1),ScoreNote(3,67,2.0,1.0,1)),PracticeMode.FOLLOW)
        e.accept(MidiEvent.On(60,80,base))
        e.accept(MidiEvent.On(60,75,at(500)))
        e.accept(MidiEvent.On(64,85,at(550)))
        e.accept(MidiEvent.Off(60,0,at(1000)))
        e.accept(MidiEvent.On(67,85,at(1000)))
        e.accept(MidiEvent.Off(60,0,at(1100)))
        e.accept(MidiEvent.Off(64,0,at(1100)))
        val result=e.finish(at(2000))
        assertEquals(4,result.correct)
        assertEquals(0,result.extra)
        assertEquals(listOf(0,1,2,3),result.take.map {it.expectedId})
        assertEquals(2.0,result.take[0].duration!!,.001)
        assertEquals(1.2,result.take[1].duration!!,.001)
        assertEquals(1,result.completeChords)
    }
    @Test fun followMatchesAdjacentNoteAfterMissingGroupExpires() {
        val e=engine(score(ScoreNote(0,60,0.0,1.0,1),ScoreNote(1,62,1.0,1.0,1),ScoreNote(2,64,2.0,1.0,1)),PracticeMode.FOLLOW)
        e.accept(MidiEvent.On(60,80,base));e.accept(MidiEvent.On(64,80,at(1000)));assertEquals(1,e.summary().missing);assertEquals(2,e.summary().correct)
    }
    @Test fun followAnchorsFirstNoteAfterLeadingRestWithoutFalseEarlyError() {
        val e=engine(score(ScoreNote(0,60,2.0,1.0,1)),PracticeMode.FOLLOW)
        e.accept(MidiEvent.On(60,80,at(5000)));assertEquals(0.0,e.summary().details[0].onsetMs!!,.01)
    }
    @Test fun waitRecognizesExpectedNotesAfterLongRunsOfErrorsWithBoundedLiveSnapshots() {
        val e=engine(score(ScoreNote(0,60,0.0,1.0,1),ScoreNote(1,64,0.0,1.0,1),ScoreNote(2,67,1.0,1.0,1)),PracticeMode.WAIT)
        val initialMatches=e.matchedIds
        repeat(10_000) {i->
            e.accept(MidiEvent.On(61,80,at(i*2)))
            e.accept(MidiEvent.Off(61,0,at(i*2+1)))
            assertEquals(listOf(60,64),e.expectedPitches)
            assertTrue(e.liveTake().size<=256)
        }
        assertSame(initialMatches,e.matchedIds)
        assertSame(e.liveTake(),e.liveTake())
        e.accept(MidiEvent.On(60,80,at(20_001)))
        assertEquals(setOf(0),e.matchedIds)
        assertEquals(listOf(64),e.expectedPitches)
        e.accept(MidiEvent.On(64,80,at(20_002)))
        assertEquals(1.0,e.currentBeat,.001)
        e.accept(MidiEvent.On(67,80,at(20_003)))
        assertTrue(e.finished)
        val result=e.finish(at(20_004))
        assertEquals(3,result.correct)
        assertEquals(10_000,result.extra)
        assertEquals(10_003,result.take.size)
        assertEquals(listOf(0,1,2),result.take.takeLast(3).map{it.expectedId})
    }
    @Test fun liveSnapshotsRemainImmutableAndRefreshAfterReleaseAndMissingNotes() {
        val e=engine(score(ScoreNote(0,60,0.0,1.0,1),ScoreNote(1,62,1.0,1.0,1)))
        e.accept(MidiEvent.On(60,80,base))
        val held=e.liveTake()
        val matched=e.matchedIds
        e.accept(MidiEvent.Off(60,0,at(500)))
        assertNull(held.single().duration)
        assertEquals(1.0,e.liveTake().single().duration!!,.001)
        assertSame(e.liveTake(),e.liveTake())
        assertSame(matched,e.matchedIds)
        val missed=e.missedIds
        e.tick(at(1200))
        assertTrue(missed.isEmpty())
        assertEquals(setOf(1),e.missedIds)
        assertSame(e.missedIds,e.missedIds)
    }

}
