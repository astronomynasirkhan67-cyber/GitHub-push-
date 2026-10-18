package com.nasir.zip2git.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import com.nasir.zip2git.data.model.FileEntryItem
import com.nasir.zip2git.ui.theme.GitHubBlue
import com.nasir.zip2git.ui.theme.GitHubBorder
import com.nasir.zip2git.ui.theme.GitHubDarkSurface
import com.nasir.zip2git.ui.theme.GitHubDarkSurfaceVariant
import com.nasir.zip2git.ui.theme.GitHubGreen
import com.nasir.zip2git.ui.theme.GitHubGreenBright
import com.nasir.zip2git.ui.theme.GitHubOrange
import com.nasir.zip2git.ui.theme.GitHubRed
import com.nasir.zip2git.ui.theme.GitHubTextMuted
import com.nasir.zip2git.ui.theme.GitHubTextPrimary
import com.nasir.zip2git.ui.theme.GitHubTextSecondary
import com.nasir.zip2git.ui.viewmodel.CurrentScreen
import com.nasir.zip2git.ui.viewmodel.Zip2GitViewModel
import java.util.Locale

@Composable
fun HomeScreen(
    viewModel: Zip2GitViewModel,
    modifier: Modifier = Modifier
) {
    val zipState by viewModel.zipUiState.collectAsStateWithLifecycle()

    val zipPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.onZipSelected(uri)
        }
    }

    val expandedMap = remember { mutableStateMapOf<String, Boolean>() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = GitHubDarkSurface),
            border = BorderStroke(1.dp, GitHubBorder),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(GitHubDarkSurfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Archive,
                            contentDescription = null,
                            tint = GitHubBlue,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = zipState.zipFileName ?: "No ZIP selected",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = GitHubTextPrimary
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (zipState.zipFileSize > 0) formatFileSize(zipState.zipFileSize) else "Tap below to browse",
                            style = MaterialTheme.typography.bodySmall.copy(color = GitHubTextSecondary)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        zipPickerLauncher.launch(
                            arrayOf(
                                "application/zip",
                                "application/x-zip-compressed",
                                "application/x-zip",
                                "application/octet-stream",
                                "*/*"
                            )
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GitHubGreen),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("select_zip_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Archive,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.select_zip_btn),
                        fontWeight = FontWeight.Bold,
                        color = GitHubTextPrimary
                    )
                }

                if (zipState.isExtracting) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = GitHubGreenBright
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Extracting: ${zipState.extractionCount} files (${zipState.extractionCurrentFile})",
                                style = MaterialTheme.typography.bodySmall.copy(color = GitHubTextSecondary),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = GitHubGreenBright,
                            trackColor = GitHubDarkSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (zipState.extractResult?.singleTopLevelFolder != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = GitHubDarkSurface),
                border = BorderStroke(1.dp, GitHubBorder),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.remove_single_folder),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = GitHubTextPrimary
                            )
                        )
                        Text(
                            text = "Strip '${zipState.extractResult?.singleTopLevelFolder}/' prefix from root",
                            style = MaterialTheme.typography.bodySmall.copy(color = GitHubTextSecondary)
                        )
                    }
                    Switch(
                        checked = zipState.removeTopLevelFolder,
                        onCheckedChange = { viewModel.setRemoveTopLevelFolder(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = GitHubTextPrimary,
                            checkedTrackColor = GitHubGreenBright
                        )
                    )
                }
            }
        }

        val hasExceededFiles = remember(zipState.fileTree) {
            hasAnyExceededFiles(zipState.fileTree)
        }
        if (hasExceededFiles) {
            Card(
                colors = CardDefaults.cardColors(containerColor = GitHubDarkSurfaceVariant),
                border = BorderStroke(1.dp, GitHubOrange),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = GitHubOrange,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.skip_large_warning),
                        style = MaterialTheme.typography.bodySmall.copy(color = GitHubTextPrimary)
                    )
                }
            }
        }

        if (zipState.fileTree.isNotEmpty()) {
            val selectedCount = remember(zipState.fileTree) { countSelected(zipState.fileTree) }
            val selectedBytes = remember(zipState.fileTree) { sumSelectedBytes(zipState.fileTree) }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(
                        R.string.files_selected_format,
                        selectedCount,
                        formatFileSize(selectedBytes)
                    ),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = GitHubGreenBright,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(GitHubDarkSurface)
                    .border(1.dp, GitHubBorder, RoundedCornerShape(8.dp))
                    .padding(4.dp)
            ) {
                items(zipState.fileTree, key = { it.relativePath }) { item ->
                    FileTreeRow(
                        item = item,
                        depth = 0,
                        expandedMap = expandedMap,
                        onToggleExpand = { path ->
                            val current = expandedMap[path] ?: false
                            expandedMap[path] = !current
                        },
                        onToggleSelect = { path, isSelected ->
                            viewModel.toggleFileSelection(path, isSelected)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = { viewModel.navigateTo(CurrentScreen.REPO_SELECTION) },
                enabled = selectedCount > 0,
                colors = ButtonDefaults.buttonColors(containerColor = GitHubGreen),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("next_to_repo_button")
            ) {
                Text(
                    text = "Next: Choose Repository ($selectedCount files)",
                    color = GitHubTextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        } else if (!zipState.isExtracting) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Select a .zip archive from your device to begin.",
                    style = MaterialTheme.typography.bodyMedium.copy(color = GitHubTextMuted)
                )
            }
        }
    }
}

