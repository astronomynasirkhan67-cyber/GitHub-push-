package com.nasir.zip2git.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nasir.zip2git.R
import com.nasir.zip2git.data.model.GitHubRepo
import com.nasir.zip2git.ui.theme.GitHubBlue
import com.nasir.zip2git.ui.theme.GitHubBorder
import com.nasir.zip2git.ui.theme.GitHubDarkBg
import com.nasir.zip2git.ui.theme.GitHubDarkSurface
import com.nasir.zip2git.ui.theme.GitHubDarkSurfaceVariant
import com.nasir.zip2git.ui.theme.GitHubGreen
import com.nasir.zip2git.ui.theme.GitHubGreenBright
import com.nasir.zip2git.ui.theme.GitHubOrange
import com.nasir.zip2git.ui.theme.GitHubTextMuted
import com.nasir.zip2git.ui.theme.GitHubTextPrimary
import com.nasir.zip2git.ui.theme.GitHubTextSecondary
import com.nasir.zip2git.ui.viewmodel.CurrentScreen
import com.nasir.zip2git.ui.viewmodel.Zip2GitViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepoScreen(
    viewModel: Zip2GitViewModel,
    modifier: Modifier = Modifier
) {
    val repoState by viewModel.repoUiState.collectAsStateWithLifecycle()

    val filteredRepos = remember(repoState.existingRepos, repoState.repoSearchQuery) {
        if (repoState.repoSearchQuery.isBlank()) {
            repoState.existingRepos
        } else {
            repoState.existingRepos.filter {
                it.name.contains(repoState.repoSearchQuery, ignoreCase = true) ||
                        it.fullName.contains(repoState.repoSearchQuery, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(GitHubDarkBg)
    ) {
        TopAppBar(
            title = {
                Text(
                    text = "Select Destination Repo",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = GitHubTextPrimary
                    )
                )
            },
            navigationIcon = {
                IconButton(onClick = { viewModel.navigateTo(CurrentScreen.HOME_ZIP) }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = GitHubTextPrimary
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = GitHubDarkSurface)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            TabRow(
                selectedTabIndex = if (repoState.isCreateNew) 0 else 1,
                containerColor = GitHubDarkSurface,
                contentColor = GitHubGreenBright,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(
                            tabPositions[if (repoState.isCreateNew) 0 else 1]
                        ),
                        color = GitHubGreenBright
                    )
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, GitHubBorder, RoundedCornerShape(8.dp))
            ) {
                Tab(
                    selected = repoState.isCreateNew,
                    onClick = { viewModel.setRepoChoiceTab(true) },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                stringResource(R.string.create_new_repo),
                                fontWeight = if (repoState.isCreateNew) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                )
                Tab(
                    selected = !repoState.isCreateNew,
                    onClick = { viewModel.setRepoChoiceTab(false) },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                stringResource(R.string.use_existing_repo),
                                fontWeight = if (!repoState.isCreateNew) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (repoState.isCreateNew) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = GitHubDarkSurface),
                    border = BorderStroke(1.dp, GitHubBorder),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.repo_name_label),
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = GitHubTextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = repoState.newRepoName,
                            onValueChange = { viewModel.setNewRepoName(it) },
                            placeholder = { Text("e.g. my-awesome-project", color = GitHubTextSecondary) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GitHubGreenBright,
                                unfocusedBorderColor = GitHubBorder,
                                focusedTextColor = GitHubTextPrimary,
                                unfocusedTextColor = GitHubTextPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("repo_name_input")
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = stringResource(R.string.repo_desc_label),
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = GitHubTextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = repoState.newRepoDescription,
                            onValueChange = { viewModel.setNewRepoDescription(it) },
                            placeholder = { Text("Uploaded via Zip2Git", color = GitHubTextSecondary) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GitHubGreenBright,
                                unfocusedBorderColor = GitHubBorder,
                                focusedTextColor = GitHubTextPrimary,
                                unfocusedTextColor = GitHubTextPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("repo_desc_input")
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (repoState.isPrivate) Icons.Default.Lock else Icons.Default.Public,
                                    contentDescription = null,
                                    tint = if (repoState.isPrivate) GitHubOrange else GitHubGreenBright,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = stringResource(R.string.private_repo),
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = GitHubTextPrimary,
                                            fontWeight = FontWeight.Medium
                                        )
                                    )
                                    Text(
                                        text = if (repoState.isPrivate) "Only you can see this repo" else "Visible to everyone",
                                        style = MaterialTheme.typography.bodySmall.copy(color = GitHubTextSecondary)
                                    )
                                }
                            }
                            Switch(
                                checked = repoState.isPrivate,
                                onCheckedChange = { viewModel.setNewRepoPrivate(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = GitHubTextPrimary,
                                    checkedTrackColor = GitHubGreenBright
                                ),
                                modifier = Modifier.testTag("private_repo_switch")
                            )
                        }
                    }
                }
            } else {
                Card(
                    colors = CardDefaults.cardColors(containerColor = GitHubDarkSurface),
                    border = BorderStroke(1.dp, GitHubBorder),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        OutlinedTextField(
                            value = repoState.repoSearchQuery,
                            onValueChange = { viewModel.setRepoSearchQuery(it) },
                            placeholder = { Text(stringResource(R.string.search_repos_hint), color = GitHubTextSecondary) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = GitHubTextSecondary
                                )
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GitHubGreenBright,
                                unfocusedBorderColor = GitHubBorder,
                                focusedTextColor = GitHubTextPrimary,
                                unfocusedTextColor = GitHubTextPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("search_repos_input")
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        if (repoState.isLoadingRepos) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = GitHubGreenBright,
                                    strokeWidth = 2.dp
                                )
                            }
                        } else if (filteredRepos.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No matching repositories found.",
                                    color = GitHubTextMuted,
                                    fontSize = 13.sp
                                )
                            }
                        } else {
                            LazyColumn(modifier = Modifier.weight(1f)) {
                                items(filteredRepos, key = { it.id }) { repo ->
                                    val isSelected = repoState.selectedExistingRepo?.id == repo.id
                                    RepoItemRow(
                                        repo = repo,
                                        isSelected = isSelected,
                                        onClick = { viewModel.selectExistingRepo(repo) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = GitHubDarkSurface),
                border = BorderStroke(1.dp, GitHubBorder),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = stringResource(R.string.branch_label),
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = GitHubTextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = repoState.targetBranch,
                        onValueChange = { viewModel.setTargetBranch(it) },
                        placeholder = { Text("main", color = GitHubTextSecondary) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GitHubGreenBright,
                            unfocusedBorderColor = GitHubBorder,
                            focusedTextColor = GitHubTextPrimary,
                            unfocusedTextColor = GitHubTextPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("branch_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = stringResource(R.string.commit_message_label),
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = GitHubTextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = repoState.commitMessage,
                        onValueChange = { viewModel.setCommitMessage(it) },
                        placeholder = { Text(stringResource(R.string.default_commit_message), color = GitHubTextSecondary) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GitHubGreenBright,
                            unfocusedBorderColor = GitHubBorder,
                            focusedTextColor = GitHubTextPrimary,
                            unfocusedTextColor = GitHubTextPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("commit_message_input")
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val canPush = if (repoState.isCreateNew) {
                repoState.newRepoName.isNotBlank()
            } else {
                repoState.selectedExistingRepo != null
            }

            Button(
                onClick = { viewModel.startPush() },
                enabled = canPush,
                colors = ButtonDefaults.buttonColors(containerColor = GitHubGreen),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("push_button")
            ) {
                Icon(
                    imageVector = Icons.Default.CloudUpload,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.push_to_github_btn),
                    color = GitHubTextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun RepoItemRow(
    repo: GitHubRepo,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) GitHubDarkSurfaceVariant else androidx.compose.ui.graphics.Color.Transparent)
            .border(
                width = if (isSelected) 1.dp else 0.dp,
                color = if (isSelected) GitHubGreenBright else androidx.compose.ui.graphics.Color.Transparent,
                shape = RoundedCornerShape(6.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (repo.isPrivate) Icons.Default.Lock else Icons.Default.Public,
            contentDescription = null,
            tint = if (repo.isPrivate) GitHubOrange else GitHubTextSecondary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = repo.name,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = GitHubTextPrimary
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!repo.description.isNullOrBlank()) {
                Text(
                    text = repo.description,
                    style = MaterialTheme.typography.bodySmall.copy(color = GitHubTextSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "Branch: ${repo.defaultBranch}",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    color = GitHubBlue
                )
            )
        }
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = GitHubGreenBright,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
