package com.gamevault.app.data.remote

import android.Manifest
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
import java.util.concurrent.TimeUnit

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
        val scraper = app.appContainer.f95ZoneScraper

        // Find tracked F95Zone games
        val allGames = repository.getAllGames()
        val trackedGames = allGames.filter { it.sourceType == SourceType.F95ZONE && it.f95Url != null }

        if (trackedGames.isEmpty()) return Result.success()

        val updated: MutableList<Game> = mutableListOf()

        for (game in trackedGames) {
            try {
                val result = scraper.scrapeGame(game.f95Url!!)
                if (result is ScrapeResult.Success) {
                    val scrapedVersion = result.game.version
                    val currentVersion = game.version
                    if (scrapedVersion != null && scrapedVersion != currentVersion) {
                        updated.add(game)
                    }
                }
            } catch (_: Exception) {
                // Skip on failure — retry next cycle
            }
        }

        if (updated.isNotEmpty()) {
            showUpdateNotification(updated)
        }

        return Result.success()
    }

    private fun showUpdateNotification(updated: List<Game>) {
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

    companion object {
        private const val CHANNEL_ID = "game_updates"
        private const val NOTIFICATION_ID = 1001
        private const val WORK_NAME = "f95zone_update_check"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<F95ZoneUpdateWorker>(
                12, TimeUnit.HOURS,
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
