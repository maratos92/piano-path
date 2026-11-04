package org.pianopath.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.pianopath.PracticeUi
import org.pianopath.core.PianoLayout
import kotlin.math.*

/**
 * Displays the player's saved MIDI take rather than an idealised score.
 * Onset and release are the recorded key events, not quantised note durations.
 * This is an in-app piano-roll replay, not an audio or video recording.
 */
@Composable fun PerformedRoll(p: PracticeUi,modifier: Modifier=Modifier) {
    val position=if(p.review)p.reviewBeat else p.transportBeat
    val lookAhead=4.0
    Canvas(modifier.semantics { contentDescription="Recorded MIDI roll" }) {
        drawRect(Color(0xff102c2d))
        val w=size.width/52f
        for(key in PianoLayout.keys) {
            val x=key.left.toFloat()*w
            if(key.black)drawRect(Color(0xff0c2325),Offset(x,0f),Size(key.width.toFloat()*w,size.height))
            else drawLine(Color(0xff2d4745),Offset(x,0f),Offset(x,size.height),.6.dp.toPx())
        }
        for(note in p.take) {
            val k=PianoLayout.key(note.pitch) ?: continue
            val duration=(note.duration ?: .12).coerceAtLeast(.08)
            val onset=note.beat
            val release=onset+duration
            if(onset>position+lookAhead || release<position)continue
            val yStart=(1-(release-position)/lookAhead).toFloat()*size.height
            val yEnd=(1-(onset-position)/lookAhead).toFloat()*size.height
            val top=yStart.coerceIn(0f,size.height)
            val bottom=yEnd.coerceIn(0f,size.height)
            val height=(bottom-top).coerceAtLeast(2.dp.toPx())
            val x=k.left.toFloat()*w
            val width=(k.width.toFloat()*w-1.dp.toPx()).coerceAtLeast(1f)
            val color=if(note.expectedId!=null) Color(0xff71d6b1) else Color(0xfff0a27f)
            drawRoundRect(color,Offset(x+.5.dp.toPx(),top),Size(width,height),CornerRadius(2.dp.toPx()))
        }
        val y=size.height-1
        drawLine(Color(0xffeed2a3),Offset(0f,y),Offset(size.width,y),2.dp.toPx())
    }
}
