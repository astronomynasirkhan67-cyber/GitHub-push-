package com.nasir.zip2git.push

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.nasir.zip2git.data.api.GitHubApiClient
import com.nasir.zip2git.data.model.GitHubRepo
import com.nasir.zip2git.data.model.PushProgress
import com.nasir.zip2git.data.model.PushResult
import com.nasir.zip2git.data.model.PushStage
import com.nasir.zip2git.data.model.TreeEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

data class PushParams(
    val token: String,
    val userLogin: String,
    val isCreateNew: Boolean,
    val newRepoName: String,
    val newRepoDescription: String,
    val newRepoPrivate: Boolean,
    val existingRepo: GitHubRepo?,
    val targetBranch: String,
    val commitMessage: String,
    val files: List<Pair<String, File>>
)

/**
 * Owns the push job. It lives in the app process (not in a ViewModel / Activity), and
 * [PushService] keeps the process alive in the background while a push is running.
 */
object PushManager {

    private const val MAX_INLINE_TEXT_BYTES = 768 * 1024
    private const val MAX_ENTRIES_PER_TREE = 300
    private const val MAX_CHARS_PER_TREE = 1_500_000L
    private const val BLOB_PARALLELISM = 4

    private val api = GitHubApiClient()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    private val _progress = MutableStateFlow(PushProgress())
    val progress: StateFlow<PushProgress> = _progress.asStateFlow()

    private val _result = MutableStateFlow<PushResult?>(null)
    val result: StateFlow<PushResult?> = _result.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    fun reportError(message: String) {
        _result.value = PushResult(isSuccess = false, errorMessage = message)
    }

    fun clearResult() {
        if (!_running.value) {
            _result.value = null
        }
    }

    fun start(context: Context, params: PushParams) {
        if (_running.value) return
        _running.value = true
        _result.value = null
        _progress.value = PushProgress(
            stage = PushStage.FETCHING_BRANCH,
            totalFiles = params.files.size,
            message = "Starting…"
        )

        val app = context.applicationContext
        try {
            ContextCompat.startForegroundService(app, Intent(app, PushService::class.java))
        } catch (_: Exception) {
            // Push still runs while the app is open even if the service could not start.
        }

        job = scope.launch {
            try {
                run(params)
            } finally {
                _running.value = false
            }
        }
    }

    fun cancel() {
        job?.cancel()
    }

    private fun setProgress(
        stage: PushStage,
        completed: Int,
        total: Int,
        message: String,
        current: String = ""
    ) {
        _progress.value = PushProgress(
            stage = stage,
            currentFileName = current,
            completedFiles = completed,
            totalFiles = total,
            message = message
        )
    }

