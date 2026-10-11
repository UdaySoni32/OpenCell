package com.opencell.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.opencell.AppContainer
import com.opencell.OpenCellApp

/** `viewModel()` that builds the ViewModel from the [AppContainer]. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(key: String? = null, noinline create: (AppContainer) -> VM): VM =
    viewModel(
        key = key,
        factory = viewModelFactory {
            initializer { create((this[APPLICATION_KEY] as OpenCellApp).container) }
        },
    )
