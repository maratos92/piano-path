package org.pianopath.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.pianopath.NoteCapture
import org.pianopath.PianoViewModel
import org.pianopath.core.noteName
import org.pianopath.midi.MidiStatus
import kotlin.math.abs
import kotlin.math.max

/** Approximate rhythmic value from actual key-down duration at the current quarter-note BPM. */
fun approximateDuration(durationMs: Long?,bpm: Double): Pair<Double,String> {
    if(durationMs==null)return 0.0 to "held"
    val beats=durationMs.coerceAtMost(12000).toDouble()*bpm.coerceIn(30.0,240.0)/60000.0
    return listOf(.25 to "1/16",.5 to "1/8",1.0 to "1/4",2.0 to "1/2",4.0 to "whole").minBy {abs(it.first-beats)}
}

/** Last N notes in time order, with rests for gaps longer than the configured threshold. */
@Composable fun LiveNotes(vm: PianoViewModel,modifier: Modifier=Modifier) {
    val all by vm.playedSequence.collectAsStateWithLifecycle()
    val isIdle by vm.playedIdle.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val practice by vm.ui.collectAsStateWithLifecycle()
    val notes=all.takeLast(settings.playedNoteCount)
    val tokens=remember(notes,settings.noteGapMs,isIdle) {
        buildList<NoteCapture?> {
            notes.forEachIndexed {index,n->
                if(index>0) {
                    val previous=notes[index-1]
                    val gap=(n.startedNs-previous.startedNs)/1_000_000L-(previous.durationMs ?: 0L)
                    if(gap>settings.noteGapMs) add(null)
                }
                add(n)
            }
            if(isIdle && notes.isNotEmpty()) add(null)
        }
    }
    if(tokens.isEmpty()) {
        Text("Play a note to start the sequence",modifier=modifier.padding(12.dp),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Row(modifier.horizontalScroll(rememberScrollState())) {
        Canvas(Modifier.width(max(320,tokens.size*55).dp).height(154.dp).semantics {
            contentDescription="Recent notes: "+tokens.joinToString {n ->
                if(n==null)"pause" else noteName(n.pitch)+", "+approximateDuration(n.durationMs,practice.bpm).second
            }
        }) {
            val d=density
            val ink=Color(0xff315650)
            val pale=Color(0xff9eafa6)
            val step=4.5f*d
            val top=13f*d
            val bassTop=77f*d
            for(i in 0..4)for(t in listOf(top,bassTop))drawLine(pale,Offset(6f*d,t+i*9f*d),Offset(size.width-6f*d,t+i*9f*d),1f*d)
            fun diatonic(pitch:Int):Int=(pitch/12)*7+intArrayOf(0,0,1,1,2,3,3,4,4,5,5,6)[pitch%12]
            fun textAt(content:String,x:Float,y:Float,px:Float,shade:Color=ink) {
                drawContext.canvas.nativeCanvas.drawText(content,x,y,android.graphics.Paint().apply {
                    color=android.graphics.Color.argb((shade.alpha*255).toInt(),(shade.red*255).toInt(),(shade.green*255).toInt(),(shade.blue*255).toInt())
                    textSize=px*d;isAntiAlias=true;textAlign=android.graphics.Paint.Align.CENTER
                })
            }
            val width=size.width/tokens.size
            tokens.forEachIndexed { index,n ->
                val x=width*(index+.5f)
                if(n==null) {
                    textAt("𝄽",x,69f*d,24f,Color(0xffb17a2d))
                    textAt("pause",x,149f*d,10f)
                } else {
                    val treble=n.pitch>=60
                    val base=if(treble)top+36f*d else bassTop+36f*d
                    val ref=if(treble)64 else 43
                    val y=(base-(diatonic(n.pitch)-diatonic(ref))*step).coerceIn((if(treble)top-16*d else bassTop-16*d),(if(treble)top+52*d else bassTop+43*d))
                    val value=approximateDuration(n.durationMs,practice.bpm)
                    val hollow=value.first>=2.0
                    val noteColor=if(n.durationMs==null)Color(0xffbd8b3b) else ink
                    drawOval(if(hollow)Color(0xfff8f6f0) else noteColor,Offset(x-6f*d,y-4f*d),Size(12f*d,8f*d))
                    if(hollow)drawOval(noteColor,Offset(x-6f*d,y-4f*d),Size(12f*d,8f*d),style=androidx.compose.ui.graphics.drawscope.Stroke(1.4f*d))
                    if(value.first>0 && value.first<4.0) {
                        drawLine(noteColor,Offset(x+5f*d,y),Offset(x+5f*d,y-23f*d),1.5f*d)
                        if(value.first<=.5) {
                            drawLine(noteColor,Offset(x+5f*d,y-23f*d),Offset(x+13f*d,y-13f*d),2f*d)
                            if(value.first==.25)drawLine(noteColor,Offset(x+5f*d,y-18f*d),Offset(x+13f*d,y-8f*d),2f*d)
                        }
                    }
                    if(n.pitch%12 in listOf(1,3,6,8,10))textAt("♯",x-13f*d,y+4f*d,12f)
                    textAt(noteName(n.pitch),x,137f*d,10f)
                    textAt(value.second,x,150f*d,10f,Color(0xff637b71))
                }
            }
        }
    }
}
@Composable fun NoteMonitorPage(vm: PianoViewModel,midi: MidiStatus) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        PageTitle("MIDI","Played notes")
        Text("Last ${settings.playedNoteCount} notes · key-down duration (approximate values)",fontSize=12.sp)
        LiveNotes(vm)
        Row(Modifier.fillMaxWidth()) {
            Text("Sustain: ${if(midi.sustain)"down" else "up"}",fontSize=12.sp,modifier=Modifier.weight(1f))
            TextButton(onClick={vm.clearPlayedSequence()}){Text("Clear")}
        }
        TextButton(onClick={vm.screen.value="Settings"}){Text("Display settings")}
        TextButton(onClick={vm.screen.value="Diagnostics"}){Text("MIDI events")}
    }
}
