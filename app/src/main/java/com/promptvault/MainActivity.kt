package com.promptvault

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.promptvault.ui.navigation.PromptVaultNavigation
import com.promptvault.ui.theme.PromptVaultTheme
import com.promptvault.ui.viewmodels.ThemeViewModel
import com.promptvault.ui.viewmodels.appViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeViewModel: ThemeViewModel = appViewModel { ThemeViewModel(it) }
            val settings by themeViewModel.settings.collectAsState()
            PromptVaultTheme(settings = settings) {
                PromptVaultNavigation()
            }
        }
    }
}
