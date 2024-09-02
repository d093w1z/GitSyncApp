package com.example.gitsyncapp

import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.merge.MergeStrategy
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.File

class GitSyncWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {
    private val TAG = "GitSyncWorker"

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.i(TAG, "started doing work.....")

        try {
            val sharedPrefs = applicationContext.getSharedPreferences("GitSyncPrefs", Context.MODE_PRIVATE)
            val remoteUrl = sharedPrefs.getString("remote_url", "") ?: ""
            val localPath = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),"GitSync/"+sharedPrefs.getString("local_path", "git-repo"))
            val branch = sharedPrefs.getString("branch", "main") ?: "main"
            val username = sharedPrefs.getString("git_username", "") ?: ""
            val password = sharedPrefs.getString("git_password", "") ?: ""

            if (remoteUrl.isEmpty()) {
                Log.d(TAG, "Failed: missing remote url")
                return@withContext Result.failure()
            }

            if (!localPath.exists()) {
                Git.cloneRepository()
                    .setURI(remoteUrl)
                    .setDirectory(localPath)
                    .setBranch(branch)
                    .setCredentialsProvider(UsernamePasswordCredentialsProvider(username, password))
                    .call()
            } else {
                val git = Git(FileRepositoryBuilder().setGitDir(File(localPath, ".git")).build())
                git.pull()
                    .setStrategy(MergeStrategy.THEIRS)
                    .setRemoteBranchName(branch)
                    .setCredentialsProvider(UsernamePasswordCredentialsProvider(username, password))
                    .call()
                git.add()
                    .addFilepattern("*")
                    .call()
                git.commit()
                    .setMessage("vault backup")
                    .call()
                git.push()
                    .setCredentialsProvider(UsernamePasswordCredentialsProvider(username, password))
                    .call()
            }

            sharedPrefs.edit().putLong("last_sync_time", System.currentTimeMillis()).apply()
            setProgress(workDataOf(PROGRESS to 50))
            Log.i(TAG, "Completed!")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Failed!", e)
            Result.failure()
        }
    }

    companion object {
        const val PROGRESS = "PROGRESS"
    }
}