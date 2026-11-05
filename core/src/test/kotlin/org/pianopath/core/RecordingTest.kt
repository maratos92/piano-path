package org.pianopath.core

import org.junit.Test
import org.junit.Assert.*
import java.time.*

class RecordingTest {
    private val id="00000000-0000-0000-0000-000000000001"
    @Test fun recorderPreservesChordsVelocityChannelsReleasesAndPedal() {
        val r=MidiCapture();r.begin(id,"Take",0,1_000_000_000)
        r.accept(MidiEvent.On(60,34,1_000_000_000,0));r.accept(MidiEvent.On(64,100,1_000_000_000,2))
        r.accept(MidiEvent.Control(64,127,1_100_000_000,2));r.accept(MidiEvent.Off(64,12,1_200_000_000,2))
        val take=r.finish(2_000_000_000)
        assertEquals(2,take.events.count{it.kind==0});assertEquals(listOf(34,100),take.events.filter{it.kind==0}.map{it.b})
        assertTrue(take.events.any{it.kind==1 && it.b==12 && it.channel==2})
        val state=RecordedMidiState();take.events.forEach(state::accept);assertTrue(state.soundingPitches.isEmpty())
        assertEquals(200_000L,recordingKeys(take).first{it.pitch==64}.endUs)
    }
    @Test fun repeatedNotesReleaseFifoAndHeldStateIsPrimedAtRecordStart() {
        val r=MidiCapture();r.accept(MidiEvent.Control(64,127,0,3));r.accept(MidiEvent.On(60,50,0,3))
        r.begin(id,"Start held",0,1_000_000);r.accept(MidiEvent.On(60,90,2_000_000,3))
        r.accept(MidiEvent.Off(60,0,3_000_000,3));r.accept(MidiEvent.Off(60,0,4_000_000,3))
        val keys=recordingKeys(r.finish(5_000_000));assertEquals(listOf(2000L,3000L),keys.map{it.endUs})
        assertEquals(listOf(50,90),keys.map{it.velocity})
    }
    @Test fun seekRestoresReleasedPedalledNotesAndDoesNotDuplicateBoundaryAttacks() {
        val data=MidiRecording(id,"Seek",0,4_000_000,listOf(RecordedMidi(0,2,2,64,127),RecordedMidi(0,0,2,60,77),
            RecordedMidi(1_000_000,1,2,60,0),RecordedMidi(2_000_000,0,0,67,90),RecordedMidi(3_000_000,2,2,64,0)))
        val replay=MidiReplay(data);val restore=replay.seek(2_000_000)
        assertTrue(restore.any{it.kind==0 && it.a==60 && it.b==77})
        assertFalse(restore.any{it.kind==0 && it.a==67})
        assertEquals(1,replay.advance(2_000_000).count{it.kind==0 && it.a==67})
        assertTrue(replay.advance(2_000_000).isEmpty())
        replay.advance(3_000_000);assertEquals(setOf(67),replay.state.soundingPitches)
    }
    @Test fun interruptedDraftCanBeClosedWithoutLeavingNotesOrPedalStuck() {
        val r=MidiCapture();r.begin(id,"Draft",0,0);r.accept(MidiEvent.Control(64,127,1));r.accept(MidiEvent.On(60,90,2))
        val draft=r.snapshot(100_000_000);assertFalse(draft.finished)
        val recovered=closeRecording(draft);val state=RecordedMidiState();recovered.events.forEach(state::accept)
        assertTrue(recovered.finished);assertTrue(state.soundingPitches.isEmpty())
    }
    @Test fun badTimestampsAndOversizedDurationsAreRejected() {
        val data=MidiRecording(id,"Bad",0,10,listOf(RecordedMidi(9,0,0,60,90),RecordedMidi(8,1,0,60,0)))
        assertThrows(IllegalArgumentException::class.java){data.validate()}
        assertThrows(IllegalArgumentException::class.java){data.copy(events=emptyList(),durationUs=Long.MAX_VALUE).validate()}
    }
    @Test fun activeClockExcludesCountInPauseAndWallClockChanges() {
        val c=PracticeClock();c.begin(1_000_000_000);assertEquals(0,c.millis(500_000_000))
        c.pause(1_500_000_000);assertEquals(500,c.millis(9_000_000_000));c.resume(9_500_000_000)
        assertEquals(1000,c.millis(10_000_000_000))
    }
    @Test fun progressDoesNotMixTemposModesHandsOrInputAndQueueUsesLatestTrial() {
        fun entry(date: Long,pitch: Double)=PracticeEntry(id+date,date,"Scale","Scales",scoreKey="c",bpm=80.0,pitch=pitch,overall=pitch,expected=8,missing=1,reviewFile="a")
        val a=entry(1,40.0);val b=entry(2,100.0).copy(missing=0)
        val other=b.copy(id="different",date=3,bpm=100.0,pitch=50.0,overall=50.0)
        assertEquals(2,PracticeInsights.trends(listOf(a,b,other)).size)
        assertEquals(listOf(other),PracticeInsights.queue(listOf(a,b,other)))
        assertEquals(4,PracticeInsights.trends(listOf(a,other,a.copy(mode="WAIT"),a.copy(source="TOUCH"))).size)
    }
    @Test fun dailyProgressUsesLocalDatesAcrossDstAndIgnoresOtherDays() {
        val zone=ZoneId.of("Europe/London");val now=Instant.parse("2026-10-25T23:45:00Z").toEpochMilli()
        val x=PracticeEntry(id,now,"Ear","Ear training",activeMs=1200)
        // The autumn transition creates a 25-hour local day: 24 hours earlier
        // is still the same date, while 25 hours earlier is the prior date.
        assertEquals(2400,PracticeInsights.activeToday(listOf(x,x.copy(date=now-86_400_000),x.copy(date=now-90_000_000)),now,zone))
    }
    @Test fun pedalAndControllerResetAreIsolatedPerChannel() {
        val state=RecordedMidiState()
        state.accept(RecordedMidi(0,2,0,64,127));state.accept(RecordedMidi(0,2,1,64,127))
        state.accept(RecordedMidi(1,0,0,60,80));state.accept(RecordedMidi(1,0,1,64,90))
        state.accept(RecordedMidi(2,1,0,60,0));state.accept(RecordedMidi(2,1,1,64,0))
        state.accept(RecordedMidi(3,2,0,121,0))
        assertEquals(setOf(64),state.soundingPitches)
        state.accept(RecordedMidi(4,2,1,64,0))
        assertTrue(state.soundingPitches.isEmpty())
    }
    @Test fun outOfOrderInputIsRecordedMonotonicallyAndClosesAtLastEvent() {
        val r=MidiCapture();r.begin(id,"Clock",0,1_000_000_000)
        r.accept(MidiEvent.On(60,80,1_200_000_000));r.accept(MidiEvent.Off(60,0,1_100_000_000))
        val take=r.finish(1_050_000_000)
        assertEquals(listOf(200_000L,200_000L),take.events.take(2).map{it.atUs})
        assertEquals(200_000L,take.durationUs)
        take.validate()
    }
    @Test fun pedalLatchedBeforeRecordingIsRestoredAndReleasedByPedalUp() {
        val r=MidiCapture();r.accept(MidiEvent.Control(64,127,0,2))
        r.accept(MidiEvent.On(62,73,1,2));r.accept(MidiEvent.Off(62,0,2,2))
        r.begin(id,"Latched",0,1_000_000);r.accept(MidiEvent.Control(64,0,2_000_000,2))
        val take=r.finish(3_000_000);val state=RecordedMidiState()
        take.events.takeWhile{it.atUs==0L}.forEach(state::accept)
        assertEquals(setOf(62),state.soundingPitches);assertTrue(state.heldPitches.isEmpty())
        take.events.dropWhile{it.atUs==0L}.forEach(state::accept)
        assertTrue(state.soundingPitches.isEmpty())
    }
    @Test fun seekRestoresTheRightVoiceWhenSamePitchIsLatchedAndHeld() {
        val take=MidiRecording(id,"Repeated",0,10,listOf(
            RecordedMidi(0,2,0,64,127),RecordedMidi(1,0,0,60,40),RecordedMidi(2,1,0,60,0),
            RecordedMidi(3,0,0,60,90)))
        val restored=RecordedMidiState()
        MidiReplay(take).seek(4).forEach(restored::accept)
        assertEquals(setOf(60),restored.heldPitches)
        // Releasing the physical key must leave the original latched voice, not the held one.
        restored.accept(RecordedMidi(5,2,0,64,0))
        assertEquals(listOf(90),restored.restore(5).filter{it.kind==0}.map{it.b})
    }
    @Test fun comparisonsRequireMatchingPassageSettingsAndScoringVersion() {
        val entry=PracticeEntry(id,1,"Piece","Pieces",scoreKey="piece",pitch=80.0)
        val variants=listOf(entry,entry.copy(startBeat=1.0),entry.copy(endBeat=4.0),entry.copy(hand="LEFT"),
            entry.copy(tolerance=100.0),entry.copy(latency=10.0),entry.copy(scoringVersion=2))
        assertEquals(variants.size,PracticeInsights.trends(variants).size)
    }
    @Test fun idleInputFloodIsBoundedAndDisconnectClearsHeldState() {
        val capture=MidiCapture()
        repeat(4096){assertTrue(capture.accept(MidiEvent.On(60,80,it.toLong())))}
        assertFalse(capture.accept(MidiEvent.On(60,80,4096)))
        capture.accept(MidiEvent.Control(64,127,5000));capture.accept(MidiEvent.On(62,75,5001))
        capture.resetLive()
        capture.begin(id,"After disconnect",0,6000)
        assertTrue(capture.finish(7000).events.isEmpty())
    }
}
