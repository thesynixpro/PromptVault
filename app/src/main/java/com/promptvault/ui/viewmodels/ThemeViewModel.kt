package com.promptvault.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.promptvault.AppContainer
import com.promptvault.data.preferences.AppSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ThemeViewModel(container: AppContainer) : ViewModel() {
    val settings = container.settings.settings.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        AppSettings()
    )
}
