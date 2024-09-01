import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.await
import com.example.gitsyncapp.GitSyncWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class WorkStatus(
    val id: UUID,
    val state: WorkInfo.State,
    val tags: Set<String>,
    val progress: Float = 0f
)

class GitSyncViewModel(private val workManager: WorkManager) : ViewModel() {
    var lastSyncStatus: String by mutableStateOf("No sync performed yet")
        private set

    var periodicWorkStatus: String by mutableStateOf("Checking periodic work status...")
        private set

    var oneTimeWorkStatus: String by mutableStateOf("No one-time work scheduled")
        private set

    var isOneTimeWorkInProgress: Boolean by mutableStateOf(false)
        private set

    var oneTimeWorkProgress: Float by mutableStateOf(0f)
        private set

    var workID: UUID? by mutableStateOf(null)
        private set

    private val _allWorks = MutableStateFlow<List<WorkStatus>>(emptyList())
    val allWorks: StateFlow<List<WorkStatus>> = _allWorks.asStateFlow()

    init {
        viewModelScope.launch {
            workManager.getWorkInfosForUniqueWorkLiveData("periodicGitSync")
                .observeForever { workInfoList ->
                    if (workInfoList.isNotEmpty()) {
                        val workInfo = workInfoList[0]
                        updatePeriodicWorkStatus(workInfo)
                    }
                }
        }
        refreshAllWorks()
    }

    fun updateWorkID(uuid: UUID?) {
        workID = uuid
        if (uuid != null) {
            viewModelScope.launch {
                workManager.getWorkInfoByIdLiveData(uuid).observeForever { workInfo ->
                    if (workInfo != null) {
                        updateOneTimeWorkStatus(workInfo)
                    }
                }
            }
        } else {
            oneTimeWorkStatus = "No one-time work scheduled"
            isOneTimeWorkInProgress = false
            oneTimeWorkProgress = 0f
        }
        refreshAllWorks()
    }

    fun updateLastSyncStatus(string: String) {
        lastSyncStatus = string
    }

    private fun updateOneTimeWorkStatus(workInfo: WorkInfo) {
        oneTimeWorkStatus = when (workInfo.state) {
            WorkInfo.State.ENQUEUED -> "One-time sync scheduled"
            WorkInfo.State.RUNNING -> "One-time sync in progress"
            WorkInfo.State.SUCCEEDED -> "One-time sync completed successfully"
            WorkInfo.State.FAILED -> "One-time sync failed"
            WorkInfo.State.BLOCKED -> "One-time sync blocked"
            WorkInfo.State.CANCELLED -> "One-time sync cancelled"
            else -> "Unknown one-time sync status"
        }
        isOneTimeWorkInProgress = workInfo.state == WorkInfo.State.RUNNING
        oneTimeWorkProgress = workInfo.progress.getFloat(GitSyncWorker.PROGRESS, 0f)
    }

    private fun updatePeriodicWorkStatus(workInfo: WorkInfo) {
        periodicWorkStatus = when (workInfo.state) {
            WorkInfo.State.ENQUEUED -> "Periodic sync scheduled"
            WorkInfo.State.RUNNING -> "Periodic sync in progress"
            WorkInfo.State.SUCCEEDED -> "Last periodic sync completed successfully"
            WorkInfo.State.FAILED -> "Last periodic sync failed"
            WorkInfo.State.BLOCKED -> "Periodic sync blocked"
            WorkInfo.State.CANCELLED -> "Periodic sync cancelled"
            else -> "Unknown periodic sync status"
        }
    }

    fun refreshAllWorks() {
        viewModelScope.launch {
            val periodicWork = workManager.getWorkInfosForUniqueWork("periodicGitSync").await()
            val oneTimeWork = workID?.let { workManager.getWorkInfoById(it).await() }

            val allWorkInfos = periodicWork + listOfNotNull(oneTimeWork)

            _allWorks.value = periodicWork.map { workInfo:WorkInfo ->
                WorkStatus(
                    id = workInfo.id,
                    state = workInfo.state,
                    tags = workInfo.tags,
                    progress = workInfo.progress.getFloat(GitSyncWorker.PROGRESS, 0f)
                )
            }
        }
    }
}