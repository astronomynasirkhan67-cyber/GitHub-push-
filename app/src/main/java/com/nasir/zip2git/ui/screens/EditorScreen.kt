package com.nasir.zip2git.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nasir.zip2git.ui.editor.CodeHighlighter
import com.nasir.zip2git.ui.theme.GitHubBlue
import com.nasir.zip2git.ui.theme.GitHubBorder
import com.nasir.zip2git.ui.theme.GitHubDarkBg
import com.nasir.zip2git.ui.theme.GitHubDarkSurface
import com.nasir.zip2git.ui.theme.GitHubDarkSurfaceVariant
import com.nasir.zip2git.ui.theme.GitHubGreen
import com.nasir.zip2git.ui.theme.GitHubGreenBright
import com.nasir.zip2git.ui.theme.GitHubOrange
import com.nasir.zip2git.ui.theme.GitHubRed
import com.nasir.zip2git.ui.theme.GitHubTextMuted
import com.nasir.zip2git.ui.theme.GitHubTextPrimary
import com.nasir.zip2git.ui.theme.GitHubTextSecondary
import com.nasir.zip2git.ui.viewmodel.EditorEntry
import com.nasir.zip2git.ui.viewmodel.Zip2GitViewModel
import kotlinx.coroutines.delay
import java.util.Locale

private val SYMBOL_KEYS: List<Pair<String, String>> = listOf(
    "Tab" to "  ",
    "{" to "{", "}" to "}", "(" to "(", ")" to ")", "[" to "[", "]" to "]",
    "<" to "<", ">" to ">", ";" to ";", ":" to ":", "\"" to "\"", "'" to "'",
    "=" to "=", "/" to "/", "\\" to "\\", "#" to "#", "_" to "_", "-" to "-",
    "+" to "+", "*" to "*", "&" to "&", "|" to "|", "!" to "!", "?" to "?",
    "$" to "$", "`" to "`", "." to ".", "," to ","
)

private fun insertAtCursor(value: TextFieldValue, insert: String): TextFieldValue {
    val start = value.selection.min
    val end = value.selection.max
    val newText = value.text.replaceRange(start, end, insert)
    return TextFieldValue(newText, TextRange(start + insert.length))
}

