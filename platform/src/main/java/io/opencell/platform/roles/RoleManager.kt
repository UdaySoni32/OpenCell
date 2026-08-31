package io.opencell.platform.roles

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoleManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "RoleManager"
    }

    private val roleManager: RoleManager? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
    } else null

    fun isDefaultDialer(): Boolean {
        return roleManager?.isRoleHeld(RoleManager.ROLE_DIALER) == true
    }

    fun isDefaultSmsApp(): Boolean {
        return roleManager?.isRoleHeld(RoleManager.ROLE_SMS) == true
    }

    fun isFullyDefault(): Boolean {
        return isDefaultDialer() && isDefaultSmsApp()
    }

    /**
     * Create an intent to request the default dialer role.
     * Returns null if the role system is not available (Android < Q) or if the
     * system cannot fulfill the request (OEM restriction, role already held by
     * a non-replaceable system app, etc.).
     */
    fun createRequestDialerRoleIntent(): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return try {
            roleManager?.createRequestRoleIntent(RoleManager.ROLE_DIALER)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create dialer role request intent", e)
            null
        }
    }

    /**
     * Create an intent to request the default SMS app role.
     * Returns null if the role system is not available (Android < Q) or if the
     * system cannot fulfill the request.
     */
    fun createRequestSmsRoleIntent(): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return try {
            roleManager?.createRequestRoleIntent(RoleManager.ROLE_SMS)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create SMS role request intent", e)
            null
        }
    }

    /**
     * Open the system "Default apps" settings page so the user can manually
     * set OpenCell as the default dialer and/or SMS app.
     * This is the fallback when [createRequestDialerRoleIntent] returns null.
     */
    fun openDefaultAppsSettings(): Intent {
        return Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    /**
     * Check whether the role request system is available on this device.
     */
    fun isRoleSystemAvailable(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && roleManager != null
    }

    fun getDialerRoleStatus(): RoleStatus {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return RoleStatus.UNSUPPORTED
        return when {
            roleManager?.isRoleHeld(RoleManager.ROLE_DIALER) == true -> RoleStatus.HELD
            roleManager?.isRoleAvailable(RoleManager.ROLE_DIALER) == true -> RoleStatus.AVAILABLE
            else -> RoleStatus.UNAVAILABLE
        }
    }

    fun getSmsRoleStatus(): RoleStatus {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return RoleStatus.UNSUPPORTED
        return when {
            roleManager?.isRoleHeld(RoleManager.ROLE_SMS) == true -> RoleStatus.HELD
            roleManager?.isRoleAvailable(RoleManager.ROLE_SMS) == true -> RoleStatus.AVAILABLE
            else -> RoleStatus.UNAVAILABLE
        }
    }
}

enum class RoleStatus {
    HELD,
    AVAILABLE,
    UNAVAILABLE,
    UNSUPPORTED
}
