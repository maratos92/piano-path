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
import org.pianopath.core.PlayedAttack

@Composable fun EarTraining(vm: PianoViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val ear by vm.ear.collectAsStateWithLifecycle()
    val midi by vm.midi.state.collectAsStateWithLifecycle()
    val playing by vm.earReferencePlaying.collectAsStateWithLifecycle()
    val pianoOutput by vm.earPianoOutput.collectAsStateWithLifecycle()
    var touchHeld by remember{mutableStateOf(emptySet<Int>())}
    var start by remember{mutableIntStateOf(ear.startOctave)}
    var span by remember{mutableIntStateOf(ear.octaves)}
    var voices by remember{mutableIntStateOf(ear.voices)}
    var full by remember{mutableStateOf(ear.fullKeyboard)}
    var triads by remember{mutableStateOf(ear.triads)}
    var setupExpanded by remember{mutableStateOf(ear.target.isEmpty())}
    val canStart=ear.target.isEmpty() || ear.revealed
    LaunchedEffect(ear.target){if(ear.target.isNotEmpty())setupExpanded=false;touchHeld=emptySet()}
    val columns=remember(ear.history,ear.guesses,ear.revealed,ear.target,settings.earAttempts) {
        fun notes(pitches: Set<Int>)=pitches.sorted().map{PlayedAttack(it,0,0L,750L)}
        buildList {
            ear.history.forEachIndexed {i,pitches->
                val correct=pitches==ear.target
                add(StaffColumn(notes(pitches),"Attempt ${i+1}",if(correct)"Correct" else "Incorrect",correct=correct))
            }
            // No answer pitches enter the staff model before correct/exhausted revelation.
            if(ear.revealed)add(StaffColumn(notes(ear.target),"Answer","●",correct=true))
            else if(ear.target.isNotEmpty())add(StaffColumn(notes(ear.guesses),"Attempt ${ear.history.size+1}","${ear.voices-ear.guesses.size} more note(s)"))
        }
    }
    val badges=remember(ear.history,ear.revealed,ear.target) {
        val values=linkedMapOf<Int,MutableList<String>>()
        ear.history.forEachIndexed{i,pitches->pitches.forEach{values.getOrPut(it){mutableListOf()}.add("${i+1}")}}
        if(ear.revealed)ear.target.forEach{values.getOrPut(it){mutableListOf()}.add("●")}
        values.mapValues{it.value.toList()}
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        PageTitle("Listen · answer · learn","Ear training")
        if(!setupExpanded)Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text("${ear.voices} note(s) · ${if(ear.fullKeyboard)"instrument range" else "${ear.octaves} octave(s)"}",style=MaterialTheme.typography.bodySmall)
            TextButton(onClick={setupExpanded=true},enabled=canStart && !playing){Text("Exercise setup")}
        }
        if(setupExpanded)Panel(Modifier.fillMaxWidth()) {
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
            Toggle("Hear reference on piano",pianoOutput,vm::setEarPianoOutput,midi.canSend && !playing)
            Text(if(midi.canSend)"Piano MIDI output available; check your instrument's receiving/sound settings." else "Phone sound · MIDI output requires a piano receive port.",style=MaterialTheme.typography.bodySmall)
        }
        Panel(Modifier.fillMaxWidth()) {
            Text(if(playing)"Listen first…" else ear.feedback,fontWeight=FontWeight.SemiBold)
            if(ear.target.isNotEmpty()) {
                Text("Attempt ${if(ear.revealed)ear.questionAttempts else (ear.questionAttempts+1).coerceAtMost(settings.earAttempts)} / ${settings.earAttempts} · answers check automatically")
                if(!ear.revealed)Text("Answer hidden · listen and play your answer")
                PlayedStaff(columns,bpm=80.0,followKey=ear.questionAttempts to ear.guesses,naming=settings.noteNaming)
                if(ear.revealed)Text("Answer: ${ear.target.sorted().joinToString{displayNoteName(it,settings.noteNaming)}}")
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick=vm::earReplay,enabled=!playing){Text("Replay")}
                    OutlinedButton(onClick=vm::earClear,enabled=!ear.revealed && !playing){Text("Clear")}
                }
            }
            Button(onClick={vm.earQuestion(start,span,voices,full,triads)},enabled=canStart && !playing){Text(if(ear.target.isEmpty())"Start game" else "Next question")}
        }
        if(ear.target.isNotEmpty()) {
            Text("Play ${ear.voices} distinct note${if(ear.voices==1)"" else "s"}. Numbered keys show your attempts; ● shows the revealed answer.",style=MaterialTheme.typography.bodySmall)
            PianoKeyboard(if(playing)emptySet() else ear.guesses+touchHeld+midi.held.keys,emptySet(),emptyList(),!playing && !ear.revealed,
                {pitch,down->touchHeld=if(down)touchHeld+pitch else touchHeld-pitch;if(down)vm.earGuess(pitch)},146,
                if(ear.fullKeyboard)settings.keyboardFirst..settings.keyboardLast else ((ear.startOctave+1)*12)..minOf(108,(ear.startOctave+ear.octaves+1)*12),
                filledInput=true,attemptBadges=badges)
        }
        Text("${ear.correct*10} points · ${ear.correct}/${ear.attempts} correct · accuracy ${if(ear.attempts==0)"—" else "${100*ear.correct/ear.attempts}%"}",style=MaterialTheme.typography.bodyMedium)
    }
}
