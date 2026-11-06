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
class EarFeedbackTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()

    @Test fun oneNoteMidiAnswersSubmitAutomaticallyAndRevealAfterThreeMisses() {
        lateinit var vm: PianoViewModel
        var previousLimit=3
        var previousFirst=21;var previousLast=108
        compose.runOnUiThread {
            vm=ViewModelProvider(compose.activity)[PianoViewModel::class.java]
            previousLimit=vm.settings.value.earAttempts
            previousFirst=vm.settings.value.keyboardFirst;previousLast=vm.settings.value.keyboardLast
            vm.setSettings{it.copy(earAttempts=3,keyboardFirst=21,keyboardLast=108)}
            vm.screen.value="Ear training"
            vm.earQuestion(4,1,1)
        }
        try {
            compose.waitUntil(15_000){vm.ear.value.target.isNotEmpty() && !vm.earReferencePlaying.value}
            val target=vm.ear.value.target.single()
            val wrong=(60..72).first{it!=target}
            val initialAttempts=vm.ear.value.attempts
            repeat(3){i->
                compose.waitUntil(5_000){!vm.earReferencePlaying.value}
                compose.runOnUiThread {
                    val t=System.nanoTime()
                    vm.midi.simulate(MidiEvent.On(wrong,80,t))
                    vm.midi.simulate(MidiEvent.Off(wrong,0,t+10_000_000))
                }
                compose.waitUntil(5_000){vm.ear.value.attempts>=initialAttempts+i+1}
                assertEquals(i+1,vm.ear.value.questionAttempts)
                assertEquals(i==2,vm.ear.value.revealed)
                assertEquals(setOf(target),vm.ear.value.target)
            }
        } finally {
            compose.runOnUiThread {vm.setSettings{it.copy(earAttempts=previousLimit,keyboardFirst=previousFirst,keyboardLast=previousLast)}}
        }
    }

    @Test fun chordWaitsForAllDistinctPitchesBeforeSubmitting() {
        lateinit var vm: PianoViewModel
        var previousFirst=21;var previousLast=108
        compose.runOnUiThread {
            vm=ViewModelProvider(compose.activity)[PianoViewModel::class.java]
            previousFirst=vm.settings.value.keyboardFirst;previousLast=vm.settings.value.keyboardLast
            vm.setSettings{it.copy(keyboardFirst=21,keyboardLast=108)}
            vm.screen.value="Ear training"
            vm.earQuestion(4,1,2)
        }
        try {
            compose.waitUntil(15_000){vm.ear.value.target.size==2 && !vm.earReferencePlaying.value}
            val notes=vm.ear.value.target.sorted();val before=vm.ear.value.attempts
            compose.runOnUiThread {vm.midi.simulate(MidiEvent.On(notes[0],80,System.nanoTime()))}
            assertEquals(before,vm.ear.value.attempts);assertFalse(vm.ear.value.revealed)
            compose.runOnUiThread {vm.midi.simulate(MidiEvent.On(notes[1],80,System.nanoTime()))}
            compose.waitUntil(5_000){vm.ear.value.revealed}
            assertEquals(before+1,vm.ear.value.attempts)
            assertEquals(1,vm.ear.value.questionAttempts)
            compose.runOnUiThread {vm.midi.simulate(MidiEvent.Off(notes[0],0,System.nanoTime()));vm.midi.simulate(MidiEvent.Off(notes[1],0,System.nanoTime()))}
        } finally {compose.runOnUiThread {vm.setSettings{it.copy(keyboardFirst=previousFirst,keyboardLast=previousLast)}}}
    }
}
