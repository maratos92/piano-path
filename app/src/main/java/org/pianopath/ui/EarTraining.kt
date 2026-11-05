package org.pianopath.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.pianopath.PianoViewModel
import org.pianopath.core.noteName

@Composable fun EarTraining(vm: PianoViewModel) {
    val ear by vm.ear.collectAsStateWithLifecycle()
    val midi by vm.midi.state.collectAsStateWithLifecycle()
    val playing by vm.earReferencePlaying.collectAsStateWithLifecycle()
    val pianoOutput by vm.earPianoOutput.collectAsStateWithLifecycle()
    var start by remember {mutableIntStateOf(4)}
    var span by remember {mutableIntStateOf(1)}
    var voices by remember {mutableIntStateOf(1)}
    var full by remember {mutableStateOf(false)}
    var triads by remember {mutableStateOf(true)}
    val canStart=ear.target.isEmpty() || ear.revealed
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        PageTitle("Listen · answer · learn","Ear training")
        Panel(Modifier.widthIn(max=680.dp).fillMaxWidth()) {
            Toggle("Full keyboard · A0–C8",full,{full=it},canStart)
            if(!full) {
                Text("Start octave",fontWeight=FontWeight.SemiBold)
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    (1..7).forEach{n->FilterChip(start==n,{start=n;span=span.coerceAtMost(8-n)},enabled=canStart,label={Text("C$n")})}
                }
                Text("Range · C$start–C${start+span}")
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    (1..8-start).forEach{n->FilterChip(span==n,{span=n},enabled=canStart,label={Text("$n octave${if(n>1)"s" else ""}")})}
                }
            }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                (1..3).forEach{n->FilterChip(voices==n,{voices=n},enabled=canStart,label={Text(when(n){1->"Single note";2->"Two notes";else->"Three notes"})})}
            }
            if(voices==3)Toggle("Major / minor triads",triads,{triads=it},canStart)
            Text(if(voices==3 && triads)"Listen for a root-position major or minor chord, then find its notes." else "Single pitches build pitch memory; pairs train simultaneous listening. Random three-note clusters are an advanced option.",style=MaterialTheme.typography.bodySmall)
            Toggle("Hear reference on piano",pianoOutput,vm::setEarPianoOutput,midi.canSend && !playing)
            Text(if(midi.canSend)"Piano MIDI output available; test your instrument's receiving/sound settings." else "Phone sound · connect a piano with a MIDI receive port to try instrument playback.",style=MaterialTheme.typography.bodySmall)
        }
        Panel(Modifier.widthIn(max=680.dp).fillMaxWidth()) {
            Text(if(playing)"Listen first…" else ear.feedback,fontWeight=FontWeight.SemiBold)
            if(ear.target.isNotEmpty()) {
                Text("${ear.guesses.size} selected")
                if(ear.revealed) {
                    Text("Answer: ${ear.target.sorted().joinToString{noteName(it)}}")
                    Text("Your notes: ${ear.guesses.sorted().joinToString{noteName(it)}}")
                }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick=vm::earReplay,enabled=!playing){Text("Replay")}
                    OutlinedButton(onClick=vm::earClear,enabled=!ear.revealed && !playing){Text("Clear")}
                    Button(onClick=vm::earCheck,enabled=!ear.revealed && !playing && ear.guesses.isNotEmpty()){Text("Check")}
                }
            }
            Button(onClick={vm.earQuestion(start,span,voices,full,triads)},enabled=canStart && !playing) {Text(if(ear.target.isEmpty())"Start game" else "Next question")}
        }
        Text("${ear.correct*10} points · ${ear.correct}/${ear.attempts} correct · accuracy ${if(ear.attempts==0)"—" else "${100*ear.correct/ear.attempts}%"}",style=MaterialTheme.typography.bodyMedium)
        if(ear.target.isNotEmpty()) {
            Text("Tap the keyboard or play your MIDI piano after the reference ends.",style=MaterialTheme.typography.bodySmall)
            PianoKeyboard(emptySet(),emptySet(),ear.guesses.toList(),!playing,{pitch,down->if(down)vm.earGuess(pitch)},146,
                if(ear.fullKeyboard)21..108 else ((ear.startOctave+1)*12)..minOf(108,(ear.startOctave+ear.octaves+1)*12))
        }
    }
}
