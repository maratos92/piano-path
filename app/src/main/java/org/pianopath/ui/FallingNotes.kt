package org.pianopath.ui

import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.pianopath.PracticeUi
import org.pianopath.core.*
import kotlin.math.*

@Composable fun FallingNotes(p: PracticeUi,zoom: Float,pan: Float,modifier: Modifier=Modifier) {
    val score=p.score ?: return
    val start=score.measures[p.startMeasure-1].start+p.startOffset
    val end=score.measures[p.endMeasure-1].start+p.endOffset
    val roll=remember(score,start,end,p.hand){PianoRoll(score,start,end,p.hand)}
    val position=if(p.transportBeat in start..end)p.transportBeat else start
    val lookAhead=(p.bpm/60.0*4).coerceAtLeast(2.0)
    val animated by animateFloatAsState(position.toFloat(),animationSpec=tween(85,easing=LinearEasing),label="falling timeline")
    val visualPosition=if(p.running || p.listening)animated.toDouble() else position
    Canvas(modifier.semantics{contentDescription="Falling notes aligned to the 88-key piano"}) {
        drawRect(Color(0xff102c2d))
        val w=size.width/52f*zoom;val offset=pan*(zoom-1)*size.width
        for(key in PianoLayout.keys) {
            val x=key.left.toFloat()*w-offset
            if(x+key.width*w<0||x>size.width)continue
            if(key.black)drawRect(Color(0xff0c2325),Offset(x,0f),Size(key.width.toFloat()*w,size.height))
            else drawLine(Color(0xff2d4745),Offset(x,0f),Offset(x,size.height),.6.dp.toPx())
        }
        val first=floor(visualPosition).toInt()
        for(beat in first..ceil(visualPosition+lookAhead).toInt()) {
            val y=(1-(beat-visualPosition)/lookAhead).toFloat()*size.height
            if(y !in 0f..size.height)continue
            drawLine(Color(0xff35534d),Offset(0f,y),Offset(size.width,y),if(beat%4==0)1.dp.toPx() else .4.dp.toPx())
        }
        for(bar in roll.visible(visualPosition,lookAhead)) {
            val n=bar.note;val key=PianoLayout.key(n.midi) ?: continue
            val x=key.left.toFloat()*w-offset
            if(x+key.width*w<0||x>size.width)continue
            val top=bar.top.toFloat()*size.height
            val height=((bar.bottom-bar.top)*size.height).toFloat().coerceAtLeast(2.dp.toPx())
            val width=(key.width.toFloat()*w-1.dp.toPx()).coerceAtLeast(1f)
            val color=when(n.id){in p.missed->Color(0xffe4786a);in p.matched->Color(0xffd5ed9c);else->if(n.hand==Hand.LEFT)Color(0xff78bfe8) else Color(0xff71d6b1)}
            drawRoundRect(color,Offset(x+.5.dp.toPx(),top),Size(width,height),CornerRadius(min(3.dp.toPx(),width/3)))
            if(width>14.dp.toPx()&&height>20.dp.toPx()) {
                val label=n.finger.ifBlank {noteName(n.midi)}
                drawContext.canvas.nativeCanvas.drawText(label,x+width*.15f,top+min(height-4.dp.toPx(),14.dp.toPx()),android.graphics.Paint().apply {
                    this.color=android.graphics.Color.rgb(16,44,45);textSize=10.sp.toPx();isAntiAlias=true
                })
            }
        }
        // A/B are musical positions, so their lines move through the rolling viewport.
        if(p.loop) for((label,boundary) in listOf("A" to start,"B" to end)) {
            val y=((1-(boundary-visualPosition)/lookAhead)*size.height).toFloat()
            if(y>=0f && y<=size.height) {
                drawLine(Color(0xffe9b764),Offset(0f,y),Offset(size.width,y),2.dp.toPx())
                drawContext.canvas.nativeCanvas.drawText(label,8.dp.toPx(),(y-6.dp.toPx()).coerceAtLeast(14.dp.toPx()),android.graphics.Paint().apply {
                    color=android.graphics.Color.rgb(238,191,105);textSize=13.sp.toPx();isAntiAlias=true;isFakeBoldText=true
                })
            }
        }
        drawLine(Color(0xffeed2a3),Offset(0f,size.height-1),Offset(size.width,size.height-1),2.dp.toPx())
    }
}
