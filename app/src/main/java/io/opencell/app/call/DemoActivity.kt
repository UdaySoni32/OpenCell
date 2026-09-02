package io.opencell.app.call

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dagger.hilt.android.AndroidEntryPoint
import io.opencell.ui.theme.OpenCellTheme

@AndroidEntryPoint
class DemoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OpenCellTheme {
                DemoScreen(onBack = { finish() })
            }
        }
    }
}
