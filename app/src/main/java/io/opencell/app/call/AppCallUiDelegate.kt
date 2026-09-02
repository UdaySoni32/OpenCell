package io.opencell.app.call

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.opencell.platform.telecom.CallUiDelegate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-level implementation of [CallUiDelegate].
 * Launches the custom incoming call and active call activities.
 */
@Singleton
class AppCallUiDelegate @Inject constructor(
    @ApplicationContext private val context: Context
) : CallUiDelegate {

    override fun showIncomingCall(
        context: Context,
        callId: String,
        phoneNumber: String,
        displayName: String?
    ) {
        IncomingCallActivity.launch(context, callId, phoneNumber, displayName)
    }

    override fun showActiveCall(
        context: Context,
        callId: String,
        phoneNumber: String,
        displayName: String?
    ) {
        ActiveCallActivity.launch(context, callId, phoneNumber, displayName)
    }
}
