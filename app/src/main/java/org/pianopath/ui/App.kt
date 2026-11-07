@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package org.pianopath.ui

import android.content.Intent
import android.app.Activity
import android.view.WindowManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.pianopath.*
import org.pianopath.core.*
import org.pianopath.data.*
import org.pianopath.midi.MidiStatus
import org.pianopath.audio.PianoSound
import android.net.Uri
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
    val exerciseState=androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    val midi by vm.midi.state.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val config=LocalConfiguration.current
    val wide=config.screenWidthDp>=780
    val compactLandscape=config.screenWidthDp>config.screenHeightDp
    val snackbar=remember { SnackbarHostState() }
    val context=LocalContext.current
    DisposableEffect(screen,settings.keepAwake) {
        val window=(context as? Activity)?.window
        if(settings.keepAwake)window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grant -> if(grant.values.all { it })vm.midi.scan() else vm.message.value="Bluetooth access is required for MIDI discovery. You can still import scores and use the simulator." }
    val importer=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { vm.import(it) } }
    val requestScan: ()->Unit={ if(vm.midi.hasPermissions())vm.midi.scan() else permission.launch(vm.midi.requiredPermissions()) }
    val drawer=rememberDrawerState(DrawerValue.Closed)
    val scope=rememberCoroutineScope()
    var screenHistory by rememberSaveable { mutableStateOf(listOf(screen)) }
    var backTarget by remember { mutableStateOf<String?>(null) }
    // All entry points, including ViewModel-generated exercises, write to this flow.
    // Observe those writes so Back returns to the screen the user actually came from.
    LaunchedEffect(vm) {
        vm.screen.collect { destination ->
            if(destination==backTarget) {
                backTarget=null
            } else if(screenHistory.lastOrNull()!=destination) {
                screenHistory=if(destination=="Home")listOf("Home") else screenHistory+destination
            }
        }
    }
    val drawerVisible=drawer.currentValue!=DrawerValue.Closed || drawer.targetValue!=DrawerValue.Closed
    BackHandler(enabled=drawerVisible || screen!="Home") {
        if(drawerVisible) scope.launch { drawer.close() }
        else {
            val visited=if(screenHistory.lastOrNull()==screen)screenHistory else screenHistory+screen
            val remaining=visited.dropLast(1).ifEmpty { listOf("Home") }
            val destination=remaining.last()
            screenHistory=remaining
            backTarget=destination
            vm.screen.value=destination
        }
    }
    val destinations=listOf("Home" to Icons.Outlined.Home,"Practice hub" to Icons.Outlined.Piano,"Library" to Icons.Outlined.LibraryMusic,"Recorder" to Icons.Outlined.GraphicEq,"Progress" to Icons.Outlined.Insights,"Settings" to Icons.Outlined.Settings)
    val dark=settings.theme==ThemeMode.DARK || settings.theme==ThemeMode.SYSTEM && isSystemInDarkTheme()
    val accent=Color(settings.accent)
    val primary=if(dark)lerp(accent,Color.White,.45f) else lerp(accent,Color.Black,.12f)
    val palette=(if(dark)Dark else Light).copy(primary=primary,onPrimary=if(primary.luminance()>.179f)Color.Black else Color.White,secondary=primary,primaryContainer=primary.copy(alpha=.15f))
    MaterialTheme(colorScheme=palette,typography=Typography(bodyLarge=androidx.compose.ui.text.TextStyle(fontSize=16.sp),titleLarge=androidx.compose.ui.text.TextStyle(fontFamily=FontFamily.Serif,fontSize=24.sp))) {
        LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.message.value=null } }
        CompositionLocalProvider(LocalKeyboardRange provides (settings.keyboardFirst..settings.keyboardLast),LocalNoteNaming provides settings.noteNaming) {
        ModalNavigationDrawer(drawerState=drawer,gesturesEnabled=false,drawerContent={
            ModalDrawerSheet(Modifier.width(280.dp)) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Piano Path",Modifier.padding(24.dp),fontSize=26.sp,fontFamily=FontFamily.Serif)
                destinations.forEach { (name,icon) ->
                    NavigationDrawerItem(label={Text(navLabel(name))},icon={Icon(icon,null)},selected=navigationSection(screen)==name,onClick={scope.launch{drawer.close()};vm.stop();vm.stopListening();vm.screen.value=name},modifier=Modifier.padding(horizontal=12.dp))
                }
                }
            }
        }) { Surface(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Scaffold(modifier=Modifier.weight(1f),snackbarHost={SnackbarHost(snackbar)},
                    topBar={ if(screen!="Practice") Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Piano,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(27.dp)); Spacer(Modifier.width(10.dp)); Text("PIANO PATH",fontWeight=FontWeight.Bold,letterSpacing=2.sp,fontSize=13.sp)
                        Spacer(Modifier.weight(1f))
                        AssistChip(onClick={vm.screen.value="Connect"},label={Text(if(midi.connected)"Piano connected" else "Connect piano",fontSize=12.sp)},leadingIcon={ConnectionStatusIcon(midi.connected,settings.reduceMotion,Modifier.size(16.dp))})
                    } },bottomBar={ if(screen!="Practice") NavigationBar {
                        destinations.forEach { (name,icon) -> NavigationBarItem(
                            selected=navigationSection(screen)==name,
                            onClick={vm.screen.value=name},
                            icon={Icon(icon,name)},
                            label={Text(navLabel(name),fontSize=11.sp,maxLines=1,softWrap=false,textAlign=androidx.compose.ui.text.style.TextAlign.Center)},
                            colors=NavigationBarItemDefaults.colors(
                                selectedIconColor=MaterialTheme.colorScheme.primary,
                                selectedTextColor=MaterialTheme.colorScheme.primary,
                                indicatorColor=Color.Transparent,
                                unselectedIconColor=MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor=MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) }
                    } }) { padding ->
                    Box(Modifier.padding(padding).fillMaxSize()) { when(screen) {
                        "Home" -> Home(vm)
                        "Practice hub" -> PracticeHub(vm)
                        "Library" -> Library(vm) { importer.launch(arrayOf("*/*")) }
                        "Connect" -> Connection(vm,midi,requestScan) { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
                        "Practice" -> Practice(vm){scope.launch{drawer.open()}}
                        "Note monitor" -> RecorderScreen(vm)
                        "Scales" -> exerciseState.SaveableStateProvider("Scales"){ScaleSetup(vm)}
                        "Sight reading" -> exerciseState.SaveableStateProvider("Sight reading"){SightSetup(vm)}
                        "Ear training" -> EarTraining(vm)
                        "Recorder" -> RecorderScreen(vm)
                        "Progress" -> ProgressScreen(vm)
                        "History" -> ProgressScreen(vm)
                        "Diagnostics" -> RecorderScreen(vm,initialEvents=true)
                        else -> SettingsScreen(vm)
                    } }
                }
            }
        } }
        }
    }
}
/** Connection feedback is local to the live status change, never an ongoing animation. */
@Composable fun ConnectionStatusIcon(
    connected: Boolean,
    reduceMotion: Boolean,
    modifier: Modifier=Modifier,
    disconnectedIcon: ImageVector=Icons.Outlined.Bluetooth
) {
    val pulse=remember { Animatable(1f) }
    var wasConnected by remember { mutableStateOf(connected) }
    LaunchedEffect(connected,reduceMotion) {
        val justConnected=connected && !wasConnected
        wasConnected=connected
        pulse.snapTo(1f)
        if(justConnected && !reduceMotion) {
            pulse.animateTo(1.25f,tween(180))
            pulse.animateTo(1f,tween(240))
        }
    }
    Icon(
        if(connected)Icons.Outlined.CheckCircle else disconnectedIcon,
        contentDescription=null,
        modifier=modifier.scale(pulse.value),
        tint=MaterialTheme.colorScheme.primary
    )
}

