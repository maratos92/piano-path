package org.pianopath.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.pianopath.core.*

/** All keyboard and roll renderers share the selected absolute MIDI range. */
val LocalKeyboardRange=staticCompositionLocalOf<IntRange>{21..108}
val LocalNoteNaming=staticCompositionLocalOf{"LETTERS"}

fun displayNoteName(pitch: Int,style: String="LETTERS"): String {
    if(style!="SOLFEGE")return noteName(pitch)
    return listOf("Do","Do♯","Re","Re♯","Mi","Fa","Fa♯","Sol","Sol♯","La","La♯","Si")[Math.floorMod(pitch,12)]+(Math.floorDiv(pitch,12)-1)
}
fun displayNoteText(text: String,style: String="LETTERS"): String {
    if(style!="SOLFEGE")return text
    return Regex("(?<![A-Za-z0-9])([A-G])([♯♭#b]?)(-?[0-9]+)(?![A-Za-z0-9])").replace(text) {match->
        mapOf("C" to "Do","D" to "Re","E" to "Mi","F" to "Fa","G" to "Sol","A" to "La","B" to "Si").getValue(match.groupValues[1])+
            match.groupValues[2].replace("#","♯").replace("b","♭")+match.groupValues[3]
    }
}

@Composable fun PianoKeyboard(held: Set<Int>,sounding: Set<Int>,expected: List<Int>,interactive: Boolean,
    onKey: (Int,Boolean)->Unit,height: Int=88,focusRange: IntRange? = null,filledInput: Boolean=false,attemptBadges: Map<Int,List<String>> = emptyMap()) {
    KeyboardCanvas(held,sounding,expected,interactive,onKey,height,1f,0f,focusRange=focusRange,filledInput=filledInput,attemptBadges=attemptBadges)
}

