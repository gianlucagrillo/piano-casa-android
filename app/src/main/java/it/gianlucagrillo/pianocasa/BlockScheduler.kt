package it.gianlucagrillo.pianocasa

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.Notification
import java.util.Calendar

/**
 * Notifiche all'attivazione dei blocchi: ogni giorno alle 7:00 (mattina) e alle 18:00 (sera), nel weekend anche alle 10:00 (faccende).
 * Gli orari sono gli stessi della pagina (EVENING_HOUR = 18, weekend 10).
 */
object BlockScheduler {
    private const val CHANNEL = "blocks"
    private const val EXTRA_KIND = "kind"

    private fun candidates(now: Calendar): List<Pair<Long, String>> {
        val out = mutableListOf<Pair<Long, String>>()
        for (d in 0..7) {
            val day = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, d) }
            val wd = day.get(Calendar.DAY_OF_WEEK)
            val weekend = wd == Calendar.SATURDAY || wd == Calendar.SUNDAY
            out += at(day, 7) to "morning"
            if (weekend) out += at(day, 10) to "weekend"
            out += at(day, 18) to "evening"
        }
        return out
    }

    private fun at(day: Calendar, hour: Int): Long = (day.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun scheduleNext(ctx: Context) {
        val now = Calendar.getInstance()
        val next = candidates(now).filter { it.first > now.timeInMillis + 1000 }.minByOrNull { it.first } ?: return
        val i = Intent(ctx, BlockAlarmReceiver::class.java).putExtra(EXTRA_KIND, next.second)
        val pi = PendingIntent.getBroadcast(ctx, 1, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.first, pi)
    }

    fun notify(ctx: Context, kind: String?) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Task blocks", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "When a new block of tasks becomes available"
        })
        val (title, text) = when (kind) {
            "morning" -> "Good morning" to "Your morning tasks are ready."
            "weekend" -> "Weekend chores are ready" to "Open your plan and start with the first card."
            else -> "Evening tasks are ready" to "Open your plan and start with the first card."
        }
        val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setColor(0xFF146C60.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try { nm.notify(100, n) } catch (_: SecurityException) {}
    }
}

class BlockAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        BlockScheduler.notify(ctx, intent.getStringExtra("kind"))
        BlockScheduler.scheduleNext(ctx)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        BlockScheduler.scheduleNext(ctx)
    }
}