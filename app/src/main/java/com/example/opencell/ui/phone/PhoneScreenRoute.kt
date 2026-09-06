package com.example.opencell.ui.phone

import androidx.compose.runtime.Composable

/**
 * Route wrapper so the navigation shell can push the standalone dialer screen
 * programmatically (e.g. future entry points).
 */
@Composable
fun PhoneScreenRoute(
    viewModel: PhoneViewModel,
    onBack: () -> Unit
) {
    PhoneScreen(viewModel = viewModel, onBack = onBack)
}
