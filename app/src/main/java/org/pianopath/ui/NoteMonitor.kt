package org.pianopath.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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

fun approximateDuration(durationMs: Long?,bpm: Double): Pair<Double,String> {
    if(durationMs==null)return 0.0 to "held"
    val beats=durationMs.coerceAtMost(12000).toDouble()*bpm.coerceIn(30.0,240.0)/60000.0
    return listOf(.25 to "1/16",.5 to "1/8",1.0 to "1/4",2.0 to "1/2",4.0 to "whole").minBy{abs(it.first-beats)}
}

/** A column is explicit: all its pitches share X, independent of their input attack times. */
data class StaffColumn(val notes: List<PlayedAttack> = emptyList(),val label: String="",val detail: String="",
    val pause: Boolean=false,val correct: Boolean?=null)

/** Native shared staff: Bravura SMuFL glyphs, licensed under the bundled SIL OFL notice. */
@Composable fun PlayedStaff(columns: List<StaffColumn>,modifier: Modifier=Modifier,bpm: Double=80.0,
    followKey: Any?=null,naming: String=LocalNoteNaming.current) {
    val context=LocalContext.current
    val font=remember(context){Typeface.createFromAsset(context.assets,"fonts/Bravura.otf")}
    val scroll=rememberScrollState()
    val ink=MaterialTheme.colorScheme.onSurface
    val lines=MaterialTheme.colorScheme.outlineVariant
    val primary=MaterialTheme.colorScheme.primary
    val wrong=MaterialTheme.colorScheme.error
    val muted=MaterialTheme.colorScheme.onSurfaceVariant
    val height=if(LocalConfiguration.current.screenHeightDp<500)124f else 160f
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val names=columns.map{column->column.notes.joinToString(" "){displayNoteName(it.pitch,naming)}}
        val widths=columns.mapIndexed {i,column->max(64f,max(names[i].length,column.label.length)*6f+24f)}
        val contentWidth=max(maxWidth.value,42f+widths.sum()+24f)
        LaunchedEffect(followKey,scroll.maxValue){scroll.scrollTo(scroll.maxValue)}
        Canvas(Modifier.fillMaxWidth().horizontalScroll(scroll).width(contentWidth.dp).height(height.dp).semantics {
            contentDescription=if(columns.isEmpty())"Played staff empty. Play a note to start the sequence." else
                "Played staff. "+columns.joinToString {column->
                    column.label+": "+if(column.pause)"pause" else column.notes.joinToString(" with "){n->
                        "${displayNoteName(n.pitch,naming)}, channel ${n.channel+1}, at ${n.startedNs} nanoseconds, "+
                            (n.durationMs?.let{"held $it milliseconds"} ?: "still held")
                    }+(column.correct?.let{if(it)" correct" else " incorrect"} ?: "")
                }
        }) {
            val d=density;val vertical=d*height/160f
            val space=7f*vertical;val step=space/2
            val trebleBottom=53f*vertical;val bassBottom=104f*vertical
            fun argb(color: Color)=android.graphics.Color.argb((color.alpha*255).toInt(),(color.red*255).toInt(),(color.green*255).toInt(),(color.blue*255).toInt())
            val glyphPaint=Paint(Paint.ANTI_ALIAS_FLAG).apply{typeface=font;textSize=space*4;textAlign=Paint.Align.LEFT}
            val textPaint=Paint(Paint.ANTI_ALIAS_FLAG).apply{textAlign=Paint.Align.CENTER}
            fun glyph(code: Int,x: Float,y: Float,color: Color=ink) {
                glyphPaint.color=argb(color)
                drawContext.canvas.nativeCanvas.drawText(code.toChar().toString(),x,y,glyphPaint)
            }
            fun text(value: String,x: Float,y: Float,dp: Float,color: Color=ink) {
                textPaint.color=argb(color);textPaint.textSize=dp*d*if(height<160f).85f else 1f
                drawContext.canvas.nativeCanvas.drawText(value,x,y,textPaint)
            }
            for(bottom in listOf(trebleBottom,bassBottom))for(line in 0..4)
                drawLine(lines,Offset(5*d,bottom-line*space),Offset(size.width-8*d,bottom-line*space),.8f*d)
            // SMuFL clef origins are the G4 and F3 staff lines, respectively.
            glyph(0xE050,7*d,trebleBottom-space)
            glyph(0xE062,7*d,bassBottom-3*space)
            if(columns.isEmpty())text("Play a note to start the sequence",size.width/2,70*vertical,11f,muted)
            var left=42f*d
            columns.forEachIndexed {index,column->
                val x=left+widths[index]*d/2
                val columnColor=when(column.correct){true->primary;false->wrong;null->ink}
                if(column.pause) {
                    for(bottom in listOf(trebleBottom,bassBottom))glyph(0xE4E5,x-.54f*space,bottom-2*space,muted)
                } else {
                    val placements=column.notes.map{it to pitchForNotation(it.pitch)}
                    placements.forEachIndexed {noteIndex,(note,pitch)->
                        val treble=pitch.writtenPitch>=60
                        val bottom=if(treble)trebleBottom else bassBottom
                        val relative=pitch.diatonic-if(treble)37 else 25
                        val y=bottom-relative*step
                        val prior=placements.take(noteIndex).filter{(it.second.writtenPitch>=60)==treble}
                        val unisons=prior.count{it.second.writtenPitch==pitch.writtenPitch}
                        val adjacent=prior.count{abs(it.second.diatonic-pitch.diatonic)<=1}
                        val headX=x+if(unisons>0)unisons*11*d else if(adjacent%2==1)8*d else 0f
                        val value=approximateDuration(note.durationMs,bpm)
                        val color=if(column.correct==null && note.durationMs==null)primary else columnColor
                        val whole=value.first>=4.0
                        val headWidth=(if(whole)1.688f else 1.18f)*space
                        val headLeft=headX-headWidth/2
                        ledgerStepsFromStaffBottom(relative).forEach{line->
                            val ledgerY=bottom-line*step
                            drawLine(color,Offset(headLeft-3*d,ledgerY),Offset(headLeft+headWidth+3*d,ledgerY),.9f*d)
                        }
                        glyph(if(whole)0xE0A2 else if(value.first>=2.0)0xE0A3 else 0xE0A4,headLeft,y,color)
                        if(!whole) {
                            val up=relative<4
                            // Official Bravura notehead stem anchors, in SMuFL staff-space units.
                            val stemX=if(up)headLeft+1.18f*space else headLeft
                            val start=y+if(up)-.168f*space else .168f*space
                            val end=y+(if(up)-3.5f else 3.5f)*space
                            drawLine(color,Offset(stemX,start),Offset(stemX,end),.12f*space)
                            if(value.first>0 && value.first<=.5) {
                                val sixteenth=value.first==.25
                                val code=if(up){if(sixteenth)0xE242 else 0xE240}else{if(sixteenth)0xE243 else 0xE241}
                                val anchor=if(up){if(sixteenth)-.088f else -.04f}else{if(sixteenth).128f else .132f}
                                glyph(code,stemX,end+anchor*space,color)
                            }
                        }
                        if(pitch.writtenPitch%12 in listOf(1,3,6,8,10))glyph(0xE262,headLeft-1.3f*space,y,color)
                        if(pitch.octaves!=0) {
                            val mark=when(pitch.octaves){1->"8va";-1->"8vb";2->"15ma";-2->"15mb";3->"22ma";-3->"22mb";else->"${pitch.octaves} oct"}
                            text(mark,headX+17*d,max(8*d,y+2*d),8f,color)
                        }
                    }
                }
                if(column.label.isNotEmpty())text(column.label,x,132*vertical,10f,columnColor)
                text(if(column.pause)"pause" else names[index],x,145*vertical,10f)
                val detail=column.detail.ifEmpty {
                    column.notes.map{approximateDuration(it.durationMs,bpm).second}.distinct().let{if(it.size==1)it.first() else if(it.isNotEmpty())"mixed holds" else ""}
                }
                text(detail,x,157*vertical,9f,muted)
                left+=widths[index]*d
            }
        }
    }
}

