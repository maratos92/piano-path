@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package org.pianopath.ui

import androidx.compose.foundation.*
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    if(score==null) {Column(Modifier.padding(24.dp)){PageTitle("Choose your music");Button(onClick={vm.screen.value="Library"}){Text("Open library")}};return}
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(if(landscape)44.dp else 54.dp).padding(horizontal=6.dp),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=openMenu){Icon(Icons.Outlined.Menu,"Open navigation menu")}
            Column(Modifier.weight(1f)) {
                Text(score.title,fontFamily=FontFamily.Serif,fontSize=if(landscape)18.sp else 22.sp,maxLines=1)
                Text("${p.bpm.toInt()} BPM · ${handName(p.hand)} · m. ${p.startMeasure}–${p.endMeasure}",fontSize=11.sp,maxLines=1)
            }
            IconButton(onClick={vm.screen.value="Connect"}) {ConnectionStatusIcon(midi.connected,settings.reduceMotion,Modifier.size(22.dp),Icons.Outlined.Piano)}
            if(p.view!=ScoreView.FALLING) {
                IconButton(onClick={vm.zoom(-.15)}){Icon(Icons.Outlined.ZoomOut,"Zoom out sheet")}
                IconButton(onClick={vm.zoom(.15)}){Icon(Icons.Outlined.ZoomIn,"Zoom in sheet")}
            }
            if(!p.running && !p.listening && (p.take.isNotEmpty() || p.matched.isNotEmpty() || p.missed.isNotEmpty()))IconButton(onClick={replaying=false;vm.stop()}){Icon(Icons.Outlined.LayersClear,"Clear sheet markings")}
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
            Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
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
                    ScoreView.SHEET -> Box(Modifier.fillMaxSize()){Notation(p,Modifier.fillMaxSize(),dark=MaterialTheme.colorScheme.background==Color(0xff142421),onSeek=vm::seek,onLoop=vm::setPassage,onPreview=vm::previewCursor,animate=!settings.reduceMotion){vm.message.value="Notation: $it"}}
                    ScoreView.FALLING -> if(p.review && p.performedRoll)PerformedRoll(p,Modifier.fillMaxSize()) else FallingNotes(p,1f,0f,Modifier.fillMaxSize(),onSeek=vm::seek,onPreview=vm::previewCursor,glow=settings.fallingGlow,animate=!settings.reduceMotion)
                    ScoreView.COMBINED -> Column(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxWidth().weight(scoreFraction)) {Box(Modifier.fillMaxSize()){Notation(p,Modifier.fillMaxSize(),dark=MaterialTheme.colorScheme.background==Color(0xff142421),onSeek=vm::seek,onLoop=vm::setPassage,onPreview=vm::previewCursor,animate=!settings.reduceMotion){vm.message.value="Notation: $it"}}}
                        HorizontalDivider()
                        Box(Modifier.fillMaxWidth().weight(1f-scoreFraction)) {if(p.review && p.performedRoll)PerformedRoll(p,Modifier.fillMaxSize()) else FallingNotes(p,1f,0f,Modifier.fillMaxSize(),onSeek=vm::seek,onPreview=vm::previewCursor,glow=settings.fallingGlow,animate=!settings.reduceMotion)}
                    }
                }
                if(p.hidden)Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.background)) {
                    Text("Score hidden · press Start to begin",Modifier.align(Alignment.Center),color=Ink)
                }
                if(p.counting>0)Box(Modifier.align(Alignment.Center)) {
                    CountdownNumber(p.counting,!settings.reduceMotion)
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
                Text(if(p.listening)"Stop music" else if(p.running)"Listen instead" else "Listen",fontSize=12.sp)
            }
            if(p.running)IconButton(onClick={replaying=false;vm.stop()}){Icon(Icons.Outlined.Stop,"Stop and clear sheet")}
            if(p.running || p.listening)IconButton(onClick={vm.togglePause()}) {Icon(if(p.paused)Icons.Outlined.PlayArrow else Icons.Outlined.Pause,if(p.paused)"Resume" else "Pause")}
            Button(onClick={if(p.running)vm.stop("Practice finished",saveResult=true) else vm.start()},modifier=Modifier.testTag("start-practice"),contentPadding=PaddingValues(horizontal=10.dp,vertical=6.dp)) {
                Icon(if(p.running)Icons.Outlined.Stop else Icons.Outlined.PlayArrow,null,Modifier.size(18.dp))
                Text(if(p.running)"Finish" else "Start",fontSize=12.sp)
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
        if(p.category=="Sight reading" && !p.running && !p.listening)TextButton(onClick={replaying=false;vm.nextSight()}){Icon(Icons.Outlined.SkipNext,null);Text("Next exercise · same settings")}
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
/** Compact pages keep the primary action fixed; dropdowns replace scrolling chip rows. */
@Composable private fun PracticeControls(vm: PianoViewModel,p: PracticeUi,readerControls: @Composable ()->Unit = {},close: ()->Unit) {
    val score=p.score ?: return
    val settings by vm.settings.collectAsStateWithLifecycle()
    var page by remember{mutableIntStateOf(0)}
    var loopPage by remember{mutableIntStateOf(0)}
    var sectionName by remember{mutableStateOf("")}
    val editable=!(p.running || p.listening)
    // The Activity uses enableEdgeToEdge(). Give this full-screen dialog the same
    // window inset policy, then apply safeDrawing padding once to the content.
    // A floating/decor-fitting dialog could measure its footer beneath the system bar.
    Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal=12.dp,vertical=4.dp)) {
                Row(Modifier.fillMaxWidth().height(44.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text("Practice settings",Modifier.weight(1f),fontSize=20.sp,fontFamily=FontFamily.Serif)
                    if(p.category=="Scales" || p.category=="Sight reading")TextButton(onClick={
                        close();vm.stop();vm.screen.value=if(p.category=="Scales")"Scales" else "Sight reading"
                    }){Text("Exercise settings",fontSize=12.sp)}
                    IconButton(onClick=close){Icon(Icons.Outlined.Close,"Back to score")}
                }
                PrimaryTabRow(selectedTabIndex=page) {
                    listOf("Practice","Loop","Score").forEachIndexed{i,label->Tab(page==i,{page=i},text={Text(label)})}
                }
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(vertical=6.dp)) {
                    val wide=maxWidth>=600.dp || maxHeight<330.dp
                    @Composable fun Columns(left: @Composable ColumnScope.()->Unit,right: @Composable ColumnScope.()->Unit) {
                        if(wide)Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp),content=left)
                            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp),content=right)
                        } else Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(6.dp)) {left();right()}
                    }
                    when(page) {
                        0->Columns(left={
                            CompactChoice("Mode",modeName(p.mode),PracticeMode.entries.map{modeName(it) to {vm.edit{p->p.copy(mode=it)}}},editable)
                            CompactChoice("Hands",handName(p.hand),Hand.entries.map{handName(it) to {vm.edit{p->p.copy(hand=it)}}},editable)
                            CompactNumber("Tempo (BPM)",p.bpm,20.0,240.0,editable){v->vm.edit{it.copy(bpm=v)}}
                        },right={
                            CompactSwitch("Count-in · 4 seconds",p.countIn,editable){v->vm.edit{it.copy(countIn=v)}}
                            CompactSwitch("Metronome",p.metronome,editable){v->vm.edit{it.copy(metronome=v)}}
                            Text(when(p.mode){PracticeMode.FREE->"Free play · no scoring";PracticeMode.WAIT->"Waits for correct notes; no rhythm score";PracticeMode.FOLLOW->"Starts with your first note, follows tempo, marks missed notes";PracticeMode.RHYTHM->"Pitch and timing against the metronome"},fontSize=12.sp)
                        })
                        1->Column {
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                                listOf("Range","Repeat","Saved").forEachIndexed{i,label->TextButton(onClick={loopPage=i},modifier=Modifier.weight(1f)){Text(label,color=if(loopPage==i)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)}}
                            }
                            when(loopPage) {
                                0->Columns(left={
                                    CompactSwitch("Select loop on score",p.selectingLoop,editable){vm.toggleLoopSelection()}
                                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                        Box(Modifier.weight(1f)){CompactNumber("From measure",p.startMeasure.toDouble(),1.0,score.measures.size.toDouble(),editable){v->vm.edit{val n=v.toInt();val e=max(n,it.endMeasure);it.copy(startMeasure=n,endMeasure=e,startOffset=0.0,endOffset=score.measures[e-1].length)}}}
                                        Box(Modifier.weight(1f)){CompactNumber("Through measure",p.endMeasure.toDouble(),1.0,score.measures.size.toDouble(),editable){v->vm.edit{val n=v.toInt();val a=min(n,it.startMeasure);it.copy(startMeasure=a,endMeasure=n,startOffset=0.0,endOffset=score.measures[n-1].length)}}}
                                    }
                                },right={
                                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                        Box(Modifier.weight(1f)){CompactNumber("Start beat offset",p.startOffset,0.0,(score.measures[p.startMeasure-1].length-.25).coerceAtLeast(0.0),editable){v->vm.edit{it.copy(startOffset=v)}}}
                                        Box(Modifier.weight(1f)){CompactNumber("End beat offset",p.endOffset,.25,score.measures[p.endMeasure-1].length,editable){v->vm.edit{it.copy(endOffset=v)}}}
                                    }
                                    Text("Drag loop edges on the score for quick selection.",fontSize=12.sp)
                                })
                                1->Columns(left={
                                    CompactSwitch("Repeat selected loop",p.loop,editable){v->vm.edit{it.copy(loop=v)}}
                                    CompactSwitch("Adaptive tempo (+4 BPM)",p.autoTempo,editable&&p.loop){v->vm.edit{it.copy(autoTempo=v)}}
                                },right={
                                    CompactChoice("Rest between loops","${settings.loopBreakSeconds} seconds",(0..5).map{n->"$n seconds" to {vm.setSettings{it.copy(loopBreakSeconds=n)}}})
                                    Text("Adaptive tempo increases after a repetition scores at least 90%.",fontSize=12.sp)
                                })
                                else->Columns(left={
                                    OutlinedTextField(sectionName,{sectionName=it},label={Text("Loop name")},singleLine=true,modifier=Modifier.fillMaxWidth(),enabled=editable)
                                    OutlinedButton(onClick={vm.saveSection(sectionName)},enabled=editable){Text("Save loop")}
                                },right={
                                    CompactChoice("Saved loops","Choose a saved loop",p.sections.map{s->s.name to {vm.useSection(s)}},editable&&p.sections.isNotEmpty())
                                })
                            }
                        }
                        else->Columns(left={
                            CompactSwitch("Horizontal score layout",p.horizontal,editable){v->vm.edit{it.copy(horizontal=v)}}
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                Text("Score zoom",Modifier.weight(1f))
                                IconButton(onClick={vm.zoom(-.15)}){Icon(Icons.Outlined.ZoomOut,"Zoom out sheet")}
                                Text("${(p.zoom*100).toInt()}%",fontSize=12.sp)
                                IconButton(onClick={vm.zoom(.15)}){Icon(Icons.Outlined.ZoomIn,"Zoom in sheet")}
                            }
                        },right={
                            OutlinedButton(onClick={close();vm.screen.value="Connect"}){Text("Piano MIDI connection")}
                            readerControls()
                            if(score.tempoMarks.size>1 || score.expressiveTempo)Text("Listen follows score tempo marks; Start uses the selected practice tempo.",fontSize=12.sp)
                        })
                    }
                }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth().height(56.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick=close,modifier=Modifier.weight(1f)){Text("Back to score")}
                    Button(onClick={close();vm.start()},enabled=editable,modifier=Modifier.weight(1f).testTag("start-practice")){Icon(Icons.Outlined.PlayArrow,null);Spacer(Modifier.width(8.dp));Text("Start")}
                }
            }
        }
    }
}
@Composable private fun CompactChoice(label: String,value: String,options: List<Pair<String,()->Unit>>,enabled: Boolean=true) {
    var expanded by remember{mutableStateOf(false)}
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick={expanded=true},enabled=enabled,modifier=Modifier.fillMaxWidth().height(52.dp),shape=RoundedCornerShape(8.dp),contentPadding=PaddingValues(horizontal=12.dp)) {
            Column(Modifier.weight(1f)){Text(label,fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(value,fontSize=13.sp,maxLines=1)}
            Icon(Icons.Outlined.ArrowDropDown,null)
        }
        DropdownMenu(expanded,onDismissRequest={expanded=false}) {options.forEach{(title,action)->DropdownMenuItem(text={Text(title)},onClick={expanded=false;action()})}}
    }
}
@Composable private fun CompactNumber(label: String,value: Double,minimum: Double,maximum: Double,enabled: Boolean,onChange: (Double)->Unit) {
    val display=if(value%1.0==0.0)value.toInt().toString() else "%.2f".format(java.util.Locale.US,value)
    var text by remember(value){mutableStateOf(display)}
    OutlinedTextField(text,{raw->text=raw.filter{it.isDigit()||it=='.'}.take(7);text.toDoubleOrNull()?.takeIf{it in minimum..maximum}?.let(onChange)},
        modifier=Modifier.fillMaxWidth(),enabled=enabled,label={Text(label,fontSize=11.sp)},singleLine=true,
        keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),textStyle=androidx.compose.ui.text.TextStyle(fontSize=14.sp))
}
@Composable private fun CompactSwitch(label: String,checked: Boolean,enabled: Boolean,onChange: (Boolean)->Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=44.dp),verticalAlignment=Alignment.CenterVertically) {
        Text(label,Modifier.weight(1f),fontSize=13.sp,maxLines=2)
        Switch(checked,onChange,enabled=enabled)
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


@Composable private fun CountdownNumber(number: Int,animate: Boolean) {
    val pulse=remember{Animatable(0f)}
    LaunchedEffect(number,animate) {
        pulse.snapTo(0f)
        if(animate)pulse.animateTo(1f,tween(850))
    }
    val fill=MaterialTheme.colorScheme.primary
    Canvas(Modifier.size(150.dp).graphicsLayer{val t=if(animate)pulse.value else 0f;scaleX=1f+.3f*t;scaleY=scaleX;alpha=1f-.65f*t}.semantics{contentDescription="Countdown $number"}) {
        val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize=84.sp.toPx();typeface=android.graphics.Typeface.DEFAULT_BOLD;textAlign=android.graphics.Paint.Align.CENTER
            strokeWidth=5.dp.toPx();strokeJoin=android.graphics.Paint.Join.ROUND
        }
        val baseline=size.height/2-(paint.fontMetrics.ascent+paint.fontMetrics.descent)/2
        paint.style=android.graphics.Paint.Style.STROKE;paint.color=android.graphics.Color.WHITE
        drawContext.canvas.nativeCanvas.drawText(number.toString(),size.width/2,baseline,paint)
        paint.style=android.graphics.Paint.Style.FILL;paint.color=fill.toArgb()
        drawContext.canvas.nativeCanvas.drawText(number.toString(),size.width/2,baseline,paint)
    }
}
