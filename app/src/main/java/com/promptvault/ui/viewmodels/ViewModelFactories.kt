package com.promptvault.ui.viewmodels

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.compose.viewModelFactory
import com.promptvault.AppContainer

@Composable
inline fun <reified VM : ViewModel> appViewModel(
    crossinline creator: (AppContainer) -> VM
): VM {
    val context: Context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    return viewModel(
        factory = viewModelFactory {
            initializer { creator(AppContainer.from(context)) }
        }
    )
}
