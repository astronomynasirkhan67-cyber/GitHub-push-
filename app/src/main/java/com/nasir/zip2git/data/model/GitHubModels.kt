package com.nasir.zip2git.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GitHubUser(
    val login: String,
    val name: String? = null,
    @SerialName("avatar_url") val avatarUrl: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    @SerialName("public_repos") val publicRepos: Int = 0,
    @SerialName("total_private_repos") val totalPrivateRepos: Int = 0
)

@Serializable
data class DeviceCodeResponse(
    @SerialName("device_code") val deviceCode: String,
    @SerialName("user_code") val userCode: String,
    @SerialName("verification_uri") val verificationUri: String,
    @SerialName("expires_in") val expiresIn: Int = 900,
    val interval: Int = 5
)

@Serializable
data class OAuthTokenResponse(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("token_type") val tokenType: String? = null,
    val scope: String? = null,
    val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null
)

@Serializable
data class GitHubRepo(
    val id: Long,
    val name: String,
    @SerialName("full_name") val fullName: String,
    @SerialName("private") val isPrivate: Boolean = false,
    val description: String? = null,
    @SerialName("default_branch") val defaultBranch: String = "main",
    @SerialName("html_url") val htmlUrl: String = "",
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class GitTreeItem(
    val path: String,
    val mode: String = "100644",
    val type: String = "blob",
    val sha: String
)

@Serializable
data class CreateTreeRequest(
    @SerialName("base_tree") val baseTree: String,
    val tree: List<GitTreeItem>
)

@Serializable
data class CreateCommitRequest(
    val message: String,
    val tree: String,
    val parents: List<String>
)

@Serializable
data class UpdateRefRequest(
    val sha: String,
    val force: Boolean = false
)

@Serializable
data class CreateBlobRequest(
    val content: String,
    val encoding: String = "base64"
)

@Serializable
data class CreateRepoRequest(
    val name: String,
    val description: String? = null,
    @SerialName("private") val isPrivate: Boolean = false,
    @SerialName("auto_init") val autoInit: Boolean = true
)

@Serializable
data class GitShaResponse(
    val sha: String = ""
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
