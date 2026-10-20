package com.nasir.zip2git.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.nasir.zip2git.R
import com.nasir.zip2git.ui.theme.GitHubBlue
import com.nasir.zip2git.ui.theme.GitHubBorder
import com.nasir.zip2git.ui.theme.GitHubDarkSurface
import com.nasir.zip2git.ui.theme.GitHubDarkSurfaceVariant
import com.nasir.zip2git.ui.theme.GitHubGreen
import com.nasir.zip2git.ui.theme.GitHubGreenBright
import com.nasir.zip2git.ui.theme.GitHubRed
import com.nasir.zip2git.ui.theme.GitHubTextPrimary
import com.nasir.zip2git.ui.theme.GitHubTextSecondary
import com.nasir.zip2git.ui.viewmodel.AuthUiState
import com.nasir.zip2git.ui.viewmodel.CurrentScreen
import com.nasir.zip2git.ui.viewmodel.Zip2GitViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: Zip2GitViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val authState by viewModel.authUiState.collectAsStateWithLifecycle()
    val clientId by viewModel.clientId.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    var patInput by remember { mutableStateOf("") }
    var showPatPassword by remember { mutableStateOf(false) }
    var showOAuthGuide by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(GitHubDarkSurfaceVariant)
                .border(2.dp, GitHubGreenBright, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Key,
                contentDescription = "Zip2Git Logo",
                tint = GitHubGreenBright,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                color = GitHubTextPrimary
            )
        )

        Text(
            text = stringResource(R.string.tagline),
            style = MaterialTheme.typography.bodyMedium.copy(
                color = GitHubTextSecondary,
                textAlign = TextAlign.Center
            ),
            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
        )

        if (authState is AuthUiState.Authenticated) {
            val user = (authState as AuthUiState.Authenticated).user
            Card(
                colors = CardDefaults.cardColors(containerColor = GitHubDarkSurface),
                border = BorderStroke(1.dp, GitHubBorder),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AsyncImage(
                            model = user.avatarUrl,
                            contentDescription = "GitHub Avatar",
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .border(1.dp, GitHubBorder, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = user.name ?: user.login,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = GitHubTextPrimary
                                )
                            )
                            Text(
                                text = "@${user.login}",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = GitHubBlue
                                )
                            )
                            Text(
                                text = "${user.publicRepos} public repos",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = GitHubTextSecondary
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.logout() },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("logout_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.logout))
                        }

                        Button(
                            onClick = { viewModel.navigateTo(CurrentScreen.HOME_ZIP) },
                            colors = ButtonDefaults.buttonColors(containerColor = GitHubGreen),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("continue_to_home_button")
                        ) {
                            Text(
                                text = "Continue",
                                color = GitHubTextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
            return
        }

        if (authState is AuthUiState.Offline) {
            Card(
                colors = CardDefaults.cardColors(containerColor = GitHubDarkSurface),
                border = BorderStroke(1.dp, GitHubBorder),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 3.dp,
                        color = GitHubGreenBright
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No stable internet connection",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = GitHubTextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Your token is safe. Reconnecting automatically as soon as internet is back.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = GitHubTextSecondary,
                            textAlign = TextAlign.Center
                        )
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.logout() },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("offline_logout_button")
                        ) {
                            Text(stringResource(R.string.logout))
                        }
                        Button(
                            onClick = { viewModel.retryConnectionNow() },
                            colors = ButtonDefaults.buttonColors(containerColor = GitHubGreen),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("retry_connection_button")
                        ) {
                            Text(
                                text = "Retry now",
                                color = GitHubTextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            return
        }

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = GitHubDarkSurface,
            contentColor = GitHubGreenBright,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = GitHubGreenBright
                )
            },
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, GitHubBorder, RoundedCornerShape(8.dp))
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = {
                    Text(
                        stringResource(R.string.device_flow_tab),
                        fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                    )
                }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = {
                    Text(
                        stringResource(R.string.token_tab),
                        fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                    )
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (selectedTab == 0) {
            Card(
                colors = CardDefaults.cardColors(containerColor = GitHubDarkSurface),
                border = BorderStroke(1.dp, GitHubBorder),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.client_id_label),
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = GitHubTextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = clientId,
                        onValueChange = { viewModel.updateClientId(it) },
                        placeholder = { Text(stringResource(R.string.client_id_hint), color = GitHubTextSecondary) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GitHubGreenBright,
                            unfocusedBorderColor = GitHubBorder,
                            focusedTextColor = GitHubTextPrimary,
                            unfocusedTextColor = GitHubTextPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("client_id_input")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { showOAuthGuide = !showOAuthGuide }
                            .padding(vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                            contentDescription = null,
                            tint = GitHubBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.oauth_setup_guide_title),
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = GitHubBlue,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }

                    if (showOAuthGuide) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(GitHubDarkSurfaceVariant)
                                .padding(12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.oauth_setup_guide),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = GitHubTextSecondary,
                                    lineHeight = 18.sp
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { viewModel.startDeviceFlow() },
                        enabled = clientId.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = GitHubGreen),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("start_device_login_button")
                    ) {
                        Text(
                            text = stringResource(R.string.start_device_login),
                            fontWeight = FontWeight.Bold,
                            color = GitHubTextPrimary
                        )
                    }

                    when (val state = authState) {
                        is AuthUiState.DeviceFlowWaiting, is AuthUiState.DeviceFlowPolling -> {
                            val userCode = if (state is AuthUiState.DeviceFlowWaiting) state.userCode
                            else (state as AuthUiState.DeviceFlowPolling).userCode
                            val uri = if (state is AuthUiState.DeviceFlowWaiting) state.verificationUri
                            else (state as AuthUiState.DeviceFlowPolling).verificationUri

                            Spacer(modifier = Modifier.height(20.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(1.dp, GitHubGreenBright, RoundedCornerShape(10.dp))
                                    .background(GitHubDarkSurfaceVariant)
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = stringResource(R.string.device_code_prompt),
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = GitHubTextSecondary,
                                            textAlign = TextAlign.Center
                                        )
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Text(
                                        text = userCode,
                                        style = MaterialTheme.typography.headlineLarge.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = GitHubGreenBright,
                                            letterSpacing = 4.sp
                                        )
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                val clip = ClipData.newPlainText("GitHub Device Code", userCode)
                                                clipboard.setPrimaryClip(clip)
                                                Toast.makeText(context, "Code copied to clipboard!", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("copy_code_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(stringResource(R.string.copy_code), fontSize = 12.sp)
                                        }

                                        Button(
                                            onClick = {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                                                context.startActivity(intent)
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = GitHubBlue),
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("open_github_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                stringResource(R.string.open_github),
                                                fontSize = 12.sp,
                                                color = GitHubTextPrimary
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(14.dp),
                                            strokeWidth = 2.dp,
                                            color = GitHubGreenBright
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = stringResource(R.string.waiting_for_authorization),
                                            style = MaterialTheme.typography.bodySmall.copy(color = GitHubTextSecondary)
                                        )
                                    }
                                }
                            }
                        }
                        is AuthUiState.Loading -> {
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = GitHubGreenBright
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Connecting to GitHub…", color = GitHubTextSecondary)
                            }
                        }
                        else -> {}
                    }
                }
            }
        }

        if (selectedTab == 1) {
            Card(
                colors = CardDefaults.cardColors(containerColor = GitHubDarkSurface),
                border = BorderStroke(1.dp, GitHubBorder),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.token_label),
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = GitHubTextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = patInput,
                        onValueChange = { patInput = it },
                        placeholder = { Text(stringResource(R.string.token_hint), color = GitHubTextSecondary) },
                        singleLine = true,
                        visualTransformation = if (showPatPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPatPassword = !showPatPassword }) {
                                Icon(
                                    imageVector = if (showPatPassword) Icons.Default.VpnKey else Icons.Default.Lock,
                                    contentDescription = "Toggle visibility",
                                    tint = GitHubTextSecondary
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GitHubGreenBright,
                            unfocusedBorderColor = GitHubBorder,
                            focusedTextColor = GitHubTextPrimary,
                            unfocusedTextColor = GitHubTextPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("token_input")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.token_note),
                        style = MaterialTheme.typography.bodySmall.copy(color = GitHubTextSecondary)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { viewModel.loginWithPersonalAccessToken(patInput) },
                        enabled = patInput.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = GitHubGreen),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("login_with_token_button")
                    ) {
                        Text(
                            text = stringResource(R.string.login_with_token),
                            fontWeight = FontWeight.Bold,
                            color = GitHubTextPrimary
                        )
                    }
                }
            }
        }

        if (authState is AuthUiState.Error) {
            val error = (authState as AuthUiState.Error).message
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = GitHubDarkSurfaceVariant),
                border = BorderStroke(1.dp, GitHubRed),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = GitHubRed,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall.copy(color = GitHubTextPrimary)
                    )
                }
            }
        }
    }
}
