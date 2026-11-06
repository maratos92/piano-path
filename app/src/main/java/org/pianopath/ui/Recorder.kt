@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package org.pianopath.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.pianopath.PianoViewModel
import org.pianopath.core.*
import java.text.DateFormat
import java.util.Date

@Composable fun RecorderScreen(vm: PianoViewModel,initialEvents: Boolean=false) {
    var events by remember{mutableStateOf(initialEvents)}
    val naming=LocalNoteNaming.current
    val r by vm.recorder.state.collectAsStateWithLifecycle()
    val recordings by vm.recorder.recordings.collectAsStateWithLifecycle()
    val midi by vm.midi.state.collectAsStateWithLifecycle()
    var rename by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf("") }
    val current=r.current
    val keys=remember(current){current?.let{recordingKeys(it)}.orEmpty()}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { PageTitle("Studio") }
        item { PrimaryTabRow(selectedTabIndex=if(events)1 else 0){Tab(!events,{events=false},text={Text("Play & record")});Tab(events,{events=true},text={Text("MIDI events")})} }
        if(events)item {Panel(Modifier.fillMaxWidth()) {
            Text("Velocity ${midi.lastVelocity} · channel ${midi.lastChannel+1}")
            Text("${midi.held.size} held · sustain ${if(midi.sustain)"down" else "up"}")
            TextButton(onClick={vm.panic()}){Text("Clear sounding notes")}
            midi.events.take(40).forEach{Text(displayNoteText(it,naming),style=MaterialTheme.typography.bodySmall)}
        }}
        if(!events)item { Panel(Modifier.fillMaxWidth()) {
            Text(if(r.recording)"Recording · ${timeLabel(r.elapsedSeconds)}" else r.status,fontWeight=FontWeight.SemiBold)
            if(r.recording)Text("${r.eventCount} MIDI events · leaving this screen saves your take")
            r.error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Button(onClick={if(r.recording)vm.recorder.stop() else vm.recordFreePlay()},enabled=r.ready && !r.saving && !r.unsaved) { Text(if(r.recording)"Stop & save" else "Record new take") }
                if(r.unsaved)OutlinedButton(onClick={vm.recorder.retrySave()}){Text("Retry save")}
                if(r.saving)CircularProgressIndicator(Modifier.size(24.dp),strokeWidth=2.dp)
            }
            KeyboardCanvas(midi.held.keys,midi.sounding,emptyList(),true,vm::key,100,1f,0f,r.held,filledInput=true)
            LiveNotes(vm,Modifier.fillMaxWidth(),includeReview=false)
            TextButton(onClick={vm.panic()}){Text("Clear sounding notes")}
        } }
        if(current!=null && !r.recording && !events)item { Panel(Modifier.fillMaxWidth()) {
            Text(current.title,fontWeight=FontWeight.SemiBold)
            if(current.recovered)Text("Recovered draft · the last unsaved seconds may be missing.",color=MaterialTheme.colorScheme.primary)
            if(keys.isNotEmpty())RecordedNoteRoll(keys,r.positionSeconds,Modifier.fillMaxWidth().height(150.dp))
            Slider(r.positionSeconds.toFloat().coerceIn(0f,(current.durationUs/1e6).toFloat()),{vm.recorder.seek(it.toDouble())},
                valueRange=0f..(current.durationUs/1e6).toFloat().coerceAtLeast(.01f),enabled=!r.saving)
            Text("${timeLabel(r.positionSeconds)} / ${timeLabel(current.durationUs/1e6)} · ${current.events.size} events")
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Button(onClick={vm.recorder.playPause()},enabled=current.events.isNotEmpty() && !r.saving){Text(if(r.playing)"Pause recording" else "Play recording")}
                TextButton(onClick={title=current.title;rename=current.id},enabled=!r.saving){Text("Rename")}
                TextButton(onClick={deleting=current.id},enabled=!r.saving){Text("Delete")}
            }
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf(.5,.75,1.0,1.5,2.0).forEach { speed->FilterChip(r.speed==speed,{vm.recorder.setSpeed(speed)},label={Text("${speed}×")}) }
            }
            Text("Replay is audible MIDI synthesis. Key bars show physical holds; pedal events control sound. No score or accuracy is inferred.",style=MaterialTheme.typography.bodySmall)
        } }
        item { Text("Saved takes · ${recordings.size}",style=MaterialTheme.typography.titleMedium) }
        if(recordings.isEmpty())item { Text("Your first saved take will appear here. Recordings are MIDI data, not microphone audio.") }
        items(recordings,key={it.id}) { take->OutlinedCard(onClick={vm.recorder.open(take.id)},modifier=Modifier.fillMaxWidth(),enabled=!r.recording && !r.saving && !r.unsaved) {
            Column(Modifier.padding(16.dp)) {
                Text(take.title,fontWeight=FontWeight.SemiBold)
                Text("${DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(Date(take.createdAt))} · ${timeLabel(take.durationUs/1e6)}${if(take.recovered)" · recovered" else ""}",style=MaterialTheme.typography.bodySmall)
            }
        } }
    }
    if(rename!=null)AlertDialog(onDismissRequest={rename=null},title={Text("Name this take")},text={OutlinedTextField(title,{title=it.take(160)},label={Text("Take name")},singleLine=true)},confirmButton={TextButton(onClick={rename?.let{vm.recorder.rename(it,title)};rename=null},enabled=title.isNotBlank()){Text("Save name")}},dismissButton={TextButton(onClick={rename=null}){Text("Cancel")}})
    if(deleting!=null)AlertDialog(onDismissRequest={deleting=null},title={Text("Delete this recording?")},text={Text("This removes the local MIDI take from this device.")},confirmButton={TextButton(onClick={deleting?.let{vm.recorder.delete(it)};deleting=null}){Text("Delete recording")}},dismissButton={TextButton(onClick={deleting=null}){Text("Keep")}})
}
private fun timeLabel(seconds: Double): String {val s=seconds.toInt().coerceAtLeast(0);return "%d:%02d".format(s/60,s%60)}
