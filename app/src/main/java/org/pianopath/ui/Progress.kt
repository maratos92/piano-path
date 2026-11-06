@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package org.pianopath.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.pianopath.PianoViewModel
import org.pianopath.core.*
import org.pianopath.reminders.PracticeReminders
import java.text.DateFormat
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.*

@Composable fun ProgressScreen(vm: PianoViewModel) {
    val sessions by vm.history.collectAsStateWithLifecycle()
    var tab by remember{mutableIntStateOf(0)}
    var showAll by remember{mutableStateOf(false)}
    val entries by vm.progress.entries.collectAsStateWithLifecycle()
    val goal by vm.progress.goalMinutes.collectAsStateWithLifecycle()
    var now by remember {mutableLongStateOf(System.currentTimeMillis())}
    LaunchedEffect(Unit){while(true){kotlinx.coroutines.delay(60_000);now=System.currentTimeMillis()}}
    val today=PracticeInsights.day(now)
    val todayMs=PracticeInsights.activeToday(entries,now)
    val queue=remember(entries){PracticeInsights.queue(entries)}
    val trends=remember(entries){PracticeInsights.trends(entries)}
    val earRounds=remember(entries){entries.filter{it.category=="Ear training"}.groupBy{it.roundId}.values.sortedByDescending{it.maxOf{e->e.date}}}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item { PageTitle("Small steps, real progress","Your practice","Private on-device progress. No account is needed.") }
        item { PrimaryScrollableTabRow(selectedTabIndex=tab) {listOf("Overview","Pitch & rhythm","Listening","Sessions").forEachIndexed{i,label->Tab(tab==i,{tab=i},text={Text(label)})}} }
        if(tab==0) {
        item { Panel(Modifier.fillMaxWidth()) {
            Text("Today · ${"%.1f".format(todayMs/60000.0)} / $goal minutes",fontWeight=FontWeight.SemiBold)
            LinearProgressIndicator(progress={(todayMs/(goal*60000f)).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth())
            Text("Daily goal · $goal minutes")
            Slider(goal.toFloat(),{vm.progress.setGoal(it.toInt())},valueRange=5f..30f,steps=24)
            Text("Practice excludes count-in and pauses. Recording time includes rests. A goal is a guide, not a streak to protect.",style=MaterialTheme.typography.bodySmall)
            val days=(6 downTo 0).map{today.minusDays(it.toLong())}
            val minutes=days.map{d->entries.filter{PracticeInsights.day(it.date)==d}.sumOf{it.activeMs}/60000.0}
            val highest=maxOf(goal.toDouble(),minutes.maxOrNull() ?: 0.0)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                days.forEachIndexed {i,d->Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally) {
                    Box(Modifier.height(70.dp).fillMaxWidth(),contentAlignment=Alignment.BottomCenter){Box(Modifier.fillMaxWidth(.65f).fillMaxHeight((minutes[i]/highest).toFloat().coerceIn(0f,1f)).background(MaterialTheme.colorScheme.primary))}
                    Text(d.dayOfWeek.getDisplayName(TextStyle.SHORT,Locale.getDefault()),style=MaterialTheme.typography.labelSmall)
                    Text("${minutes[i].toInt()}m",style=MaterialTheme.typography.labelSmall)
                }}
            }
        } }
        item { ReminderControls() }
        }
        if(tab==1) {
        item { Text("Comparable passage trends",style=MaterialTheme.typography.titleMedium) }
        if(trends.isEmpty())item {Text("Complete scored practice to see pitch and rhythm separately here.")}
        items(trends.take(20),key={it.latest.id}) { trend->val e=trend.latest
            Panel(Modifier.fillMaxWidth()) {
                Text(e.title,fontWeight=FontWeight.SemiBold)
                Text("${e.bpm.toInt()} BPM · ${e.mode} · ${e.hand} · ${e.source}",style=MaterialTheme.typography.bodySmall)
                Text("Beats ${"%.1f".format(e.startBeat)}–${"%.1f".format(e.endBeat)} · ±${e.tolerance.toInt()} ms · latency ${e.latency.toInt()} ms",style=MaterialTheme.typography.labelSmall)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                    Metric("${e.pitch?.toInt() ?: 0}%","pitch ${changeLabel(trend.pitchChange)}",Modifier.weight(1f))
                    Metric(e.rhythm?.let{"${it.toInt()}%"} ?: "—","rhythm ${changeLabel(trend.rhythmChange)}",Modifier.weight(1f))
                }
                Text("${trend.trials} comparable attempt(s) · ${DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(e.date))}",style=MaterialTheme.typography.labelSmall)
                if(e.rhythm==null)Text("Wait mode: pitch completion only; rhythm is not graded.",style=MaterialTheme.typography.bodySmall)
            }
        }
        }
        if(tab==2) {
        item { Text("Listening rounds",style=MaterialTheme.typography.titleMedium) }
        if(earRounds.isEmpty())item {Text("Ear-training answers will be dated here. Lifetime totals are shown in Ear training.")}
        items(earRounds.take(20),key={it.first().roundId}) { round->Panel(Modifier.fillMaxWidth()) {
            val latest=round.maxBy{it.date}
            Text("${round.sumOf{it.correct}} / ${round.sumOf{it.total}} correct",fontWeight=FontWeight.SemiBold)
            Text("${DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(Date(latest.date))} · ${latest.title}",style=MaterialTheme.typography.bodySmall)
            Text("A round contains up to 10 answers at the same difficulty.",style=MaterialTheme.typography.labelSmall)
        } }
        }
        if(tab==3) {
        item { Text("Session history",style=MaterialTheme.typography.titleMedium) }
        if(sessions.isEmpty())item {Text("Completed scored sessions appear here, alongside your trends and listening rounds.")}
        items(if(showAll)sessions else sessions.take(20),key={"session-${it.id}"}) {session->Panel(Modifier.fillMaxWidth()) {
            Text(session.title,fontWeight=FontWeight.SemiBold)
            Text("${DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(session.date))} · ${session.bpm.toInt()} BPM · ${session.mode}",style=MaterialTheme.typography.bodySmall)
            Text("Pitch ${session.pitch.toInt()}% · rhythm ${session.rhythm?.toInt()?.let{"$it%"} ?: "—"} · ${session.missing} missing · ${session.extra} extra")
            if(session.reviewFile!=null)Row {TextButton(onClick={vm.openReview(session)}){Text("Review score")};TextButton(onClick={vm.openReview(session,true)}){Text("Replay MIDI roll")}}
        }}
        if(sessions.size>20)item {TextButton(onClick={showAll=!showAll}){Text(if(showAll)"Show recent" else "Show all stored results")}}
        }
    }
}
private fun changeLabel(value: Double?)=value?.let{String.format(Locale.getDefault(),"%+.1f points",it)} ?: "· first result"

