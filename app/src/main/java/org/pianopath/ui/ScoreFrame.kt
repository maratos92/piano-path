package org.pianopath.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import org.pianopath.SheetDecoration
import org.pianopath.core.noteName

/** Decorative strips occupy their own space; no artwork is drawn behind musical notation. */
@Composable fun ScoreFrame(style: SheetDecoration,modifier: Modifier=Modifier,content: @Composable ()->Unit) {
    if(style==SheetDecoration.NONE){Box(modifier){content()};return}
    Column(modifier) {
        FrameStrip(style)
        Box(Modifier.weight(1f).fillMaxWidth()){content()}
        FrameStrip(style)
    }
}
@Composable private fun FrameStrip(style: SheetDecoration) {
    val colour=MaterialTheme.colorScheme.primary.copy(alpha=.23f)
    val base=MaterialTheme.colorScheme.surfaceContainerLow
    Canvas(Modifier.fillMaxWidth().height(12.dp)) {
        drawRect(base)
        val h=size.height
        val count=(size.width/48.dp.toPx()).toInt()+1
        for(i in 0..count){
            val x=i*48.dp.toPx()+12.dp.toPx()
            when(style) {
                SheetDecoration.RAIN->repeat(3){n->drawLine(colour,Offset(x+n*8.dp.toPx(),h*.15f),Offset(x+n*8.dp.toPx()-3.dp.toPx(),h*.8f),1.dp.toPx())}
                SheetDecoration.LEAVES->{
                    val path=Path().apply{moveTo(x-6.dp.toPx(),h*.75f);cubicTo(x-7.dp.toPx(),0f,x+5.dp.toPx(),0f,x+7.dp.toPx(),h*.25f);cubicTo(x+5.dp.toPx(),h,x-3.dp.toPx(),h,x-6.dp.toPx(),h*.75f)}
                    drawPath(path,colour);drawLine(colour,Offset(x-7.dp.toPx(),h*.8f),Offset(x+8.dp.toPx(),h*.2f),.6.dp.toPx())
                }
                SheetDecoration.CLOUDS->{drawCircle(colour,3.dp.toPx(),Offset(x-4.dp.toPx(),h*.6f));drawCircle(colour,4.dp.toPx(),Offset(x+2.dp.toPx(),h*.45f));drawCircle(colour,3.dp.toPx(),Offset(x+7.dp.toPx(),h*.6f))}
                SheetDecoration.ORBIT->{drawCircle(colour,2.4.dp.toPx(),Offset(x,h*.5f));drawOval(colour,Offset(x-8.dp.toPx(),h*.25f),Size(16.dp.toPx(),h*.5f),style=Stroke(.7.dp.toPx()));drawCircle(colour,.8.dp.toPx(),Offset(x+19.dp.toPx(),h*.25f))}
                SheetDecoration.NONE->Unit
            }
        }
    }
}

@Composable fun KeyboardRangeSettings(first: Int,last: Int,onChange: (Int,Int)->Unit) {
    val naming=LocalNoteNaming.current
    var low by remember(first){mutableStateOf(first.toString())}
    var high by remember(last){mutableStateOf(last.toString())}
    Text("Keyboard range",style=MaterialTheme.typography.titleMedium)
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        listOf(49 to (36..84),61 to (36..96),76 to (28..103),88 to (21..108)).forEach{(count,range)->
            FilterChip(first==range.first && last==range.last,{onChange(range.first,range.last)},label={Text("$count keys")})
        }
    }
    Text("${last-first+1} keys · ${displayNoteName(first,naming)}–${displayNoteName(last,naming)}. MIDI pitch stays unchanged.",style=MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(low,{low=it.filter{c->c.isDigit()}.take(3)},Modifier.weight(1f),label={Text("Lowest MIDI note")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
        OutlinedTextField(high,{high=it.filter{c->c.isDigit()}.take(3)},Modifier.weight(1f),label={Text("Highest MIDI note")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
    }
    val a=low.toIntOrNull();val b=high.toIntOrNull()
    TextButton(onClick={if(a!=null && b!=null)onChange(a,b)},enabled=a!=null && b!=null && a in 0..127 && b in a..127 && (a!=first || b!=last)){Text("Apply custom range")}
    Text("Presets use common endpoints; check your instrument's lowest and highest notes. Out-of-range score notes remain graded and are flagged in Practice.",style=MaterialTheme.typography.bodySmall)
}
