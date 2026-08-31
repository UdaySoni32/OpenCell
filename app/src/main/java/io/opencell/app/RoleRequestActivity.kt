package io.opencell.app

import android.app.role.RoleManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import dagger.hilt.android.AndroidEntryPoint

/**
 * Transparent activity that handles role request results.
 * This is needed because role request intents must be launched from an Activity.
 */
@AndroidEntryPoint
class RoleRequestActivity : ComponentActivity() {

    private val roleRequestLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // Role request completed - result will be checked by the main activity
        setResult(result.resultCode, Intent().apply {
            putExtra("role_granted", result.resultCode == RESULT_OK)
        })
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        when (intent?.action) {
            ACTION_REQUEST_DIALER_ROLE -> {
                val roleManager = getSystemService(ROLE_SERVICE) as RoleManager
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                roleRequestLauncher.launch(intent)
            }
            ACTION_REQUEST_SMS_ROLE -> {
                val roleManager = getSystemService(ROLE_SERVICE) as RoleManager
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
                roleRequestLauncher.launch(intent)
            }
            else -> finish()
        }
    }

    companion object {
        const val ACTION_REQUEST_DIALER_ROLE = "io.opencell.REQUEST_DIALER_ROLE"
        const val ACTION_REQUEST_SMS_ROLE = "io.opencell.REQUEST_SMS_ROLE"
    }
}
