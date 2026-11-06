package org.pianopath

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.pianopath.core.MidiEvent

@RunWith(AndroidJUnit4::class)
class KeyboardProfileTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()

    @Test fun profilePersistsAndOutsideRangeMidiStillRecordsAtItsOriginalPitch() {
        lateinit var vm: PianoViewModel
        var previousFirst=21;var previousLast=108
        compose.runOnUiThread {
            vm=ViewModelProvider(compose.activity)[PianoViewModel::class.java]
            previousFirst=vm.settings.value.keyboardFirst;previousLast=vm.settings.value.keyboardLast
            vm.setSettings{it.copy(keyboardFirst=48,keyboardLast=72)}
            vm.openRecorder()
        }
        var id: String?=null
        try {
            compose.waitUntil(15_000){vm.recorder.state.value.ready}
            compose.runOnUiThread {vm.recordFreePlay();vm.midi.simulate(MidiEvent.On(36,81,System.nanoTime(),2))}
            compose.waitUntil(5_000){36 in vm.recorder.state.value.held}
            compose.runOnUiThread {vm.midi.simulate(MidiEvent.Off(36,0,System.nanoTime(),2))}
            compose.waitUntil(5_000){36 !in vm.recorder.state.value.held}
            compose.runOnUiThread {vm.recorder.stop()}
            id=requireNotNull(vm.recorder.state.value.current).id
            compose.waitUntil(10_000){!vm.recorder.state.value.saving}
            assertTrue(vm.recorder.state.value.current!!.events.any{it.kind==0 && it.a==36 && it.channel==2})
            compose.runOnUiThread {vm.recorder.delete(id!!)}
            compose.waitUntil(10_000){!vm.recorder.state.value.saving}
            id=null
            compose.runOnUiThread {
                compose.activity.viewModelStore.clear()
                vm=ViewModelProvider(compose.activity)[PianoViewModel::class.java]
            }
            assertEquals(48,vm.settings.value.keyboardFirst);assertEquals(72,vm.settings.value.keyboardLast)
        } finally {
            id?.let {savedId ->
                compose.waitUntil(10_000){!vm.recorder.state.value.saving}
                compose.runOnUiThread {vm.recorder.delete(savedId)}
                compose.waitUntil(10_000){!vm.recorder.state.value.saving}
            }
            compose.runOnUiThread {vm.setSettings{it.copy(keyboardFirst=previousFirst,keyboardLast=previousLast)}}
        }
    }
}
