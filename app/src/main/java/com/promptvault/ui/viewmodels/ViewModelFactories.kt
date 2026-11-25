package com.promptvault.ui.viewmodels

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.promptvault.AppContainer

@Composable
inline fun <reified VM : ViewModel> appViewModel(
    crossinline creator: (AppContainer) -> VM
): VM {
    val context: Context = LocalContext.current.applicationContext
    val factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            creator(AppContainer.from(context)) as T
    }
    return viewModel(factory = factory)
}
