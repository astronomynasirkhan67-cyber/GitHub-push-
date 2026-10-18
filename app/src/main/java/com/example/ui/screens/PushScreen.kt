package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.PushStage
import com.example.ui.theme.GitHubBlue
import com.example.ui.theme.GitHubBorder
import com.example.ui.theme.GitHubDarkBg
import com.example.ui.theme.GitHubDarkSurface
import com.example.ui.theme.GitHubDarkSurfaceVariant
import com.example.ui.theme.GitHubGreen
import com.example.ui.theme.GitHubGreenBright
import com.example.ui.theme.GitHubOrange
import com.example.ui.theme.GitHubPurple
import com.example.ui.theme.GitHubRed
import com.example.ui.theme.GitHubTextMuted
import com.example.ui.theme.GitHubTextPrimary
import com.example.ui.theme.GitHubTextSecondary
import com.example.ui.viewmodel.CurrentScreen
import com.example.ui.viewmodel.Zip2GitViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PushScreen(
    viewModel: Zip2GitViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val progress by viewModel.pushProgress.collectAsStateWithLifecycle()
    val result by viewModel.pushResult.collectAsStateWithLifecycle()

    val isFinished = result != null
    val isSuccess = result?.isSuccess == true

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(GitHubDarkBg)
    ) {
        TopAppBar(
            title = {
                Text(
                    text = if (isSuccess) "Push Complete" else "Pushing to GitHub",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = GitHubTextPrimary
                    )
                )
            },
            navigationIcon = {
                if (isFinished) {
                    IconButton(onClick = { viewModel.navigateTo(CurrentScreen.REPO_SELECTION) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = GitHubTextPrimary
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = GitHubDarkSurface)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // ICON INDICATOR
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isSuccess -> GitHubGreen
                            result?.isSuccess == false -> GitHubDarkSurfaceVariant
                            else -> GitHubDarkSurfaceVariant
                        }
                    )
                    .border(
                        2.dp,
                        when {
                            isSuccess -> GitHubGreenBright
                            result?.isSuccess == false -> GitHubRed
                            else -> GitHubBlue
                        },
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isSuccess -> {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Success",
                            tint = GitHubTextPrimary,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                    result?.isSuccess == false -> {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = "Failed",
                            tint = GitHubRed,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                    else -> {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = "Pushing",
                            tint = GitHubBlue,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // STATUS TITLE
            Text(
                text = when {
                    isSuccess -> stringResource(R.string.push_success_title)
                    result?.isSuccess == false -> "Upload Failed"
                    else -> progress.message.ifBlank { "Pushing files to GitHub…" }
                },
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = GitHubTextPrimary,
                    textAlign = TextAlign.Center
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // IN-PROGRESS DETAILS
            if (!isFinished) {
                val fraction = if (progress.totalFiles > 0) {
                    progress.completedFiles.toFloat() / progress.totalFiles.toFloat()
                } else 0f

                Card(
                    colors = CardDefaults.cardColors(containerColor = GitHubDarkSurface),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(GitHubBorder)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Stage: ${formatStageName(progress.stage)}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = GitHubBlue
                                )
                            )
                            if (progress.totalFiles > 0) {
                                Text(
                                    text = "${progress.completedFiles} / ${progress.totalFiles}",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        color = GitHubGreenBright
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        LinearProgressIndicator(
                            progress = { if (progress.stage == PushStage.UPLOADING_BLOBS) fraction else 0.5f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = GitHubGreenBright,
                            trackColor = GitHubDarkSurfaceVariant
                        )

                        if (progress.currentFileName.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "File: ${progress.currentFileName}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    color = GitHubTextSecondary
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                    onClick = { viewModel.cancelPush() },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GitHubRed),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("cancel_push_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Cancel,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.cancel_upload))
                }
            }

            // SUCCESS CARD
            if (isSuccess && result != null) {
                val res = result!!
                Card(
                    colors = CardDefaults.cardColors(containerColor = GitHubDarkSurface),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(GitHubGreenBright)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Repository: ${res.repoFullName}",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = GitHubTextPrimary
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Branch: ${res.branch}",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = GitHubBlue,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                        if (!res.commitSha.isNullOrBlank()) {
                            Text(
                                text = "Commit: ${res.commitSha.take(7)}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    color = GitHubTextMuted
                                )
                            )
                        }
                        Text(
                            text = "Files uploaded: ${res.filesUploaded}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = GitHubGreenBright,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // BUTTON: OPEN IN BROWSER
                if (!res.repoUrl.isNullOrBlank()) {
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(res.repoUrl))
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GitHubGreen),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("open_repo_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.open_repo_browser),
                            color = GitHubTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // BUTTON: VIEW GITHUB ACTIONS
                if (!res.actionsUrl.isNullOrBlank()) {
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(res.actionsUrl))
                            context.startActivity(intent)
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("view_actions_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = GitHubPurple,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.open_actions_tab),
                            color = GitHubTextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // BUTTON: UPLOAD ANOTHER ZIP
                Button(
                    onClick = { viewModel.navigateTo(CurrentScreen.HOME_ZIP) },
                    colors = ButtonDefaults.buttonColors(containerColor = GitHubDarkSurfaceVariant),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("upload_another_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Archive,
                        contentDescription = null,
                        tint = GitHubTextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.push_another_btn),
                        color = GitHubTextPrimary
                    )
                }
            }

            // FAILURE / ERROR CARD
            if (result?.isSuccess == false) {
                val res = result!!
                Card(
                    colors = CardDefaults.cardColors(containerColor = GitHubDarkSurface),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(GitHubRed)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = GitHubRed,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Push Error",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = GitHubRed
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = res.errorMessage ?: "Unknown error occurred",
                            style = MaterialTheme.typography.bodySmall.copy(color = GitHubTextPrimary)
                        )

                        // Special explanation for workflow scope
                        if (res.isWorkflowScopeError) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(GitHubDarkSurfaceVariant)
                                    .border(1.dp, GitHubOrange, RoundedCornerShape(8.dp))
                                    .padding(12.dp)
                            ) {
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Security,
                                            contentDescription = null,
                                            tint = GitHubOrange,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Missing 'workflow' Scope",
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = GitHubOrange
                                            )
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "GitHub requires the 'workflow' OAuth scope to create or modify GitHub Actions workflows in '.github/workflows/'. Please log out and authorize again with workflow scope enabled, or use a Personal Access Token with the 'workflow' scope checked.",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = GitHubTextSecondary,
                                            lineHeight = 18.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.navigateTo(CurrentScreen.REPO_SELECTION) },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("back_to_settings_button")
                    ) {
                        Text("Repo Settings")
                    }

                    Button(
                        onClick = { viewModel.startPush() },
                        colors = ButtonDefaults.buttonColors(containerColor = GitHubGreen),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("retry_push_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.retry_btn),
                            fontWeight = FontWeight.Bold,
                            color = GitHubTextPrimary
                        )
                    }
                }
            }
        }
    }
}

private fun formatStageName(stage: PushStage): String = when (stage) {
    PushStage.IDLE -> "Ready"
    PushStage.FETCHING_BRANCH -> "Fetching Branch Ref"
    PushStage.UPLOADING_BLOBS -> "Uploading Git Blobs"
    PushStage.CREATING_TREE -> "Creating Tree"
    PushStage.CREATING_COMMIT -> "Creating Commit"
    PushStage.UPDATING_REF -> "Updating Branch Ref"
    PushStage.COMPLETED -> "Completed"
    PushStage.FAILED -> "Failed"
}
