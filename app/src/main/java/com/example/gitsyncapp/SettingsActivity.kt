package com.example.gitsyncapp

import android.os.Bundle
import android.util.Log
import android.view.ViewTreeObserver
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.work.WorkManager
import java.io.File


class SettingsActivity : AppCompatActivity() {
    // Declare UI components
    private lateinit var remoteUrlEditText: EditText
    private lateinit var localPathEditText: EditText
    private lateinit var branchEditText: EditText
    private lateinit var usernameEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var syncIntervalEditText: EditText
    private lateinit var saveButton: Button

    // Tag for logging
    private val TAG = "SettingsActivity"
    private val REQUEST_CODE_OPEN_DIRECTORY = 1


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate: Starting SettingsActivity creation")
        try {

            // Set the layout for this activity
            setContentView(R.layout.activity_settings)
            Log.d(TAG, "onCreate: Layout set successfully")

            // Initialize UI components
            initializeUIComponents()

            // Load existing settings
            loadSettings()

            // Set up save button click listener
            setupSaveButtonListener()

            Log.d(TAG, "onCreate: SettingsActivity setup completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "onCreate: Error setting up SettingsActivity", e)
            // Show an error message to the user (you might want to add a TextView for this)
        }
    }

    private fun initializeUIComponents() {
        Log.d(TAG, "initializeUIComponents: Starting UI component initialization")

        // Find views by their IDs
        remoteUrlEditText = findViewById(R.id.remoteUrlEditText)
        localPathEditText = findViewById(R.id.localPathEditText)
        branchEditText = findViewById(R.id.branchEditText)
        usernameEditText = findViewById(R.id.usernameEditText)
        passwordEditText = findViewById(R.id.passwordEditText)
        syncIntervalEditText = findViewById(R.id.syncIntervalEditText)
        saveButton = findViewById(R.id.saveButton)
        remoteUrlEditText.viewTreeObserver.addOnGlobalLayoutListener(
            ViewTreeObserver.OnGlobalLayoutListener {
                localPathEditText.setText(extractRepoName(remoteUrlEditText.text.toString()))
            })

        Log.d(TAG, "initializeUIComponents: UI components initialized successfully")
    }
    private fun extractRepoName(url: String): String? {
        val filePath = url // Replace with your file path
        val file = File(filePath)
        val fileName = file.name
        return fileName
    }

    private fun loadSettings() {
        Log.d(TAG, "loadSettings: Loading saved settings")

        val sharedPrefs = getSharedPreferences("GitSyncPrefs", MODE_PRIVATE)

        // Load each setting and set it to the corresponding EditText
        remoteUrlEditText.setText(sharedPrefs.getString("remote_url", ""))
        localPathEditText.setText(sharedPrefs.getString("local_path", extractRepoName(sharedPrefs.getString("remote_url", "")?: "")?: "git-repo"))
        branchEditText.setText(sharedPrefs.getString("branch", "main"))
        usernameEditText.setText(sharedPrefs.getString("git_username", ""))
        passwordEditText.setText(sharedPrefs.getString("git_password", ""))
        syncIntervalEditText.setText(sharedPrefs.getLong("sync_interval", 60).toString())

        Log.d(TAG, "loadSettings: Settings loaded successfully")
    }

    private fun setupSaveButtonListener() {
        Log.d(TAG, "setupSaveButtonListener: Setting up save button click listener")

        saveButton.setOnClickListener {
            Log.d(TAG, "Save button clicked")
            saveSettings()
        }

        Log.d(TAG, "setupSaveButtonListener: Save button listener set up successfully")
    }

    private fun saveSettings() {
        Log.d(TAG, "saveSettings: Starting to save settings")

        try {
            val sharedPrefs = getSharedPreferences("GitSyncPrefs", MODE_PRIVATE)
            with(sharedPrefs.edit()) {
                // Save each setting
                putString("remote_url", remoteUrlEditText.text.toString())
                putString("local_path", localPathEditText.text.toString())
                putString("branch", branchEditText.text.toString())
                putString("git_username", usernameEditText.text.toString())
                putString("git_password", passwordEditText.text.toString())
                putLong("sync_interval", syncIntervalEditText.text.toString().toLongOrNull() ?: 60)
                apply()
            }

            Log.d(TAG, "saveSettings: Settings saved successfully")

            // Cancel existing work and reschedule with new interval
            WorkManager.getInstance(applicationContext).cancelUniqueWork("periodicGitSync")
            (application as GitSyncApp).schedulePeriodicSync()

            Log.d(TAG, "saveSettings: Periodic sync rescheduled")

            // Close the activity
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "saveSettings: Error saving settings", e)
            // Show an error message to the user (you might want to add a TextView for this)
        }
    }
}