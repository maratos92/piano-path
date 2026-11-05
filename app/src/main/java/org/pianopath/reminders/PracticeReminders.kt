package org.pianopath.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import org.pianopath.R
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

data class ReminderConfig(val enabled: Boolean=false,val hour: Int=19,val minute: Int=0)

/** One optional local, inexact daily reminder. No exact-alarm permission or remote scheduler. */
object PracticeReminders {
    const val ACTION_FIRE="org.pianopath.PRACTICE_REMINDER"
    private const val CHANNEL="practice-reminders"
    private const val REQUEST=4901
    private const val NOTIFICATION=4901
    private fun preferences(context: Context)=context.getSharedPreferences("practice-reminders",Context.MODE_PRIVATE)

    fun config(context: Context): ReminderConfig {
        val prefs=preferences(context)
        return ReminderConfig(prefs.getBoolean("enabled",false),prefs.getInt("hour",19).coerceIn(0,23),prefs.getInt("minute",0).coerceIn(0,59))
    }
    fun set(context: Context,enabled: Boolean,hour: Int,minute: Int) {
        require(hour in 0..23 && minute in 0..59)
        preferences(context).edit().putBoolean("enabled",enabled).putInt("hour",hour).putInt("minute",minute).apply()
        if(!enabled)context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION)
        schedule(context)
    }
    private fun channel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL,"Practice reminders",NotificationManager.IMPORTANCE_DEFAULT).apply {
                description="Your optional daily piano practice reminder"
                lockscreenVisibility=Notification.VISIBILITY_PRIVATE
            })
    }
    fun notificationsAllowed(context: Context): Boolean {
        if(Build.VERSION.SDK_INT>=33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return false
        val manager=context.getSystemService(NotificationManager::class.java)
        return manager.areNotificationsEnabled() && manager.getNotificationChannel(CHANNEL)?.importance!=NotificationManager.IMPORTANCE_NONE
    }
    private fun alarmIntent(context: Context)=PendingIntent.getBroadcast(context,REQUEST,
        Intent(context,PracticeReminderReceiver::class.java).setAction(ACTION_FIRE),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun schedule(context: Context) {
        val alarms=context.getSystemService(AlarmManager::class.java)
        val pending=alarmIntent(context)
        alarms.cancel(pending)
        val settings=config(context)
        if(!settings.enabled)return
        channel(context)
        if(!notificationsAllowed(context))return
        val zone=ZoneId.systemDefault()
        val now=ZonedDateTime.now(zone)
        var date=LocalDate.now(zone)
        var next=date.atTime(settings.hour,settings.minute).atZone(zone)
        if(!next.isAfter(now)) {date=date.plusDays(1);next=date.atTime(settings.hour,settings.minute).atZone(zone)}
        // The OS may delay delivery for power saving. Recompute local wall time after every alarm.
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next.toInstant().toEpochMilli(),pending)
    }
    internal fun deliver(context: Context) {
        if(!config(context).enabled)return
        channel(context)
        if(notificationsAllowed(context)) {
            val launch=context.packageManager.getLaunchIntentForPackage(context.packageName)
            val content=launch?.let {PendingIntent.getActivity(context,REQUEST,it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)}
            val notification=Notification.Builder(context,CHANNEL)
                .setSmallIcon(R.drawable.ic_piano).setContentTitle("Time for piano")
                .setContentText("A few minutes of practice can keep your routine going.")
                .setCategory(Notification.CATEGORY_REMINDER).setVisibility(Notification.VISIBILITY_PRIVATE)
                .setAutoCancel(true).apply{if(content!=null)setContentIntent(content)}.build()
            try {context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION,notification)}
            catch(_: SecurityException) {/* Permission can change between the check and delivery. */}
        }
        schedule(context)
    }
}

class PracticeReminderReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context,intent: Intent) {
        when(intent.action) {
            PracticeReminders.ACTION_FIRE->PracticeReminders.deliver(context)
            Intent.ACTION_BOOT_COMPLETED,Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,Intent.ACTION_TIMEZONE_CHANGED->PracticeReminders.schedule(context)
        }
    }
}
