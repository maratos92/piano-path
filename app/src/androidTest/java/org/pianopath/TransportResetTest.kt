package org.pianopath

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.pianopath.core.*

@RunWith(AndroidJUnit4::class)
class TransportResetTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()

    @Test fun inputResetStopsAnActiveScoredSession() {
        lateinit var vm: PianoViewModel
        compose.runOnUiThread {vm=ViewModelProvider(compose.activity)[PianoViewModel::class.java];vm.scale(0,ScaleKind.MAJOR,1,Hand.RIGHT,true)}
        compose.waitUntil(15_000){vm.ui.value.score!=null}
        compose.runOnUiThread {
            vm.edit{it.copy(mode=PracticeMode.WAIT,countIn=false,loop=false)}
            vm.start();assertTrue(vm.ui.value.running)
            vm.midi.simulate(MidiEvent.On(60,90,System.nanoTime()))
            requireNotNull(vm.midi.onInputReset).invoke()
        }
        compose.waitUntil(5_000){!vm.ui.value.running}
        assertTrue(vm.ui.value.feedback.contains("reset",ignoreCase=true))
        assertTrue(vm.playedSequence.value.isEmpty())
    }

    @Test fun inputResetClosesHeldRecordingNotes() {
        lateinit var vm: PianoViewModel
        compose.runOnUiThread {vm=ViewModelProvider(compose.activity)[PianoViewModel::class.java];vm.openRecorder()}
        compose.waitUntil(15_000){vm.recorder.state.value.ready}
        compose.runOnUiThread {vm.recordFreePlay();vm.midi.simulate(MidiEvent.On(60,90,System.nanoTime(),2))}
        compose.waitUntil(5_000){60 in vm.recorder.state.value.held}
        compose.runOnUiThread {requireNotNull(vm.midi.onInputReset).invoke()}
        assertTrue(vm.recorder.state.value.held.isEmpty())
        compose.runOnUiThread {vm.recorder.stop()}
        val take=requireNotNull(vm.recorder.state.value.current)
        try {
            assertTrue(take.events.any{it.kind==0 && it.a==60 && it.channel==2})
            val state=RecordedMidiState();take.events.forEach(state::accept)
            assertTrue(state.soundingPitches.isEmpty())
            compose.waitUntil(10_000){!vm.recorder.state.value.saving}
        } finally {
            compose.runOnUiThread {vm.recorder.delete(take.id)}
            compose.waitUntil(10_000){!vm.recorder.state.value.saving}
        }
    }
}