@Composable fun KeyboardCanvas(held: Set<Int>,sounding: Set<Int>,expected: List<Int>,interactive: Boolean,
    onKey: (Int,Boolean)->Unit,height: Int,zoom: Float,pan: Float,playback: Set<Int> = emptySet(),
    focusRange: IntRange? = null,filledInput: Boolean=false,attemptBadges: Map<Int,List<String>> = emptyMap(),paused: Boolean=false) {
    val currentOnKey by rememberUpdatedState(onKey)
    val accent=MaterialTheme.colorScheme.primary
    val range=LocalKeyboardRange.current
    val naming=LocalNoteNaming.current
    val geometry=remember(range){KeyboardGeometry(range)}
    Canvas(Modifier.fillMaxWidth().height(height.dp).clipToBounds().background(Color(0xff1b2e2b))
        .semantics {contentDescription="${range.last-range.first+1}-key piano, ${noteName(range.first)} to ${noteName(range.last)}"+
            if(attemptBadges.isEmpty())"" else ". Attempts: "+attemptBadges.entries.sortedBy{it.key}.joinToString{(pitch,labels)->
                "${displayNoteName(pitch,naming)} ${labels.joinToString{if(it=="●")"answer" else it}}"
            }}
        .pointerInput(interactive,zoom,pan,range) {
            if(!interactive)return@pointerInput
            val down=mutableMapOf<Long,Int>()
            try {
                awaitPointerEventScope {
                    while(true) {
                        val event=awaitPointerEvent()
                        for(c in event.changes) {
                            val id=c.id.value;val old=down[id]
                            val next=if(c.pressed) geometry.hit(
                                geometry.unitX(c.position.x/size.width.toDouble(),zoom.toDouble(),pan.toDouble()),
                                c.position.y/size.height.toDouble()) else null
                            if(old!=next) {
                                if(old!=null){down.remove(id);if(old !in down.values)currentOnKey(old,false)}
                                if(next!=null){val already=next in down.values;down[id]=next;if(!already)currentOnKey(next,true)}
                            }
                            c.consume()
                        }
                    }
                }
            } finally {down.values.toSet().forEach {currentOnKey(it,false)}}
        }) {
        val w=size.width/geometry.whiteSpan.toFloat()*zoom
        val offset=pan.coerceIn(0f,1f)*(zoom-1)*size.width
        val gap=.7.dp.toPx().coerceAtMost(w*.12f)
        fun color(pitch: Int,black: Boolean) = when {
            filledInput && (pitch in held || pitch in sounding)->Color(0xfff5b942)
            focusRange!=null && pitch !in focusRange -> if(black)Color(0xff243633) else Color(0xff83918b)
            else -> when(pitch) {
            in playback->if(paused)lerp(Color(0xff78bfe8),Color.White,.12f) else Color(0xff78bfe8)
            in expected->if(black)lerp(Color.Black,accent,.65f) else lerp(Color.White,accent,.1f)
            else->if(black)Color(0xff182b29) else Color(0xfffaf7ef)
        }
        }
        fun drawBadges(pitch: Int,x: Float,width: Float,keyHeight: Float,black: Boolean) {
            val labels=attemptBadges[pitch].orEmpty()
            if(labels.isEmpty())return
            val area=(keyHeight-if(black)12.dp.toPx() else 28.dp.toPx()).coerceAtLeast(1f)
            val row=(area/labels.size).coerceAtMost(14.dp.toPx())
            val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color=if(black && !(filledInput && (pitch in held || pitch in sounding)))android.graphics.Color.WHITE else android.graphics.Color.rgb(62,42,15)
                textAlign=android.graphics.Paint.Align.CENTER;typeface=android.graphics.Typeface.DEFAULT_BOLD
            }
            labels.forEachIndexed {i,label->
                paint.textSize=minOf(11.dp.toPx(),row*.8f,width*.78f).coerceAtLeast(2.dp.toPx())
                val measured=paint.measureText(label)
                if(measured>width*.9f)paint.textSize*=width*.9f/measured
                drawContext.canvas.nativeCanvas.drawText(label,x+width/2,6.dp.toPx()+(i+1)*row,paint)
            }
        }
        for(key in geometry.whiteKeys) {
            val x=key.left.toFloat()*w-offset
            if(x+w<0||x>size.width)continue
            drawRect(color(key.pitch,false),Offset(x+gap,0f),Size((w-gap*2).coerceAtLeast(.5f),size.height-1))
            if(key.pitch in sounding || key.pitch in held)drawRect(if(filledInput)Color(0xff694100) else if(key.pitch in held)accent else accent.copy(alpha=.8f),Offset(x+gap+1,1f),Size((w-gap*2-2).coerceAtLeast(.5f),size.height-3),style=androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx().coerceAtMost(w*.4f)))
            drawBadges(key.pitch,x,w,size.height,false)
            if(w>=17.dp.toPx()&&(key.pitch%12==0||key.pitch==range.first))drawContext.canvas.nativeCanvas.drawText(
                displayNoteName(key.pitch,naming),x+w*.14f,size.height-6.dp.toPx(),android.graphics.Paint().apply {
                    this.color=android.graphics.Color.DKGRAY;textSize=10.sp.toPx();isAntiAlias=true
                })
        }
        for(key in geometry.blackKeys) {
            val x=key.left.toFloat()*w-offset
            if(x+key.width*w<0||x>size.width)continue
            drawRect(color(key.pitch,true),Offset(x,0f),Size(key.width.toFloat()*w,size.height*.62f))
            if(key.pitch in sounding || key.pitch in held)drawRect(if(filledInput)Color(0xff694100) else if(key.pitch in held)accent else accent.copy(alpha=.8f),Offset(x+1,1f),Size((key.width.toFloat()*w-2).coerceAtLeast(.5f),size.height*.62f-2),style=androidx.compose.ui.graphics.drawscope.Stroke(2.5.dp.toPx().coerceAtMost(w*.35f)))
            drawBadges(key.pitch,x,key.width.toFloat()*w,size.height*.62f,true)
        }
    }
}
