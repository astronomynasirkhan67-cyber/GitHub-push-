package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.GitHubApiClient
import com.example.data.model.FileEntryItem
import com.example.data.model.GitHubRepo
import com.example.data.model.GitHubUser
import com.example.data.model.GitTreeItem
import com.example.data.model.PushProgress
import com.example.data.model.PushResult
import com.example.data.model.PushStage
import com.example.data.storage.SecureStorage
import com.example.data.zip.ZipExtractResult
import com.example.data.zip.ZipExtractor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import java.io.File

sealed interface AuthUiState {
    data object Loading : AuthUiState
    data object LoggedOut : AuthUiState
    data class DeviceFlowWaiting(
        val userCode: String,
        val verificationUri: String,
        val expiresIn: Int,
        val interval: Int
    ) : AuthUiState
    data class DeviceFlowPolling(
        val userCode: String,
        val verificationUri: String,
        val statusMessage: String
    ) : AuthUiState
    data class Authenticated(val user: GitHubUser) : AuthUiState
    data class Error(val message: String) : AuthUiState
}

enum class CurrentScreen {
    LOGIN,
    HOME_ZIP,
    REPO_SELECTION,
    PUSH_PROGRESS
}

data class ZipUiState(
    val zipUri: Uri? = null,
    val zipFileName: String? = null,
    val zipFileSize: Long = 0L,
    val isExtracting: Boolean = false,
    val extractionCount: Int = 0,
    val extractionCurrentFile: String = "",
    val extractResult: ZipExtractResult? = null,
    val fileTree: List<FileEntryItem> = emptyList(),
    val removeTopLevelFolder: Boolean = true,
    val errorMessage: String? = null
)

data class RepoUiState(
    val isCreateNew: Boolean = true,
    val newRepoName: String = "",
    val newRepoDescription: String = "",
    val isPrivate: Boolean = false,
    val existingRepos: List<GitHubRepo> = emptyList(),
    val isLoadingRepos: Boolean = false,
    val repoSearchQuery: String = "",
    val selectedExistingRepo: GitHubRepo? = null,
    val targetBranch: String = "main",
    val commitMessage: String = "Upload from Zip2Git",
    val errorMessage: String? = null
)

class Zip2GitViewModel(application: Application) : AndroidViewModel(application) {

    private val storage = SecureStorage(application)
    private val apiClient = GitHubApiClient()
    private val extractor = ZipExtractor()

    private val _currentScreen = MutableStateFlow(CurrentScreen.LOGIN)
    val currentScreen: StateFlow<CurrentScreen> = _currentScreen.asStateFlow()

    private val _authUiState = MutableStateFlow<AuthUiState>(AuthUiState.Loading)
    val authUiState: StateFlow<AuthUiState> = _authUiState.asStateFlow()

    private val _clientId = MutableStateFlow(storage.getClientId())
    val clientId: StateFlow<String> = _clientId.asStateFlow()

    private val _zipUiState = MutableStateFlow(ZipUiState())
    val zipUiState: StateFlow<ZipUiState> = _zipUiState.asStateFlow()

    private val _repoUiState = MutableStateFlow(RepoUiState())
    val repoUiState: StateFlow<RepoUiState> = _repoUiState.asStateFlow()

    private val _pushProgress = MutableStateFlow(PushProgress())
    val pushProgress: StateFlow<PushProgress> = _pushProgress.asStateFlow()

    private val _pushResult = MutableStateFlow<PushResult?>(null)
    val pushResult: StateFlow<PushResult?> = _pushResult.asStateFlow()

    private var pollingJob: Job? = null
    private var pushJob: Job? = null

    init {
        checkExistingAuth()
    }

    fun navigateTo(screen: CurrentScreen) {
        _currentScreen.value = screen
    }

    fun updateClientId(id: String) {
        _clientId.value = id
        storage.saveClientId(id)
    }