@Composable
fun EditorScreen(
    viewModel: Zip2GitViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.editorState.collectAsStateWithLifecycle()

    var showNewFile by remember { mutableStateOf(false) }
    var showNewFolder by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<EditorEntry?>(null) }
    var deleteTarget by remember { mutableStateOf<EditorEntry?>(null) }
    var showUnsaved by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        if (state.message != null) {
            delay(2500)
            viewModel.editorClearMessage()
        }
    }

    BackHandler {
        when {
            state.openFilePath != null -> {
                if (state.isDirty) showUnsaved = true else viewModel.editorCloseFile()
            }
            state.currentDir.isNotEmpty() -> viewModel.editorNavigateUp()
            else -> viewModel.exitEditor()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(GitHubDarkBg)
            .imePadding()
    ) {
        val openPath = state.openFilePath

        if (openPath == null) {
            // ---------- Browser header ----------
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GitHubDarkSurface)
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            ) {
                IconButton(onClick = {
                    if (state.currentDir.isNotEmpty()) viewModel.editorNavigateUp() else viewModel.exitEditor()
                }) {
                    Icon(
                        imageVector = if (state.currentDir.isNotEmpty()) Icons.Default.ArrowUpward else Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = GitHubTextPrimary
                    )
                }
                Text(
                    text = "/" + state.currentDir,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        color = GitHubTextPrimary,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { showNewFile = true }) {
                    Icon(Icons.Default.NoteAdd, contentDescription = "New file", tint = GitHubGreenBright)
                }
                IconButton(onClick = { showNewFolder = true }) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = "New folder", tint = GitHubBlue)
                }
            }
        } else {
            // ---------- File header ----------
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GitHubDarkSurface)
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            ) {
                IconButton(onClick = {
                    if (state.isDirty) showUnsaved = true else viewModel.editorCloseFile()
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = GitHubTextPrimary)
                }
                Text(
                    text = openPath.substringAfterLast('/') + if (state.isDirty) " \u2022" else "",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        color = if (state.isDirty) GitHubOrange else GitHubTextPrimary,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { viewModel.editorSave() }) {
                    Icon(Icons.Default.Save, contentDescription = "Save", tint = GitHubGreenBright)
                }
            }
        }

        state.message?.let { msg ->
            Text(
                text = msg,
                style = MaterialTheme.typography.bodySmall.copy(color = GitHubTextPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GitHubDarkSurfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        if (openPath == null) {
            FileBrowser(
                entries = state.entries,
                onOpenDir = { viewModel.editorOpenDir(it.relativePath) },
                onOpenFile = { viewModel.editorOpenFile(it.relativePath) },
                onRename = { renameTarget = it },
                onDelete = { deleteTarget = it },
                modifier = Modifier.weight(1f)
            )

            Button(
                onClick = { viewModel.exitEditor() },
                colors = ButtonDefaults.buttonColors(containerColor = GitHubGreen),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .height(46.dp)
            ) {
                Text(
                    text = "Done - back to push",
                    color = GitHubTextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            FileEditor(
                path = openPath,
                initialContent = state.fileContent,
                onContentChange = { viewModel.editorUpdateContent(it) },
                modifier = Modifier.weight(1f)
            )
        }
    }

    if (showNewFile) {
        NameDialog(
            title = "New file",
            hint = "e.g. main.py or src/App.kt",
            initial = "",
            confirmLabel = "Create",
            onConfirm = {
                showNewFile = false
                viewModel.editorCreateFile(it)
            },
            onDismiss = { showNewFile = false }
        )
    }

    if (showNewFolder) {
        NameDialog(
            title = "New folder",
            hint = "e.g. src",
            initial = "",
            confirmLabel = "Create",
            onConfirm = {
                showNewFolder = false
                viewModel.editorCreateFolder(it)
            },
            onDismiss = { showNewFolder = false }
        )
    }

    renameTarget?.let { target ->
        NameDialog(
            title = "Rename",
            hint = "New name",
            initial = target.name,
            confirmLabel = "Rename",
            onConfirm = {
                renameTarget = null
                viewModel.editorRename(target.relativePath, it)
            },
            onDismiss = { renameTarget = null }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = GitHubDarkSurface,
            titleContentColor = GitHubTextPrimary,
            textContentColor = GitHubTextSecondary,
            title = { Text("Delete ${target.name}?") },
            text = {
                Text(
                    if (target.isDirectory) "This folder and everything inside it will be deleted."
                    else "This file will be deleted."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    viewModel.editorDelete(target.relativePath)
                }) { Text("Delete", color = GitHubRed) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel", color = GitHubTextSecondary) }
            }
        )
    }

    if (showUnsaved) {
        AlertDialog(
            onDismissRequest = { showUnsaved = false },
            containerColor = GitHubDarkSurface,
            titleContentColor = GitHubTextPrimary,
            textContentColor = GitHubTextSecondary,
            title = { Text("Unsaved changes") },
            text = { Text("Do you want to save your changes before closing this file?") },
            confirmButton = {
                TextButton(onClick = {
                    showUnsaved = false
                    if (viewModel.editorSave()) viewModel.editorCloseFile()
                }) { Text("Save", color = GitHubGreenBright) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showUnsaved = false
                    viewModel.editorCloseFile()
                }) { Text("Discard", color = GitHubRed) }
            }
        )
    }
}

@Composable
private fun FileBrowser(
    entries: List<EditorEntry>,
    onOpenDir: (EditorEntry) -> Unit,
    onOpenFile: (EditorEntry) -> Unit,
    onRename: (EditorEntry) -> Unit,
    onDelete: (EditorEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    if (entries.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Empty folder.\nTap the file or folder icon above to create something.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium.copy(color = GitHubTextMuted),
                modifier = Modifier.padding(24.dp)
            )
        }
        return
    }

    LazyColumn(modifier = modifier.fillMaxWidth()) {
        items(entries, key = { it.relativePath }) { entry ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (entry.isDirectory) onOpenDir(entry) else onOpenFile(entry)
                    }
                    .padding(start = 14.dp, end = 4.dp)
            ) {
                Icon(
                    imageVector = if (entry.isDirectory) Icons.Default.Folder else Icons.Default.Description,
                    contentDescription = null,
                    tint = if (entry.isDirectory) GitHubBlue else GitHubTextSecondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f).padding(vertical = 10.dp)) {
                    Text(
                        text = entry.name,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            color = GitHubTextPrimary,
                            fontWeight = if (entry.isDirectory) FontWeight.SemiBold else FontWeight.Normal
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!entry.isDirectory) {
                        Text(
                            text = readableSize(entry.sizeBytes),
                            style = MaterialTheme.typography.labelSmall.copy(color = GitHubTextMuted)
                        )
                    }
                }
                IconButton(onClick = { onRename(entry) }, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Default.DriveFileRenameOutline,
                        contentDescription = "Rename",
                        tint = GitHubTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = { onDelete(entry) }, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = GitHubRed,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FileEditor(
    path: String,
    initialContent: String,
    onContentChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var field by remember(path) { mutableStateOf(TextFieldValue(initialContent)) }
    val extension = path.substringAfterLast('.', "")
    val highlighter = remember(extension) { CodeHighlighter(extension) }
    val config = LocalConfiguration.current

    val codeStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        color = GitHubTextPrimary
    )

    val lineCount = remember(field.text) { field.text.count { it == '\n' } + 1 }
    val lineNumbers = remember(lineCount) { (1..lineCount).joinToString("\n") }

    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()
    val keysScroll = rememberScrollState()

    Column(modifier = modifier.fillMaxWidth()) {
        // Extra symbol keys that are awkward to reach on a phone keyboard
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .background(GitHubDarkSurface)
                .horizontalScroll(keysScroll)
                .padding(horizontal = 6.dp, vertical = 5.dp)
        ) {
            for ((label, insert) in SYMBOL_KEYS) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(GitHubDarkSurfaceVariant)
                        .clickable {
                            field = insertAtCursor(field, insert)
                            onContentChange(field.text)
                        }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = label,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            color = GitHubTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(verticalScroll)
        ) {
            Text(
                text = lineNumbers,
                style = codeStyle.copy(color = GitHubTextMuted, textAlign = TextAlign.End),
                modifier = Modifier
                    .background(GitHubDarkSurface)
                    .widthIn(min = 34.dp)
                    .padding(horizontal = 6.dp, vertical = 8.dp)
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(horizontalScroll)
            ) {
                BasicTextField(
                    value = field,
                    onValueChange = {
                        val changed = it.text != field.text
                        field = it
                        if (changed) onContentChange(it.text)
                    },
                    textStyle = codeStyle,
                    cursorBrush = SolidColor(GitHubBlue),
                    visualTransformation = highlighter,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                    modifier = Modifier
                        .defaultMinSize(
                            minWidth = (config.screenWidthDp - 50).dp,
                            minHeight = (config.screenHeightDp * 0.7f).dp
                        )
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun NameDialog(
    title: String,
    hint: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GitHubDarkSurface,
        titleContentColor = GitHubTextPrimary,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(hint, color = GitHubTextSecondary) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GitHubGreenBright,
                    unfocusedBorderColor = GitHubBorder,
                    focusedTextColor = GitHubTextPrimary,
                    unfocusedTextColor = GitHubTextPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank()
            ) { Text(confirmLabel, color = GitHubGreenBright) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = GitHubTextSecondary) }
        }
    )
}

private fun readableSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
    return String.format(Locale.US, "%.1f MB", kb / 1024.0)
}