/** Recent attacks retain timing; the 60 ms heuristic groups visual columns only. */
@Composable fun LiveNotes(vm: PianoViewModel,modifier: Modifier=Modifier,includeReview: Boolean=true) {
    val all by vm.playedSequence.collectAsStateWithLifecycle()
    val idle by vm.playedIdle.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val practice by vm.ui.collectAsStateWithLifecycle()
    val notes=remember(all,settings.playedNoteCount,practice.review,practice.take,practice.reviewBeat,practice.bpm,includeReview) {
        if(practice.review && includeReview)practice.take.filter{it.beat<=practice.reviewBeat}.takeLast(settings.playedNoteCount).map {
            PlayedAttack(it.pitch,it.channel,(it.beat*60_000_000_000.0/practice.bpm).toLong(),it.duration?.let{duration->(duration*60_000.0/practice.bpm).toLong()})
        } else all.takeLast(settings.playedNoteCount).map{PlayedAttack(it.pitch,it.channel,it.startedNs,it.durationMs)}
    }
    val columns=remember(notes,settings.noteGapMs,idle) {
        val groups=groupPlayedAttacks(notes)
        buildList {
            groups.forEachIndexed {index,group->
                if(index>0) {
                    val previous=groups[index-1]
                    val end=previous.maxOf{it.startedNs+(it.durationMs ?: 0)*1_000_000}
                    if(previous.all{it.durationMs!=null} && group.first().startedNs-end>settings.noteGapMs*1_000_000L)add(StaffColumn(pause=true))
                }
                add(StaffColumn(group))
            }
            if(idle && notes.isNotEmpty())add(StaffColumn(pause=true))
        }
    }
    Column(modifier.fillMaxWidth()) {
        Text("Recent notes · attacks within 60 ms shown as chords (approximate)",fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        PlayedStaff(columns,bpm=practice.bpm,followKey=notes.lastOrNull()?.startedNs to idle,naming=settings.noteNaming)
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
