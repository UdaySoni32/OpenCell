package io.opencell.platform.telecom

import android.content.Context

/**
 * Interface for launching call UI activities.
 *
 * Implemented by the app module to launch IncomingCallActivity / ActiveCallActivity.
 * The platform module (InCallService) uses this to show the UI without directly
 * depending on the app module.
 */
interface CallUiDelegate {
    fun showIncomingCall(context: Context, callId: String, phoneNumber: String, displayName: String?)
    fun showActiveCall(context: Context, callId: String, phoneNumber: String, displayName: String?)
}
