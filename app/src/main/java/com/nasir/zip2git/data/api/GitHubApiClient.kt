package com.nasir.zip2git.data.api

import android.util.Log
import com.nasir.zip2git.data.model.CreateBlobRequest
import com.nasir.zip2git.data.model.CreateCommitRequest
import com.nasir.zip2git.data.model.CreateRepoRequest
import com.nasir.zip2git.data.model.CreateTreeRequest
import com.nasir.zip2git.data.model.DeviceCodeResponse
import com.nasir.zip2git.data.model.GitHubRepo
import com.nasir.zip2git.data.model.GitHubUser
import com.nasir.zip2git.data.model.GitShaResponse
import com.nasir.zip2git.data.model.GitTreeItem
import com.nasir.zip2git.data.model.OAuthTokenResponse
import com.nasir.zip2git.data.model.UpdateRefRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class GitHubApiClient {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        coerceInputValues = true
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun requestDeviceCode(clientId: String): Result<DeviceCodeResponse> =
        withContext(Dispatchers.IO) {
            try {
                val jsonPayload = """{"client_id":"$clientId","scope":"repo workflow"}"""
                val request = Request.Builder()
                    .url("https://github.com/login/device/code")
                    .addHeader("Accept", "application/json")
                    .post(jsonPayload.toRequestBody(jsonMediaType))
                    .build()

                val response = client.newCall(request).execute()
                val responseString = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IOException("HTTP ${response.code}: $responseString")
                    )
                }

                val parsed = json.decodeFromString<DeviceCodeResponse>(responseString)
                Result.success(parsed)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun pollAccessToken(clientId: String, deviceCode: String): Result<OAuthTokenResponse> =
        withContext(Dispatchers.IO) {
            try {
                val jsonPayload = """{"client_id":"$clientId","device_code":"$deviceCode","grant_type":"urn:ietf:params:oauth:grant-type:device_code"}"""
                val request = Request.Builder()
                    .url("https://github.com/login/oauth/access_token")
                    .addHeader("Accept", "application/json")
                    .post(jsonPayload.toRequestBody(jsonMediaType))
                    .build()

                val response = client.newCall(request).execute()
                val responseString = response.body?.string().orEmpty()

                val parsed = json.decodeFromString<OAuthTokenResponse>(responseString)
                Result.success(parsed)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun getUserProfile(token: String): Result<GitHubUser> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("https://api.github.com/user")
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Accept", "application/vnd.github+json")
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                val responseString = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IOException("Failed to fetch profile: HTTP ${response.code} $responseString")
                    )
                }

                val user = json.decodeFromString<GitHubUser>(responseString)
                Result.success(user)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun getUserRepos(token: String): Result<List<GitHubRepo>> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("https://api.github.com/user/repos?per_page=100&sort=updated&affiliation=owner,collaborator")
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Accept", "application/vnd.github+json")
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                val responseString = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IOException("HTTP ${response.code}: $responseString")
                    )
                }

                val list = json.decodeFromString<List<GitHubRepo>>(responseString)
                Result.success(list)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun createRepository(
        token: String,
        name: String,
        description: String,
        isPrivate: Boolean
    ): Result<GitHubRepo> = withContext(Dispatchers.IO) {
        try {
            val reqModel = CreateRepoRequest(
                name = name.trim(),
                description = description.trim().ifBlank { null },
                isPrivate = isPrivate,
                autoInit = true
            )
            val jsonPayload = json.encodeToString(reqModel)

            val request = Request.Builder()
                .url("https://api.github.com/user/repos")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/vnd.github+json")
                .post(jsonPayload.toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            val responseString = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    IOException("Failed to create repo: HTTP ${response.code} $responseString")
                )
            }

            val repo = json.decodeFromString<GitHubRepo>(responseString)
            Result.success(repo)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getBranchCommitSha(
        token: String,
        owner: String,
        repo: String,
        branch: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val refUrl = "https://api.github.com/repos/$owner/$repo/git/ref/heads/$branch"
            val request = Request.Builder()
                .url(refUrl)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/vnd.github+json")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val responseString = response.body?.string().orEmpty()

            if (response.isSuccessful) {
                val element = json.parseToJsonElement(responseString)
                val sha = element.jsonObject["object"]?.jsonObject?.get("sha")?.jsonPrimitive?.content
                if (!sha.isNullOrBlank()) {
                    return@withContext Result.success(sha)
                }
            }

            val branchUrl = "https://api.github.com/repos/$owner/$repo/branches/$branch"
            val branchRequest = Request.Builder()
                .url(branchUrl)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/vnd.github+json")
                .get()
                .build()

            val branchResponse = client.newCall(branchRequest).execute()
            val branchResponseString = branchResponse.body?.string().orEmpty()

            if (branchResponse.isSuccessful) {
                val element = json.parseToJsonElement(branchResponseString)
                val sha = element.jsonObject["commit"]?.jsonObject?.get("sha")?.jsonPrimitive?.content
                if (!sha.isNullOrBlank()) {
                    return@withContext Result.success(sha)
                }
            }

            Result.failure(
                IOException("Branch '$branch' not found in $owner/$repo. Please ensure the repository is not empty and the branch exists.")
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCommitTreeSha(
        token: String,
        owner: String,
        repo: String,
        commitSha: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.github.com/repos/$owner/$repo/git/commits/$commitSha"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/vnd.github+json")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val responseString = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    IOException("HTTP ${response.code}: $responseString")
                )
            }

            val element = json.parseToJsonElement(responseString)
            val treeSha = element.jsonObject["tree"]?.jsonObject?.get("sha")?.jsonPrimitive?.content
                ?: return@withContext Result.failure(IOException("Missing tree sha in commit response"))

            Result.success(treeSha)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createBlobWithRetry(
        token: String,
        owner: String,
        repo: String,
        base64Content: String,
        semaphore: Semaphore
    ): Result<String> = withContext(Dispatchers.IO) {
        semaphore.withPermit {
            var attempt = 0
            val maxAttempts = 3
            var delayMs = 1000L

            while (attempt < maxAttempts) {
                attempt++
                try {
                    val reqModel = CreateBlobRequest(content = base64Content, encoding = "base64")
                    val jsonPayload = json.encodeToString(reqModel)

                    val request = Request.Builder()
                        .url("https://api.github.com/repos/$owner/$repo/git/blobs")
                        .addHeader("Authorization", "Bearer $token")
                        .addHeader("Accept", "application/vnd.github+json")
                        .post(jsonPayload.toRequestBody(jsonMediaType))
                        .build()

                    val response = client.newCall(request).execute()
                    val responseString = response.body?.string().orEmpty()

                    if (response.isSuccessful) {
                        val parsed = json.decodeFromString<GitShaResponse>(responseString)
                        return@withContext Result.success(parsed.sha)
                    }

                    val code = response.code
                    if (code == 403 || code == 429 || code in 500..599) {
                        val retryAfterHeader = response.header("Retry-After")
                        val backoff = retryAfterHeader?.toLongOrNull()?.times(1000) ?: delayMs
                        if (attempt < maxAttempts) {
                            Log.w("GitHubApiClient", "Blob upload hit HTTP $code, retrying in ${backoff}ms (attempt $attempt)")
                            delay(backoff)
                            delayMs *= 2
                            continue
                        }
                    }

                    return@withContext Result.failure(
                        IOException("HTTP $code creating blob: $responseString")
                    )
                } catch (e: Exception) {
                    if (attempt >= maxAttempts) {
                        return@withContext Result.failure(e)
                    }
                    delay(delayMs)
                    delayMs *= 2
                }
            }
            Result.failure(IOException("Failed to create blob after $maxAttempts attempts"))
        }
    }

    suspend fun createTree(
        token: String,
        owner: String,
        repo: String,
        baseTreeSha: String,
        items: List<GitTreeItem>
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val reqModel = CreateTreeRequest(baseTree = baseTreeSha, tree = items)
            val jsonPayload = json.encodeToString(reqModel)

            val request = Request.Builder()
                .url("https://api.github.com/repos/$owner/$repo/git/trees")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/vnd.github+json")
                .post(jsonPayload.toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            val responseString = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    IOException("Failed to create git tree (HTTP ${response.code}): $responseString")
                )
            }

            val parsed = json.decodeFromString<GitShaResponse>(responseString)
            Result.success(parsed.sha)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createCommit(
        token: String,
        owner: String,
        repo: String,
        message: String,
        treeSha: String,
        parentCommitSha: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val reqModel = CreateCommitRequest(
                message = message.ifBlank { "Upload from Zip2Git" },
                tree = treeSha,
                parents = listOf(parentCommitSha)
            )
            val jsonPayload = json.encodeToString(reqModel)

            val request = Request.Builder()
                .url("https://api.github.com/repos/$owner/$repo/git/commits")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/vnd.github+json")
                .post(jsonPayload.toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            val responseString = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    IOException("Failed to create commit (HTTP ${response.code}): $responseString")
                )
            }

            val parsed = json.decodeFromString<GitShaResponse>(responseString)
            Result.success(parsed.sha)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateRef(
        token: String,
        owner: String,
        repo: String,
        branch: String,
        newCommitSha: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val reqModel = UpdateRefRequest(sha = newCommitSha, force = false)
            val jsonPayload = json.encodeToString(reqModel)

            val request = Request.Builder()
                .url("https://api.github.com/repos/$owner/$repo/git/refs/heads/$branch")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/vnd.github+json")
                .patch(jsonPayload.toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            val responseString = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    IOException("Failed to update ref heads/$branch (HTTP ${response.code}): $responseString")
                )
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
