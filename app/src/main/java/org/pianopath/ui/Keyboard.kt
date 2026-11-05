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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.pianopath.core.*

@Composable fun PianoKeyboard(held: Set<Int>,sounding: Set<Int>,expected: List<Int>,interactive: Boolean,
    onKey: (Int,Boolean)->Unit,height: Int=88,focusRange: IntRange? = null) {
    KeyboardCanvas(held,sounding,expected,interactive,onKey,height,1f,0f,focusRange=focusRange)
}

@Composable fun KeyboardCanvas(held: Set<Int>,sounding: Set<Int>,expected: List<Int>,interactive: Boolean,
    onKey: (Int,Boolean)->Unit,height: Int,zoom: Float,pan: Float,playback: Set<Int> = emptySet(),
    focusRange: IntRange? = null) {
    val currentOnKey by rememberUpdatedState(onKey)
    val accent=MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(height.dp).background(Color(0xff1b2e2b))
        .semantics {contentDescription="88-key piano, A0 to C8"}
        .pointerInput(interactive,zoom,pan) {
            if(!interactive)return@pointerInput
            val down=mutableMapOf<Long,Int>()
            try {
                awaitPointerEventScope {
                    while(true) {
                        val event=awaitPointerEvent()
                        for(c in event.changes) {
                            val id=c.id.value;val old=down[id]
                            val next=if(c.pressed) PianoLayout.hit(
                                PianoLayout.unitX(c.position.x/size.width.toDouble(),zoom.toDouble(),pan.toDouble()),
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
        val w=size.width/52f*zoom
        val offset=pan*(zoom-1)*size.width
        val gap=.7.dp.toPx().coerceAtMost(w*.12f)
        fun color(pitch: Int,black: Boolean) = when {
            focusRange!=null && pitch !in focusRange -> if(black)Color(0xff243633) else Color(0xff83918b)
            else -> when(pitch) {
            in playback->Color(0xff78bfe8)
            in expected->if(black)lerp(Color.Black,accent,.65f) else lerp(Color.White,accent,.3f)
            else->if(black)Color(0xff182b29) else Color(0xfffaf7ef)
        }
        }
        for(key in PianoLayout.whiteKeys) {
            val x=key.left.toFloat()*w-offset
            if(x+w<0||x>size.width)continue
            drawRect(color(key.pitch,false),Offset(x+gap,0f),Size((w-gap*2).coerceAtLeast(.5f),size.height-1))
            if(key.pitch in sounding || key.pitch in held)drawRect(if(key.pitch in held)accent else accent.copy(alpha=.65f),Offset(x+gap+1,1f),Size((w-gap*2-2).coerceAtLeast(.5f),size.height-3),style=androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx().coerceAtMost(w*.3f)))
            if(w>=17.dp.toPx()&&(key.pitch%12==0||key.pitch==21))drawContext.canvas.nativeCanvas.drawText(
                noteName(key.pitch),x+w*.14f,size.height-6.dp.toPx(),android.graphics.Paint().apply {
                    this.color=android.graphics.Color.DKGRAY;textSize=10.sp.toPx();isAntiAlias=true
                })
        }
        for(key in PianoLayout.blackKeys) {
            val x=key.left.toFloat()*w-offset
            if(x+key.width*w<0||x>size.width)continue
            drawRect(color(key.pitch,true),Offset(x,0f),Size(key.width.toFloat()*w,size.height*.62f))
            if(key.pitch in sounding || key.pitch in held)drawRect(if(key.pitch in held)accent else accent.copy(alpha=.65f),Offset(x+1,1f),Size((key.width.toFloat()*w-2).coerceAtLeast(.5f),size.height*.62f-2),style=androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx().coerceAtMost(w*.25f)))
        }
    }
}
