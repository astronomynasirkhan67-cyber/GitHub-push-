package com.nasir.zip2git

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nasir.zip2git.data.model.PushStage
import com.nasir.zip2git.ui.screens.HomeScreen
import com.nasir.zip2git.ui.screens.LoginScreen
import com.nasir.zip2git.ui.screens.PushScreen
import com.nasir.zip2git.ui.screens.RepoScreen
import com.nasir.zip2git.ui.theme.GitHubDarkBg
import com.nasir.zip2git.ui.theme.Zip2GitTheme
import com.nasir.zip2git.ui.viewmodel.CurrentScreen
import com.nasir.zip2git.ui.viewmodel.Zip2GitViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: Zip2GitViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            Zip2GitTheme {
                val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
                val progress by viewModel.pushProgress.collectAsStateWithLifecycle()

                BackHandler(enabled = currentScreen != CurrentScreen.HOME_ZIP && currentScreen != CurrentScreen.LOGIN) {
                    when (currentScreen) {
                        CurrentScreen.REPO_SELECTION -> viewModel.navigateTo(CurrentScreen.HOME_ZIP)
                        CurrentScreen.PUSH_PROGRESS -> {
                            if (progress.stage == PushStage.COMPLETED || progress.stage == PushStage.FAILED) {
                                viewModel.navigateTo(CurrentScreen.REPO_SELECTION)
                            } else {
                                viewModel.cancelPush()
                            }
                        }
                        else -> {}
                    }
                }

                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(GitHubDarkBg)
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (currentScreen) {
                            CurrentScreen.LOGIN -> LoginScreen(viewModel = viewModel)
                            CurrentScreen.HOME_ZIP -> HomeScreen(viewModel = viewModel)
                            CurrentScreen.REPO_SELECTION -> RepoScreen(viewModel = viewModel)
                            CurrentScreen.PUSH_PROGRESS -> PushScreen(viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }
}
