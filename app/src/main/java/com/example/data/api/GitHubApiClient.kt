package com.example.data.api

import android.util.Log
import com.example.data.model.DeviceCodeResponse
import com.example.data.model.GitHubRepo
import com.example.data.model.GitHubUser
import com.example.data.model.GitTreeItem
import com.example.data.model.OAuthTokenResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class GitHubApiClient {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun requestDeviceCode(clientId: String): Result<DeviceCodeResponse> =
        withContext(Dispatchers.IO) {
            try {
                val jsonBody = JSONObject().apply {
                    put("client_id", clientId)
                    put("scope", "repo workflow")
                }.toString()

                val request = Request.Builder()
                    .url("https://github.com/login/device/code")
                    .addHeader("Accept", "application/json")
                    .post(jsonBody.toRequestBody(jsonMediaType))
                    .build()

                val response = client.newCall(request).execute()
                val responseString = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IOException("HTTP ${response.code}: $responseString")
                    )
                }

                val json = JSONObject(responseString)
                if (json.has("error")) {
                    return@withContext Result.failure(
                        IOException(json.optString("error_description", json.getString("error")))
                    )
                }

                Result.success(
                    DeviceCodeResponse(
                        deviceCode = json.getString("device_code"),
                        userCode = json.getString("user_code"),
                        verificationUri = json.getString("verification_uri"),
                        expiresIn = json.optInt("expires_in", 900),
                        interval = json.optInt("interval", 5)
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun pollAccessToken(clientId: String, deviceCode: String): Result<OAuthTokenResponse> =
        withContext(Dispatchers.IO) {
            try {
                val jsonBody = JSONObject().apply {
                    put("client_id", clientId)
                    put("device_code", deviceCode)
                    put("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                }.toString()

                val request = Request.Builder()
                    .url("https://github.com/login/oauth/access_token")
                    .addHeader("Accept", "application/json")
                    .post(jsonBody.toRequestBody(jsonMediaType))
                    .build()

                val response = client.newCall(request).execute()
                val responseString = response.body?.string().orEmpty()

                val json = JSONObject(responseString)
                if (json.has("access_token")) {
                    return@withContext Result.success(
                        OAuthTokenResponse(
                            accessToken = json.getString("access_token"),
                            tokenType = json.optString("token_type"),
                            scope = json.optString("scope"),
                            error = null,
                            errorDescription = null
                        )
                    )
                } else if (json.has("error")) {
                    return@withContext Result.success(
                        OAuthTokenResponse(
                            accessToken = null,
                            tokenType = null,
                            scope = null,
                            error = json.getString("error"),
                            errorDescription = json.optString("error_description")
                        )
                    )
                }

                Result.failure(IOException("Unexpected response: $responseString"))
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

                val json = JSONObject(responseString)
                Result.success(
                    GitHubUser(
                        login = json.getString("login"),
                        name = if (json.isNull("name")) null else json.optString("name"),
                        avatarUrl = json.getString("avatar_url"),
                        htmlUrl = json.getString("html_url"),
                        publicRepos = json.optInt("public_repos", 0),
                        totalPrivateRepos = json.optInt("total_private_repos", 0)
                    )
                )
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

                val jsonArray = JSONArray(responseString)
                val repos = mutableListOf<GitHubRepo>()
                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(i)
                    repos.add(
                        GitHubRepo(
                            id = item.getLong("id"),
                            name = item.getString("name"),
                            fullName = item.getString("full_name"),
                            isPrivate = item.getBoolean("private"),
                            description = if (item.isNull("description")) null else item.optString("description"),
                            defaultBranch = item.optString("default_branch", "main"),
                            htmlUrl = item.getString("html_url"),
                            updatedAt = if (item.isNull("updated_at")) null else item.optString("updated_at")
                        )
                    )
                }
                Result.success(repos)
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
            val jsonBody = JSONObject().apply {
                put("name", name.trim())
                if (description.isNotBlank()) {
                    put("description", description.trim())
                }
                put("private", isPrivate)
                put("auto_init", true)
            }.toString()

            val request = Request.Builder()
                .url("https://api.github.com/user/repos")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/vnd.github+json")
                .post(jsonBody.toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            val responseString = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    IOException("Failed to create repo: HTTP ${response.code} $responseString")
                )
            }

            val item = JSONObject(responseString)
            Result.success(
                GitHubRepo(
                    id = item.getLong("id"),
                    name = item.getString("name"),
                    fullName = item.getString("full_name"),
                    isPrivate = item.getBoolean("private"),
                    description = if (item.isNull("description")) null else item.optString("description"),
                    defaultBranch = item.optString("default_branch", "main"),
                    htmlUrl = item.getString("html_url"),
                    updatedAt = item.optString("updated_at")
                )
            )
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
            // First attempt: git/ref/heads/{branch}
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
                val json = JSONObject(responseString)
                val obj = json.getJSONObject("object")
                return@withContext Result.success(obj.getString("sha"))
            }

            // Fallback attempt: /branches/{branch}
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
                val json = JSONObject(branchResponseString)
                val commitObj = json.getJSONObject("commit")
                return@withContext Result.success(commitObj.getString("sha"))
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

            val json = JSONObject(responseString)
            val treeObj = json.getJSONObject("tree")
            Result.success(treeObj.getString("sha"))
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
                    val jsonBody = JSONObject().apply {
                        put("content", base64Content)
                        put("encoding", "base64")
                    }.toString()

                    val request = Request.Builder()
                        .url("https://api.github.com/repos/$owner/$repo/git/blobs")
                        .addHeader("Authorization", "Bearer $token")
                        .addHeader("Accept", "application/vnd.github+json")
                        .post(jsonBody.toRequestBody(jsonMediaType))
                        .build()

                    val response = client.newCall(request).execute()
                    val responseString = response.body?.string().orEmpty()

                    if (response.isSuccessful) {
                        val json = JSONObject(responseString)
                        return@withContext Result.success(json.getString("sha"))
                    }

                    // Handle rate limiting / retry headers
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
            val treeArray = JSONArray()
            for (item in items) {
                val node = JSONObject().apply {
                    put("path", item.path)
                    put("mode", item.mode)
                    put("type", item.type)
                    put("sha", item.sha)
                }
                treeArray.put(node)
            }

            val jsonBody = JSONObject().apply {
                put("base_tree", baseTreeSha)
                put("tree", treeArray)
            }.toString()

            val request = Request.Builder()
                .url("https://api.github.com/repos/$owner/$repo/git/trees")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/vnd.github+json")
                .post(jsonBody.toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            val responseString = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    IOException("Failed to create git tree (HTTP ${response.code}): $responseString")
                )
            }

            val json = JSONObject(responseString)
            Result.success(json.getString("sha"))
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
            val parentsArray = JSONArray().apply {
                put(parentCommitSha)
            }
            val jsonBody = JSONObject().apply {
                put("message", message.ifBlank { "Upload from Zip2Git" })
                put("tree", treeSha)
                put("parents", parentsArray)
            }.toString()

            val request = Request.Builder()
                .url("https://api.github.com/repos/$owner/$repo/git/commits")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/vnd.github+json")
                .post(jsonBody.toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            val responseString = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    IOException("Failed to create commit (HTTP ${response.code}): $responseString")
                )
            }

            val json = JSONObject(responseString)
            Result.success(json.getString("sha"))
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
            val jsonBody = JSONObject().apply {
                put("sha", newCommitSha)
                put("force", false)
            }.toString()

            val request = Request.Builder()
                .url("https://api.github.com/repos/$owner/$repo/git/refs/heads/$branch")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/vnd.github+json")
                .patch(jsonBody.toRequestBody(jsonMediaType))
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
