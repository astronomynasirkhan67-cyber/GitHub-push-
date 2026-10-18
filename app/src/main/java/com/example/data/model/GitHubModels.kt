package com.example.data.model

data class GitHubUser(
    val login: String,
    val name: String?,
    val avatarUrl: String,
    val htmlUrl: String,
    val publicRepos: Int = 0,
    val totalPrivateRepos: Int = 0
)

data class DeviceCodeResponse(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val expiresIn: Int,
    val interval: Int
)

data class OAuthTokenResponse(
    val accessToken: String?,
    val tokenType: String?,
    val scope: String?,
    val error: String?,
    val errorDescription: String?
)

data class GitHubRepo(
    val id: Long,
    val name: String,
    val fullName: String,
    val isPrivate: Boolean,
    val description: String?,
    val defaultBranch: String,
    val htmlUrl: String,
    val updatedAt: String?
)

data class FileEntryItem(
    val relativePath: String,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val isSelected: Boolean = true,
    val isExcluded: Boolean = false,
    val exclusionReason: String? = null,
    val children: MutableList<FileEntryItem> = mutableListOf()
)

data class GitTreeItem(
    val path: String,
    val mode: String = "100644",
    val type: String = "blob",
    val sha: String
)

data class PushProgress(
    val stage: PushStage = PushStage.IDLE,
    val currentFileName: String = "",
    val completedFiles: Int = 0,
    val totalFiles: Int = 0,
    val message: String = ""
)

enum class PushStage {
    IDLE,
    FETCHING_BRANCH,
    UPLOADING_BLOBS,
    CREATING_TREE,
    CREATING_COMMIT,
    UPDATING_REF,
    COMPLETED,
    FAILED
}

data class PushResult(
    val isSuccess: Boolean,
    val commitSha: String? = null,
    val repoFullName: String? = null,
    val branch: String? = null,
    val repoUrl: String? = null,
    val actionsUrl: String? = null,
    val filesUploaded: Int = 0,
    val errorMessage: String? = null,
    val isWorkflowScopeError: Boolean = false
)