    private suspend fun run(p: PushParams) {
        val total = p.files.size
        try {
            val owner: String
            val repoName: String
            val branch: String

            // ---- 1. Repository ------------------------------------------------
            if (p.isCreateNew) {
                val wanted = p.newRepoName.trim()
                if (wanted.isBlank()) throw IllegalArgumentException("Repository name cannot be empty.")

                setProgress(PushStage.FETCHING_BRANCH, 0, total, "Creating repository '$wanted'…")
                val created = api.createRepositoryV2(p.token, wanted, p.newRepoDescription, p.newRepoPrivate)
                val repo = created.getOrElse { err ->
                    val msg = err.message.orEmpty()
                    if (msg.contains("422") && msg.contains("already exists", ignoreCase = true)) {
                        // The repo was created on a previous attempt: just use it.
                        api.getRepo(p.token, p.userLogin, wanted).getOrElse { throw err }
                    } else {
                        throw err
                    }
                }
                owner = repo.fullName.substringBefore('/', p.userLogin)
                repoName = repo.name
                // IMPORTANT: use the new repo's own default branch, not the one from the last-selected repo.
                branch = repo.defaultBranch.ifBlank { "main" }
            } else {
                val existing = p.existingRepo
                    ?: throw IllegalArgumentException("Please select an existing repository.")
                owner = existing.fullName.substringBefore('/', p.userLogin)
                repoName = existing.name
                branch = p.targetBranch.trim().ifBlank { existing.defaultBranch }
            }

            // ---- 2. Branch head (a new repo needs a few seconds to become ready) --------
            setProgress(PushStage.FETCHING_BRANCH, 0, total, "Fetching branch '$branch'…")
            val attempts = if (p.isCreateNew) 25 else 3
            var commitSha: String? = null
            var lastErr: Throwable? = null
            for (i in 1..attempts) {
                val r = api.getBranchHeadSha(p.token, owner, repoName, branch)
                commitSha = r.getOrNull()
                if (commitSha != null) break
                lastErr = r.exceptionOrNull()
                if (lastErr is GitHubApiClient.UnauthorizedException) throw lastErr
                if (i < attempts) delay(1500L)
            }
            if (commitSha == null) {
                throw IOException(
                    "Branch '$branch' not found in $owner/$repoName (${lastErr?.message ?: "unknown error"}). " +
                        "Make sure the repository is not empty and the branch name is right."
                )
            }
            val baseTreeSha = api.getCommitTree(p.token, owner, repoName, commitSha).getOrThrow()

            // ---- 3. Read files: text goes inline, binary files become streamed blobs ---------
            val entries = ArrayList<TreeEntry>(total)
            val blobFiles = ArrayList<Pair<String, File>>()
            var done = 0

            setProgress(PushStage.UPLOADING_BLOBS, 0, total, "Preparing $total files…")
            withContext(Dispatchers.IO) {
                for ((gitPath, file) in p.files) {
                    currentCoroutineContext().ensureActive()
                    val text = readAsTextOrNull(file)
                    if (text != null) {
                        entries.add(TreeEntry(path = gitPath, content = text))
                        done++
                        if (done % 25 == 0) {
                            setProgress(PushStage.UPLOADING_BLOBS, done, total, "Prepared $done of $total files", gitPath)
                        }
                    } else {
                        blobFiles.add(gitPath to file)
                    }
                }
            }

            if (blobFiles.isNotEmpty()) {
                setProgress(
                    PushStage.UPLOADING_BLOBS, done, total,
                    "Uploading ${blobFiles.size} binary/large files…"
                )
                val sem = Semaphore(BLOB_PARALLELISM)
                coroutineScope {
                    blobFiles.map { (gitPath, file) ->
                        async(Dispatchers.IO) {
                            sem.withPermit {
                                val sha = api.createBlobFromFile(p.token, owner, repoName, file).getOrThrow()
                                synchronized(entries) {
                                    entries.add(TreeEntry(path = gitPath, sha = sha))
                                    done++
                                    setProgress(
                                        PushStage.UPLOADING_BLOBS, done, total,
                                        "Uploaded $done of $total files", gitPath
                                    )
                                }
                            }
                        }
                    }.awaitAll()
                }
            }

            // ---- 4. Trees, in a few big requests instead of thousands of small ones ----------
            val batches = ArrayList<List<TreeEntry>>()
            var cur = ArrayList<TreeEntry>()
            var curChars = 0L
            for (e in entries) {
                val size = (e.content?.length ?: 0).toLong() + e.path.length + 120L
                if (cur.isNotEmpty() && (cur.size >= MAX_ENTRIES_PER_TREE || curChars + size > MAX_CHARS_PER_TREE)) {
                    batches.add(cur)
                    cur = ArrayList()
                    curChars = 0L
                }
                cur.add(e)
                curChars += size
            }
            if (cur.isNotEmpty()) batches.add(cur)

            var treeSha = baseTreeSha
            for ((i, batch) in batches.withIndex()) {
                currentCoroutineContext().ensureActive()
                setProgress(
                    PushStage.CREATING_TREE, total, total,
                    "Saving files to GitHub (${i + 1}/${batches.size})…"
                )
                treeSha = api.createTreeBatch(p.token, owner, repoName, treeSha, batch).getOrThrow()
            }

            // ---- 5. Commit + move the branch ---------------------------------------------------
            setProgress(PushStage.CREATING_COMMIT, total, total, "Creating commit…")
            val newCommitSha = api.createCommitV2(
                p.token, owner, repoName,
                p.commitMessage.ifBlank { "Upload from Zip2Git" },
                treeSha, commitSha
            ).getOrThrow()

            setProgress(PushStage.UPDATING_REF, total, total, "Updating branch '$branch'…")
            api.updateRefV2(p.token, owner, repoName, branch, newCommitSha).getOrThrow()

            setProgress(PushStage.COMPLETED, total, total, "Done! All files pushed to $owner/$repoName")
            _result.value = PushResult(
                isSuccess = true,
                commitSha = newCommitSha,
                repoFullName = "$owner/$repoName",
                branch = branch,
                repoUrl = "https://github.com/$owner/$repoName",
                actionsUrl = "https://github.com/$owner/$repoName/actions",
                filesUploaded = total
            )
        } catch (ce: CancellationException) {
            _progress.value = PushProgress(stage = PushStage.FAILED, message = "Upload cancelled.")
            _result.value = PushResult(isSuccess = false, errorMessage = "Operation cancelled.")
        } catch (oom: OutOfMemoryError) {
            val msg = "The phone ran out of memory. Close other apps, or untick the biggest files, and try again."
            _progress.value = PushProgress(stage = PushStage.FAILED, message = msg)
            _result.value = PushResult(isSuccess = false, errorMessage = msg)
        } catch (e: Exception) {
            val msg = e.message ?: "An unknown error occurred during push."
            _progress.value = PushProgress(stage = PushStage.FAILED, message = msg)
            _result.value = PushResult(
                isSuccess = false,
                errorMessage = msg,
                isWorkflowScopeError = msg.contains("workflow", ignoreCase = true)
            )
        }
    }

    /** Returns the file as text if it is small, valid UTF-8 and not binary; otherwise null. */
    private fun readAsTextOrNull(file: File): String? {
        val len = file.length()
        if (len <= 0L || len > MAX_INLINE_TEXT_BYTES) return null // empty/large files go through blobs
        val bytes = try {
            file.readBytes()
        } catch (_: Exception) {
            return null
        }
        val probe = minOf(bytes.size, 8000)
        for (i in 0 until probe) {
            if (bytes[i].toInt() == 0) return null
        }
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: Exception) {
            null
        }
    }
}
