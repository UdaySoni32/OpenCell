package com.example.opencell.ui.incall

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.opencell.domain.model.CallState
import com.example.opencell.telecom.CallEngine
import com.example.opencell.ui.theme.OpenCellTheme
import kotlinx.coroutines.delay

class InCallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        configureWindowForInCall()

        setContent {
            val callEngine = CallEngine.instance
            val activeSessionState = callEngine?.activeCallSession
            val sessionState by (activeSessionState?.collectAsStateWithLifecycle()
                ?: remember { mutableStateOf(null) })

            LaunchedEffect(sessionState) {
                if (sessionState == null) {
                    delay(500)
                    if (callEngine?.activeCallSession?.value == null) {
                        finish()
                    }
                } else {
                    val state = sessionState?.state
                    if (state == CallState.ENDED || state == CallState.FAILED || state == CallState.REJECTED) {
                        delay(1000)
                        finish()
                    }
                }
            }

            val session = sessionState
            if (session != null) {
                OpenCellTheme {
                    InCallScreen(
                        callSession = session,
                        onAnswerClick = { callEngine?.answerCall() },
                        onRejectClick = { callEngine?.rejectCall() },
                        onHangupClick = { callEngine?.hangupCall() },
                        onToggleMute = { callEngine?.toggleMute() },
                        onToggleSpeaker = { callEngine?.toggleSpeaker() },
                        onToggleHold = { callEngine?.toggleHold() },
                        onSendDtmf = { digit -> callEngine?.sendDtmf(digit) }
                    )
                }
            }
        }
    }

    private fun configureWindowForInCall() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
