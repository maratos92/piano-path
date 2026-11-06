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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.pianopath.PianoViewModel
import org.pianopath.core.*
import org.pianopath.midi.MidiStatus
import kotlin.math.abs
import kotlin.math.max

/** Approximate rhythmic value from actual key-down duration at the current quarter-note BPM. */
fun approximateDuration(durationMs: Long?,bpm: Double): Pair<Double,String> {
    if(durationMs==null)return 0.0 to "held"
    val beats=durationMs.coerceAtMost(12000).toDouble()*bpm.coerceIn(30.0,240.0)/60000.0
    return listOf(.25 to "1/16",.5 to "1/8",1.0 to "1/4",2.0 to "1/2",4.0 to "whole").minBy {abs(it.first-beats)}
}

/** Recent attacks keep their actual timing; proximity only groups their visual columns. */
@Composable fun LiveNotes(vm: PianoViewModel,modifier: Modifier=Modifier,includeReview: Boolean=true) {
    val all by vm.playedSequence.collectAsStateWithLifecycle()
    val isIdle by vm.playedIdle.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val practice by vm.ui.collectAsStateWithLifecycle()
    val notes=remember(all,settings.playedNoteCount,practice.review,practice.take,practice.reviewBeat,practice.bpm,includeReview) {
        if(practice.review && includeReview)practice.take.filter{it.beat<=practice.reviewBeat}.takeLast(settings.playedNoteCount).map {
            PlayedAttack(it.pitch,it.channel,(it.beat*60_000_000_000.0/practice.bpm).toLong(),
                it.duration?.let{duration->(duration*60_000.0/practice.bpm).toLong()})
        }
        else all.takeLast(settings.playedNoteCount).map{PlayedAttack(it.pitch,it.channel,it.startedNs,it.durationMs)}
    }
    val columns=remember(notes,settings.noteGapMs,isIdle) {
        val groups=groupPlayedAttacks(notes)
        buildList<List<PlayedAttack>?> {
            groups.forEachIndexed {index,group->
                if(index>0) {
                    val previous=groups[index-1]
                    val previousEnd=previous.maxOf{it.startedNs+(it.durationMs ?: 0)*1_000_000}
                    if(previous.all{it.durationMs!=null} && group.first().startedNs-previousEnd>settings.noteGapMs*1_000_000L)add(null)
                }
                add(group)
            }
            if(isIdle && notes.isNotEmpty())add(null)
        }
    }
    val scroll=rememberScrollState()
    val ink=MaterialTheme.colorScheme.onSurface
    val staff=MaterialTheme.colorScheme.outlineVariant
    val background=MaterialTheme.colorScheme.surface
    val held=MaterialTheme.colorScheme.primary
    val muted=MaterialTheme.colorScheme.onSurfaceVariant
    val canvasHeight=if(LocalConfiguration.current.screenHeightDp<500)124f else 160f
    Column(modifier.fillMaxWidth()) {
        Text("Recent notes · attacks within 60 ms shown as chords (approximate)",fontSize=10.sp,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(columns.isEmpty()) {
            Text("Play a note to start the sequence",Modifier.padding(vertical=12.dp),fontSize=12.sp,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        } else BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Labels have their own column width; the final head and flag retain a full margin.
            val widths=columns.map{group->if(group==null)42f else max(58f,group.joinToString(" "){displayNoteName(it.pitch,settings.noteNaming)}.length*6f+22f)}
            val contentWidth=max(maxWidth.value,38f+widths.sum()+24f)
            LaunchedEffect(notes.lastOrNull()?.startedNs,notes.lastOrNull()?.pitch,isIdle,scroll.maxValue) {
                scroll.scrollTo(scroll.maxValue)
            }
            Canvas(Modifier.fillMaxWidth().horizontalScroll(scroll).width(contentWidth.dp).height(canvasHeight.dp).semantics {
                val origin=notes.firstOrNull()?.startedNs ?: 0
                contentDescription="Recent played notes. Chord grouping is approximate. "+columns.joinToString {group->
                    group?.joinToString(" with ") {n->
                        "${displayNoteName(n.pitch,settings.noteNaming)}, channel ${n.channel+1}, at ${(n.startedNs-origin)/1_000_000} milliseconds, "+
                            (n.durationMs?.let{"held $it milliseconds"} ?: "still held")
                    } ?: "pause"
                }
            }) {
                val d=density
                val vertical=d*canvasHeight/160f
                val step=3.5f*vertical
                val trebleBottom=53f*vertical
                val bassBottom=110f*vertical
                fun text(value: String,x: Float,y: Float,sp: Float,color: Color=ink) {
                    drawContext.canvas.nativeCanvas.drawText(value,x,y,android.graphics.Paint().apply {
                        this.color=android.graphics.Color.argb((color.alpha*255).toInt(),(color.red*255).toInt(),(color.green*255).toInt(),(color.blue*255).toInt())
                        textSize=sp*d;isAntiAlias=true;textAlign=android.graphics.Paint.Align.CENTER
                    })
                }
                for(bottom in listOf(trebleBottom,bassBottom))for(line in 0..4)
                    drawLine(staff,Offset(5*d,bottom-line*step*2),Offset(size.width-8*d,bottom-line*step*2),.8f*d)
                text("𝄞",15*d,54*vertical,35f*canvasHeight/160f)
                text("𝄢",15*d,106*vertical,27f*canvasHeight/160f)
                var left=38f*d
                columns.forEachIndexed {index,group->
                    val width=widths[index]*d
                    val x=left+width/2
                    if(group==null) {
                        val rest=Path().apply {
                            moveTo(x-1.5f*d,64*vertical);lineTo(x+2*d,68*vertical);lineTo(x-2*d,72*vertical)
                            lineTo(x+2*d,75*vertical);lineTo(x,78*vertical)
                        }
                        drawPath(rest,held,style=Stroke(1.1f*d,cap=StrokeCap.Round))
                        text("pause",x,148*vertical,10f)
                    } else {
                        val placements=group.map{it to pitchForNotation(it.pitch)}
                        placements.forEachIndexed {noteIndex,(note,pitch)->
                            val treble=pitch.writtenPitch>=60
                            val bottom=if(treble)trebleBottom else bassBottom
                            val reference=if(treble)37 else 25 // E4 and G2, in diatonic steps.
                            val relative=pitch.diatonic-reference
                            val y=bottom-relative*step
                            // Adjacent chord heads alternate sides; duplicate unisons stay visible.
                            val collision=placements.take(noteIndex).count{(_,other)->
                                (other.writtenPitch>=60)==treble && abs(other.diatonic-pitch.diatonic)<=1
                            }
                            val unisons=placements.take(noteIndex).count{it.second.writtenPitch==pitch.writtenPitch}
                            val headX=x+if(unisons>0)unisons*13*d else if(collision%2==1)9*d else 0f
                            ledgerStepsFromStaffBottom(relative).forEach{line->
                                val ledgerY=bottom-line*step
                                drawLine(ink,Offset(headX-8*d,ledgerY),Offset(headX+8*d,ledgerY),.9f*d)
                            }
                            val value=approximateDuration(note.durationMs,practice.bpm)
                            val color=if(note.durationMs==null)held else ink
                            val hollow=value.first>=2.0
                            val headWidth=if(value.first>=4.0)13f*d else 11f*d
                            rotate(-18f,Offset(headX,y)) {
                                drawOval(if(hollow)background else color,Offset(headX-headWidth/2,y-3.4f*d),Size(headWidth,6.8f*d))
                                if(hollow)drawOval(color,Offset(headX-headWidth/2,y-3.4f*d),Size(headWidth,6.8f*d),style=Stroke(1.3f*d))
                            }
                            if(value.first<4.0) {
                                val up=relative<4
                                val direction=if(up)-1f else 1f
                                val stemX=headX+(if(up)4.8f else -4.8f)*d
                                val end=y+direction*22*vertical
                                drawLine(color,Offset(stemX,y+direction*1.2f*d),Offset(stemX,end),1.2f*d)
                                if(value.first>0 && value.first<=.5)repeat(if(value.first==.25)2 else 1) {flag->
                                    val start=end-direction*flag*4*d
                                    val toward=-direction
                                    val path=Path().apply {
                                        moveTo(stemX,start)
                                        cubicTo(stemX+7*d,start+toward*2*d,stemX+9*d,start+toward*6*d,stemX+3*d,start+toward*11*d)
                                    }
                                    drawPath(path,color,style=Stroke(1.1f*d,cap=StrokeCap.Round))
                                }
                            }
                            if(pitch.writtenPitch%12 in listOf(1,3,6,8,10))text("♯",headX-12*d,y+3.5f*d,12f,color)
                            if(pitch.octaves!=0) {
                                val mark=when(pitch.octaves) {
                                    1->"8va";-1->"8vb";2->"15ma";-2->"15mb";3->"22ma";-3->"22mb"
                                    else->"${pitch.octaves} oct"
                                }
                                text(mark,headX+18*d,max(8*d,y+2*d),8f,color)
                            }
                        }
                        text(group.joinToString(" "){displayNoteName(it.pitch,settings.noteNaming)},x,143*vertical,10f)
                        val values=group.map{approximateDuration(it.durationMs,practice.bpm).second}.distinct()
                        text(if(values.size==1)values.first() else "mixed holds",x,156*vertical,9f,muted)
                    }
                    left+=width
                }
            }
        }
    }
}

@Composable fun NoteMonitorPage(vm: PianoViewModel,midi: MidiStatus) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        PageTitle("MIDI","Played notes")
        Text("Last ${settings.playedNoteCount} attacks · key-down duration (approximate values)",fontSize=12.sp)
        LiveNotes(vm,Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth()) {
            Text("Sustain: ${if(midi.sustain)"down" else "up"}",fontSize=12.sp,modifier=Modifier.weight(1f))
            TextButton(onClick={vm.clearPlayedSequence()}){Text("Clear")}
        }
        TextButton(onClick={vm.screen.value="Settings"}){Text("Display settings")}
        TextButton(onClick={vm.screen.value="Diagnostics"}){Text("MIDI events")}
    }
}
