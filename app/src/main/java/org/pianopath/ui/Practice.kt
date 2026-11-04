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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.pianopath.*
import org.pianopath.core.*
import org.pianopath.audio.PianoSound
import kotlin.math.*

@Composable fun Practice(vm: PianoViewModel,openMenu: ()->Unit = {}) {
    val p by vm.ui.collectAsStateWithLifecycle()
    val midi by vm.midi.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var monitor by androidx.compose.runtime.saveable.rememberSaveable {mutableStateOf(false)}
    var controls by remember {mutableStateOf(false)}
    var result by remember {mutableStateOf(false)}
    var scorePopup by remember {mutableStateOf(false)}
    var scoreFraction by androidx.compose.runtime.saveable.rememberSaveable {mutableFloatStateOf(.55f)}
    var keyZoom by androidx.compose.runtime.saveable.rememberSaveable {mutableFloatStateOf(1f)}
    var keyPan by androidx.compose.runtime.saveable.rememberSaveable {mutableFloatStateOf(0f)}
    val landscape=LocalConfiguration.current.screenWidthDp>LocalConfiguration.current.screenHeightDp
    val score=p.score
    if(score==null) {Column(Modifier.padding(24.dp)){PageTitle("Your practice space","Choose your music.");Button(onClick={vm.screen.value="Library"}){Text("Open library")}};return}
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(if(landscape)44.dp else 54.dp).padding(horizontal=6.dp),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=openMenu){Icon(Icons.Outlined.Menu,"Open navigation menu")}
            Column(Modifier.weight(1f)) {
                Text(score.title,fontFamily=FontFamily.Serif,fontSize=if(landscape)18.sp else 22.sp,maxLines=1)
                Text("${p.bpm.toInt()} BPM · ${handName(p.hand)} · m. ${p.startMeasure}–${p.endMeasure} · ${if(midi.connected) "MIDI connected" else if(settings.simulator) "Touch" else "No MIDI"}",fontSize=11.sp,maxLines=1)
            }
            if(p.view!=ScoreView.FALLING) {
                IconButton(onClick={vm.zoom(-.15)}){Icon(Icons.Outlined.ZoomOut,"Zoom out sheet")}
                IconButton(onClick={vm.zoom(.15)}){Icon(Icons.Outlined.ZoomIn,"Zoom in sheet")}
            }
            IconButton(onClick={controls=true}){Icon(Icons.Outlined.Tune,"Practice controls")}
        }
        Row(Modifier.fillMaxWidth().padding(horizontal=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            FilterChip(p.view==ScoreView.SHEET,{vm.setView(ScoreView.SHEET)},label={Text("Sheet",fontSize=12.sp)})
            FilterChip(p.view==ScoreView.FALLING,{vm.setView(ScoreView.FALLING)},label={Text("Falling",fontSize=12.sp)})
            FilterChip(p.view==ScoreView.COMBINED,{vm.setView(ScoreView.COMBINED)},label={Text("Both",fontSize=12.sp)})
            Spacer(Modifier.weight(1f))
            IconButton(onClick={scorePopup=true},modifier=Modifier.size(36.dp)) {Icon(Icons.Outlined.OpenInNew,"Floating score window",Modifier.size(18.dp))}
        }
        Row(Modifier.fillMaxWidth().height(40.dp).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
            FilterChip(monitor,{monitor=!monitor},label={Text("Played notes",fontSize=12.sp)})
            Spacer(Modifier.weight(1f))
            TextButton(onClick={if(settings.simulator)vm.setSettings{it.copy(simulator=false)} else vm.enableTouch()},contentPadding=PaddingValues(horizontal=6.dp)) {
                Text(if(settings.simulator)"Touch piano ✓" else "Touch piano",fontSize=12.sp)
            }
        }
        if(monitor)LiveNotes(midi,Modifier.fillMaxWidth().padding(horizontal=12.dp))
        if(p.view==ScoreView.COMBINED)Row(Modifier.fillMaxWidth().height(30.dp).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("Score height",fontSize=11.sp)
            Slider(scoreFraction,{scoreFraction=it},Modifier.weight(1f).padding(horizontal=10.dp),valueRange=.25f..0.75f)
        }
        if(p.review && !p.running && !p.listening) {
            Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
                Text("Visual review",fontSize=11.sp)
                Slider(p.reviewBeat.toFloat(),{vm.seekReview(it.toDouble())},Modifier.weight(1f),valueRange=0f..score.endBeat.toFloat().coerceAtLeast(.1f))
                TextButton(onClick={vm.edit{it.copy(review=false)}}){Text("Close",fontSize=11.sp)}
            }
        }
        if(p.view==ScoreView.SHEET && (p.running || p.review))Text("● correct   ● early/late   ● extra   ○ missing · traces show key holds",fontSize=10.sp,modifier=Modifier.padding(horizontal=12.dp))
        KeyboardControls(keyZoom,keyPan,{keyZoom=it},{keyPan=it})
        Column(Modifier.weight(1f).fillMaxWidth()) {
            Box(Modifier.weight(1f).fillMaxWidth().background(Color(0xfffffefb))) {
                when(p.view) {
                    ScoreView.SHEET -> Notation(p,Modifier.fillMaxSize(),dark=MaterialTheme.colorScheme.background==Color(0xff142421),onSeek=vm::seek,onLoop=vm::setPassage){vm.message.value="Notation: $it"}
                    ScoreView.FALLING -> FallingNotes(p,keyZoom,keyPan,Modifier.fillMaxSize())
                    ScoreView.COMBINED -> Column(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxWidth().weight(scoreFraction)) {Notation(p,Modifier.fillMaxSize(),dark=MaterialTheme.colorScheme.background==Color(0xff142421),onSeek=vm::seek){vm.message.value="Notation: $it"}}
                        HorizontalDivider()
                        Box(Modifier.fillMaxWidth().weight(1f-scoreFraction)) {FallingNotes(p,keyZoom,keyPan,Modifier.fillMaxSize())}
                    }
                }
                if(p.hidden)Box(Modifier.matchParentSize().background(Color(0xfffffefb))) {
                    Text("Score hidden · press Practice to begin",Modifier.align(Alignment.Center),color=Ink)
                }
                if(p.counting>0)Surface(Modifier.align(Alignment.Center),shape=RoundedCornerShape(20.dp),color=Pine.copy(alpha=.94f)) {
                    Text(p.counting.toString(),fontSize=48.sp,color=Color.White,modifier=Modifier.padding(horizontal=30.dp,vertical=8.dp))
                }
            }
            KeyboardCanvas(midi.held.keys,midi.sounding,if(p.hidden)emptyList() else p.expected,settings.simulator || p.listening,vm::key,
                if(landscape)64 else 100,keyZoom,keyPan,p.playbackHeld)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal=10.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            Column(Modifier.weight(1f)) {
                Text(p.feedback,fontSize=12.sp,maxLines=1)
                if(!landscape)Text(if(p.listening)"Tap keys to play along" else if(midi.connected)"MIDI connected" else if(settings.simulator)"Touch piano ready" else "Connect a piano or tap Touch piano",fontSize=10.sp,maxLines=1)
            }
            OutlinedButton(onClick={vm.listen()},contentPadding=PaddingValues(horizontal=10.dp,vertical=6.dp)) {
                Icon(if(p.listening)Icons.Outlined.Stop else Icons.Outlined.VolumeUp,null,Modifier.size(18.dp))
                Text(if(p.listening)"Stop music" else "Listen",fontSize=12.sp)
            }
            if(p.running || p.listening)IconButton(onClick={vm.togglePause()}) {Icon(if(p.paused)Icons.Outlined.PlayArrow else Icons.Outlined.Pause,if(p.paused)"Resume" else "Pause")}
            Button(onClick={if(p.running)vm.stop() else vm.start()},contentPadding=PaddingValues(horizontal=10.dp,vertical=6.dp)) {
                Icon(if(p.running)Icons.Outlined.Stop else Icons.Outlined.PlayArrow,null,Modifier.size(18.dp))
                Text(if(p.running)"Finish" else "Practice",fontSize=12.sp)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("Piece",fontSize=11.sp)
            Slider(value=p.transportBeat.toFloat().coerceIn(0f,score.endBeat.toFloat()),onValueChange={vm.seek(it.toDouble())},
                valueRange=0f..score.endBeat.toFloat().coerceAtLeast(.1f),modifier=Modifier.weight(1f).height(34.dp))
            Text("m.${score.measures.lastOrNull {it.start<=p.transportBeat+.0001}?.number ?: 1}/${score.measures.size}",fontSize=11.sp)
        }
        if(p.result!=null)Row(Modifier.fillMaxWidth().height(30.dp).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick={vm.review()},contentPadding=PaddingValues(0.dp)){Text("Review on score",fontSize=11.sp)}
            Spacer(Modifier.width(12.dp))
            TextButton(onClick={result=true},contentPadding=PaddingValues(0.dp)){Text(if(p.running)"Loop ${p.loops.size} · ${p.result!!.overall.toInt()}%" else "View results · ${p.result!!.overall.toInt()}%",fontSize=11.sp)}
        }
    }
    if(scorePopup)Dialog(onDismissRequest={scorePopup=false}) {
        Surface(shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth().height(if(landscape)320.dp else 430.dp)) {
            Column {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text("Score · floating view",Modifier.weight(1f).padding(start=16.dp),fontWeight=FontWeight.SemiBold)
                    IconButton(onClick={scorePopup=false}){Icon(Icons.Outlined.Close,"Close floating score")}
                }
                Notation(p,Modifier.weight(1f).fillMaxWidth(),dark=MaterialTheme.colorScheme.background==Color(0xff142421),onSeek=vm::seek){vm.message.value="Notation: $it"}
            }
        }
    }
    if(controls)PracticeControls(vm,p){controls=false}
    if(result&&p.result!=null)ResultDialog(p.result!!,p.simulated){result=false}
}
fun modeName(m: PracticeMode)=when(m){PracticeMode.FREE->"Free play";PracticeMode.FOLLOW->"Follow score";PracticeMode.WAIT->"Wait mode";PracticeMode.RHYTHM->"Rhythm"}
fun handName(h: Hand)=when(h){Hand.LEFT->"Left hand";Hand.RIGHT->"Right hand";Hand.BOTH->"Both hands"}
@Composable private fun PracticeControls(vm: PianoViewModel,p: PracticeUi,close: ()->Unit) {
    val score=p.score ?: return
    val settings by vm.settings.collectAsStateWithLifecycle()
    var sectionName by remember{mutableStateOf("")}
    ModalBottomSheet(onDismissRequest=close) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=24.dp).padding(bottom=32.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("Shape your practice",fontFamily=FontFamily.Serif,fontSize=28.sp)
            if(p.running || p.listening)Text("Stop playback or finish practice to change these settings.",color=MaterialTheme.colorScheme.primary)
            Text("Mode",fontWeight=FontWeight.SemiBold)
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) { PracticeMode.entries.forEach { mode-> FilterChip(p.mode==mode,{vm.edit{it.copy(mode=mode)}},enabled=!(p.running || p.listening),label={Text(modeName(mode))}) } }
            Text(when(p.mode){PracticeMode.FREE->"No scoring";PracticeMode.FOLLOW->"Follows your playing";PracticeMode.WAIT->"Waits for each chord";PracticeMode.RHYTHM->"Scores timing and pitch"},fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider()
            Text("Hands",fontWeight=FontWeight.SemiBold)
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) { Hand.entries.forEach { h-> FilterChip(p.hand==h,{vm.edit{it.copy(hand=h)}},enabled=!(p.running || p.listening),label={Text(handName(h))}) } }
            HorizontalDivider()
            Text("Tempo · ${p.bpm.toInt()} BPM",fontWeight=FontWeight.SemiBold)
            Slider(p.bpm.toFloat(),{v->vm.edit{it.copy(bpm=v.toDouble())}},valueRange=30f..240f,steps=209,enabled=!(p.running || p.listening))
            if(score.tempoMarks.isNotEmpty())Text("Score tempo markings: "+score.tempoMarks.take(6).joinToString { mark ->
                "m.${score.measures.lastOrNull {it.start<=mark.beat+.0001}?.number ?: 1} · ${mark.bpm.toInt()} BPM"
            }+if(score.tempoMarks.size>6)" …" else "",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(score.expressiveTempo)Text("Expressive tempo text (e.g. accelerando or ritardando) detected; words alone do not define an exact BPM curve.",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(score.tempoMarks.size>1 || score.expressiveTempo)Text("Listen follows explicit BPM changes; scored Practice uses your selected fixed tempo.",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Piano sound",fontWeight=FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                PianoSound.entries.forEach { sound -> FilterChip(settings.sound==sound,{vm.setSettings{it.copy(sound=sound)}},modifier=Modifier.weight(1f),enabled=!(p.running || p.listening),label={Text(sound.label,fontSize=11.sp,maxLines=2)}) }
            }
            HorizontalDivider()
            Text("Passage · drag handles to select a loop",fontWeight=FontWeight.SemiBold)
            val from=score.measures[p.startMeasure-1].start+p.startOffset
            val to=score.measures[p.endMeasure-1].start+p.endOffset
            RangeSlider(value=from.toFloat()..to.toFloat(),onValueChange={vm.setPassage(it.start.toDouble(),it.endInclusive.toDouble())},
                valueRange=0f..score.endBeat.toFloat().coerceAtLeast(.25f),enabled=!(p.running || p.listening))
            Text("Selection: beat ${"%.1f".format(from)}–${"%.1f".format(to)} of ${"%.1f".format(score.endBeat)}",fontSize=12.sp)
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
            Text("Score side margin · ${p.sheetMargin} px",fontSize=12.sp)
            Slider(p.sheetMargin.toFloat(),{v->vm.edit{it.copy(sheetMargin=v.toInt())}},valueRange=0f..32f,enabled=!(p.running || p.listening))
            OutlinedTextField(sectionName,{sectionName=it},label={Text("Passage name")},singleLine=true,modifier=Modifier.fillMaxWidth(),enabled=!(p.running || p.listening))
            OutlinedButton(onClick={vm.saveSection(sectionName)},enabled=!(p.running || p.listening)){Icon(Icons.Outlined.BookmarkAdd,null);Spacer(Modifier.width(8.dp));Text("Save passage")}
            p.sections.forEach { s->TextButton(onClick={vm.useSection(s)},enabled=!(p.running || p.listening)){Icon(Icons.Outlined.BookmarkBorder,null);Spacer(Modifier.width(8.dp));Text("${s.name} · ${s.bpm.toInt()} BPM")} }
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
