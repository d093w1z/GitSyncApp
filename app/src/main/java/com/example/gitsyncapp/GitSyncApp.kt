package com.example.gitsyncapp

import android.app.Application
import androidx.work.*
import java.util.concurrent.TimeUnit

class GitSyncApp : Application() {
    override fun onCreate() {
        super.onCreate()
        schedulePeriodicSync()
    }

    fun schedulePeriodicSync() {
        val sharedPrefs = getSharedPreferences("GitSyncPrefs", MODE_PRIVATE)
        val syncInterval = sharedPrefs.getLong("sync_interval", 60) // Default to 60 minutes

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodicSyncRequest = PeriodicWorkRequestBuilder<GitSyncWorker>(
            syncInterval, TimeUnit.MINUTES,
            15, TimeUnit.MINUTES
        ).setConstraints(constraints).build()

        WorkManager.getInstance(applicationContext)
            .enqueueUniquePeriodicWork(
                "periodicGitSync",
                ExistingPeriodicWorkPolicy.UPDATE,
                periodicSyncRequest
            )
    }
}