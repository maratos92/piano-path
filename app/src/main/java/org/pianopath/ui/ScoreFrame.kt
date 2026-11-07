package org.pianopath.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

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