    private fun checkExistingAuth() {
        viewModelScope.launch {
            val token = storage.getToken()
            if (token.isNullOrBlank()) {
                _authUiState.value = AuthUiState.LoggedOut
                _currentScreen.value = CurrentScreen.LOGIN
                return@launch
            }

            _authUiState.value = AuthUiState.Loading
            val profileResult = apiClient.getUserProfile(token)
            profileResult.fold(
                onSuccess = { user ->
                    _authUiState.value = AuthUiState.Authenticated(user)
                    _currentScreen.value = CurrentScreen.HOME_ZIP
                    loadUserRepos(token)
                },
                onFailure = {
                    storage.clearToken()
                    _authUiState.value = AuthUiState.LoggedOut
                    _currentScreen.value = CurrentScreen.LOGIN
                }
            )
        }
    }

    fun startDeviceFlow() {
        val cid = _clientId.value.trim()
        if (cid.isBlank()) {
            _authUiState.value = AuthUiState.Error("Please enter your GitHub OAuth Client ID first.")
            return
        }

        pollingJob?.cancel()
        _authUiState.value = AuthUiState.Loading

        viewModelScope.launch {
            val result = apiClient.requestDeviceCode(cid)
            result.fold(
                onSuccess = { resp ->
                    _authUiState.value = AuthUiState.DeviceFlowWaiting(
                        userCode = resp.userCode,
                        verificationUri = resp.verificationUri,
                        expiresIn = resp.expiresIn,
                        interval = resp.interval
                    )
                    startPolling(cid, resp.deviceCode, resp.userCode, resp.verificationUri, resp.interval)
                },
                onFailure = { error ->
                    _authUiState.value = AuthUiState.Error(
                        error.message ?: "Failed to initiate Device Flow. Verify Client ID and internet connection."
                    )
                }
            )
        }
    }

    private fun startPolling(
        cid: String,
        deviceCode: String,
        userCode: String,
        verificationUri: String,
        initialInterval: Int
    ) {
        pollingJob = viewModelScope.launch {
            var interval = if (initialInterval < 5) 5 else initialInterval
            _authUiState.value = AuthUiState.DeviceFlowPolling(
                userCode = userCode,
                verificationUri = verificationUri,
                statusMessage = "Waiting for authorization on GitHub…"
            )

            while (isActive) {
                delay(interval * 1000L)

                val pollResult = apiClient.pollAccessToken(cid, deviceCode)
                pollResult.fold(
                    onSuccess = { tokenResp ->
                        if (!tokenResp.accessToken.isNullOrBlank()) {
                            storage.saveToken(tokenResp.accessToken)
                            val profile = apiClient.getUserProfile(tokenResp.accessToken)
                            profile.fold(
                                onSuccess = { user ->
                                    _authUiState.value = AuthUiState.Authenticated(user)
                                    _currentScreen.value = CurrentScreen.HOME_ZIP
                                    loadUserRepos(tokenResp.accessToken)
                                },
                                onFailure = {
                                    _authUiState.value = AuthUiState.Error("Authenticated, but could not load profile.")
                                }
                            )
                            return@launch
                        }

                        when (tokenResp.error) {
                            "authorization_pending" -> {
                                // Continue waiting
                            }
                            "slow_down" -> {
                                interval += 5
                            }
                            "expired_token" -> {
                                _authUiState.value = AuthUiState.Error("Device code expired. Please start over.")
                                return@launch
                            }
                            "access_denied" -> {
                                _authUiState.value = AuthUiState.Error("Authorization declined on GitHub.")
                                return@launch
                            }
                            else -> {
                                if (tokenResp.error != null) {
                                    _authUiState.value = AuthUiState.Error(
                                        tokenResp.errorDescription ?: "Error: ${tokenResp.error}"
                                    )
                                    return@launch
                                }
                            }
                        }
                    },
                    onFailure = {
                        // Network glitch; keep polling unless cancelled
                    }
                )
            }
        }
    }

    fun loginWithPersonalAccessToken(pat: String) {
        val cleanToken = pat.trim()
        if (cleanToken.isBlank()) {
            _authUiState.value = AuthUiState.Error("Please enter a valid Personal Access Token.")
            return
        }

        viewModelScope.launch {
            _authUiState.value = AuthUiState.Loading
            val profileResult = apiClient.getUserProfile(cleanToken)
            profileResult.fold(
                onSuccess = { user ->
                    storage.saveToken(cleanToken)
                    _authUiState.value = AuthUiState.Authenticated(user)
                    _currentScreen.value = CurrentScreen.HOME_ZIP
                    loadUserRepos(cleanToken)
                },
                onFailure = { err ->
                    _authUiState.value = AuthUiState.Error("Invalid Token or Network error: ${err.message}")
                }
            )
        }
    }

