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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.nasir.zip2git.data.model.TreeEntry
import android.util.Base64
import android.util.Base64OutputStream
import okhttp3.Call
import okhttp3.Callback
import okhttp3.RequestBody
import okhttp3.Response
import okio.BufferedSink
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
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

    /** Thrown ONLY when GitHub says the token itself is invalid (HTTP 401). */
    class UnauthorizedException(message: String) : IOException(message)

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
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

                if (response.code == 401) {
                    return@withContext Result.failure(
                        UnauthorizedException("Token is invalid or revoked (HTTP 401)")
                    )
                }
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

    // ==================================================================
    // v2: cancellable, retrying, low-memory helpers used by PushManager
    // ==================================================================

    private suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation {
            try { cancel() } catch (_: Throwable) { }
        }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (cont.isActive) cont.resume(response) else response.close()
            }
        })
    }

    private fun authed(token: String, url: String): Request.Builder =
        Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Accept", "application/vnd.github+json")
            .addHeader("X-GitHub-Api-Version", "2022-11-28")

    /**
     * Runs a request, retrying network errors, 5xx and GitHub rate limits (waiting as long as
     * GitHub asks). Returns the response body on success.
     */
    private suspend fun requestWithRetry(
        maxAttempts: Int = 8,
        build: () -> Request
    ): Result<String> {
        var attempt = 0
        var backoffMs = 1500L
        var lastError: Throwable? = null
        while (attempt < maxAttempts) {
            attempt++
            try {
                val response = client.newCall(build()).awaitResponse()
                val code = response.code
                val retryAfter = response.header("Retry-After")
                val remaining = response.header("x-ratelimit-remaining")
                val reset = response.header("x-ratelimit-reset")
                val body = try {
                    response.body?.string().orEmpty()
                } finally {
                    response.close()
                }

                if (response.isSuccessful) return Result.success(body)
                if (code == 401) {
                    return Result.failure(UnauthorizedException("Token is invalid or revoked (HTTP 401)"))
                }

                val lower = body.lowercase()
                val rateLimited = code == 429 ||
                    (code == 403 && ("rate limit" in lower || "abuse" in lower || "secondary" in lower))
                val retryable = rateLimited || code in 500..599

                if (retryable && attempt < maxAttempts) {
                    var waitMs = backoffMs
                    if (rateLimited) {
                        val ra = retryAfter?.toLongOrNull()
                        waitMs = when {
                            ra != null -> ra * 1000L
                            remaining == "0" && reset != null ->
                                (((reset.toLongOrNull() ?: 0L) * 1000L) - System.currentTimeMillis())
                                    .coerceIn(5_000L, 120_000L)
                            else -> 60_000L
                        }
                    }
                    waitMs = waitMs.coerceIn(1_000L, 120_000L)
                    Log.w("GitHubApiClient", "HTTP $code, retrying in ${waitMs}ms (attempt $attempt)")
                    delay(waitMs)
                    backoffMs = (backoffMs * 2).coerceAtMost(30_000L)
                    continue
                }

                return Result.failure(IOException("HTTP $code: ${body.take(600)}"))
            } catch (e: IOException) {
                lastError = e
                if (attempt >= maxAttempts) return Result.failure(e)
                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(30_000L)
            }
        }
        return Result.failure(lastError ?: IOException("Request failed"))
    }

    suspend fun getRepo(token: String, owner: String, repo: String): Result<GitHubRepo> =
        requestWithRetry(maxAttempts = 3) {
            authed(token, "https://api.github.com/repos/$owner/$repo").get().build()
        }.mapCatching { json.decodeFromString<GitHubRepo>(it) }

    suspend fun createRepositoryV2(
        token: String,
        name: String,
        description: String,
        isPrivate: Boolean
    ): Result<GitHubRepo> {
        val payload = json.encodeToString(
            CreateRepoRequest(
                name = name.trim(),
                description = description.trim().ifBlank { null },
                isPrivate = isPrivate,
                autoInit = true
            )
        )
        return requestWithRetry(maxAttempts = 3) {
            authed(token, "https://api.github.com/user/repos")
                .post(payload.toRequestBody(jsonMediaType))
                .build()
        }.mapCatching { json.decodeFromString<GitHubRepo>(it) }
    }

    /** Single attempt, no retry: used while waiting for a brand-new repo's branch to appear. */
    suspend fun getBranchHeadSha(
        token: String,
        owner: String,
        repo: String,
        branch: String
    ): Result<String> =
        requestWithRetry(maxAttempts = 2) {
            authed(token, "https://api.github.com/repos/$owner/$repo/git/ref/heads/$branch").get().build()
        }.mapCatching { body ->
            json.parseToJsonElement(body).jsonObject["object"]?.jsonObject?.get("sha")?.jsonPrimitive?.content
                ?: throw IOException("Missing sha for branch $branch")
        }

    suspend fun getCommitTree(
        token: String,
        owner: String,
        repo: String,
        commitSha: String
    ): Result<String> =
        requestWithRetry {
            authed(token, "https://api.github.com/repos/$owner/$repo/git/commits/$commitSha").get().build()
        }.mapCatching { body ->
            json.parseToJsonElement(body).jsonObject["tree"]?.jsonObject?.get("sha")?.jsonPrimitive?.content
                ?: throw IOException("Missing tree sha in commit response")
        }

    /**
     * Streams "{"encoding":"base64","content":"<file as base64>"}" straight from disk,
     * so a 70 MB file never has to sit in memory (twice) like before.
     */
    private class Base64FileBody(private val file: File) : RequestBody() {
        private val header = "{\"encoding\":\"base64\",\"content\":\""
        private val footer = "\"}"

        override fun contentType() = "application/json; charset=utf-8".toMediaType()

        override fun contentLength(): Long {
            val n = file.length()
            return header.length + 4L * ((n + 2L) / 3L) + footer.length
        }

        override fun writeTo(sink: BufferedSink) {
            sink.writeUtf8(header)
            val out = Base64OutputStream(sink.outputStream(), Base64.NO_WRAP or Base64.NO_CLOSE)
            file.inputStream().use { input ->
                val buf = ByteArray(48 * 1024)
                while (true) {
                    val r = input.read(buf)
                    if (r < 0) break
                    out.write(buf, 0, r)
                }
            }
            out.close() // flushes padding; NO_CLOSE keeps the sink open
            sink.writeUtf8(footer)
        }
    }

    suspend fun createBlobFromFile(
        token: String,
        owner: String,
        repo: String,
        file: File
    ): Result<String> =
        requestWithRetry {
            authed(token, "https://api.github.com/repos/$owner/$repo/git/blobs")
                .post(Base64FileBody(file))
                .build()
        }.mapCatching { json.decodeFromString<GitShaResponse>(it).sha }

    /** One tree request for many files. Text files go inline (no blob request needed). */
    suspend fun createTreeBatch(
        token: String,
        owner: String,
        repo: String,
        baseTreeSha: String,
        entries: List<TreeEntry>
    ): Result<String> {
        val payload = buildJsonObject {
            put("base_tree", baseTreeSha)
            put("tree", buildJsonArray {
                for (e in entries) {
                    add(buildJsonObject {
                        put("path", e.path)
                        put("mode", "100644")
                        put("type", "blob")
                        if (e.content != null) put("content", e.content) else put("sha", e.sha ?: "")
                    })
                }
            })
        }.toString()
        return requestWithRetry {
            authed(token, "https://api.github.com/repos/$owner/$repo/git/trees")
                .post(payload.toRequestBody(jsonMediaType))
                .build()
        }.mapCatching { json.decodeFromString<GitShaResponse>(it).sha }
    }

    suspend fun createCommitV2(
        token: String,
        owner: String,
        repo: String,
        message: String,
        treeSha: String,
        parentCommitSha: String
    ): Result<String> {
        val payload = json.encodeToString(
            CreateCommitRequest(
                message = message.ifBlank { "Upload from Zip2Git" },
                tree = treeSha,
                parents = listOf(parentCommitSha)
            )
        )
        return requestWithRetry {
            authed(token, "https://api.github.com/repos/$owner/$repo/git/commits")
                .post(payload.toRequestBody(jsonMediaType))
                .build()
        }.mapCatching { json.decodeFromString<GitShaResponse>(it).sha }
    }

    suspend fun updateRefV2(
        token: String,
        owner: String,
        repo: String,
        branch: String,
        newCommitSha: String
    ): Result<Unit> {
        val payload = json.encodeToString(UpdateRefRequest(sha = newCommitSha, force = false))
        return requestWithRetry {
            authed(token, "https://api.github.com/repos/$owner/$repo/git/refs/heads/$branch")
                .patch(payload.toRequestBody(jsonMediaType))
                .build()
        }.map { }
    }
}
