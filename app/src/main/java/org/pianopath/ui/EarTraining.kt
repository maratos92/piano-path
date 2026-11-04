package org.pianopath.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Star
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

@Composable fun EarTraining(vm: PianoViewModel) {
    val ear by vm.ear.collectAsStateWithLifecycle()
    var start by remember {mutableIntStateOf(4)}
    var span by remember {mutableIntStateOf(1)}
    var voices by remember {mutableIntStateOf(1)}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement=Arrangement.spacedBy(14.dp)) {
        PageTitle("Practice","Ear training")
        Panel(Modifier.fillMaxWidth()) {
            Text("Start octave",fontWeight=FontWeight.SemiBold)
            (1..7).chunked(4).forEach {row ->
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    row.forEach { n ->
                        FilterChip(start==n,{start=n;span=span.coerceAtMost(minOf(3,8-n))},
                            modifier=Modifier.weight(1f),label={Text("C$n")})
                    }
                }
            }
            HorizontalDivider()
            Text("Range · C$start–C${start+span}",fontWeight=FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                (1..minOf(3,8-start)).forEach {n->
                    FilterChip(span==n,{span=n},modifier=Modifier.weight(1f),label={Text("$n octave${if(n>1)"s" else ""}",fontSize=12.sp)})
                }
            }
            HorizontalDivider()
            Text("Play together",fontWeight=FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                (1..3).forEach {n->
                    FilterChip(voices==n,{voices=n},modifier=Modifier.weight(1f),label={Text("$n note${if(n>1)"s" else ""}",fontSize=12.sp)})
                }
            }
            Button(onClick={vm.earQuestion(start,span,voices)},modifier=Modifier.fillMaxWidth(),
                enabled=ear.target.isEmpty() || ear.revealed) {
                Text(if(ear.target.isEmpty())"Start game" else "Next question")
            }
        }
        Panel(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Icon(Icons.Outlined.Star,"Score",tint=MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("${ear.correct*10} points",fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
                Text("${ear.correct}/${ear.attempts} correct · ${ear.streak} streak",fontSize=12.sp)
            }
            Text("Accuracy · ${if(ear.attempts==0)"—" else "${(100.0*ear.correct/ear.attempts).toInt()}%"}",fontSize=12.sp)
            LinearProgressIndicator(progress={ (ear.attempts%10)/10f },modifier=Modifier.fillMaxWidth())
            Text("${ear.attempts%10}/10 questions in this round · best streak ${ear.bestStreak}",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if(ear.target.isNotEmpty()) {
            Panel(Modifier.fillMaxWidth()) {
                Text("Question · C${ear.startOctave}–C${ear.startOctave+ear.octaves} · ${ear.voices} note${if(ear.voices>1)"s" else ""}",fontWeight=FontWeight.SemiBold)
                Text(ear.feedback,fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${ear.guesses.size} selected",fontSize=12.sp)
                if(ear.revealed) {
                    Text("Answer: ${ear.target.sorted().joinToString {noteName(it)}}",fontWeight=FontWeight.SemiBold)
                    Text("Your notes: ${ear.guesses.sorted().joinToString {noteName(it)}}",fontSize=12.sp)
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick=vm::earReplay,modifier=Modifier.weight(1f)){Text("Replay")}
                    OutlinedButton(onClick=vm::earClear,modifier=Modifier.weight(1f),enabled=!ear.revealed){Text("Clear")}
                    Button(onClick=vm::earCheck,modifier=Modifier.weight(1f),enabled=!ear.revealed && ear.guesses.isNotEmpty()){Text("Check")}
                }
            }
            Text("Tap the highlighted register or play your MIDI piano.",fontSize=12.sp)
            PianoKeyboard(held=emptySet(),sounding=emptySet(),expected=ear.guesses.toList(),
                interactive=true,onKey={pitch,down->if(down)vm.earGuess(pitch)},height=146,
                focusRange=((ear.startOctave+1)*12)..minOf(108,(ear.startOctave+ear.octaves+1)*12))
        }
    }
}