@Composable private fun ReminderControls() {
    val context=LocalContext.current
    var config by remember { mutableStateOf(PracticeReminders.config(context)) }
    var denied by remember { mutableStateOf(false) }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {granted->
        denied=!granted
        PracticeReminders.set(context,granted,config.hour,config.minute);config=PracticeReminders.config(context)
    }
    Panel(Modifier.fillMaxWidth()) {
        Text("A gentle reminder",fontWeight=FontWeight.SemiBold)
        Toggle("Daily practice reminder",config.enabled,{on->
            if(on && Build.VERSION.SDK_INT>=33 && !PracticeReminders.notificationsAllowed(context))permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            else {PracticeReminders.set(context,on,config.hour,config.minute);config=PracticeReminders.config(context)}
        })
        if(config.enabled)Row(verticalAlignment=Alignment.CenterVertically) {
            Text("Around %02d:%02d".format(config.hour,config.minute),Modifier.weight(1f))
            TextButton(onClick={PracticeReminders.set(context,true,(config.hour+23)%24,config.minute);config=PracticeReminders.config(context)}){Text("Earlier")}
            TextButton(onClick={PracticeReminders.set(context,true,(config.hour+1)%24,config.minute);config=PracticeReminders.config(context)}){Text("Later")}
        }
        if(denied || config.enabled && !PracticeReminders.notificationsAllowed(context))Text("Notifications are disabled. Allow them in Android Settings to receive reminders.",style=MaterialTheme.typography.bodySmall)
        Text("Optional, approximate local time. Android may delay reminders to save battery.",style=MaterialTheme.typography.bodySmall)
    }
}
