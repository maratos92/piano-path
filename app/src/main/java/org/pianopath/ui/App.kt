@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package org.pianopath.ui

import android.content.Intent
import android.app.Activity
import android.view.WindowManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.pianopath.*
import org.pianopath.core.*
import org.pianopath.data.*
import org.pianopath.midi.MidiStatus
import java.text.SimpleDateFormat
import java.util.*

val Pine=Color(0xff1b554b)
val Ink=Color(0xff173c3b)
val Cream=Color(0xfff8f6f0)
val Muted=Color(0xff6a7870)
val Apricot=Color(0xfff5c9a4)
private val Light=lightColorScheme(primary=Pine,onPrimary=Color.White,secondary=Color(0xff896039),background=Cream,surface=Cream,surfaceVariant=Color(0xffeaece3),onBackground=Ink,onSurface=Ink)
private val Dark=darkColorScheme(primary=Color(0xff9cd6c0),background=Color(0xff142421),surface=Color(0xff142421),surfaceVariant=Color(0xff2a3c35))
@Composable fun PianoApp(vm: PianoViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val screen by vm.screen.collectAsStateWithLifecycle()
    val midi by vm.midi.state.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val config=LocalConfiguration.current
    val wide=config.screenWidthDp>=780
    val compactLandscape=config.screenWidthDp>config.screenHeightDp
    val snackbar=remember { SnackbarHostState() }
    val context=LocalContext.current
    DisposableEffect(screen) {
        val window=(context as? Activity)?.window
        if(screen=="Practice")window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grant -> if(grant.values.all { it })vm.midi.scan() else vm.message.value="Bluetooth access is required for MIDI discovery. You can still import scores and use the simulator." }
    val importer=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { vm.import(it) } }
    val requestScan: ()->Unit={ if(vm.midi.hasPermissions())vm.midi.scan() else permission.launch(vm.midi.requiredPermissions()) }
    MaterialTheme(colorScheme=if(settings.dark)Dark else Light,typography=Typography(bodyLarge=androidx.compose.ui.text.TextStyle(fontSize=16.sp),titleLarge=androidx.compose.ui.text.TextStyle(fontFamily=FontFamily.Serif,fontSize=28.sp))) {
        LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.message.value=null } }
        Surface(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                if(wide || compactLandscape) NavigationRail(containerColor=MaterialTheme.colorScheme.surface) {
                    Text("P",Modifier.padding(16.dp),fontFamily=FontFamily.Serif,fontSize=30.sp,color=MaterialTheme.colorScheme.primary)
                    (if(config.screenHeightDp<500) listOf("Home" to Icons.Outlined.Home,"Library" to Icons.Outlined.LibraryMusic,"History" to Icons.Outlined.Insights,"Settings" to Icons.Outlined.Settings) else listOf("Home" to Icons.Outlined.Home,"Library" to Icons.Outlined.LibraryMusic,"Scales" to Icons.Outlined.Piano,"Sight reading" to Icons.Outlined.Visibility,"History" to Icons.Outlined.Insights,"Settings" to Icons.Outlined.Settings)).forEach { (name,icon) ->
                        NavigationRailItem(selected=screen==name,onClick={vm.screen.value=name},icon={Icon(icon,name)},label={Text(if(name=="Sight reading")"Read" else name)},alwaysShowLabel=config.screenHeightDp>=600)
                    }
                }
                Scaffold(modifier=Modifier.weight(1f),snackbarHost={SnackbarHost(snackbar)},
                    topBar={ if(screen!="Practice") Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Piano,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(27.dp)); Spacer(Modifier.width(10.dp)); Text("PIANO PATH",fontWeight=FontWeight.Bold,letterSpacing=2.sp,fontSize=13.sp)
                        Spacer(Modifier.weight(1f))
                        AssistChip(onClick={vm.screen.value="Connect"},label={Text(if(midi.connected)"Piano connected" else "Connect piano",fontSize=12.sp)},leadingIcon={Icon(Icons.Outlined.Bluetooth, null,Modifier.size(16.dp))})
                    } },bottomBar={ if(!wide && !compactLandscape && screen!="Practice") NavigationBar {
                        listOf("Home" to Icons.Outlined.Home,"Library" to Icons.Outlined.LibraryMusic,"Scales" to Icons.Outlined.Piano,"Sight reading" to Icons.Outlined.Visibility,"Settings" to Icons.Outlined.Settings).forEach { (name,icon) -> NavigationBarItem(selected=screen==name,onClick={vm.screen.value=name},icon={Icon(icon,name)},label={Text(if(name=="Sight reading")"Read" else name)}) }
                    } }) { padding ->
                    Box(Modifier.padding(padding).fillMaxSize()) { when(screen) {
                        "Home" -> Home(vm)
                        "Library" -> Library(vm) { importer.launch(arrayOf("*/*")) }
                        "Connect" -> Connection(vm,midi,requestScan) { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
                        "Practice" -> Practice(vm)
                        "Scales" -> ScaleSetup(vm)
                        "Sight reading" -> SightSetup(vm)
                        "History" -> History(vm)
                        "Diagnostics" -> Diagnostics(vm,midi)
                        else -> SettingsScreen(vm)
                    } }
                }
            }
        }
    }
}
@Composable fun PageTitle(eyebrow: String,title: String,subtitle: String="") {
    Column(Modifier.padding(bottom=20.dp)) {
        Text(eyebrow.uppercase(),fontSize=11.sp,letterSpacing=2.sp,fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.primary)
        Text(title,fontFamily=FontFamily.Serif,fontSize=34.sp,lineHeight=40.sp,modifier=Modifier.padding(top=6.dp))
        if(subtitle.isNotEmpty()) Text(subtitle,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=8.dp))
    }
}
@Composable fun Panel(modifier: Modifier=Modifier,content: @Composable ColumnScope.()->Unit) {
    Surface(modifier=modifier,shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.surfaceContainerLow,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant.copy(alpha=.5f))) { Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp),content=content) }
}
@Composable fun Metric(value: String,label: String,modifier: Modifier=Modifier) {
    Column(modifier) { Text(value,fontSize=28.sp,fontWeight=FontWeight.Medium,color=MaterialTheme.colorScheme.primary);Text(label,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) }
}
@Composable private fun Home(vm: PianoViewModel) {
    val history by vm.history.collectAsStateWithLifecycle(); val p by vm.ui.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        item { PageTitle("A little practice, every day","Make room for music.","Your piano. Your pace. Every note matters.") }
        item { Surface(shape=RoundedCornerShape(24.dp),color=Pine,onClick={if(p.score!=null)vm.screen.value="Practice" else vm.open(vm.library.first())}) {
            Row(Modifier.fillMaxWidth().padding(24.dp),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("YOUR NEXT SESSION",color=Color(0xffbbd8c9),letterSpacing=2.sp,fontSize=11.sp);Spacer(Modifier.height(14.dp));Text(p.score?.title ?: "Start with a simple melody",color=Color.White,fontFamily=FontFamily.Serif,fontSize=27.sp);Spacer(Modifier.height(8.dp));Text(if(p.score==null)"Build confidence, one phrase at a time." else "Continue your selected passage",color=Color(0xffd1e4d8));Spacer(Modifier.height(18.dp));Text("Open practice  →",color=Apricot,fontWeight=FontWeight.Bold) }
                Icon(Icons.Outlined.Piano,null,Modifier.size(72.dp).padding(start=12.dp),tint=Color(0xff9ec9b5))
            }
        } }
        item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            Metric(history.size.toString(),"sessions",Modifier.weight(1f)); Metric(if(history.isEmpty())"—" else "${history.take(10).map { it.pitch }.average().toInt()}%","recent pitch",Modifier.weight(1f)); Metric(history.map { SimpleDateFormat("yyyy-MM-dd",Locale.US).format(Date(it.date)) }.distinct().size.toString(),"practice days",Modifier.weight(1f))
        } }
        item { OutlinedButton(onClick={vm.playWithoutPiano()},modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.Piano,null);Spacer(Modifier.width(8.dp));Text("Play without a piano")} }
        item { Text("Find your focus",fontFamily=FontFamily.Serif,fontSize=24.sp) }
        item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            FocusCard("Technique","Scales & arpeggios",Icons.Outlined.Piano,Modifier.weight(1f)){vm.screen.value="Scales"}
            FocusCard("Read ahead","Fresh sight reading",Icons.Outlined.Visibility,Modifier.weight(1f)){vm.screen.value="Sight reading"}
        } }
        item { Panel(Modifier.fillMaxWidth()) { Row(verticalAlignment=Alignment.CenterVertically) { Icon(Icons.Outlined.Bluetooth,null);Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text("Made for your Roland FP-10",fontWeight=FontWeight.SemiBold);Text("Direct MIDI. Every chord, release and velocity.",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)} }; TextButton(onClick={vm.screen.value="Connect"}){Text("Set up piano connection  →")} } }
        item { TextButton(onClick={vm.screen.value="History"}) { Icon(Icons.Outlined.Insights,null);Spacer(Modifier.width(8.dp));Text("View practice history") } }
    }
}
@Composable private fun FocusCard(title: String,description: String,icon: ImageVector,modifier: Modifier,onClick: ()->Unit) {
    Surface(onClick=onClick,modifier=modifier,shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surfaceContainerHigh) { Column(Modifier.padding(18.dp)) { Icon(icon,null,tint=MaterialTheme.colorScheme.primary);Spacer(Modifier.height(16.dp));Text(title,fontWeight=FontWeight.SemiBold);Text(description,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) } }
}
@Composable private fun Library(vm: PianoViewModel,onImport: ()->Unit) {
    var query by remember { mutableStateOf("") }; var difficulty by remember { mutableStateOf("All") };var info by remember { mutableStateOf<LibraryPiece?>(null) }
    val imports by vm.imports.collectAsStateWithLifecycle()
    val visible=vm.library.filter { (difficulty=="All" || it.difficulty==difficulty) && (it.title+it.composer).contains(query,true) }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { PageTitle("Your music shelf","The library","Offline studies and your own MusicXML editions.") }
        item { Button(onClick=onImport) { Icon(Icons.Outlined.FileOpen,null);Spacer(Modifier.width(8.dp));Text("Import MusicXML / MXL") } }
        item { OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),singleLine=true,placeholder={Text("Search titles or composers")},leadingIcon={Icon(Icons.Outlined.Search,null)}) }
        item { Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf("All","Beginner","Elementary","Intermediate","Upper-intermediate","Advanced").forEach { FilterChip(selected=difficulty==it,onClick={difficulty=it},label={Text(it)}) } } }
        if(imports.isNotEmpty()) {
            item { Text("YOUR IMPORTS",fontSize=11.sp,letterSpacing=2.sp,color=MaterialTheme.colorScheme.primary) }
            items(imports.filter { (it.title+it.composer).contains(query,true) },key={it.key}) { item -> ScoreRow(item.title,item.composer,"Imported",{vm.openImport(item)}) }
        }
        item { Text("${visible.size} ORIGINAL STUDIES",fontSize=11.sp,letterSpacing=2.sp,color=MaterialTheme.colorScheme.primary) }
        items(visible,key={it.key}) { piece -> ScoreRow(piece.title,piece.composer,piece.difficulty,{vm.open(piece)}) {info=piece} }
        item { Panel { Text("Bring your Chopin editions",fontFamily=FontFamily.Serif,fontSize=23.sp);Text("Waltz in A minor, B.150 and Waltz in C-sharp minor, Op. 64 No. 2 are supported through MusicXML import. Their engravings are not bundled because edition-specific redistribution rights have not been verified.",fontSize=14.sp);TextButton(onClick=onImport){Text("Import a legally obtained edition")} } }
    }
    info?.let { piece -> AlertDialog(onDismissRequest={info=null},confirmButton={TextButton(onClick={info=null}){Text("Close")}},title={Text(piece.title)},text={Text("${piece.composer}\n${piece.difficulty}\n\n${piece.license}\n${piece.attribution}\n\nSource: ${piece.source}")}) }
}
@Composable private fun ScoreRow(title: String,composer: String,level: String,onClick: ()->Unit,onInfo: (()->Unit)?=null) {
    Surface(onClick=onClick,shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically) {
            Surface(shape=RoundedCornerShape(10.dp),color=MaterialTheme.colorScheme.surfaceContainerHigh) { Icon(Icons.Outlined.MusicNote,null,Modifier.padding(12.dp),tint=MaterialTheme.colorScheme.primary) }
            Column(Modifier.weight(1f).padding(horizontal=14.dp)) { Text(title,fontWeight=FontWeight.Medium);Text(composer,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(level,fontSize=11.sp,color=MaterialTheme.colorScheme.primary,modifier=Modifier.padding(top=3.dp)) }
            if(onInfo!=null)IconButton(onClick=onInfo){Icon(Icons.Outlined.Info,"Edition and licence")}
            else Icon(Icons.Outlined.ChevronRight,null)
        }
    }
}
@Composable private fun Connection(vm: PianoViewModel,midi: MidiStatus,scan: ()->Unit,bluetoothSettings: ()->Unit) {
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { PageTitle("A direct connection","Meet your piano.","BLE MIDI carries each note directly from the FP-10. No microphone is used.") }
        item { Panel(Modifier.fillMaxWidth()) { Row(verticalAlignment=Alignment.CenterVertically) { Icon(if(midi.connected)Icons.Outlined.CheckCircle else Icons.Outlined.Bluetooth,null,tint=MaterialTheme.colorScheme.primary);Spacer(Modifier.width(12.dp));Text(midi.status,fontWeight=FontWeight.Medium) };if(midi.scanning)LinearProgressIndicator(Modifier.fillMaxWidth());Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick=scan,enabled=!midi.scanning){Text("Find piano")};if(midi.connected)OutlinedButton(onClick={vm.midi.disconnect()}){Text("Disconnect")}} } }
        items(midi.devices,key={it.address}){ d -> Surface(onClick={vm.midi.connect(d.address)},shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.surfaceContainerHigh) { Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Outlined.Piano,null);Column(Modifier.weight(1f).padding(horizontal=14.dp)){Text(d.name,fontWeight=FontWeight.SemiBold);Text(d.address,fontSize=12.sp)};Text("Connect",color=MaterialTheme.colorScheme.primary)} } }
        item { Panel { Text("FP-10 setup",fontFamily=FontFamily.Serif,fontSize=24.sp);Text("1. Power on the piano and enable its Bluetooth MIDI function.\n\n2. Close other apps that may be connected to the FP-10.\n\n3. Tap Find piano and allow Bluetooth access. Select FP-10 from the results.\n\nOn Android 8–11, Android also requires Location permission and the system Location switch for BLE scans.");Text("Connect inside this app. A separate Bluetooth audio pairing does not provide MIDI.",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant);TextButton(onClick=bluetoothSettings){Text("Open Android Bluetooth settings")};TextButton(onClick={vm.screen.value="Diagnostics"}){Text("Open MIDI diagnostics  →")} } }
        item { Text("Hardware status: real FP-10 testing is still required. The app never claims a simulated connection is a hardware connection.",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
@Composable private fun History(vm: PianoViewModel) {
    val all by vm.history.collectAsStateWithLifecycle();var category by remember{mutableStateOf("All")}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { PageTitle("Every repetition counts","Your progress", "Pitch and rhythm are tracked separately, on this device.") }
        item { Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("All","Scales","Sight reading","Score","Imported score").forEach {FilterChip(category==it,{category=it},label={Text(it)})}} }
        if(all.isEmpty()) item { Text("Complete a practice session to start your history.") }
        items(all.filter{category=="All"||it.category==category},key={it.id}) { s -> Panel(Modifier.fillMaxWidth()) {
            Text(s.title,fontWeight=FontWeight.SemiBold);Text(SimpleDateFormat("MMM d, HH:mm",Locale.getDefault()).format(Date(s.date))+" · ${s.bpm.toInt()} BPM · ${s.mode}",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp)){Metric("${s.pitch.toInt()}%","pitch",Modifier.weight(1f));Metric(s.rhythm?.let{"${it.toInt()}%"}?:"—","rhythm",Modifier.weight(1f));Metric("${s.overall.toInt()}%","overall",Modifier.weight(1f))}
            Text("${s.correct}/${s.expected} notes · ${s.extra} extra · ${s.missing} missing${if(s.simulated)" · TOUCH / TEST INPUT" else ""}",fontSize=12.sp)
        } }
    }
}
@Composable private fun SettingsScreen(vm: PianoViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle();val calibrating by vm.calibrating.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        PageTitle("Make it yours","Practice settings")
        Panel(Modifier.fillMaxWidth()) {
            Text("Timing tolerance · ${s.tolerance.toInt()} ms",fontWeight=FontWeight.SemiBold)
            Slider(s.tolerance.toFloat(),{v->vm.setSettings{it.copy(tolerance=v.toDouble())}},valueRange=60f..350f,steps=28)
            Text("Notes inside this onset window are on time. Chord notes may spread over a short interval.",fontSize=13.sp)
            Text("Input latency compensation · ${s.latency.toInt()} ms",fontWeight=FontWeight.SemiBold)
            Slider(s.latency.toFloat(),{v->vm.setSettings{it.copy(latency=v.toDouble())}},valueRange=-300f..300f,steps=119)
            Text("Positive compensation subtracts input delay. Tap a piano key with the 12 clicks to estimate your combined input and tapping offset; use headphones or a low-volume piano.",fontSize=13.sp)
            OutlinedButton(onClick={vm.calibrate()},enabled=!calibrating){Text(if(calibrating)"Tap with each click…" else "Calibrate with MIDI taps")}
        }
        Panel(Modifier.fillMaxWidth()) {
            Toggle("Dark interface",s.dark,{v->vm.setSettings{it.copy(dark=v)}})
            Toggle("Touch piano with sound",s.simulator,{v->vm.setSettings{it.copy(simulator=v)}})
            Text("Play on the phone without an instrument. Listen plays scores aloud; touch keys use a built-in synthesized piano tone. Touch practice is marked in history.",fontSize=13.sp)
            TextButton(onClick={vm.screen.value="Diagnostics"}){Text("MIDI diagnostics")}
            TextButton(onClick={vm.screen.value="History"}){Text("Practice history")}
        }
        Text("Piano Path 0.2.0 · Open source under MIT\nOriginal study scores: CC0-1.0\nNotation: OpenSheetMusicDisplay 1.9.2 (BSD-3-Clause)\nOffline by design. No account, analytics, or network permission.",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable fun Toggle(label: String,checked: Boolean,onChange: (Boolean)->Unit,enabled: Boolean=true) { Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(label,Modifier.weight(1f));Switch(checked,onChange,enabled=enabled)} }
@Composable private fun Diagnostics(vm: PianoViewModel,midi: MidiStatus) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        PageTitle("Direct from the keys","MIDI monitor",midi.status)
        Text("Held: ${midi.held.keys.sorted().joinToString {noteName(it)}}\nSustain: ${if(midi.sustain)"down" else "up"} · Sounding: ${midi.sounding.size}",fontSize=14.sp)
        PianoKeyboard(midi.held.keys,midi.sounding,emptyList(),settings.simulator,vm::key)
        if(settings.simulator)Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick={vm.simulateNext()}){Text("Test chord")};OutlinedButton(onClick={vm.pedal(!midi.sustain)}){Text("Pedal ${if(midi.sustain)"up" else "down"}")}}
        Row { TextButton(onClick={vm.midi.clearKeys()}){Text("Panic / clear notes")};TextButton(onClick={vm.screen.value="Connect"}){Text("Connection")}}
        LazyColumn(Modifier.weight(1f)) { items(midi.events) { Text(it,fontFamily=FontFamily.Monospace,fontSize=11.sp,modifier=Modifier.padding(vertical=4.dp)) } }
    }
}
