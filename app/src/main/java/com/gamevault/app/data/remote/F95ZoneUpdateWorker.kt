package com.gamevault.app.data.remote

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.gamevault.app.GameVaultApp
import com.gamevault.app.MainActivity
import com.gamevault.app.domain.model.Game
import com.gamevault.app.domain.model.SourceType
import com.gamevault.app.domain.source.SourceResult
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

/**
 * Periodically checks tracked F95Zone games for version updates.
 * Shows a notification when a new version is detected.
 */
class F95ZoneUpdateWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as GameVaultApp
        val repository = app.appContainer.gameRepository
        val source = app.appContainer.f95ZoneSource

        val interval = app.appContainer.appSettings.updateCheckInterval.first()
        val windowMillis = interval.intervalMillis ?: return Result.success()
        val now = System.currentTimeMillis()

        // Find tracked F95Zone games not yet checked within the fetch window
        val trackedGames = repository.getAllGames().filter { game ->
            if (game.sourceType != SourceType.F95ZONE || game.f95Url == null) return@filter false
            val lastChecked = game.lastChecked ?: return@filter true
            now - lastChecked >= windowMillis
        }

        if (trackedGames.isEmpty()) return Result.success()

        val updated: MutableList<Game> = mutableListOf()

        for (game in trackedGames) {
            try {
                val result = source.fetchDetail(game.f95Url!!)
                if (result is SourceResult.Success) {
                    // Game was actually checked: refresh the timestamp so the
                    // fetch window applies to the next run.
                    val scrapedVersion = result.data.version
                    val currentVersion = game.version
                    val hasNewerVersion = scrapedVersion != null && scrapedVersion != currentVersion
                    repository.updateGame(
                        game.copy(
                            lastChecked = now,
                            updateAvailable = if (hasNewerVersion) true else game.updateAvailable,
                        )
                    )
                    if (hasNewerVersion) {
                        updated.add(game)
                    }
                }
            } catch (_: Exception) {
                // Skip on failure — retry next cycle
            }
        }

        // Muted games are still checked and flagged above; they are only
        // excluded from the notification batch.
        val notified = updated.filter { !it.updatesMuted }
        if (notified.isNotEmpty()) {
            showUpdateNotification(notified)
        }

        return Result.success()
    }

    private fun showUpdateNotification(updated: List<Game>) {
        ensureNotificationChannel()

        val title = if (updated.size == 1) {
            "${updated.first().title} has an update"
        } else {
            "${updated.size} games have updates"
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(updated.joinToString(", ") { it.title })
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ActivityCompat.checkSelfPermission(
                applicationContext, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManagerCompat.from(applicationContext)
                .createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "Game updates",
                        NotificationManager.IMPORTANCE_DEFAULT,
                    )
                )
        }
    }

    companion object {
        private const val CHANNEL_ID = "game_updates"
        private const val NOTIFICATION_ID = 1001
        private const val WORK_NAME = "f95zone_update_check"

        fun schedule(context: Context, intervalHours: Int) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<F95ZoneUpdateWorker>(
                intervalHours.toLong(), TimeUnit.HOURS,
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        /** Cancel the periodic update check (used when the interval is Off). */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