@Composable fun PageTitle(title: String,modifier: Modifier=Modifier) {
    Text(title,fontFamily=FontFamily.Serif,fontSize=26.sp,lineHeight=32.sp,modifier=modifier)
}
@Composable fun Panel(modifier: Modifier=Modifier,content: @Composable ColumnScope.()->Unit) {
    Surface(modifier=modifier,shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.surfaceContainerLow,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant.copy(alpha=.5f))) { Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp),content=content) }
}
@Composable fun Metric(value: String,label: String,modifier: Modifier=Modifier) {
    Column(modifier) { Text(value,fontSize=28.sp,fontWeight=FontWeight.Medium,color=MaterialTheme.colorScheme.primary);Text(label,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) }
}
@Composable private fun PracticeHub(vm: PianoViewModel) {
    val p by vm.ui.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { PageTitle("Practice") }
        item { Panel(Modifier.fillMaxWidth()) {
            Text("Choose a session",fontWeight=FontWeight.SemiBold)
            OutlinedButton(onClick={vm.screen.value="Sight reading"},modifier=Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Visibility,null);Spacer(Modifier.width(10.dp));Text("Sight reading",Modifier.weight(1f))
                Icon(Icons.Outlined.MusicNote,null)
            }
            OutlinedButton(onClick={vm.screen.value="Ear training"},modifier=Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.SurroundSound,null);Spacer(Modifier.width(10.dp));Text("Listening",Modifier.weight(1f))
                Icon(Icons.Outlined.MusicNote,null)
            }
            OutlinedButton(onClick={vm.screen.value="Scales"},modifier=Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Piano,null);Spacer(Modifier.width(10.dp));Text("Scales",Modifier.weight(1f))
                Icon(Icons.Outlined.MusicNote,null)
            }
        } }
        item { Panel(Modifier.fillMaxWidth()) {
            Text("Pieces & free play",fontWeight=FontWeight.SemiBold)
            if(p.score!=null) Button(onClick={vm.screen.value="Practice"},modifier=Modifier.fillMaxWidth()){
                Icon(Icons.Outlined.PlayArrow,null);Spacer(Modifier.width(8.dp));Text("Start")
            }
            OutlinedButton(onClick={vm.screen.value="Library"},modifier=Modifier.fillMaxWidth()){Text("Choose a piece from Library")}
            OutlinedButton(onClick={vm.openRecorder()},modifier=Modifier.fillMaxWidth()){Text("Studio · play, monitor & record")}
        } }
        item { TextButton(onClick={vm.screen.value="Progress"}){Icon(Icons.Outlined.Insights,null);Spacer(Modifier.width(8.dp));Text("Your practice · goals & progress")} }
    }
}
@Composable private fun Home(vm: PianoViewModel) {
    val history by vm.history.collectAsStateWithLifecycle(); val p by vm.ui.collectAsStateWithLifecycle()
    val imports by vm.imports.collectAsStateWithLifecycle();val recents by vm.recentKeys.collectAsStateWithLifecycle()
    val recent=recents.mapNotNull{key->imports.find{it.key==key}}
    LazyColumn(Modifier.fillMaxSize().testTag("home-feed"),contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        item { PageTitle("Home") }
        item { Surface(modifier=Modifier.testTag("home-start-session"),shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.primary,onClick={if(p.score!=null)vm.screen.value="Practice" else vm.scale(0,ScaleKind.MAJOR,1,Hand.BOTH,true)}) {
            Row(Modifier.fillMaxWidth().padding(24.dp),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("YOUR NEXT SESSION",color=MaterialTheme.colorScheme.onPrimary.copy(alpha=.8f),letterSpacing=2.sp,fontSize=11.sp);Spacer(Modifier.height(14.dp));Text(p.score?.title ?: "Start with a scale",color=MaterialTheme.colorScheme.onPrimary,fontFamily=FontFamily.Serif,fontSize=27.sp);Spacer(Modifier.height(8.dp));Text(if(p.score==null)"C major · both hands" else "Continue practice",color=MaterialTheme.colorScheme.onPrimary.copy(alpha=.8f));Spacer(Modifier.height(18.dp));Text("Start",color=MaterialTheme.colorScheme.onPrimary,fontWeight=FontWeight.Bold) }
                Icon(Icons.Outlined.Piano,null,Modifier.size(72.dp).padding(start=12.dp),tint=MaterialTheme.colorScheme.onPrimary.copy(alpha=.7f))
            }
        } }
        item { Text("Recently opened",style=MaterialTheme.typography.titleLarge) }
        if(recent.isEmpty())item { Text("Your imported pieces appear here after you open them.",color=MaterialTheme.colorScheme.onSurfaceVariant) }
        items(recent.take(5),key={it.key}) {piece->ScoreRow(piece.title,piece.composer,"Continue",{vm.openImport(piece)})}
        item { OutlinedButton(onClick={vm.playWithoutPiano()},modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.Piano,null);Spacer(Modifier.width(8.dp));Text("Play without a piano")} }
        item { OutlinedButton(onClick={vm.openRecorder()},modifier=Modifier.fillMaxWidth()){Text("Free play recordings")} }
        item { OutlinedButton(onClick={vm.screen.value="Progress"},modifier=Modifier.fillMaxWidth()){Text("Your practice · goals & progress")} }
        item { Text("Find your focus",fontFamily=FontFamily.Serif,fontSize=24.sp) }
        item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            FocusCard("Scales","",Icons.Outlined.Piano,Modifier.weight(1f)){vm.screen.value="Scales"}
            FocusCard("Sight reading","",Icons.Outlined.Visibility,Modifier.weight(1f)){vm.screen.value="Sight reading"}
            FocusCard("Listening","",Icons.Outlined.SurroundSound,Modifier.weight(1f)){vm.screen.value="Ear training"}
        } }

    }
}
@Composable private fun FocusCard(title: String,description: String,icon: ImageVector,modifier: Modifier,onClick: ()->Unit) {
    Surface(onClick=onClick,modifier=modifier.height(118.dp),shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surfaceContainerHigh) { Column(Modifier.fillMaxSize().padding(12.dp),verticalArrangement=Arrangement.Center) { Icon(icon,null,tint=MaterialTheme.colorScheme.primary);Spacer(Modifier.height(16.dp));Text(title,fontWeight=FontWeight.SemiBold);if(description.isNotEmpty())Text(description,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) } }
}
@Composable private fun Library(vm: PianoViewModel,onImport: ()->Unit) {
    var query by remember { mutableStateOf("") }
    val imports by vm.imports.collectAsStateWithLifecycle()
    val favourites by vm.favourites.collectAsStateWithLifecycle()
    var onlyFavourites by remember{mutableStateOf(false)}
    val visible=imports.filter {(it.title+it.composer).contains(query,true) && (!onlyFavourites || it.key in favourites)}.sortedByDescending{it.key in favourites}
    LazyColumn(Modifier.fillMaxSize().testTag("library-feed"),contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { PageTitle("Library") }
        item { Button(onClick=onImport,modifier=Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.FileOpen,null);Spacer(Modifier.width(8.dp));Text("Import MusicXML / MXL")
        } }
        item {FilterChip(onlyFavourites,{onlyFavourites=!onlyFavourites},label={Text("Favourites")},leadingIcon={Icon(Icons.Outlined.Star,null)})}
        if(imports.isEmpty()) item { Text("No scores imported yet.",color=MaterialTheme.colorScheme.onSurfaceVariant) }
        else {
            item { OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),singleLine=true,
                placeholder={Text("Find an imported score")},leadingIcon={Icon(Icons.Outlined.Search,null)}) }
            items(visible,key={it.key}) { piece -> Row(verticalAlignment=Alignment.CenterVertically) {
                Box(Modifier.weight(1f)){ScoreRow(piece.title,piece.composer,"Imported",{vm.openImport(piece)})}
                IconToggleButton(piece.key in favourites,{vm.toggleFavourite(piece.key)}){Icon(if(piece.key in favourites)Icons.Outlined.Star else Icons.Outlined.StarBorder,if(piece.key in favourites)"Remove favourite ${piece.title}" else "Favourite ${piece.title}")}
            }}
        }
    }
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
    val settings by vm.settings.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { PageTitle("Connect piano") }
        item { Text("Connect a compatible instrument by Bluetooth LE MIDI or USB MIDI. No microphone is used.",style=MaterialTheme.typography.bodyMedium) }
        item { Panel(Modifier.fillMaxWidth()) { Row(verticalAlignment=Alignment.CenterVertically) { ConnectionStatusIcon(midi.connected,settings.reduceMotion);Spacer(Modifier.width(12.dp));Text(midi.status,fontWeight=FontWeight.Medium) };if(midi.scanning)LinearProgressIndicator(Modifier.fillMaxWidth());Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick=scan,enabled=!midi.scanning){Text("Find piano")};if(midi.connected)OutlinedButton(onClick={vm.midi.disconnect()}){Text("Disconnect")}} } }
        item { Panel(Modifier.fillMaxWidth()) {
            Text("USB MIDI",fontFamily=FontFamily.Serif,fontSize=24.sp)
            Text("Connect the piano’s USB-to-host port to your phone using a data cable and USB-C host/OTG adapter. Switch the piano to standard/class-compliant MIDI mode if available. Bluetooth permission is not needed.")
            OutlinedButton(onClick={vm.midi.refreshUsb()}) {Text("Refresh USB devices")}
            if(midi.usbDevices.isEmpty())Text("No USB MIDI input detected",fontSize=12.sp)
            midi.usbDevices.forEach { d -> TextButton(onClick={vm.midi.connectUsb(d.id,d.port)}) {Text("Connect ${d.name} · ${d.portName}")} }
        } }
        items(midi.devices,key={it.address}){ d -> Surface(onClick={vm.midi.connect(d.address)},shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.surfaceContainerHigh) { Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Outlined.Piano,null);Column(Modifier.weight(1f).padding(horizontal=14.dp)){Text(d.name,fontWeight=FontWeight.SemiBold);Text(d.address,fontSize=12.sp)};Text("Connect",color=MaterialTheme.colorScheme.primary)} } }
        item { Panel { Text("Bluetooth MIDI setup",fontFamily=FontFamily.Serif,fontSize=24.sp);Text("1. Power on the piano and enable its Bluetooth MIDI function.\n\n2. Close other apps that may be connected to your piano.\n\n3. Tap Find piano and allow Bluetooth access. Select your instrument from the results.\n\nOn Android 8–11, Android also requires Location permission and the system Location switch for BLE scans.");Text("Connect inside this app. A separate Bluetooth audio pairing does not provide MIDI.",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant);TextButton(onClick=bluetoothSettings){Text("Open Android Bluetooth settings")};TextButton(onClick={vm.screen.value="Diagnostics"}){Text("Open MIDI diagnostics  →")} } }
        item { Text("Compatibility: you reported successful Pixel / Roland FP-10 testing. USB, other piano models and physical tablets still need testing. MIDI support depends on the instrument and Android device.",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
@Composable private fun SettingsScreen(vm: PianoViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle();val calibrating by vm.calibrating.collectAsStateWithLifecycle()
    var notices by remember{mutableStateOf(false)}
    var support by remember{mutableStateOf(false)}
    val context=LocalContext.current
    if(support)AlertDialog(onDismissRequest={support=false},title={Text("Support Piano Path")},text={
        Text("If Piano Path helps your practice, you can leave a voluntary tip. Every app feature stays free. No subscription or digital reward is required. Ko-fi opens in your browser and handles payment under its own privacy terms.")
    },confirmButton={TextButton(onClick={
        support=false
        runCatching{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(BuildConfig.SUPPORT_URL)))}.onFailure{vm.message.value="No browser is available to open the support page"}
    }){Text("Open Ko-fi")}},dismissButton={TextButton(onClick={support=false}){Text("Maybe later")}})
    if(notices)AlertDialog(onDismissRequest={notices=false},confirmButton={TextButton(onClick={notices=false}){Text("Close")}},title={Text("Open-source notices and licences")},text={
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("Salamander Grand Piano · Alexander Holm\nCC BY 3.0\nhttps://creativecommons.org/licenses/by/3.0/\nSource: https://github.com/Tonejs/audio/tree/869b6f8d9cddb47d966238c012041480b1ce517a/salamander\nAdapted to 24 kHz mono PCM, trimmed to ten seconds with a fade. One sampled dynamic layer; velocity controls amplitude.\n\n")
            Text(remember{context.assets.open("piano/LICENSE.txt").bufferedReader().use{it.readText()}},fontSize=11.sp)
            Text("Music notation font: Bravura · Steinberg Media Technologies · SIL Open Font License 1.1",fontSize=12.sp)
            Text(remember{context.assets.open("fonts/Bravura-LICENSE.txt").bufferedReader().use{it.readText()}},fontSize=11.sp)
            listOf("licenses/NOTICE.txt","licenses/Apache-2.0.txt","licenses/PianoPath-MIT.txt","notation/OSMD-LICENSE","notation/OSMD-AUTHORS","notation/opensheetmusicdisplay.min.js.LICENSE.txt").forEach { path ->
                Text(remember(path){context.assets.open(path).bufferedReader().use{it.readText()}},fontSize=11.sp)
            }
        }
    })
    var section by remember {mutableStateOf("General")}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        PageTitle("Settings")
        PrimaryTabRow(selectedTabIndex=listOf("General","Practice","About").indexOf(section)) {
            listOf("General","Practice","About").forEach { tab ->
                Tab(selected=section==tab,onClick={section=tab},text={Text(tab)})
            }
        }
        when(section) {
            "General" -> {
                Panel(Modifier.fillMaxWidth()) {
                    Toggle("Keep device awake",s.keepAwake,{v->vm.setSettings{it.copy(keepAwake=v)}})
                    Text("Note names",fontWeight=FontWeight.SemiBold)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(s.noteNaming=="LETTERS",{vm.setSettings{it.copy(noteNaming="LETTERS")}},label={Text("C D E")});FilterChip(s.noteNaming=="SOLFEGE",{vm.setSettings{it.copy(noteNaming="SOLFEGE")}},label={Text("Do Re Mi")})}
                    KeyboardRangeSettings(s.keyboardFirst,s.keyboardLast){a,b->vm.setSettings{it.copy(keyboardFirst=a,keyboardLast=b)}}
                    Text("Appearance",fontWeight=FontWeight.SemiBold)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach {mode ->
                            FilterChip(s.theme==mode,{vm.setSettings{it.copy(theme=mode)}},label={Text(mode.name.lowercase().replaceFirstChar{it.uppercase()})})
                        }
                    }
                    AccentPicker(s.accent){value->vm.setSettings{it.copy(accent=value)}}
                    Toggle("Reduce motion",s.reduceMotion,{v->vm.setSettings{it.copy(reduceMotion=v)}})
                    Toggle("Glow on falling notes",s.fallingGlow,{v->vm.setSettings{it.copy(fallingGlow=v)}})
                    HorizontalDivider()
                    Text("Piano sound",fontWeight=FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        PianoSound.entries.forEach {sound ->
                            FilterChip(s.sound==sound,{vm.setSettings{it.copy(sound=sound)}},modifier=Modifier.weight(1f),label={Text(sound.label,fontSize=11.sp,maxLines=2)})
                        }
                    }
                    HorizontalDivider()
                    Text("Score padding · ${s.sheetMargin} px",fontWeight=FontWeight.SemiBold)
                    Slider(s.sheetMargin.toFloat(),{v->vm.setSettings{it.copy(sheetMargin=v.toInt())}},valueRange=0f..32f)
                }
            }
            "Practice" -> {
                Panel(Modifier.fillMaxWidth()) {
                    Text("Listening attempts · ${s.earAttempts}")
                    Slider(s.earAttempts.toFloat(),{v->vm.setSettings{it.copy(earAttempts=v.toInt())}},valueRange=1f..10f,steps=8)
                    Text("Metronome volume · ${(s.metronomeVolume*100).toInt()}%")
                    Slider(s.metronomeVolume,{v->vm.setSettings{it.copy(metronomeVolume=v)}},valueRange=0f..1f)
                    Text("Rest before next loop count-in · ${s.loopBreakSeconds}s",fontWeight=FontWeight.SemiBold)
                    Slider(s.loopBreakSeconds.toFloat(),{v->vm.setSettings{it.copy(loopBreakSeconds=v.toInt())}},valueRange=0f..5f,steps=4)
                    Text("Applies to Practice and Listen. Pausing also pauses this rest.",fontSize=12.sp)
                    Text("Timing tolerance · ${s.tolerance.toInt()} ms",fontWeight=FontWeight.SemiBold)
                    Slider(s.tolerance.toFloat(),{v->vm.setSettings{it.copy(tolerance=v.toDouble())}},valueRange=60f..350f,steps=28)
                    HorizontalDivider()
                    Text("Input delay · ${s.latency.toInt()} ms",fontWeight=FontWeight.SemiBold)
                    Slider(s.latency.toFloat(),{v->vm.setSettings{it.copy(latency=v.toDouble())}},valueRange=-300f..300f,steps=119)
                    OutlinedButton(onClick={vm.calibrate()},enabled=!calibrating){Text(if(calibrating)"Calibrating…" else "Calibrate with MIDI")}
                }
                Panel(Modifier.fillMaxWidth()) {
                    Text("Played-note history",fontWeight=FontWeight.SemiBold)
                    Text("Visible notes · ${s.playedNoteCount} (maximum 32)",fontSize=13.sp)
                    Slider(s.playedNoteCount.toFloat(),{v->vm.setSettings{it.copy(playedNoteCount=v.toInt())}},valueRange=1f..32f,steps=30)
                    HorizontalDivider()
                    Text("Insert pause after · ${"%.1f".format(s.noteGapMs/1000.0)} s",fontSize=13.sp)
                    Slider(s.noteGapMs.toFloat(),{v->vm.setSettings{it.copy(noteGapMs=(v/500).toInt()*500)}},valueRange=500f..10000f,steps=18)
                    Text("Durations appear after releasing a key; note values are approximate.",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> {
                Panel(Modifier.fillMaxWidth()) {
                    Text("About Piano Path",fontWeight=FontWeight.SemiBold)
                    Text("Version ${BuildConfig.VERSION_NAME} · build ${BuildConfig.VERSION_CODE}",fontWeight=FontWeight.SemiBold)
                    Text(if(BuildConfig.DEBUG)"Development APK" else "Release APK",fontSize=12.sp)
                    Text("Revision ${BuildConfig.BUILD_REVISION}",fontSize=12.sp)
                    Text("Open source · offline · no account",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick={notices=true}){Text("Open-source notices and licences")}
                }
                if(BuildConfig.SUPPORT_URL.isNotBlank())OutlinedButton(onClick={support=true}) {
                    Icon(Icons.Outlined.FavoriteBorder,null);Spacer(Modifier.width(8.dp));Text("Support Piano Path")
                }
            }
        }
    }
}
@Composable fun Toggle(label: String,checked: Boolean,onChange: (Boolean)->Unit,enabled: Boolean=true) { Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(label,Modifier.weight(1f));Switch(checked,onChange,enabled=enabled)} }
@Composable private fun Diagnostics(vm: PianoViewModel,midi: MidiStatus) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        PageTitle("MIDI monitor")
        Text(midi.status,style=MaterialTheme.typography.bodyMedium)
        Text("Held: ${midi.held.keys.sorted().joinToString {noteName(it)}}\nSustain: ${if(midi.sustain)"down" else "up"} · Sounding: ${midi.sounding.size}",fontSize=14.sp)
        PianoKeyboard(midi.held.keys,midi.sounding,emptyList(),settings.simulator,vm::key)
        if(settings.simulator)Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick={vm.simulateNext()}){Text("Test chord")};OutlinedButton(onClick={vm.pedal(!midi.sustain)}){Text("Pedal ${if(midi.sustain)"up" else "down"}")}}
        Row { TextButton(onClick={vm.panic()}){Text("Clear sounding notes")};TextButton(onClick={vm.screen.value="Connect"}){Text("Connection")}}
        LazyColumn(Modifier.weight(1f)) { items(midi.events) { Text(it,fontFamily=FontFamily.Monospace,fontSize=11.sp,modifier=Modifier.padding(vertical=4.dp)) } }
    }
}

