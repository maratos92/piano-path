@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package org.pianopath.ui

import androidx.compose.foundation.*
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
    var replaying by remember {mutableStateOf(false)}
    var scoreFraction by androidx.compose.runtime.saveable.rememberSaveable {mutableFloatStateOf(.55f)}
    val landscape=LocalConfiguration.current.screenWidthDp>LocalConfiguration.current.screenHeightDp
    val score=p.score
    LaunchedEffect(replaying,p.key) {
        if(!replaying || !p.review || !p.performedRoll)return@LaunchedEffect
        val musicalEnd=p.score?.endBeat ?: return@LaunchedEffect
        val initial=p.reviewBeat
        val origin=System.nanoTime()
        while(isActive && replaying && vm.ui.value.review && vm.ui.value.performedRoll) {
            val position=initial+(System.nanoTime()-origin)/1e9*p.bpm/60
            vm.seekReview(position.coerceAtMost(musicalEnd))
            if(position>=musicalEnd)break
            delay(16)
        }
        replaying=false
    }
    if(score==null) {Column(Modifier.padding(24.dp)){PageTitle("Your practice space","Choose your music.");Button(onClick={vm.screen.value="Library"}){Text("Open library")}};return}
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(if(landscape)44.dp else 54.dp).padding(horizontal=6.dp),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=openMenu){Icon(Icons.Outlined.Menu,"Open navigation menu")}
            Column(Modifier.weight(1f)) {
                Text(score.title,fontFamily=FontFamily.Serif,fontSize=if(landscape)18.sp else 22.sp,maxLines=1)
                Text("${p.bpm.toInt()} BPM · ${handName(p.hand)} · m. ${p.startMeasure}–${p.endMeasure}",fontSize=11.sp,maxLines=1)
            }
            IconButton(onClick={vm.screen.value="Connect"}) {Icon(Icons.Outlined.Piano,if(midi.connected)"MIDI piano connected" else "MIDI piano disconnected",Modifier.size(16.dp),tint=if(midi.connected)Color(0xff2a9a6a) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=.38f))}
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
            if(p.view!=ScoreView.SHEET) {
                IconButton(onClick={vm.scaleFalling(-.2)},modifier=Modifier.size(32.dp)){Icon(Icons.Outlined.UnfoldLess,"Compress falling timeline")}
                IconButton(onClick={vm.scaleFalling(.2)},modifier=Modifier.size(32.dp)){Icon(Icons.Outlined.UnfoldMore,"Stretch falling timeline")}
            }
            IconButton(onClick=vm::toggleLoopSelection,enabled=!(p.running||p.listening),modifier=Modifier.size(34.dp)){Icon(Icons.Outlined.Repeat,"Select loop",Modifier.size(19.dp),tint=if(p.selectingLoop)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)}
            IconButton(onClick={monitor=!monitor},modifier=Modifier.size(36.dp)) {Icon(Icons.Outlined.QueueMusic,"Played notes",Modifier.size(19.dp),tint=if(monitor)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)}
        }
        val outOfRange=remember(score,p.startMeasure,p.endMeasure,p.startOffset,p.endOffset,p.hand,settings.keyboardFirst,settings.keyboardLast) {
            val a=score.measures[p.startMeasure-1].start+p.startOffset;val b=score.measures[p.endMeasure-1].start+p.endOffset
            score.selected(a,b,p.hand).count{it.midi !in settings.keyboardFirst..settings.keyboardLast}
        }
        if(outOfRange>0)Text("$outOfRange score notes outside ${displayNoteName(settings.keyboardFirst,settings.noteNaming)}–${displayNoteName(settings.keyboardLast,settings.noteNaming)} · select another hand, passage or keyboard range",fontSize=11.sp,color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(horizontal=12.dp))
        if(monitor)Box(Modifier.fillMaxWidth().heightIn(max=if(landscape)140.dp else 220.dp).verticalScroll(rememberScrollState())) {LiveNotes(vm,Modifier.fillMaxWidth())}
        if(p.view==ScoreView.COMBINED && !landscape)Row(Modifier.fillMaxWidth().height(30.dp).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("Score height",fontSize=11.sp)
            Slider(scoreFraction,{scoreFraction=it},Modifier.weight(1f).padding(horizontal=10.dp),valueRange=.25f..0.75f)
        }
        if(p.review && !p.running && !p.listening) {
            Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
                FilterChip(!p.performedRoll,{replaying=false;vm.showPerformedRoll(false)},label={Text("Score",fontSize=11.sp)})
                FilterChip(p.performedRoll,{replaying=false;vm.showPerformedRoll(true)},label={Text("Recorded",fontSize=11.sp)})
                if(p.performedRoll)IconButton(onClick={
                    if(replaying)replaying=false else {
                        if(p.reviewBeat>=score.endBeat-.01)vm.seekReview(score.measures[p.startMeasure-1].start+p.startOffset)
                        replaying=true
                    }
                }){Icon(if(replaying)Icons.Outlined.Pause else Icons.Outlined.PlayArrow,if(replaying)"Pause replay" else "Animate recorded notes")}
                if(landscape){
                    Slider(p.reviewBeat.toFloat(),{vm.seekReview(it.toDouble())},Modifier.weight(1f),valueRange=0f..score.endBeat.toFloat().coerceAtLeast(.1f))
                    TextButton(onClick={replaying=false;vm.edit{it.copy(review=false)}}){Text("Close",fontSize=11.sp)}
                }
            }
            if(!landscape)Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
                Text("Review",fontSize=11.sp)
                Slider(p.reviewBeat.toFloat(),{vm.seekReview(it.toDouble())},Modifier.weight(1f),valueRange=0f..score.endBeat.toFloat().coerceAtLeast(.1f))
                TextButton(onClick={vm.edit{it.copy(review=false)}}){Text("Close",fontSize=11.sp)}
            }
        }
        if(p.view==ScoreView.SHEET && (p.running || p.review))Text(if(landscape)"Green correct · amber timing · red extra · ○ missing" else "● correct   ● early/late   ● extra   ○ missing · traces show key holds",fontSize=10.sp,modifier=Modifier.padding(horizontal=12.dp))
        Column(Modifier.weight(1f).fillMaxWidth()) {
            Box(Modifier.weight(1f).fillMaxWidth().background(Color(0xfffffefb))) {
                when(p.view) {
                    ScoreView.SHEET -> ScoreFrame(settings.sheetDecoration,Modifier.fillMaxSize()){Notation(p,Modifier.fillMaxSize(),dark=MaterialTheme.colorScheme.background==Color(0xff142421),onSeek=vm::seek,onLoop=vm::setPassage,onPreview=vm::previewCursor,animate=!settings.reduceMotion){vm.message.value="Notation: $it"}}
                    ScoreView.FALLING -> if(p.review && p.performedRoll)PerformedRoll(p,Modifier.fillMaxSize()) else FallingNotes(p,1f,0f,Modifier.fillMaxSize(),onSeek=vm::seek,onPreview=vm::previewCursor,glow=settings.fallingGlow,animate=!settings.reduceMotion)
                    ScoreView.COMBINED -> Column(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxWidth().weight(scoreFraction)) {ScoreFrame(settings.sheetDecoration,Modifier.fillMaxSize()){Notation(p,Modifier.fillMaxSize(),dark=MaterialTheme.colorScheme.background==Color(0xff142421),onSeek=vm::seek,onLoop=vm::setPassage,onPreview=vm::previewCursor,animate=!settings.reduceMotion){vm.message.value="Notation: $it"}}}
                        HorizontalDivider()
                        Box(Modifier.fillMaxWidth().weight(1f-scoreFraction)) {if(p.review && p.performedRoll)PerformedRoll(p,Modifier.fillMaxSize()) else FallingNotes(p,1f,0f,Modifier.fillMaxSize(),onSeek=vm::seek,onPreview=vm::previewCursor,glow=settings.fallingGlow,animate=!settings.reduceMotion)}
                    }
                }
                if(p.hidden)Box(Modifier.matchParentSize().background(Color(0xfffffefb))) {
                    Text("Score hidden · press Practice to begin",Modifier.align(Alignment.Center),color=Ink)
                }
                if(p.counting>0)Box(Modifier.align(Alignment.Center)) {
                    if(settings.reduceMotion)Text(p.counting.toString(),fontSize=80.sp,color=MaterialTheme.colorScheme.primary)
                    else AnimatedContent(targetState=p.counting,transitionSpec={ (fadeIn(tween(150))+scaleIn(tween(300),initialScale=.65f)) togetherWith (fadeOut(tween(300))+scaleOut(tween(300),targetScale=1.5f)) },label="Practice countdown"){number->Text(number.toString(),fontSize=80.sp,color=MaterialTheme.colorScheme.primary)}
                }
            }
            KeyboardCanvas(midi.held.keys,midi.sounding,if(p.hidden)emptyList() else if(p.cursorPreview!=null || !p.running)expectedAt(score,p.cursorPreview ?: if(p.review)p.reviewBeat else p.transportBeat,p.hand) else p.expected,true,vm::key,
                if(landscape)64 else 100,1f,0f,if(p.review && p.performedRoll)p.take.filter {n->p.reviewBeat>=n.beat && p.reviewBeat<=n.beat+(n.duration ?: .12)}.map {it.pitch}.toSet() else if(p.paused)expectedAt(score,p.transportBeat,p.hand).toSet() else p.playbackHeld,paused=p.paused)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal=10.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            Column(Modifier.weight(1f)) {
                Text(if(p.focusRemainingMs>0)"${(p.focusRemainingMs/1000)}s focus · ${p.feedback}" else displayNoteText(p.feedback,settings.noteNaming),fontSize=12.sp,maxLines=1)

            }
            OutlinedButton(onClick={vm.listen()},contentPadding=PaddingValues(horizontal=10.dp,vertical=6.dp)) {
                Icon(if(p.listening)Icons.Outlined.Stop else Icons.Outlined.VolumeUp,null,Modifier.size(18.dp))
                Text(if(p.listening)"Stop music" else "Listen",fontSize=12.sp)
            }
            if(p.running || p.listening)IconButton(onClick={vm.togglePause()}) {Icon(if(p.paused)Icons.Outlined.PlayArrow else Icons.Outlined.Pause,if(p.paused)"Resume" else "Pause")}
            Button(onClick={if(p.running)vm.stop("Practice finished",saveResult=true) else vm.start()},contentPadding=PaddingValues(horizontal=10.dp,vertical=6.dp)) {
                Icon(if(p.running)Icons.Outlined.Stop else Icons.Outlined.PlayArrow,null,Modifier.size(18.dp))
                Text(if(p.running)"Finish" else "Practice",fontSize=12.sp)
            }
        }
        if(!p.review)Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
            val start=score.measures[p.startMeasure-1].start+p.startOffset
            val end=score.measures[p.endMeasure-1].start+p.endOffset
            if(p.selectingLoop)Text("${"%.1f".format(start)}",fontSize=10.sp)
            Slider(value=p.transportBeat.toFloat().coerceIn(0f,score.endBeat.toFloat()),onValueChange={vm.seek(it.toDouble())},
                valueRange=0f..score.endBeat.toFloat().coerceAtLeast(.1f),modifier=Modifier.weight(1f).height(34.dp))
            if(p.selectingLoop)Text("${"%.1f".format(end)}",fontSize=10.sp)
        }
        if(p.result!=null && !p.review)Row(Modifier.fillMaxWidth().height(30.dp).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick={vm.review()},contentPadding=PaddingValues(0.dp)){Text("Review on score",fontSize=11.sp)}
            Spacer(Modifier.width(12.dp))
            TextButton(onClick={result=true},contentPadding=PaddingValues(0.dp)){Text(if(p.running)"Loop ${p.loops.size} · ${p.result!!.overall.toInt()}%" else "View results · ${p.result!!.overall.toInt()}%",fontSize=11.sp)}
        }
    }
    if(controls)PracticeControls(vm,p,readerControls={
        if(p.view==ScoreView.COMBINED && landscape) {
            Text("Score height")
            Slider(scoreFraction,{scoreFraction=it},valueRange=.25f..0.75f)
        }
    }){controls=false}
    if(result&&p.result!=null)ResultDialog(p.result!!,p.simulated,p.mode,p.visualTolerance){result=false}
}
fun modeName(m: PracticeMode)=when(m){PracticeMode.FREE->"Free play";PracticeMode.FOLLOW->"Follow score";PracticeMode.WAIT->"Wait mode";PracticeMode.RHYTHM->"Rhythm"}
fun handName(h: Hand)=when(h){Hand.LEFT->"Left hand";Hand.RIGHT->"Right hand";Hand.BOTH->"Both hands"}
@Composable private fun PracticeControls(vm: PianoViewModel,p: PracticeUi,readerControls: @Composable ()->Unit = {},close: ()->Unit) {
    val score=p.score ?: return
    val naming=LocalNoteNaming.current
    var sectionName by remember{mutableStateOf("")}
    var advanced by remember{mutableStateOf(false)}
    ModalBottomSheet(onDismissRequest=close) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=24.dp).padding(bottom=32.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("Shape your practice",fontFamily=FontFamily.Serif,fontSize=28.sp)
            readerControls()
            if(p.running || p.listening)Text("Stop playback or finish practice to change these settings.",color=MaterialTheme.colorScheme.primary)
            Text("Mode",fontWeight=FontWeight.SemiBold)
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) { PracticeMode.entries.forEach { mode-> FilterChip(p.mode==mode,{vm.edit{it.copy(mode=mode)}},enabled=!(p.running || p.listening),label={Text(modeName(mode))}) } }
            Text(when(p.mode){PracticeMode.FREE->"No scoring";PracticeMode.FOLLOW->"Follows your playing";PracticeMode.WAIT->"Waits for each chord";PracticeMode.RHYTHM->"Scores timing and pitch"},fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider()
            Text("Hands",fontWeight=FontWeight.SemiBold)
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) { Hand.entries.forEach { h-> FilterChip(p.hand==h,{vm.edit{it.copy(hand=h)}},enabled=!(p.running || p.listening),label={Text(handName(h))}) } }
            HorizontalDivider()
            Text("Tempo · ${p.bpm.toInt()} BPM",fontWeight=FontWeight.SemiBold)
            Slider(p.bpm.toFloat(),{v->vm.edit{it.copy(bpm=v.toDouble())}},valueRange=20f..240f,steps=219,enabled=!(p.running || p.listening))
            if(score.tempoMarks.isNotEmpty())Text("Score tempo markings: "+score.tempoMarks.take(6).joinToString { mark ->
                "m.${score.measures.lastOrNull {it.start<=mark.beat+.0001}?.number ?: 1} · ${mark.bpm.toInt()} BPM"
            }+if(score.tempoMarks.size>6)" …" else "",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(score.expressiveTempo)Text("Expressive tempo text (e.g. accelerando or ritardando) detected; words alone do not define an exact BPM curve.",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(score.tempoMarks.size>1 || score.expressiveTempo)Text("Listen follows explicit BPM changes; scored Practice uses your selected fixed tempo.",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider()
            Text("Loop · drag the edge handles",fontWeight=FontWeight.SemiBold)
            val from=score.measures[p.startMeasure-1].start+p.startOffset
            val to=score.measures[p.endMeasure-1].start+p.endOffset
            if(p.selectingLoop)RangeSlider(value=from.toFloat()..to.toFloat(),onValueChange={vm.setPassage(it.start.toDouble(),it.endInclusive.toDouble())},
                valueRange=0f..score.endBeat.toFloat().coerceAtLeast(.25f),enabled=!(p.running || p.listening))
            if(p.selectingLoop)Text("${"%.1f".format(from)}  →  ${"%.1f".format(to)}",fontSize=12.sp)
            TextButton(onClick={advanced=!advanced}){Text(if(advanced)"Hide fine adjustments" else "Fine adjustments")}
            if(advanced) {
            MeasureStepper("From measure",p.startMeasure,score.measures.size,!(p.running || p.listening)) { n -> vm.edit { val end=max(n,it.endMeasure);it.copy(startMeasure=n,endMeasure=end,startOffset=0.0,endOffset=score.measures[end-1].length) } }
            MeasureStepper("Through measure",p.endMeasure,score.measures.size,!(p.running || p.listening)) { n -> vm.edit { val start=min(n,it.startMeasure);it.copy(startMeasure=start,endMeasure=n,startOffset=0.0,endOffset=score.measures[n-1].length) } }
            Text("Start offset · ${"%.2f".format(p.startOffset)} quarter beats",fontSize=12.sp)
            Slider(p.startOffset.toFloat(),{v->vm.edit{it.copy(startOffset=v.toDouble())}},valueRange=0f..(score.measures[p.startMeasure-1].length-.25).coerceAtLeast(.25).toFloat(),enabled=!(p.running || p.listening))
            Text("End offset · ${"%.2f".format(p.endOffset)} quarter beats",fontSize=12.sp)
            Slider(p.endOffset.toFloat(),{v->vm.edit{it.copy(endOffset=v.toDouble())}},valueRange=.25f..score.measures[p.endMeasure-1].length.toFloat().coerceAtLeast(.26f),enabled=!(p.running || p.listening))
            }
            Toggle("Repeat selected passage",p.loop,{v->vm.edit{it.copy(loop=v)}},!(p.running || p.listening))
            Toggle("Count-in before each repetition",p.countIn,{v->vm.edit{it.copy(countIn=v)}},!(p.running || p.listening))
            Text("Count-in plays a bar of preparation clicks. Rest between loops is separate; both are optional.",fontSize=12.sp)
            Toggle("Metronome",p.metronome,{v->vm.edit{it.copy(metronome=v)}},!(p.running || p.listening))
            if(p.loop)Toggle("Adaptive tempo · +4 BPM after ≥90% score",p.autoTempo,{v->vm.edit{it.copy(autoTempo=v)}},!(p.running || p.listening))
            if(p.loop)Text("Adaptive tempo is optional: increase speed only after a successful repetition. Leave off for a fixed tempo.",fontSize=12.sp)
            Text("Rest before next count-in · ${settingsLoopBreak(vm)}s",fontSize=12.sp)
            Slider(settingsLoopBreak(vm).toFloat(),{v->vm.setSettings{it.copy(loopBreakSeconds=v.toInt())}},valueRange=0f..5f,steps=4)
            OutlinedButton(onClick={close();vm.screen.value="Connect"}){Text("Piano MIDI connection")}
            Toggle("Horizontal score layout",p.horizontal,{v->vm.edit{it.copy(horizontal=v)}},!(p.running || p.listening))
            OutlinedTextField(sectionName,{sectionName=it},label={Text("Loop name")},singleLine=true,modifier=Modifier.fillMaxWidth(),enabled=!(p.running || p.listening))
            OutlinedButton(onClick={vm.saveSection(sectionName)},enabled=!(p.running || p.listening)){Icon(Icons.Outlined.BookmarkAdd,null);Spacer(Modifier.width(8.dp));Text("Save loop")}
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
@Composable fun ResultDialog(r: SessionResult,simulated: Boolean,mode: PracticeMode=PracticeMode.RHYTHM,tolerance: Double=180.0,close: ()->Unit) {
    val naming=LocalNoteNaming.current
    val metrics=PracticeMetrics.from(r,mode,tolerance)
    AlertDialog(onDismissRequest=close,confirmButton={TextButton(onClick=close){Text("Keep practicing")}},title={Text("Every note is progress",fontFamily=FontFamily.Serif)},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            if(simulated)Text("TOUCH / TEST INPUT",color=MaterialTheme.colorScheme.primary,fontSize=11.sp)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(14.dp)){Metric("${r.pitch.toInt()}%","note accuracy",Modifier.weight(1f));Metric(metrics.onTime?.let{"${it.toInt()}%"}?:"—","on time",Modifier.weight(1f))}
            Text("${r.correct} correct · ${r.missing} missing · ${r.extra} extra\nChords complete: ${r.completeChords} / ${r.totalChords}")
            Text("Left hand: ${r.leftPitch?.let{"${it.toInt()}%"}?:"—"}    Right hand: ${r.rightPitch?.let{"${it.toInt()}%"}?:"—"}")
            if(r.rhythm!=null) {
                Text("Mean onset deviation: ${r.meanOnsetMs?.toInt()?.let{"$it ms"}?:"—"}\nMean release deviation: ${r.meanDurationMs?.toInt()?.let{"$it ms"}?:"—"}\n${r.early} early · ${r.late} late\nOnset variation: ${r.tempoConsistency?.toInt()?.let{"$it ms"}?:"—"}")
                Text("Releases measure physical key-up. Pedal sustain is shown separately. Hand scores follow MusicXML staff assignments.",fontSize=12.sp)
            } else Text("Wait mode measures pitch completion without a tempo requirement.",fontSize=12.sp)
            HorizontalDivider()
            Text("Note detail",fontWeight=FontWeight.SemiBold)
            r.details.take(80).forEach { n -> Text("m.${n.note.measure}  ${displayNoteName(n.note.midi,naming)}  "+if(n.missing)"Missing" else if(r.rhythm==null)"Correct" else "onset ${n.onsetMs?.toInt() ?: 0} ms · release ${n.durationErrorMs?.toInt()?.let{"$it ms"} ?: "—"}",fontSize=12.sp) }
            if(r.details.size>80)Text("Showing the first 80 notes. Aggregate scores include all notes.",fontSize=11.sp)
        }
    })
}

@Composable private fun settingsLoopBreak(vm: PianoViewModel): Int = vm.settings.collectAsStateWithLifecycle().value.loopBreakSeconds
