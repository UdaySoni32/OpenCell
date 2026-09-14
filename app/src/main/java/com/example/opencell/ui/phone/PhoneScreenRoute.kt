package com.example.opencell.ui.phone

import androidx.compose.runtime.Composable

/**
 * Route wrapper so the navigation shell can push the dialer screen programmatically.
 */
@Composable
fun PhoneScreenRoute(
    viewModel: PhoneViewModel,
    onBack: (() -> Unit)? = null,
    onMessageClick: (String) -> Unit = {},
    onContactDetailsClick: (String) -> Unit = {}
) {
    PhoneScreen(
        viewModel = viewModel,
        onBack = onBack,
        onMessageClick = onMessageClick,
        onContactDetailsClick = onContactDetailsClick
    )
}
