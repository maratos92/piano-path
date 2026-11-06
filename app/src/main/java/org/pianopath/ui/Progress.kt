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
    val entries by vm.progress.entries.collectAsStateWithLifecycle()
    val sessions by vm.history.collectAsStateWithLifecycle()
    val goal by vm.progress.goalMinutes.collectAsStateWithLifecycle()
    var tab by remember{mutableIntStateOf(0)}
    val tabs=listOf("Overview","Pieces","Scales","Sight reading","Listening")
    val list=androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(tab){list.scrollToItem(0)}
    var now by remember{mutableLongStateOf(System.currentTimeMillis())}
    LaunchedEffect(Unit){while(true){kotlinx.coroutines.delay(60_000);now=System.currentTimeMillis()}}
    val graded=entries.filter{it.category!="Free play"}
    val selected=graded.filter{progressCategory(it)==tabs[tab]}
    Column(Modifier.fillMaxSize()) {
        Text("Progress",style=MaterialTheme.typography.headlineMedium,modifier=Modifier.padding(horizontal=20.dp,vertical=12.dp))
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
            if(maxWidth>=600.dp)PrimaryTabRow(selectedTabIndex=tab){tabs.forEachIndexed{i,label->Tab(tab==i,{tab=i},text={Text(label,maxLines=1)})}}
            else PrimaryScrollableTabRow(selectedTabIndex=tab,edgePadding=0.dp){tabs.forEachIndexed{i,label->Tab(tab==i,{tab=i},text={Text(label,maxLines=1)})}}
        }
        LazyColumn(state=list,modifier=Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            if(tab==0) {
                item {Panel(Modifier.fillMaxWidth()) {
                    val minutes=PracticeInsights.activeToday(graded,now)/60000.0
                    Text("Today · ${"%.1f".format(minutes)} / $goal minutes",fontWeight=FontWeight.SemiBold)
                    LinearProgressIndicator(progress={(minutes/goal).toFloat().coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth())
                    Text("Daily goal · $goal minutes")
                    Slider(goal.toFloat(),{vm.progress.setGoal(it.toInt())},valueRange=5f..30f,steps=24)
                    Text("Completed runs and Finish actions are tracked. Opening scores, cancelled sessions and free play do not earn accuracy results.",style=MaterialTheme.typography.bodySmall)
                }}
                item {MetricChart(graded,now,activity=true)}
                item {ReminderControls()}
                item {Text("Modes",style=MaterialTheme.typography.titleMedium)}
                item {Text("Wait: note accuracy only. Rhythm: note accuracy and on-time notes against a fixed clock. Follow score: both, with timing aligned to your playing. Free play: no accuracy score.")}
                item {Text("Previous session results",style=MaterialTheme.typography.titleMedium)}
                items(sessions.take(15),key={"old-${it.id}"}) {session->Panel(Modifier.fillMaxWidth()) {
                    Text(session.title,fontWeight=FontWeight.SemiBold)
                    Text("${DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(session.date))} · ${session.mode} · ${session.bpm.toInt()} BPM",style=MaterialTheme.typography.bodySmall)
                    Text("Note accuracy ${session.pitch.toInt()}% · ${session.missing} missing · ${session.extra} extra")
                    if(session.reviewFile!=null)TextButton(onClick={vm.openReview(session)}){Text("Review performance")}
                }}
            } else {
                item {MetricChart(selected,now,listening=tab==4)}
                item {Text(if(tab==4)"Listening precision = correct complete answers / submitted attempts." else "Note accuracy = matched / (expected + extra). On-time = expected notes played within your timing tolerance / expected. Missing notes receive no credit.",style=MaterialTheme.typography.bodySmall)}
                if(tab==4) {
                    val rounds=selected.groupBy{it.roundId}.values.sortedByDescending{it.maxOf{e->e.date}}
                    if(rounds.isEmpty())item{Text("Your listening attempts will appear here.")}
                    items(rounds.take(30),key={it.first().id}){round->Panel(Modifier.fillMaxWidth()) {
                        Text(round.maxBy{it.date}.title,fontWeight=FontWeight.SemiBold)
                        Text("${round.sumOf{it.correct}} / ${round.sumOf{it.total}} correct · ${DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(round.maxOf{it.date}))}")
                    }}
                } else {
                    val comparable=PracticeInsights.trends(selected.filter{it.scoringVersion>=2})
                    if(comparable.isEmpty())item{Text("Finish a practice run in this category to start these metrics. Earlier composite rhythm scores remain in previous results.")}
                    items(comparable.take(30),key={it.latest.id}){trend->val e=trend.latest
                        Panel(Modifier.fillMaxWidth()) {
                            Text(e.title,fontWeight=FontWeight.SemiBold)
                            Text("${e.bpm.toInt()} BPM · ${e.mode} · ${e.hand} · ${e.source}",style=MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement=Arrangement.spacedBy(18.dp)) {
                                Metric("${e.pitch?.toInt() ?: 0}%","note accuracy",Modifier.weight(1f))
                                Metric(e.onTime?.let{"${it.toInt()}%"} ?: "—","on time",Modifier.weight(1f))
                            }
                            Text("${e.correct} matched · ${e.expected} expected · ${e.extra} extra · ${trend.trials} comparable run(s)",style=MaterialTheme.typography.bodySmall)
                            if(e.mode=="WAIT")Text("Wait mode does not measure rhythm.",style=MaterialTheme.typography.bodySmall)
                            if(e.mode=="FOLLOW")Text("Performance-aligned timing; not a strict metronome test.",style=MaterialTheme.typography.bodySmall)
                            trend.pitchChange?.let{Text("Accuracy change ${String.format(Locale.getDefault(),"%+.1f",it)} points at the same settings",style=MaterialTheme.typography.bodySmall)}
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun MetricChart(entries: List<PracticeEntry>,now: Long,activity: Boolean=false,listening: Boolean=false) {
    val today=PracticeInsights.day(now)
    val days=(6 downTo 0).map{today.minusDays(it.toLong())}
    val groups=days.map{day->entries.filter{PracticeInsights.day(it.date)==day && (activity || listening || it.scoringVersion>=2)}}
    val first=groups.map {g->when {
        activity->g.sumOf{it.activeMs}/60000.0
        listening->g.sumOf{it.total}.takeIf{it>0}?.let{100.0*g.sumOf{e->e.correct}/it}
        else->g.sumOf{it.expected+it.extra}.takeIf{it>0}?.let{100.0*g.sumOf{e->e.correct}/it}
    }}
    val second=groups.map{g->val timed=g.filter{it.onTimeCorrect!=null};timed.sumOf{it.expected}.takeIf{it>0}?.let{100.0*timed.sumOf{e->e.onTimeCorrect ?: 0}/it}}
    val primary=MaterialTheme.colorScheme.primary;val timing=MaterialTheme.colorScheme.tertiary;val grid=MaterialTheme.colorScheme.outlineVariant
    Panel(Modifier.fillMaxWidth()) {
        Text(if(activity)"Practice minutes · 7 days" else if(listening)"Listening precision · 7 days" else "Accuracy and on-time notes · 7 days",fontWeight=FontWeight.SemiBold)
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(130.dp)) {
            val top=12.dp.toPx();val bottom=size.height-12.dp.toPx();val maximum=if(activity)maxOf(5.0,first.filterNotNull().maxOrNull() ?: 0.0) else 100.0
            for(i in 0..4)drawLine(grid,androidx.compose.ui.geometry.Offset(0f,top+(bottom-top)*i/4),androidx.compose.ui.geometry.Offset(size.width,top+(bottom-top)*i/4),1f)
            fun line(values: List<Double?>,colour: androidx.compose.ui.graphics.Color){var previous: androidx.compose.ui.geometry.Offset?=null;values.forEachIndexed{i,value->
                if(value==null){previous=null}else{val point=androidx.compose.ui.geometry.Offset(size.width*(i+.5f)/7,(bottom-(bottom-top)*value/maximum).toFloat());previous?.let{drawLine(colour,it,point,2.5.dp.toPx())};drawCircle(colour,3.5.dp.toPx(),point);previous=point}
            }}
            line(first,primary);if(!activity && !listening)line(second,timing)
        }
        Row(Modifier.fillMaxWidth()){days.forEach{day->Text(day.dayOfWeek.getDisplayName(TextStyle.SHORT,Locale.getDefault()),Modifier.weight(1f),style=MaterialTheme.typography.labelSmall)}}
        Text(if(activity)"Minutes exclude pauses and count-in." else if(listening)"Complete answers, including retries." else "Accuracy (primary) · on time (secondary). Daily totals use selected settings; only matching contexts are compared below.",style=MaterialTheme.typography.bodySmall)
    }
}
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