@Composable private fun AccentPicker(selected: Int,onChange: (Int)->Unit) {
    var custom by remember(selected){mutableStateOf("#%06X".format(selected and 0xffffff))}
    Text("Accent colour",fontWeight=FontWeight.SemiBold)
    val colours=listOf("Green" to 0xff1b554b,"Blue" to 0xff3d6489,"Red" to 0xff884754,"Orange" to 0xffad693b,"Gray" to 0xff667078,"Mustard" to 0xff948035)
    colours.chunked(3).forEach{row->Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){row.forEach{(name,value)->FilterChip(selected==value.toInt(),{onChange(value.toInt())},label={Text(name)})}}}
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(custom,{custom=it.take(7)},label={Text("Custom #RRGGBB")},modifier=Modifier.weight(1f),singleLine=true)
        TextButton(onClick={onChange((0xff000000L or custom.removePrefix("#").toLong(16)).toInt())},enabled=custom.matches(Regex("#[0-9a-fA-F]{6}"))){Text("Apply")}
    }
}

private fun navigationSection(screen: String)=when(screen){"Practice","Scales","Sight reading","Ear training"->"Practice hub";"History"->"Progress";else->screen}

private fun navLabel(screen: String)=when(screen){"Practice hub"->"Practice";"Recorder"->"Studio";else->screen}