@Composable
fun FileTreeRow(
    item: FileEntryItem,
    depth: Int,
    expandedMap: Map<String, Boolean>,
    onToggleExpand: (String) -> Unit,
    onToggleSelect: (String, Boolean) -> Unit
) {
    val isExpanded = expandedMap[item.relativePath] ?: false

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .clickable {
                    if (item.isDirectory) {
                        onToggleExpand(item.relativePath)
                    } else if (!item.isExcluded) {
                        onToggleSelect(item.relativePath, !item.isSelected)
                    }
                }
                .padding(vertical = 2.dp)
                .padding(start = (depth * 16).dp, end = 4.dp)
        ) {
            Checkbox(
                checked = item.isSelected,
                onCheckedChange = { checked ->
                    onToggleSelect(item.relativePath, checked)
                },
                enabled = !item.isExcluded,
                colors = CheckboxDefaults.colors(
                    checkedColor = GitHubGreenBright,
                    uncheckedColor = GitHubBorder
                ),
                modifier = Modifier.size(36.dp)
            )

            if (item.isDirectory) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                    contentDescription = "Expand folder",
                    tint = GitHubTextSecondary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    imageVector = if (isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder,
                    contentDescription = null,
                    tint = GitHubBlue,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Spacer(modifier = Modifier.width(20.dp))
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = if (item.isExcluded) GitHubRed else GitHubTextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = item.name,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    color = if (item.isExcluded) GitHubTextMuted else GitHubTextPrimary,
                    fontWeight = if (item.isDirectory) FontWeight.SemiBold else FontWeight.Normal
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            if (!item.isDirectory) {
                if (item.isExcluded) {
                    Text(
                        text = ">100MB",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = GitHubRed,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.padding(end = 4.dp)
                    )
                } else {
                    Text(
                        text = formatFileSize(item.sizeBytes),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = GitHubTextMuted
                        ),
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
            }
        }

        if (item.isDirectory && isExpanded) {
            for (child in item.children) {
                FileTreeRow(
                    item = child,
                    depth = depth + 1,
                    expandedMap = expandedMap,
                    onToggleExpand = onToggleExpand,
                    onToggleSelect = onToggleSelect
                )
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
    val pre = "KMGTPE"[exp - 1]
    return String.format(Locale.US, "%.1f %sB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
}

private fun countSelected(items: List<FileEntryItem>): Int {
    var count = 0
    fun traverse(node: FileEntryItem) {
        if (!node.isSelected || node.isExcluded) return
        if (node.isDirectory) {
            node.children.forEach { traverse(it) }
        } else {
            count++
        }
    }
    items.forEach { traverse(it) }
    return count
}

private fun sumSelectedBytes(items: List<FileEntryItem>): Long {
    var total = 0L
    fun traverse(node: FileEntryItem) {
        if (!node.isSelected || node.isExcluded) return
        if (node.isDirectory) {
            node.children.forEach { traverse(it) }
        } else {
            total += node.sizeBytes
        }
    }
    items.forEach { traverse(it) }
    return total
}

private fun hasAnyExceededFiles(items: List<FileEntryItem>): Boolean {
    fun traverse(node: FileEntryItem): Boolean {
        if (node.isExcluded) return true
        if (node.isDirectory) {
            return node.children.any { traverse(it) }
        }
        return false
    }
    return items.any { traverse(it) }
}
