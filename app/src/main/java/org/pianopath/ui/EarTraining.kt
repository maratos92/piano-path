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
import org.pianopath.PracticeUi
import org.pianopath.core.*

@Composable fun EarTraining(vm: PianoViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var touchHeld by remember{mutableStateOf(emptySet<Int>())}
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
            Toggle("Instrument range · ${displayNoteName(settings.keyboardFirst,settings.noteNaming)}–${displayNoteName(settings.keyboardLast,settings.noteNaming)}",full,{full=it},canStart)
            if(!full) {
                Text("Start octave",fontWeight=FontWeight.SemiBold)
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    (1..7).forEach{n->FilterChip(start==n,{start=n;span=span.coerceAtMost(8-n)},enabled=canStart,label={Text(displayNoteName((n+1)*12,settings.noteNaming))})}
                }
                Text("Range · ${displayNoteName((start+1)*12,settings.noteNaming)}–${displayNoteName((start+span+1)*12,settings.noteNaming)}")
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
                Text("Attempt ${if(ear.revealed)ear.questionAttempts else (ear.questionAttempts+1).coerceAtMost(settings.earAttempts)} / ${settings.earAttempts} · answers check automatically")
                if(!ear.revealed)Surface(Modifier.fillMaxWidth().height(120.dp),color=MaterialTheme.colorScheme.surfaceContainerLow){Box(contentAlignment=androidx.compose.ui.Alignment.Center){Text("Score hidden · listen and play your answer")}}
                if(ear.revealed) {
                    Text("Answer: ${ear.target.sorted().joinToString{displayNoteName(it,settings.noteNaming)}}")
                    Text("Your notes: ${ear.guesses.sorted().joinToString{displayNoteName(it,settings.noteNaming)}}")
                    val answer=remember(ear.target) {
                        val notes=ear.target.sorted().mapIndexed {index,pitch->ScoreNote(index,pitch,0.0,1.0,1,if(pitch>=60)1 else 2)}
                        val score=Score("Answer","Piano Path",notes,listOf(Measure(1,0.0,1.0)),80.0)
                        PracticeUi(score=score,xml=Exercises.write(score),endMeasure=1,endOffset=1.0)
                    }
                    Notation(answer,Modifier.fillMaxWidth().height(160.dp),animate=!settings.reduceMotion)
                }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick=vm::earReplay,enabled=!playing){Text("Replay")}
                    OutlinedButton(onClick=vm::earClear,enabled=!ear.revealed && !playing){Text("Clear")}
                }
            }
            Button(onClick={vm.earQuestion(start,span,voices,full,triads)},enabled=canStart && !playing) {Text(if(ear.target.isEmpty())"Start game" else "Next question")}
        }
        Text("${ear.correct*10} points · ${ear.correct}/${ear.attempts} correct · accuracy ${if(ear.attempts==0)"—" else "${100*ear.correct/ear.attempts}%"}",style=MaterialTheme.typography.bodyMedium)
        if(ear.target.isNotEmpty()) {
            Text("Tap or play ${ear.voices} distinct note${if(ear.voices==1)"" else "s"} after the reference ends. Each complete answer checks automatically.",style=MaterialTheme.typography.bodySmall)
            PianoKeyboard(if(playing)emptySet() else ear.guesses+touchHeld+midi.held.keys,emptySet(),emptyList(),!playing && !ear.revealed,{pitch,down->touchHeld=if(down)touchHeld+pitch else touchHeld-pitch;if(down)vm.earGuess(pitch)},146,
                if(ear.fullKeyboard)settings.keyboardFirst..settings.keyboardLast else ((ear.startOctave+1)*12)..minOf(108,(ear.startOctave+ear.octaves+1)*12),filledInput=true)
        }
    }
}
