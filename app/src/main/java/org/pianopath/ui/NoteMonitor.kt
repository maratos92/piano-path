package org.pianopath.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.pianopath.PianoViewModel
import org.pianopath.core.noteName
import org.pianopath.midi.MidiStatus
import kotlin.math.pow

@Composable fun LiveNotes(midi: MidiStatus,modifier: Modifier=Modifier) {
    Row(modifier, horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(midi.held.keys.sorted().joinToString("  "){noteName(it)}.ifEmpty{"No keys held"},Modifier.weight(1f),fontWeight=FontWeight.SemiBold,fontSize=14.sp,maxLines=2)
        midi.lastNote?.let { n -> Text("Last ${noteName(n)} · v${midi.lastVelocity} · ch ${midi.lastChannel+1}",fontSize=11.sp) }
    }
}
@Composable fun NoteMonitorPage(vm: PianoViewModel,midi: MidiStatus) {
    Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        PageTitle("See what you play","Live note monitor",midi.status)
        LiveNotes(midi)
        midi.lastNote?.let { n ->
            Text(noteName(n),fontSize=64.sp,fontWeight=FontWeight.Medium,color=MaterialTheme.colorScheme.primary)
            Text("MIDI $n · ${"%.1f".format(440*2.0.pow((n-69)/12.0))} Hz · velocity ${midi.lastVelocity}")
        }
        Text("Sustain ${if(midi.sustain)"down" else "up"} · ${midi.sounding.size} sounding notes")
        Text("MIDI tells us pitch, velocity and channel. Finger choice and physical hand cannot be measured.",fontSize=13.sp)
        TextButton(onClick={vm.screen.value="Diagnostics"}){Text("Detailed MIDI events")}
        TextButton(onClick={if(vm.ui.value.score!=null)vm.screen.value="Practice" else vm.playWithoutPiano()}){Text("Open practice / touch piano")}
    }
}
