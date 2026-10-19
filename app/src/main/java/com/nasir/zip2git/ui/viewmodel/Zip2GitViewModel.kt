package com.nasir.zip2git.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nasir.zip2git.data.api.GitHubApiClient
import com.nasir.zip2git.data.model.FileEntryItem
import com.nasir.zip2git.data.model.GitHubRepo
import com.nasir.zip2git.data.model.GitHubUser
import com.nasir.zip2git.data.model.GitTreeItem
import com.nasir.zip2git.data.model.PushProgress
import com.nasir.zip2git.data.model.PushResult
import com.nasir.zip2git.data.model.PushStage
import com.nasir.zip2git.data.storage.SecureStorage
import com.nasir.zip2git.data.zip.ZipExtractResult
import com.nasir.zip2git.data.zip.ZipExtractor
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
    PUSH_PROGRESS,
    EDITOR
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

data class EditorEntry(
    val name: String,
    val relativePath: String,
    val isDirectory: Boolean,
    val sizeBytes: Long
)

data class EditorUiState(
    val currentDir: String = "",
    val entries: List<EditorEntry> = emptyList(),
    val openFilePath: String? = null,
    val fileContent: String = "",
    val isDirty: Boolean = false,
    val message: String? = null
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

    private val _editorState = MutableStateFlow(EditorUiState())
    val editorState: StateFlow<EditorUiState> = _editorState.asStateFlow()

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
                            "authorization_pending" -> {}
                            "slow_down" -> { interval += 5 }
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
                    onFailure = {}
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
        val (fileName, fileSize) = extractor.getZipMetadata(context, uri)

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


    // ------------------------------------------------------------------
    // Code editor
    // ------------------------------------------------------------------

    private val maxEditableBytes = 300 * 1024

    private fun workspaceRoot(): File? = _zipUiState.value.extractResult?.extractDir

    private fun resolveInWorkspace(relativePath: String): File? {
        val root = workspaceRoot() ?: return null
        val target = if (relativePath.isEmpty()) root else File(root, relativePath)
        return try {
            val rootPath = root.canonicalPath
            val targetPath = target.canonicalPath
            if (targetPath == rootPath || targetPath.startsWith(rootPath + File.separator)) target else null
        } catch (e: Exception) {
            null
        }
    }

    private fun relativeToWorkspace(file: File): String {
        val root = workspaceRoot() ?: return file.name
        return file.relativeTo(root).path.replace('\\', '/')
    }

    private fun cleanRelativeName(raw: String): String? {
        val parts = raw.trim().replace('\\', '/').split('/').filter { it.isNotBlank() && it != "." }
        if (parts.isEmpty()) return null
        if (parts.any { it == ".." || it == ".git" }) return null
        return parts.joinToString("/")
    }

    private fun joinPath(dir: String, name: String): String =
        if (dir.isEmpty()) name else "$dir/$name"

    private fun setEditorMessage(msg: String?) {
        _editorState.update { it.copy(message = msg) }
    }

    fun editorClearMessage() {
        setEditorMessage(null)
    }

    fun openEditor() {
        val context = getApplication<Application>()
        if (_zipUiState.value.extractResult == null) {
            val dir = File(context.cacheDir, "extracted_zip").apply {
                if (exists()) deleteRecursively()
                mkdirs()
            }
            _zipUiState.update {
                it.copy(
                    zipUri = null,
                    zipFileName = "New project (Code Editor)",
                    zipFileSize = 0L,
                    extractResult = ZipExtractResult(dir, 0, 0L, null, emptyList()),
                    fileTree = emptyList(),
                    removeTopLevelFolder = false,
                    errorMessage = null
                )
            }
        }
        _editorState.value = EditorUiState()
        editorOpenDir("")
        _currentScreen.value = CurrentScreen.EDITOR
    }

    fun exitEditor() {
        refreshTreeFromDisk()
        _currentScreen.value = CurrentScreen.HOME_ZIP
    }

    fun editorOpenDir(dir: String) {
        val folder = resolveInWorkspace(dir) ?: return
        val items = (folder.listFiles() ?: emptyArray<File>())
            .filter { it.name != ".git" }
            .sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
            .map {
                EditorEntry(
                    name = it.name,
                    relativePath = relativeToWorkspace(it),
                    isDirectory = it.isDirectory,
                    sizeBytes = if (it.isDirectory) 0L else it.length()
                )
            }
        _editorState.update { it.copy(currentDir = dir, entries = items) }
    }

    fun editorNavigateUp() {
        val parent = _editorState.value.currentDir.substringBeforeLast('/', "")
        editorOpenDir(parent)
    }

    private fun looksBinary(bytes: ByteArray): Boolean {
        val limit = minOf(bytes.size, 8000)
        for (i in 0 until limit) {
            if (bytes[i] == 0.toByte()) return true
        }
        return false
    }

    fun editorOpenFile(relativePath: String) {
        val file = resolveInWorkspace(relativePath) ?: return
        if (!file.isFile) return
        if (file.length() > maxEditableBytes) {
            setEditorMessage("File is too large to edit here (max 300 KB).")
            return
        }
        try {
            val bytes = file.readBytes()
            if (looksBinary(bytes)) {
                setEditorMessage("Binary file - cannot be edited as text.")
                return
            }
            _editorState.update {
                it.copy(
                    openFilePath = relativePath,
                    fileContent = String(bytes, Charsets.UTF_8),
                    isDirty = false,
                    message = null
                )
            }
        } catch (e: Exception) {
            setEditorMessage("Could not open file: ${e.message}")
        }
    }

    fun editorUpdateContent(text: String) {
        _editorState.update { it.copy(fileContent = text, isDirty = true) }
    }

    fun editorSave(): Boolean {
        val path = _editorState.value.openFilePath ?: return false
        val file = resolveInWorkspace(path) ?: return false
        return try {
            file.writeText(_editorState.value.fileContent)
            _editorState.update { it.copy(isDirty = false, message = "Saved") }
            true
        } catch (e: Exception) {
            setEditorMessage("Save failed: ${e.message}")
            false
        }
    }

    fun editorCloseFile() {
        _editorState.update { it.copy(openFilePath = null, fileContent = "", isDirty = false) }
        editorOpenDir(_editorState.value.currentDir)
    }

    fun editorCreateFile(rawName: String) {
        val name = cleanRelativeName(rawName)
        if (name == null) {
            setEditorMessage("Invalid file name.")
            return
        }
        val relative = joinPath(_editorState.value.currentDir, name)
        val file = resolveInWorkspace(relative)
        if (file == null) {
            setEditorMessage("Invalid file path.")
            return
        }
        if (file.exists()) {
            setEditorMessage("Already exists: $name")
            return
        }
        try {
            file.parentFile?.mkdirs()
            file.createNewFile()
        } catch (e: Exception) {
            setEditorMessage("Could not create file: ${e.message}")
            return
        }
        editorOpenDir(_editorState.value.currentDir)
        editorOpenFile(relative)
    }

    fun editorCreateFolder(rawName: String) {
        val name = cleanRelativeName(rawName)
        if (name == null) {
            setEditorMessage("Invalid folder name.")
            return
        }
        val relative = joinPath(_editorState.value.currentDir, name)
        val folder = resolveInWorkspace(relative)
        if (folder == null) {
            setEditorMessage("Invalid folder path.")
            return
        }
        if (folder.exists()) {
            setEditorMessage("Already exists: $name")
            return
        }
        try {
            folder.mkdirs()
            // Git does not track empty folders, so add a placeholder file.
            File(folder, ".gitkeep").createNewFile()
            setEditorMessage("Folder created (.gitkeep added so Git keeps it).")
        } catch (e: Exception) {
            setEditorMessage("Could not create folder: ${e.message}")
            return
        }
        editorOpenDir(_editorState.value.currentDir)
    }

    fun editorDelete(relativePath: String) {
        if (relativePath.isEmpty()) return
        val target = resolveInWorkspace(relativePath) ?: return
        try {
            target.deleteRecursively()
        } catch (e: Exception) {
            setEditorMessage("Delete failed: ${e.message}")
            return
        }
        val open = _editorState.value.openFilePath
        if (open != null && (open == relativePath || open.startsWith("$relativePath/"))) {
            _editorState.update { it.copy(openFilePath = null, fileContent = "", isDirty = false) }
        }
        editorOpenDir(_editorState.value.currentDir)
    }

    fun editorRename(relativePath: String, rawNewName: String) {
        val newName = rawNewName.trim()
        if (newName.isBlank() || newName.contains('/') || newName.contains('\\') ||
            newName == "." || newName == ".." || newName == ".git"
        ) {
            setEditorMessage("Invalid name.")
            return
        }
        val source = resolveInWorkspace(relativePath) ?: return
        val destination = File(source.parentFile, newName)
        if (destination.exists()) {
            setEditorMessage("Already exists: $newName")
            return
        }
        if (!source.renameTo(destination)) {
            setEditorMessage("Rename failed.")
            return
        }
        editorOpenDir(_editorState.value.currentDir)
    }

    /**
     * Rebuilds the file tree from disk after editing, keeping the user's
     * existing deselections so nothing they unchecked gets pushed by accident.
     */
    private fun refreshTreeFromDisk() {
        val current = _zipUiState.value
        val result = current.extractResult ?: return
        val dir = result.extractDir

        val deselected = mutableSetOf<String>()
        fun collect(items: List<FileEntryItem>) {
            for (item in items) {
                if (item.isDirectory) {
                    collect(item.children)
                } else if (!item.isSelected && !item.isExcluded) {
                    deselected.add(item.relativePath)
                }
            }
        }
        collect(current.fileTree)

        fun applySelection(item: FileEntryItem): FileEntryItem {
            val kids = item.children.map { applySelection(it) }.toMutableList()
            val selected = if (item.isDirectory) {
                item.isSelected && (kids.isEmpty() || kids.any { it.isSelected })
            } else {
                item.isSelected && item.relativePath !in deselected
            }
            return item.copy(isSelected = selected, children = kids)
        }

        val newTree = extractor.buildFileTree(dir).map { applySelection(it) }

        val roots = dir.listFiles() ?: emptyArray<File>()
        val single = if (roots.size == 1 && roots[0].isDirectory) roots[0].name else null
        val keepStripToggle = single != null && single == result.singleTopLevelFolder

        val files = dir.walkTopDown().filter { it.isFile }.toList()

        _zipUiState.update {
            it.copy(
                extractResult = result.copy(
                    totalExtractedFiles = files.size,
                    totalBytes = files.sumOf { f -> f.length() },
                    singleTopLevelFolder = single,
                    fileTree = newTree
                ),
                fileTree = newTree,
                removeTopLevelFolder = if (keepStripToggle) it.removeTopLevelFolder else false
            )
        }
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
                    delay(2000L)
                } else {
                    val existing = _repoUiState.value.selectedExistingRepo
                        ?: throw IllegalArgumentException("Please select an existing repository.")
                    repoName = existing.name
                    branch = _repoUiState.value.targetBranch.trim().ifBlank { existing.defaultBranch }
                }

                _pushProgress.value = PushProgress(
                    stage = PushStage.FETCHING_BRANCH,
                    message = "Fetching branch '$branch' reference…"
                )

                val commitSha = apiClient.getBranchCommitSha(token, owner, repoName, branch).getOrThrow()
                val baseTreeSha = apiClient.getCommitTreeSha(token, owner, repoName, commitSha).getOrThrow()

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
