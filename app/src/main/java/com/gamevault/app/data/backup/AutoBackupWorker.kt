package com.gamevault.app.data.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.gamevault.app.GameVaultApp
import com.gamevault.app.data.local.BackupOptions
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Periodically exports a full backup (all groups) to the app-specific external
 * files dir and keeps only the newest N files. Silent on success; retries with
 * backoff on failure. No notifications.
 */
class AutoBackupWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as GameVaultApp
        val backup = app.appContainer.gameVaultBackup
        val appSettings = app.appContainer.appSettings

        return try {
            // Defense in depth: a stale enqueued job (e.g. the toggle was
            // switched off between runs) must not write anything.
            if (!appSettings.autoBackupEnabled.first()) return Result.success()
            val keepCount = appSettings.autoBackupKeepCount.first()
            val json = backup.exportToJson(BackupOptions.ALL)

            withContext(Dispatchers.IO) {
                val dir = backupDir(applicationContext)
                if (dir == null || (!dir.exists() && !dir.mkdirs())) {
                    return@withContext Result.retry()
                }

                // Atomic write: temp file + rename so an interrupted run can
                // never leave a truncated .json that rotation would rank as
                // the newest valid backup.
                dir.listFiles { file ->
                    file.isFile && file.name.startsWith(FILE_PREFIX) && file.name.endsWith(".tmp")
                }?.forEach { it.delete() }

                val tmp = File(dir, "$FILE_PREFIX${timestamp()}.json.tmp")
                try {
                    tmp.writeText(json)
                    if (!tmp.renameTo(File(dir, "$FILE_PREFIX${timestamp()}.json"))) {
                        return@withContext Result.retry()
                    }
                } finally {
                    if (tmp.exists()) tmp.delete()
                }

                // Keep the newest N auto-backups only. Timestamped names share a
                // fixed-width format, so lexicographic order is chronological.
                dir.listFiles { file ->
                    file.isFile && file.name.startsWith(FILE_PREFIX) && file.name.endsWith(".json")
                }
                    ?.sortedByDescending { it.name }
                    ?.drop(keepCount)
                    ?.forEach { it.delete() }

                Result.success()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Transient I/O or database failure — retry with backoff
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "gamevault-auto-backup"
        private const val FILE_PREFIX = "gamevault-auto-"

        /**
         * App-specific external dir holding automatic backups. No permission
         * required; device-local storage that does NOT survive uninstall.
         */
        fun backupDir(context: Context): File? =
            context.getExternalFilesDir(null)?.resolve("backups")

        fun schedule(context: Context, intervalDays: Int) {
            val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(
                intervalDays.toLong(), TimeUnit.DAYS,
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        /** Cancel the periodic automatic backup (used when the feature is off). */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        private fun timestamp(): String =
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
    }
}
