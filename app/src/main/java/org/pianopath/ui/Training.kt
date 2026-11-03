package org.pianopath.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.pianopath.PianoViewModel
import org.pianopath.core.*

@Composable fun ScaleSetup(vm: PianoViewModel) {
    var root by remember {mutableIntStateOf(0)};var kind by remember{mutableStateOf(ScaleKind.MAJOR)};var octaves by remember{mutableIntStateOf(1)};var hand by remember{mutableStateOf(Hand.BOTH)};var descending by remember{mutableStateOf(true)}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        PageTitle("A foundation for fluency","Scales & technique","Find the shape of each key, then make it effortless.")
        Panel(Modifier.fillMaxWidth()) {
            Text("Choose a tonic",fontWeight=FontWeight.SemiBold)
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){(0..11).forEach { n->FilterChip(root==n,{root=n},label={Text(noteName(60+n).dropLast(1))})}}
            Text("Exercise",fontWeight=FontWeight.SemiBold)
            ScaleKind.entries.chunked(2).forEach { row-> Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){row.forEach{k->FilterChip(kind==k,{kind=k},label={Text(k.label)})}} }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){(1..2).forEach{n->FilterChip(octaves==n,{octaves=n},label={Text("$n octave${if(n==2)"s" else ""}")})}}
            HandChoice(hand){hand=it}
            Toggle("Ascend and descend",descending,{descending=it})
        }
        Panel(Modifier.fillMaxWidth()) {
            Text("Fingering you can trust",fontFamily=FontFamily.Serif,fontSize=23.sp)
            Text("Conventional finger numbers are included for common keys. A thumb crossing appears as 1 after a higher finger. Uncurated keys are explicitly left without numbers.",fontSize=14.sp)
            Text("MIDI cannot identify which finger you used. Pitch and timing are evaluated; physical fingering is not measured.",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Button(onClick={vm.scale(root,kind,octaves,hand,descending)},modifier=Modifier.fillMaxWidth()){Text("Open technique practice")}
        TextButton(onClick={vm.screen.value="History"}){Text("View technique progress  →")}
    }
}
@Composable fun SightSetup(vm: PianoViewModel) {
    var level by remember{mutableIntStateOf(0)};var fifths by remember{mutableIntStateOf(0)};var beats by remember{mutableIntStateOf(4)};var hand by remember{mutableStateOf(Hand.BOTH)};var preview by remember{mutableIntStateOf(10)}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        PageTitle("A fresh page, every time","Learn to read ahead.","Short, generated exercises with a clear pulse and room to grow.")
        Panel(Modifier.fillMaxWidth()) {
            Text("Difficulty",fontWeight=FontWeight.SemiBold)
            listOf("Beginner","Elementary","Intermediate","Advanced").chunked(2).forEachIndexed{row,names->Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){names.forEachIndexed{col,name->val n=row*2+col;FilterChip(level==n,{level=n},label={Text(name)})}}}
            Text("Key signature",fontWeight=FontWeight.SemiBold)
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){(-4..4).forEach{n->FilterChip(fifths==n,{fifths=n},label={Text(mapOf(-4 to "A♭",-3 to "E♭",-2 to "B♭",-1 to "F",0 to "C",1 to "G",2 to "D",3 to "A",4 to "E")[n]!!)})}}
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf(3,4,6).forEach{n->FilterChip(beats==n,{beats=n},label={Text(if(n==6)"6/8" else "$n/4")})}}
            HandChoice(hand){hand=it}
            Text("Preview",fontWeight=FontWeight.SemiBold)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf(0,5,10,20).forEach{n->FilterChip(preview==n,{preview=n},label={Text(if(n==0)"None" else "$n sec")})}}
        }
        Text("Each exercise uses a new seed, bounded melodic movement, varied rhythms and independent staves. Higher levels add shorter notes, rests and chords. These levels are training settings, not examination grades.",fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick={vm.sight(level,fifths,beats,hand,preview)},modifier=Modifier.fillMaxWidth()){Text("Generate a new exercise")}
        TextButton(onClick={vm.screen.value="History"}){Text("View sight-reading progress  →")}
    }
}
@Composable private fun HandChoice(hand: Hand,change: (Hand)->Unit) { Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){Hand.entries.forEach{h->FilterChip(hand==h,{change(h)},label={Text(handName(h))})}} }
