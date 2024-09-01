package com.example.gitsyncapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.work.*
import java.util.concurrent.TimeUnit
import android.view.View
import android.widget.ProgressBar

class MainActivity : AppCompatActivity() {
    // Declare UI components
    private lateinit var syncButton: Button
    private lateinit var settingsButton: Button
    private lateinit var statusTextView: TextView
    private lateinit var lastSyncTextView: TextView
    private lateinit var progressBar: ProgressBar


    // Tag for logging
    private val TAG = "GitSyncMainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate: Starting MainActivity creation")

        try {
            // Set the layout for this activity
            setContentView(R.layout.activity_main)
            Log.d(TAG, "onCreate: Layout set successfully")

            // Initialize UI components
            initializeUIComponents()

            // Set up button click listeners
            setupButtonListeners()

            // Set up WorkManager observer
            setupWorkManagerObserver()

            // Schedule periodic sync
            schedulePeriodicSync()

            // Update last sync time
            updateLastSyncTime()

            Log.d(TAG, "onCreate: MainActivity setup completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "onCreate: Error setting up MainActivity", e)
            // Show an error message to the user
            statusTextView.text = "Error: Unable to set up the app"
        }
    }

    private fun initializeUIComponents() {
        Log.d(TAG, "initializeUIComponents: Starting UI component initialization")

        // Find views by their IDs
        syncButton = findViewById(R.id.syncButton)
        settingsButton = findViewById(R.id.settingsButton)
        statusTextView = findViewById(R.id.statusTextView)
        lastSyncTextView = findViewById(R.id.lastSyncTextView)
        progressBar = findViewById(R.id.progressBar)


        Log.d(TAG, "initializeUIComponents: UI components initialized successfully")
    }

    private fun setupButtonListeners() {
        Log.d(TAG, "setupButtonListeners: Setting up button click listeners")

        // Set up sync button click listener
        syncButton.setOnClickListener {
            Log.d(TAG, "Sync button clicked")
            scheduleSync()
        }

        // Set up settings button click listener
        settingsButton.setOnClickListener {
            Log.d(TAG, "Settings button clicked")
            // Start the SettingsActivity
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        Log.d(TAG, "setupButtonListeners: Button listeners set up successfully")
    }

    private fun setupWorkManagerObserver() {
        Log.d(TAG, "setupWorkManagerObserver: Setting up WorkManager observer")

        // Get the WorkManager instance
        val workManager = WorkManager.getInstance(applicationContext)

        // Observe the status of the periodic sync work
        workManager.getWorkInfosForUniqueWorkLiveData("periodicGitSync")
            .observe(this) { workInfoList ->
                if (workInfoList.isNotEmpty()) {
                    val workInfo = workInfoList[0]
                    updateUIBasedOnWorkInfo(workInfo)
                }
            }

        Log.d(TAG, "setupWorkManagerObserver: WorkManager observer set up successfully")
    }

    private fun updateUIBasedOnWorkInfo(workInfo: WorkInfo) {
        Log.d(TAG, "updateUIBasedOnWorkInfo: Updating UI based on WorkInfo state: ${workInfo.state}")

        when (workInfo.state) {
            WorkInfo.State.RUNNING -> {
                progressBar.visibility = View.VISIBLE
                statusTextView.text = "Sync in progress..."
                Log.d(TAG, "updateUIBasedOnWorkInfo: Sync is running")
            }
            WorkInfo.State.SUCCEEDED -> {
                progressBar.visibility = View.GONE
                statusTextView.text = "Last sync successful"
                updateLastSyncTime()
                Log.d(TAG, "updateUIBasedOnWorkInfo: Sync completed successfully")
            }
            WorkInfo.State.FAILED -> {
                progressBar.visibility = View.GONE
                statusTextView.text = "Last sync failed"
                Log.d(TAG, "updateUIBasedOnWorkInfo: Sync failed")
            }
            else -> {
                progressBar.visibility = View.GONE
                statusTextView.text = "Sync scheduled"
                Log.d(TAG, "updateUIBasedOnWorkInfo: Sync is in state: ${workInfo.state}")
            }
        }
    }

    private fun scheduleSync() {
        Log.d(TAG, "scheduleSync: Scheduling immediate sync")

        // Create constraints for the work request
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // Create a one-time work request
        val syncWorkRequest = OneTimeWorkRequestBuilder<GitSyncWorker>()
            .setConstraints(constraints)
            .build()

        // Enqueue the work request
        WorkManager.getInstance(applicationContext).enqueue(syncWorkRequest)

        statusTextView.text = "Sync scheduled"
        Log.d(TAG, "scheduleSync: Sync work request enqueued")
    }

    private fun schedulePeriodicSync() {
        Log.d(TAG, "schedulePeriodicSync: Scheduling periodic sync")

        // Get the sync interval from SharedPreferences
        val sharedPrefs = getSharedPreferences("GitSyncPrefs", MODE_PRIVATE)
        val syncInterval = sharedPrefs.getLong("sync_interval", 15) // Default to 60 minutes
        Log.d(TAG, "schedulePeriodicSync: Sync interval set to $syncInterval minutes")

        // Create constraints for the work request
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // Create a periodic work request
        val periodicSyncRequest = PeriodicWorkRequestBuilder<GitSyncWorker>(
            syncInterval, TimeUnit.MINUTES,
            5, TimeUnit.MINUTES // Flex period
        ).setConstraints(constraints).build()

        // Enqueue the periodic work request
        WorkManager.getInstance(applicationContext)
            .enqueueUniquePeriodicWork(
                "periodicGitSync",
                ExistingPeriodicWorkPolicy.REPLACE,
                periodicSyncRequest
            )

        Log.d(TAG, "schedulePeriodicSync: Periodic sync scheduled successfully")
    }

    private fun updateLastSyncTime() {
        Log.d(TAG, "updateLastSyncTime: Updating last sync time")

        val sharedPrefs = getSharedPreferences("GitSyncPrefs", MODE_PRIVATE)
        val lastSyncTime = sharedPrefs.getLong("last_sync_time", 0)

        if (lastSyncTime > 0) {
            val dateString = android.text.format.DateFormat.format("yyyy-MM-dd HH:mm:ss", lastSyncTime)
            lastSyncTextView.text = "Last successful sync: $dateString"
            Log.d(TAG, "updateLastSyncTime: Last sync time updated to $dateString")
        } else {
            lastSyncTextView.text = "No successful sync yet"
            Log.d(TAG, "updateLastSyncTime: No successful sync recorded yet")
        }
    }
}