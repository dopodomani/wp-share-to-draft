package io.github.dopodomani.wpsharetodraft.presentation.share

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import io.github.dopodomani.wpsharetodraft.domain.CaptureItem
import io.github.dopodomani.wpsharetodraft.domain.SettingsRepository
import io.github.dopodomani.wpsharetodraft.presentation.confirm.ConfirmDraftScreen
import io.github.dopodomani.wpsharetodraft.presentation.confirm.ConfirmDraftViewModel
import io.github.dopodomani.wpsharetodraft.presentation.settings.SettingsScreen
import javax.inject.Inject

private object Routes {
    const val SETTINGS = "settings"
    const val CONFIRM = "confirm"
}

/**
 * The only Activity in this app -- answers both a normal launcher intent (ACTION_MAIN,
 * routes to Settings) and Chrome's share sheet (ACTION_SEND, routes to Confirm once
 * Settings is known to be configured). See
 * docs/phase3-android-app-design.md#1-screen-transition-diagram.
 */
@AndroidEntryPoint
class ShareReceiverActivity : ComponentActivity() {
    @Inject lateinit var intentParser: IntentParser

    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val navController = rememberNavController()
            var startDestination by remember { mutableStateOf<String?>(null) }
            var capturedItem by remember { mutableStateOf<CaptureItem?>(null) }
            var settingsRefresh by remember { mutableIntStateOf(0) }

            LaunchedEffect(settingsRefresh) {
                val settings = settingsRepository.get()
                if (settings != null) {
                    if (intent?.action == Intent.ACTION_SEND) {
                        capturedItem = intentParser.parse(intent, settings.titleMode)
                    }
                    if (startDestination == null) {
                        startDestination = if (intent?.action == Intent.ACTION_SEND) Routes.CONFIRM else Routes.SETTINGS
                    } else if (settingsRefresh > 0 && intent?.action == Intent.ACTION_SEND) {
                        navController.navigate(Routes.CONFIRM) {
                            popUpTo(Routes.SETTINGS) { inclusive = true }
                        }
                    }
                } else if (startDestination == null) {
                    startDestination = Routes.SETTINGS
                }
            }

            val destination = startDestination ?: return@setContent

            NavHost(navController = navController, startDestination = destination) {
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        onSaved = { settingsRefresh++ },
                    )
                }
                composable(Routes.CONFIRM) {
                    if (capturedItem == null) {
                        // Reached only if something navigates here with nothing pending --
                        // shouldn't normally happen since ACTION_MAIN routes to Settings.
                        LaunchedEffect(Unit) {
                            navController.navigate(Routes.SETTINGS) { popUpTo(Routes.CONFIRM) { inclusive = true } }
                        }
                    } else {
                        val item = requireNotNull(capturedItem)
                        val viewModel: ConfirmDraftViewModel = hiltViewModel()
                        LaunchedEffect(item) { viewModel.initialize(item) }
                        ConfirmDraftScreen(
                            onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                            onDone = { finish() },
                            onCancel = { finish() },
                            viewModel = viewModel,
                        )
                    }
                }
            }
        }
    }
}
