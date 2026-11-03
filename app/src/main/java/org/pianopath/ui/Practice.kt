@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package org.pianopath.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.pianopath.*
import org.pianopath.core.*
import kotlin.math.*

@Composable fun Practice(vm: PianoViewModel) {
    val p by vm.ui.collectAsStateWithLifecycle()
    val midi by vm.midi.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var controls by remember {mutableStateOf(false)}
    var result by remember {mutableStateOf(false)}
    var keyZoom by androidx.compose.runtime.saveable.rememberSaveable {mutableFloatStateOf(1f)}
    var keyPan by androidx.compose.runtime.saveable.rememberSaveable {mutableFloatStateOf(0f)}
    val landscape=LocalConfiguration.current.screenWidthDp>LocalConfiguration.current.screenHeightDp
    val score=p.score
    if(score==null) {Column(Modifier.padding(24.dp)){PageTitle("Your practice space","Choose your music.");Button(onClick={vm.screen.value="Library"}){Text("Open library")}};return}
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(if(landscape)44.dp else 54.dp).padding(horizontal=6.dp),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick={vm.screen.value="Library"}){Icon(Icons.Outlined.ArrowBack,"Library")}
            Column(Modifier.weight(1f)) {
                Text(score.title,fontFamily=FontFamily.Serif,fontSize=if(landscape)18.sp else 22.sp,maxLines=1)
                Text("${p.bpm.toInt()} BPM · ${handName(p.hand)} · m. ${p.startMeasure}–${p.endMeasure} · ${if(midi.connected) "MIDI connected" else if(settings.simulator) "Touch" else "No MIDI"}",fontSize=11.sp,maxLines=1)
            }
            if(p.view==ScoreView.SHEET) {
                IconButton(onClick={vm.zoom(-.15)}){Icon(Icons.Outlined.ZoomOut,"Zoom out sheet")}
                IconButton(onClick={vm.zoom(.15)}){Icon(Icons.Outlined.ZoomIn,"Zoom in sheet")}
            }
            IconButton(onClick={controls=true}){Icon(Icons.Outlined.Tune,"Practice controls")}
        }
        Row(Modifier.fillMaxWidth().padding(horizontal=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            FilterChip(p.view==ScoreView.SHEET,{vm.setView(ScoreView.SHEET)},label={Text("Sheet",fontSize=12.sp)})
            FilterChip(p.view==ScoreView.FALLING,{vm.setView(ScoreView.FALLING)},label={Text("Falling notes",fontSize=12.sp)})
            Spacer(Modifier.weight(1f))
            TextButton(onClick={if(settings.simulator)vm.setSettings{it.copy(simulator=false)} else vm.enableTouch()},contentPadding=PaddingValues(horizontal=6.dp)) {
                Text(if(settings.simulator)"Touch piano ✓" else "Touch piano",fontSize=12.sp)
            }
        }
        KeyboardControls(keyZoom,keyPan,{keyZoom=it},{keyPan=it})
        Column(Modifier.weight(1f).fillMaxWidth()) {
            Box(Modifier.weight(1f).fillMaxWidth().background(Color(0xfffffefb))) {
                if(p.view==ScoreView.SHEET)Notation(p,Modifier.fillMaxSize()){vm.message.value="Notation: $it"}
                else FallingNotes(p,keyZoom,keyPan,Modifier.fillMaxSize())
                if(p.hidden)Box(Modifier.matchParentSize().background(Color(0xfffffefb))) {
                    Text("Score hidden · press Practice to begin",Modifier.align(Alignment.Center),color=Ink)
                }
                if(p.counting>0)Surface(Modifier.align(Alignment.Center),shape=RoundedCornerShape(20.dp),color=Pine.copy(alpha=.94f)) {
                    Text(p.counting.toString(),fontSize=48.sp,color=Color.White,modifier=Modifier.padding(horizontal=30.dp,vertical=8.dp))
                }
            }
            KeyboardCanvas(midi.held.keys,midi.sounding,if(p.hidden)emptyList() else p.expected,settings.simulator,vm::key,
                if(landscape)64 else 100,keyZoom,keyPan,p.playbackHeld)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal=10.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            Column(Modifier.weight(1f)) {
                Text(p.feedback,fontSize=12.sp,maxLines=1)
                if(!landscape)Text(if(midi.connected)"BLE MIDI connected" else if(settings.simulator)"Touch keys play sound · no piano needed" else "Use Listen, Touch piano, or connect MIDI",fontSize=10.sp,maxLines=1)
            }
            OutlinedButton(onClick={vm.listen()},contentPadding=PaddingValues(horizontal=10.dp,vertical=6.dp)) {
                Icon(if(p.listening)Icons.Outlined.Stop else Icons.Outlined.VolumeUp,null,Modifier.size(18.dp))
                Text(if(p.listening)"Stop music" else "Listen",fontSize=12.sp)
            }
            Button(onClick={if(p.running)vm.stop() else vm.start()},contentPadding=PaddingValues(horizontal=10.dp,vertical=6.dp)) {
                Icon(if(p.running)Icons.Outlined.Stop else Icons.Outlined.PlayArrow,null,Modifier.size(18.dp))
                Text(if(p.running)"Finish" else "Practice",fontSize=12.sp)
            }
        }
        if(p.result!=null)Row(Modifier.fillMaxWidth().height(30.dp).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick={result=true},contentPadding=PaddingValues(0.dp)){Text(if(p.running)"Loop ${p.loops.size} · ${p.result!!.overall.toInt()}%" else "View results · ${p.result!!.overall.toInt()}%",fontSize=11.sp)}
        }
    }
    if(controls)PracticeControls(vm,p){controls=false}
    if(result&&p.result!=null)ResultDialog(p.result!!,p.simulated){result=false}
}
fun modeName(m: PracticeMode)=when(m){PracticeMode.FREE->"Free play";PracticeMode.FOLLOW->"Follow score";PracticeMode.WAIT->"Wait mode";PracticeMode.RHYTHM->"Rhythm"}
fun handName(h: Hand)=when(h){Hand.LEFT->"Left hand";Hand.RIGHT->"Right hand";Hand.BOTH->"Both hands"}
@Composable private fun PracticeControls(vm: PianoViewModel,p: PracticeUi,close: ()->Unit) {
    val score=p.score ?: return
    var sectionName by remember{mutableStateOf("")}
    ModalBottomSheet(onDismissRequest=close) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=24.dp).padding(bottom=32.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("Shape your practice",fontFamily=FontFamily.Serif,fontSize=28.sp)
            if(p.running || p.listening)Text("Stop playback or finish practice to change these settings.",color=MaterialTheme.colorScheme.primary)
            Text("Mode",fontWeight=FontWeight.SemiBold)
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) { PracticeMode.entries.forEach { mode-> FilterChip(p.mode==mode,{vm.edit{it.copy(mode=mode)}},enabled=!(p.running || p.listening),label={Text(modeName(mode))}) } }
            Text(when(p.mode){PracticeMode.FREE->"Live MIDI display. No score or timing assessment.";PracticeMode.FOLLOW->"Start on the first expected note. The follower adapts gradually to your timing. Written order is followed; repeats are not unfolded.";PracticeMode.WAIT->"Complete each chord to advance. Pitch is assessed; rhythm and duration are not scored.";PracticeMode.RHYTHM->"Play on the metronome timeline. Early, late, missing notes and releases are assessed."},fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) { Hand.entries.forEach { h-> FilterChip(p.hand==h,{vm.edit{it.copy(hand=h)}},enabled=!(p.running || p.listening),label={Text(handName(h))}) } }
            Text("Tempo · ${p.bpm.toInt()} BPM",fontWeight=FontWeight.SemiBold)
            Slider(p.bpm.toFloat(),{v->vm.edit{it.copy(bpm=v.toDouble())}},valueRange=30f..240f,steps=209,enabled=!(p.running || p.listening))
            HorizontalDivider()
            Text("Passage",fontWeight=FontWeight.SemiBold)
            MeasureStepper("From measure",p.startMeasure,score.measures.size,!(p.running || p.listening)) { n -> vm.edit { val end=max(n,it.endMeasure);it.copy(startMeasure=n,endMeasure=end,startOffset=0.0,endOffset=score.measures[end-1].length) } }
            MeasureStepper("Through measure",p.endMeasure,score.measures.size,!(p.running || p.listening)) { n -> vm.edit { val start=min(n,it.startMeasure);it.copy(startMeasure=start,endMeasure=n,startOffset=0.0,endOffset=score.measures[n-1].length) } }
            Text("Start offset · ${"%.2f".format(p.startOffset)} quarter beats",fontSize=12.sp)
            Slider(p.startOffset.toFloat(),{v->vm.edit{it.copy(startOffset=v.toDouble())}},valueRange=0f..(score.measures[p.startMeasure-1].length-.25).coerceAtLeast(.25).toFloat(),enabled=!(p.running || p.listening))
            Text("End offset · ${"%.2f".format(p.endOffset)} quarter beats",fontSize=12.sp)
            Slider(p.endOffset.toFloat(),{v->vm.edit{it.copy(endOffset=v.toDouble())}},valueRange=.25f..score.measures[p.endMeasure-1].length.toFloat().coerceAtLeast(.26f),enabled=!(p.running || p.listening))
            Toggle("Loop selected passage",p.loop,{v->vm.edit{it.copy(loop=v)}},!(p.running || p.listening))
            Toggle("Count in each repetition",p.countIn,{v->vm.edit{it.copy(countIn=v)}},!(p.running || p.listening))
            Toggle("Metronome",p.metronome,{v->vm.edit{it.copy(metronome=v)}},!(p.running || p.listening))
            Toggle("Raise tempo by 4 BPM after 90%",p.autoTempo,{v->vm.edit{it.copy(autoTempo=v)}},!(p.running || p.listening))
            Toggle("Horizontal score layout",p.horizontal,{v->vm.edit{it.copy(horizontal=v)}},!(p.running || p.listening))
            OutlinedTextField(sectionName,{sectionName=it},label={Text("Passage name")},singleLine=true,modifier=Modifier.fillMaxWidth(),enabled=!(p.running || p.listening))
            OutlinedButton(onClick={vm.saveSection(sectionName)},enabled=!(p.running || p.listening)){Icon(Icons.Outlined.BookmarkAdd,null);Spacer(Modifier.width(8.dp));Text("Save passage")}
            p.sections.forEach { s->TextButton(onClick={vm.useSection(s)},enabled=!(p.running || p.listening)){Icon(Icons.Outlined.BookmarkBorder,null);Spacer(Modifier.width(8.dp));Text("${s.name} · ${s.bpm.toInt()} BPM")} }
            if(p.guidance.isNotBlank())Text(p.guidance,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick=close,modifier=Modifier.fillMaxWidth()){Text("Back to score")}
        }
    }
}
@Composable private fun MeasureStepper(label: String,value: Int,max: Int,enabled: Boolean,onChange: (Int)->Unit) {
    var typed by remember(value){mutableStateOf(value.toString())}
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Text(label,Modifier.weight(1f),fontSize=14.sp)
        IconButton(onClick={onChange((value-1).coerceAtLeast(1))},enabled=enabled&&value>1){Icon(Icons.Outlined.Remove,"Previous measure")}
        OutlinedTextField(typed,{s->typed=s.filter{it.isDigit()};typed.toIntOrNull()?.takeIf{it in 1..max}?.let(onChange)},Modifier.width(80.dp),singleLine=true,enabled=enabled)
        IconButton(onClick={onChange((value+1).coerceAtMost(max))},enabled=enabled&&value<max){Icon(Icons.Outlined.Add,"Next measure")}
        Text("/ $max",fontSize=12.sp)
    }
}
@Composable fun ResultDialog(r: SessionResult,simulated: Boolean,close: ()->Unit) {
    AlertDialog(onDismissRequest=close,confirmButton={TextButton(onClick=close){Text("Keep practicing")}},title={Text("Every note is progress",fontFamily=FontFamily.Serif)},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            if(simulated)Text("TOUCH / TEST INPUT",color=MaterialTheme.colorScheme.primary,fontSize=11.sp)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(14.dp)){Metric("${r.pitch.toInt()}%","pitch",Modifier.weight(1f));Metric(r.rhythm?.let{"${it.toInt()}%"}?:"—","rhythm",Modifier.weight(1f));Metric("${r.overall.toInt()}%","overall",Modifier.weight(1f))}
            Text("${r.correct} correct · ${r.missing} missing · ${r.extra} extra\nChords complete: ${r.completeChords} / ${r.totalChords}")
            Text("Left hand: ${r.leftPitch?.let{"${it.toInt()}%"}?:"—"}    Right hand: ${r.rightPitch?.let{"${it.toInt()}%"}?:"—"}")
            if(r.rhythm!=null) {
                Text("Mean onset deviation: ${r.meanOnsetMs?.toInt()?.let{"$it ms"}?:"—"}\nMean release deviation: ${r.meanDurationMs?.toInt()?.let{"$it ms"}?:"—"}\n${r.early} early · ${r.late} late\nOnset variation: ${r.tempoConsistency?.toInt()?.let{"$it ms"}?:"—"}")
                Text("Releases measure physical key-up. Pedal sustain is shown separately. Hand scores follow MusicXML staff assignments.",fontSize=12.sp)
            } else Text("Wait mode measures pitch completion without a tempo requirement.",fontSize=12.sp)
            HorizontalDivider()
            Text("Note detail",fontWeight=FontWeight.SemiBold)
            r.details.take(80).forEach { n -> Text("m.${n.note.measure}  ${noteName(n.note.midi)}  "+if(n.missing)"Missing" else if(r.rhythm==null)"Correct" else "onset ${n.onsetMs?.toInt() ?: 0} ms · release ${n.durationErrorMs?.toInt()?.let{"$it ms"} ?: "—"}",fontSize=12.sp) }
            if(r.details.size>80)Text("Showing the first 80 notes. Aggregate scores include all notes.",fontSize=11.sp)
        }
    })
}
