package org.pianopath.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.pianopath.PianoViewModel
import org.pianopath.core.noteName
import org.pianopath.midi.MidiStatus
import kotlin.math.pow

/** Compact, live two-staff notation: held MIDI pitches appear as noteheads, never text labels. */
@Composable fun LiveNotes(midi: MidiStatus,modifier: Modifier=Modifier) {
    val held=midi.held.keys.sorted().take(16).ifEmpty { midi.lastNote?.let {listOf(it)} ?: emptyList() }
    Canvas(modifier.fillMaxWidth().height(126.dp).semantics {
        contentDescription=if(held.isEmpty())"No keys held" else "Playing "+held.joinToString {noteName(it)}
    }) {
        val unit=density
        val ink=Color(0xff315650)
        val line=Color(0xffa5b6ac)
        val step=4.5f*unit
        // Bottom lines: E4 (treble) and G2 (bass).
        val top=12f*unit
        val bassTop=78f*unit
        for(i in 0..4) {
            drawLine(line,Offset(8f*unit,top+i*9f*unit),Offset(size.width-8f*unit,top+i*9f*unit),1f*unit)
            drawLine(line,Offset(8f*unit,bassTop+i*9f*unit),Offset(size.width-8f*unit,bassTop+i*9f*unit),1f*unit)
        }
        fun diatonic(pitch: Int): Int = (pitch/12)*7+intArrayOf(0,0,1,1,2,3,3,4,4,5,5,6)[pitch%12]
        val span=(size.width-48f*unit).coerceAtLeast(10f*unit)
        held.forEachIndexed { index,pitch ->
            val treble=pitch>=60
            val x=24f*unit+((index+.5f)/held.size)*span
            val base=if(treble)top+36f*unit else bassTop+36f*unit
            val ref=if(treble)64 else 43
            val y=base-(diatonic(pitch)-diatonic(ref))*step
            val topLine=if(treble)top else bassTop
            val bottomLine=topLine+36f*unit
            var ledger=if(y<topLine)topLine-9f*unit else bottomLine+9f*unit
            while(if(y<topLine)ledger>=y-1f*unit else ledger<=y+1f*unit) {
                drawLine(line,Offset(x-8f*unit,ledger),Offset(x+8f*unit,ledger),1.1f*unit)
                ledger+=if(y<topLine)-9f*unit else 9f*unit
            }
            drawOval(ink,Offset(x-6f*unit,y-3.5f*unit),Size(12f*unit,7f*unit))
            if(pitch%12 in listOf(1,3,6,8,10)) {
                drawContext.canvas.nativeCanvas.drawText("♯",x-15f*unit,y+4f*unit,android.graphics.Paint().apply {
                    color=android.graphics.Color.rgb(49,86,80);textSize=14f*unit;isAntiAlias=true
                })
            }
        }
    }
}
@Composable fun NoteMonitorPage(vm: PianoViewModel,midi: MidiStatus) {
    Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        PageTitle("See what you play","Live note monitor",midi.status)
        LiveNotes(midi)
        midi.lastNote?.let { n ->Text("Last: MIDI $n · velocity ${midi.lastVelocity} · channel ${midi.lastChannel+1}",fontSize=12.sp) }
        Text("Sustain ${if(midi.sustain)"down" else "up"} · ${midi.sounding.size} sounding notes")
        Text("MIDI tells us pitch, velocity and channel. Finger choice and physical hand cannot be measured.",fontSize=13.sp)
        TextButton(onClick={vm.screen.value="Diagnostics"}){Text("Detailed MIDI events")}
        TextButton(onClick={if(vm.ui.value.score!=null)vm.screen.value="Practice" else vm.playWithoutPiano()}){Text("Open practice / touch piano")}
    }
}
