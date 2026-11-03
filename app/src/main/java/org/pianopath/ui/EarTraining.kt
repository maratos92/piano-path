package org.pianopath.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.pianopath.PianoViewModel
import org.pianopath.core.noteName

/** Pitch-only identification (not score playback or a graded timing test). */
@Composable fun EarTraining(vm: PianoViewModel) {
    val ear by vm.ear.collectAsStateWithLifecycle()
    var octaves by remember {mutableIntStateOf(1)}
    var voices by remember {mutableIntStateOf(1)}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement=Arrangement.spacedBy(14.dp)) {
        PageTitle("Ear training","Hear it. Find it.")
        Panel(Modifier.fillMaxWidth()) {
            Text("Octaves · C4–C${4+octaves}",fontWeight=FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                (1..3).forEach { n -> FilterChip(octaves==n,{octaves=n},modifier=Modifier.weight(1f),label={Text("$n octave${if(n>1)"s" else ""}",fontSize=12.sp)}) }
            }
            Text("Notes played together",fontWeight=FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                (1..3).forEach { n -> FilterChip(voices==n,{voices=n},modifier=Modifier.weight(1f),label={Text("$n note${if(n>1)"s" else ""}",fontSize=12.sp)}) }
            }
            Text("Questions use C4–C${4+octaves}",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick={vm.earQuestion(octaves,voices)},modifier=Modifier.fillMaxWidth()) {Text(if(ear.target.isEmpty())"Play first question" else "Next question")}
        }
        if(ear.target.isNotEmpty()) {
            Panel(Modifier.fillMaxWidth()) {
                Text("C4–C${4+ear.octaves} · ${ear.voices} note${if(ear.voices>1)"s" else ""}",fontWeight=FontWeight.SemiBold)
                Text(ear.feedback,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Guesses selected: ${ear.guesses.size} / ${ear.voices}",fontSize=12.sp)
                if(ear.revealed) {
                    Text("Answer: ${ear.target.sorted().joinToString { noteName(it) }}",fontWeight=FontWeight.SemiBold)
                    Text("Your notes: ${ear.guesses.sorted().joinToString { noteName(it) }}")
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick={vm.earReplay()},modifier=Modifier.weight(1f)){Text("Hear again")}
                    OutlinedButton(onClick={vm.earClear()},modifier=Modifier.weight(1f),enabled=!ear.revealed){Text("Clear")}
                    Button(onClick={vm.earCheck()},modifier=Modifier.weight(1f),enabled=!ear.revealed && ear.guesses.isNotEmpty()){Text("Check")}
                }
            }
            Text("Tap the keys or play your connected piano.",fontSize=12.sp)
            PianoKeyboard(held=emptySet(),sounding=emptySet(),expected=ear.guesses.toList(),
                interactive=true,onKey={pitch,down -> if(down) vm.earGuess(pitch)},height=156,focusRange=60..(60+ear.octaves*12))
        }
        if(ear.attempts>0) Text("Questions correct: ${ear.correct} / ${ear.attempts}",fontWeight=FontWeight.SemiBold)
    }
}
