package com.example.gitsyncapp

import GitSyncViewModel
import WorkStatus
import androidx.work.*
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.work.*
import java.util.concurrent.TimeUnit
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch


class MainActivity : AppCompatActivity() {
    private val TAG = "GitSyncMainActivity"
    private lateinit var viewModel: GitSyncViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate: Starting MainActivity creation")

        val workManager = WorkManager.getInstance(applicationContext)
        viewModel = ViewModelProvider(this, GitSyncViewModelFactory(workManager))[GitSyncViewModel::class.java]

        setContent {
            MaterialTheme {
                Surface {
                    AppNavigation()
                }
            }
        }

        try {
            setupWorkManagerObserver()
            schedulePeriodicSync()
            updateLastSyncTime()
            Log.d(TAG, "onCreate: MainActivity setup completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "onCreate: Error setting up MainActivity", e)
            viewModel.updateLastSyncStatus("Error: Unable to set up the app")
        }
    }

    @Composable
    fun AppNavigation() {
        val navController = rememberNavController()

        NavHost(navController = navController, startDestination = "main") {
            composable("main") {
                MainScreen(
                    onNavigateToWorkStatus = { navController.navigate("workStatus") }
                )
            }
            composable("workStatus") {
                WorkStatusScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }

    @Composable
    fun MainScreen(onNavigateToWorkStatus: () -> Unit) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = viewModel.lastSyncStatus,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = viewModel.periodicWorkStatus,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
            )

            Text(
                text = viewModel.oneTimeWorkStatus,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
            )

            if (viewModel.isOneTimeWorkInProgress) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .padding(vertical = 12.dp)
                        .fillMaxWidth()
                )
            }

            Button(
                onClick = {
                    Log.d(TAG, "Settings button clicked")
                    Intent(applicationContext, SettingsActivity::class.java).also {
                        startActivity(it)
                    }
                },
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                Text(text = "Settings")
            }

            Button(
                onClick = {
                    Log.d(TAG, "Sync button clicked")
                    scheduleSync()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Sync now")
            }

            Button(
                onClick = onNavigateToWorkStatus,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text(text = "View All Work Status")
            }
        }
    }

    @Composable
    fun WorkStatusScreen(onNavigateBack: () -> Unit) {
        val allWorks by viewModel.allWorks.collectAsState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Button(onClick = onNavigateBack) {
                Text("Back")
            }

            Text(
                text = "All Work Status",
                style = MaterialTheme.typography.h5,
                modifier = Modifier.padding(vertical = 16.dp)
            )

            LazyColumn {
                items(allWorks) { workStatus ->
                    WorkStatusItem(workStatus)
                }
            }

            Button(
                onClick = { viewModel.refreshAllWorks() },
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text("Refresh")
            }
        }
    }

    @Composable
    fun WorkStatusItem(workStatus: WorkStatus) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            elevation = 4.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text("ID: ${workStatus.id}")
                Text("State: ${workStatus.state}")
                Text("Tags: ${workStatus.tags.joinToString(", ")}")
                if (workStatus.progress > 0) {
                    LinearProgressIndicator(
                        progress = workStatus.progress,
                        modifier = Modifier
                            .padding(vertical = 8.dp)
                            .fillMaxWidth()
                    )
                }
            }
        }
    }

    private fun setupWorkManagerObserver() {
        Log.d(TAG, "setupWorkManagerObserver: Setting up WorkManager observer")
        val workManager = WorkManager.getInstance(applicationContext)

        workManager.getWorkInfosForUniqueWorkLiveData("periodicGitSync")
            .observe(this) { workInfoList ->
                if (workInfoList.isNotEmpty()) {
                    val workInfo = workInfoList[0]
                    updateUIBasedOnWorkInfo(workInfo)
                }
            }

        Log.d(TAG, "setupWorkManagerObserver: WorkManager observer set up successfully")
    }

    private fun scheduleSync() {
        Log.d(TAG, "scheduleSync: Scheduling immediate sync")

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncWorkRequest = OneTimeWorkRequestBuilder<GitSyncWorker>()
            .setConstraints(constraints)
            .addTag("GitSync")
            .build()

        viewModel.updateWorkID(syncWorkRequest.id)

        val workManager = WorkManager.getInstance(applicationContext)
        workManager.enqueue(syncWorkRequest)

        Log.d(TAG, "scheduleSync: Sync work request enqueued")
    }

    private fun schedulePeriodicSync() {
        Log.d(TAG, "schedulePeriodicSync: Scheduling periodic sync")

        val sharedPrefs = getSharedPreferences("GitSyncPrefs", MODE_PRIVATE)
        val syncInterval = sharedPrefs.getLong("sync_interval", 15)
        Log.d(TAG, "schedulePeriodicSync: Sync interval set to $syncInterval minutes")

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodicSyncRequest = PeriodicWorkRequestBuilder<GitSyncWorker>(
            syncInterval, TimeUnit.MINUTES,
            5, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .addTag("GitSync")
            .build()

        val workManager = WorkManager.getInstance(applicationContext)

        workManager.enqueueUniquePeriodicWork(
            "periodicGitSync",
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicSyncRequest
        )

        Log.d(TAG, "schedulePeriodicSync: Periodic sync scheduled successfully")
    }

    private fun updateUIBasedOnWorkInfo(workInfo: WorkInfo) {
        Log.d(TAG, "updateUIBasedOnWorkInfo: Updating UI based on WorkInfo state: ${workInfo.state}")

        when (workInfo.state) {
            WorkInfo.State.SUCCEEDED -> {
                updateLastSyncTime()
                Log.d(TAG, "updateUIBasedOnWorkInfo: Sync completed successfully")
            }
            WorkInfo.State.FAILED -> {
                viewModel.updateLastSyncStatus("Last sync failed")
                Log.d(TAG, "updateUIBasedOnWorkInfo: Sync failed")
            }
            else -> {
                Log.d(TAG, "updateUIBasedOnWorkInfo: Sync is in state: ${workInfo.state}")
            }
        }
    }

    private fun updateLastSyncTime() {
        Log.d(TAG, "updateLastSyncTime: Updating last sync time")

        val sharedPrefs = getSharedPreferences("GitSyncPrefs", MODE_PRIVATE)
        val lastSyncTime = sharedPrefs.getLong("last_sync_time", 0)

        if (lastSyncTime > 0) {
            val dateString = DateFormat.format("yyyy-MM-dd HH:mm:ss", lastSyncTime)
            viewModel.updateLastSyncStatus("Last successful sync: $dateString")
            Log.d(TAG, "updateLastSyncTime: Last sync time updated to $dateString")
        } else {
            viewModel.updateLastSyncStatus("No successful sync yet")
            Log.d(TAG, "updateLastSyncTime: No successful sync recorded yet")
        }
    }
}

class GitSyncViewModelFactory(private val workManager: WorkManager) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GitSyncViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return GitSyncViewModel(workManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}