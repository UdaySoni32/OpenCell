package io.opencell.ui.dialer

import androidx.compose.runtime.Composable

/**
 * DialerScreen is now split into:
 * - HomeScreen (favorites + recents)
 * - KeypadScreen (standalone keypad)
 *
 * This file is kept for backward compatibility but simply delegates to KeypadScreen.
 */
@Composable
fun DialerScreen() {
    KeypadScreen()
}
