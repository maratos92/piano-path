package org.pianopath.core

import org.junit.Assert.*
import org.junit.Test

class PerformanceTest {
    private val base=1_000_000_000L
    private fun at(ms: Int)=base+ms*1_000_000L
    private fun score(vararg notes: ScoreNote)=Score("Test","Test",notes.toList(),listOf(Measure(1,0.0,4.0)),120.0)
    private fun engine(s: Score,mode: PracticeMode=PracticeMode.RHYTHM,start: Double=0.0,end: Double=4.0,hand: Hand=Hand.BOTH,bpm: Double=120.0)=PerformanceEngine(s,PracticeConfig(mode,hand,bpm,toleranceMs=100.0,startBeat=start,endBeat=end)).also{it.start(base)}
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
    @Test fun expressivePauseDoesNotSkipUnplayedMeasures() {
        val e=engine(score(ScoreNote(0,60,0.0,1.0,1),ScoreNote(1,62,1.0,1.0,1),ScoreNote(2,64,2.0,1.0,1)),PracticeMode.FOLLOW)
        e.accept(MidiEvent.On(60,80,base));e.tick(at(20_000));assertEquals(1.0,e.currentBeat,.01);assertEquals(0,e.summary().missing)
        e.accept(MidiEvent.On(62,80,at(20_000)));assertEquals(2,e.summary().correct);assertEquals(2.0,e.currentBeat,.01)
    }
    @Test fun followInfersMissingNotesOnlyFromAdjacentMusicalEvidence() {
        val e=engine(score(ScoreNote(0,60,0.0,1.0,1),ScoreNote(1,62,1.0,1.0,1),ScoreNote(2,64,2.0,1.0,1)),PracticeMode.FOLLOW)
        e.accept(MidiEvent.On(60,80,base));e.accept(MidiEvent.On(64,80,at(1000)));assertEquals(1,e.summary().missing);assertEquals(2,e.summary().correct)
    }
    @Test fun followAnchorsFirstNoteAfterLeadingRestWithoutFalseEarlyError() {
        val e=engine(score(ScoreNote(0,60,2.0,1.0,1)),PracticeMode.FOLLOW)
        e.accept(MidiEvent.On(60,80,at(5000)));assertEquals(0.0,e.summary().details[0].onsetMs!!,.01)
    }
}