    fun logout() {
        pollingJob?.cancel()
        storage.clearToken()
        _authUiState.value = AuthUiState.LoggedOut
        _currentScreen.value = CurrentScreen.LOGIN
    }

    private fun loadUserRepos(token: String) {
        viewModelScope.launch {
            _repoUiState.update { it.copy(isLoadingRepos = true) }
            val result = apiClient.getUserRepos(token)
            result.fold(
                onSuccess = { list ->
                    _repoUiState.update {
                        it.copy(
                            existingRepos = list,
                            isLoadingRepos = false,
                            selectedExistingRepo = list.firstOrNull(),
                            targetBranch = list.firstOrNull()?.defaultBranch ?: "main"
                        )
                    }
                },
                onFailure = {
                    _repoUiState.update { state -> state.copy(isLoadingRepos = false) }
                }
            )
        }
    }

    fun onZipSelected(uri: Uri) {
        val context = getApplication<Application>()
        var fileName = "archive.zip"
        var fileSize = 0L

        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex != -1) fileName = cursor.getString(nameIndex) ?: fileName
                if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
            }
        }

        _zipUiState.update {
            it.copy(
                zipUri = uri,
                zipFileName = fileName,
                zipFileSize = fileSize,
                isExtracting = true,
                extractionCount = 0,
                errorMessage = null
            )
        }

        viewModelScope.launch {
            val result = extractor.extractZip(context, uri) { count, name ->
                _zipUiState.update {
                    it.copy(
                        extractionCount = count,
                        extractionCurrentFile = name
                    )
                }
            }

            result.fold(
                onSuccess = { extractResult ->
                    _zipUiState.update {
                        it.copy(
                            isExtracting = false,
                            extractResult = extractResult,
                            fileTree = extractResult.fileTree,
                            removeTopLevelFolder = extractResult.singleTopLevelFolder != null
                        )
                    }
                },
                onFailure = { err ->
                    _zipUiState.update {
                        it.copy(
                            isExtracting = false,
                            errorMessage = "Extraction failed: ${err.message}"
                        )
                    }
                }
            )
        }
    }

    fun toggleFileSelection(relativePath: String, isSelected: Boolean) {
        fun updateTree(items: List<FileEntryItem>): List<FileEntryItem> {
            return items.map { item ->
                if (item.relativePath == relativePath) {
                    if (item.isExcluded) {
                        item
                    } else if (item.isDirectory) {
                        // Recursively toggle children
                        fun setChildren(child: FileEntryItem): FileEntryItem {
                            return child.copy(
                                isSelected = if (child.isExcluded) false else isSelected,
                                children = child.children.map { setChildren(it) }.toMutableList()
                            )
                        }
                        item.copy(
                            isSelected = isSelected,
                            children = item.children.map { setChildren(it) }.toMutableList()
                        )
                    } else {
                        item.copy(isSelected = isSelected)
                    }
                } else if (item.isDirectory && relativePath.startsWith("${item.relativePath}/")) {
                    item.copy(children = updateTree(item.children).toMutableList())
                } else {
                    item
                }
            }
        }

        _zipUiState.update { it.copy(fileTree = updateTree(it.fileTree)) }
    }

    fun setRemoveTopLevelFolder(remove: Boolean) {
        _zipUiState.update { it.copy(removeTopLevelFolder = remove) }
    }

    fun setRepoChoiceTab(createNew: Boolean) {
        _repoUiState.update { it.copy(isCreateNew = createNew) }
    }

    fun setNewRepoName(name: String) {
        _repoUiState.update { it.copy(newRepoName = name) }
    }

    fun setNewRepoDescription(desc: String) {
        _repoUiState.update { it.copy(newRepoDescription = desc) }
    }

    fun setNewRepoPrivate(isPrivate: Boolean) {
        _repoUiState.update { it.copy(isPrivate = isPrivate) }
    }

    fun setRepoSearchQuery(query: String) {
        _repoUiState.update { it.copy(repoSearchQuery = query) }
    }

    fun selectExistingRepo(repo: GitHubRepo) {
        _repoUiState.update {
            it.copy(
                selectedExistingRepo = repo,
                targetBranch = repo.defaultBranch
            )
        }
    }

    fun setTargetBranch(branch: String) {
        _repoUiState.update { it.copy(targetBranch = branch) }
    }

    fun setCommitMessage(msg: String) {
        _repoUiState.update { it.copy(commitMessage = msg) }
    }

    fun cancelPush() {
        pushJob?.cancel()
        _pushProgress.value = PushProgress(
            stage = PushStage.FAILED,
            message = "Push operation cancelled by user."
        )
        _pushResult.value = PushResult(
            isSuccess = false,
            errorMessage = "Operation cancelled."
        )
    }

    fun startPush() {
        val token = storage.getToken()
        if (token.isNullOrBlank()) {
            _pushResult.value = PushResult(isSuccess = false, errorMessage = "User is not logged in.")
            return
        }

        val extractResult = _zipUiState.value.extractResult
        if (extractResult == null) {
            _pushResult.value = PushResult(isSuccess = false, errorMessage = "No ZIP files extracted.")
            return
        }

        val stripSingleTopFolder = if (_zipUiState.value.removeTopLevelFolder) {
            extractResult.singleTopLevelFolder
        } else null

        val selectedFiles = extractor.flattenSelectedFiles(
            _zipUiState.value.fileTree,
            extractResult.extractDir,
            stripSingleTopFolder
        )

        if (selectedFiles.isEmpty()) {
            _pushResult.value = PushResult(
                isSuccess = false,
                errorMessage = "No files selected to push. Please select at least one file."
            )
            return
        }

        _currentScreen.value = CurrentScreen.PUSH_PROGRESS
        _pushResult.value = null

        pushJob = viewModelScope.launch {
            try {
                val isCreate = _repoUiState.value.isCreateNew
                val userState = _authUiState.value as? AuthUiState.Authenticated
                val owner = userState?.user?.login ?: ""
                var repoName = ""
                var branch = _repoUiState.value.targetBranch.trim().ifBlank { "main" }

                if (isCreate) {
                    repoName = _repoUiState.value.newRepoName.trim()
                    if (repoName.isBlank()) {
                        throw IllegalArgumentException("Repository name cannot be empty.")
                    }

                    _pushProgress.value = PushProgress(
                        stage = PushStage.FETCHING_BRANCH,
                        message = "Creating new repository '$repoName'…"
                    )

                    val createRes = apiClient.createRepository(
                        token = token,
                        name = repoName,
                        description = _repoUiState.value.newRepoDescription,
                        isPrivate = _repoUiState.value.isPrivate
                    )

                    createRes.getOrThrow()
                    // Allow GitHub to finish initializing the auto_init default branch
                    delay(2000L)
                } else {
                    val existing = _repoUiState.value.selectedExistingRepo
                        ?: throw IllegalArgumentException("Please select an existing repository.")
                    repoName = existing.name
                    branch = _repoUiState.value.targetBranch.trim().ifBlank { existing.defaultBranch }
                }

                // Step A: Get latest commit on target branch
                _pushProgress.value = PushProgress(
                    stage = PushStage.FETCHING_BRANCH,
                    message = "Fetching branch '$branch' reference…"
                )

                val commitSha = apiClient.getBranchCommitSha(token, owner, repoName, branch).getOrThrow()
                val baseTreeSha = apiClient.getCommitTreeSha(token, owner, repoName, commitSha).getOrThrow()

                // Step B: Upload Blobs with concurrency limit 4
                val totalFiles = selectedFiles.size
                var completedFiles = 0
                val semaphore = Semaphore(4)
                val treeItems = mutableListOf<GitTreeItem>()

                _pushProgress.value = PushProgress(
                    stage = PushStage.UPLOADING_BLOBS,
                    completedFiles = 0,
                    totalFiles = totalFiles,
                    message = "Uploading 0/$totalFiles files…"
                )

                // Process in chunks or parallel deferreds
                val blobUploadTasks = selectedFiles.map { (gitPath, physicalFile) ->
                    async {
                        if (!isActive) throw CancellationException()

                        val bytes = physicalFile.readBytes()
                        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)

                        val blobResult = apiClient.createBlobWithRetry(
                            token = token,
                            owner = owner,
                            repo = repoName,
                            base64Content = base64,
                            semaphore = semaphore
                        )

                        val blobSha = blobResult.getOrElse { error ->
                            if (gitPath.contains(".github/workflows")) {
                                throw Exception("WORKFLOW_SCOPE_ERROR: ${error.message}")
                            }
                            throw error
                        }

                        synchronized(treeItems) {
                            treeItems.add(
                                GitTreeItem(
                                    path = gitPath,
                                    mode = "100644",
                                    type = "blob",
                                    sha = blobSha
                                )
                            )
                            completedFiles++
                            _pushProgress.value = PushProgress(
                                stage = PushStage.UPLOADING_BLOBS,
                                currentFileName = gitPath,
                                completedFiles = completedFiles,
                                totalFiles = totalFiles,
                                message = "Uploaded $completedFiles of $totalFiles files"
                            )
                        }
                    }
                }

                blobUploadTasks.awaitAll()

                // Step C: Create Git Tree
                _pushProgress.value = PushProgress(
                    stage = PushStage.CREATING_TREE,
                    completedFiles = totalFiles,
                    totalFiles = totalFiles,
                    message = "Creating Git tree with ${treeItems.size} entries…"
                )

                val newTreeSha = apiClient.createTree(
                    token = token,
                    owner = owner,
                    repo = repoName,
                    baseTreeSha = baseTreeSha,
                    items = treeItems
                ).getOrThrow()

                // Step D: Create Commit
                _pushProgress.value = PushProgress(
                    stage = PushStage.CREATING_COMMIT,
                    completedFiles = totalFiles,
                    totalFiles = totalFiles,
                    message = "Creating commit…"
                )

                val commitMessage = _repoUiState.value.commitMessage.ifBlank { "Upload from Zip2Git" }
                val newCommitSha = apiClient.createCommit(
                    token = token,
                    owner = owner,
                    repo = repoName,
                    message = commitMessage,
                    treeSha = newTreeSha,
                    parentCommitSha = commitSha
                ).getOrThrow()

                // Step E: Update Ref
                _pushProgress.value = PushProgress(
                    stage = PushStage.UPDATING_REF,
                    completedFiles = totalFiles,
                    totalFiles = totalFiles,
                    message = "Updating branch '$branch' reference…"
                )

                apiClient.updateRef(
                    token = token,
                    owner = owner,
                    repo = repoName,
                    branch = branch,
                    newCommitSha = newCommitSha
                ).getOrThrow()

                _pushProgress.value = PushProgress(
                    stage = PushStage.COMPLETED,
                    completedFiles = totalFiles,
                    totalFiles = totalFiles,
                    message = "Done! All files pushed to $owner/$repoName"
                )

                val repoUrl = "https://github.com/$owner/$repoName"
                val actionsUrl = "https://github.com/$owner/$repoName/actions"

                _pushResult.value = PushResult(
                    isSuccess = true,
                    commitSha = newCommitSha,
                    repoFullName = "$owner/$repoName",
                    branch = branch,
                    repoUrl = repoUrl,
                    actionsUrl = actionsUrl,
                    filesUploaded = totalFiles
                )

            } catch (ce: CancellationException) {
                _pushProgress.value = PushProgress(
                    stage = PushStage.FAILED,
                    message = "Upload cancelled."
                )
                _pushResult.value = PushResult(
                    isSuccess = false,
                    errorMessage = "Operation cancelled."
                )
            } catch (e: Exception) {
                val errorMsg = e.message ?: "An unknown error occurred during push."
                val isWorkflowError = errorMsg.contains("WORKFLOW_SCOPE_ERROR") ||
                        errorMsg.contains("workflow", ignoreCase = true)

                _pushProgress.value = PushProgress(
                    stage = PushStage.FAILED,
                    message = errorMsg
                )

                _pushResult.value = PushResult(
                    isSuccess = false,
                    errorMessage = errorMsg,
                    isWorkflowScopeError = isWorkflowError
                )
            }
        }
    }
}
