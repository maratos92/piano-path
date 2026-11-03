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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.pianopath.core.*

@Composable fun KeyboardControls(zoom: Float,pan: Float,onZoom: (Float)->Unit,onPan: (Float)->Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(34.dp).padding(horizontal=10.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
            Text("88 keys · A0–C8",fontSize=11.sp,modifier=Modifier.weight(1f))
            IconButton(onClick={onZoom((zoom-.75f).coerceAtLeast(1f))},modifier=Modifier.size(32.dp),enabled=zoom>1f){Icon(Icons.Outlined.Remove,"Smaller piano keys",Modifier.size(18.dp))}
            TextButton(onClick={onZoom(1f);onPan(0f)},contentPadding=PaddingValues(horizontal=8.dp),modifier=Modifier.height(32.dp)){Text("Fit all 88",fontSize=11.sp)}
            IconButton(onClick={onZoom((zoom+.75f).coerceAtMost(6f))},modifier=Modifier.size(32.dp),enabled=zoom<6f){Icon(Icons.Outlined.Add,"Larger piano keys",Modifier.size(18.dp))}
            if(zoom>1f)TextButton(onClick={
                val target=((PianoLayout.key(60)!!.left+.5)/52.0*zoom-.5)/(zoom-1)
                onPan(target.toFloat().coerceIn(0f,1f))
            },contentPadding=PaddingValues(horizontal=4.dp),modifier=Modifier.height(32.dp)){Text("C4",fontSize=11.sp)}
        }
        if(zoom>1f)Row(Modifier.fillMaxWidth().height(28.dp).padding(horizontal=12.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
            Text("A0",fontSize=10.sp)
            Slider(pan,onPan,modifier=Modifier.weight(1f).height(28.dp).semantics{contentDescription="Keyboard range"})
            Text("C8",fontSize=10.sp)
        }
    }
}

@Composable fun PianoKeyboard(held: Set<Int>,sounding: Set<Int>,expected: List<Int>,interactive: Boolean,onKey: (Int,Boolean)->Unit,height: Int=88) {
    var zoom by rememberSaveable {mutableFloatStateOf(1f)}
    var pan by rememberSaveable {mutableFloatStateOf(0f)}
    Column {KeyboardControls(zoom,pan,{zoom=it},{pan=it});KeyboardCanvas(held,sounding,expected,interactive,onKey,height,zoom,pan)}
}

@Composable fun KeyboardCanvas(held: Set<Int>,sounding: Set<Int>,expected: List<Int>,interactive: Boolean,
    onKey: (Int,Boolean)->Unit,height: Int,zoom: Float,pan: Float,playback: Set<Int> = emptySet()) {
    val currentOnKey by rememberUpdatedState(onKey)
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
        fun color(pitch: Int,black: Boolean) = when(pitch) {
            in held->Color(0xff39a888)
            in playback->Color(0xff78bfe8)
            in sounding->Color(0xff99cdb8)
            in expected->if(black)Color(0xffb38853) else Color(0xfff4d5ae)
            else->if(black)Color(0xff182b29) else Color(0xfffaf7ef)
        }
        for(key in PianoLayout.whiteKeys) {
            val x=key.left.toFloat()*w-offset
            if(x+w<0||x>size.width)continue
            drawRect(color(key.pitch,false),Offset(x+gap,0f),Size((w-gap*2).coerceAtLeast(.5f),size.height-1))
            if(w>=17.dp.toPx()&&(key.pitch%12==0||key.pitch==21))drawContext.canvas.nativeCanvas.drawText(
                noteName(key.pitch),x+w*.14f,size.height-6.dp.toPx(),android.graphics.Paint().apply {
                    this.color=android.graphics.Color.DKGRAY;textSize=10.sp.toPx();isAntiAlias=true
                })
        }
        for(key in PianoLayout.blackKeys) {
            val x=key.left.toFloat()*w-offset
            if(x+key.width*w<0||x>size.width)continue
            drawRect(color(key.pitch,true),Offset(x,0f),Size(key.width.toFloat()*w,size.height*.62f))
        }
    }
}
