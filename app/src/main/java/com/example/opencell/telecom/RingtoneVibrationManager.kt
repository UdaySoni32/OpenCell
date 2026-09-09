package com.example.opencell.telecom

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object RingtoneVibrationManager {

    private var currentRingtone: Ringtone? = null
    private var isRinging = false

    @Synchronized
    fun startRingtoneAndVibration(context: Context) {
        if (isRinging) return
        isRinging = true

        try {
            // 1. Play default incoming call ringtone
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            if (ringtoneUri != null) {
                val ringtone = RingtoneManager.getRingtone(context, ringtoneUri)
                ringtone?.run {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        isLooping = true
                    }
                    audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    play()
                }
                currentRingtone = ringtone
            }

            // 2. Start looping vibration effect (pattern: 0ms delay, 1000ms vibrate, 1000ms pause, repeat at 0)
            val vibrator = getVibrator(context)
            if (vibrator != null && vibrator.hasVibrator()) {
                val pattern = longArrayOf(0, 1000, 1000)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val effect = VibrationEffect.createWaveform(pattern, 0)
                    vibrator.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(pattern, 0)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Synchronized
    fun stopRingtoneAndVibration(context: Context) {
        try {
            currentRingtone?.let {
                if (it.isPlaying) {
                    it.stop()
                }
            }
            currentRingtone = null

            val vibrator = getVibrator(context)
            vibrator?.cancel()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isRinging = false
        }
    }

    fun playSmsSoundAndVibration(context: Context) {
        try {
            // Short notification chime
            val notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            if (notificationUri != null) {
                val ringtone = RingtoneManager.getRingtone(context, notificationUri)
                ringtone?.run {
                    audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    play()
                }
            }

            // Short vibration pattern for SMS (0ms delay, 250ms vibrate, 250ms pause, 250ms vibrate, no repeat)
            val vibrator = getVibrator(context)
            if (vibrator != null && vibrator.hasVibrator()) {
                val pattern = longArrayOf(0, 250, 250, 250)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val effect = VibrationEffect.createWaveform(pattern, -1)
                    vibrator.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(pattern, -1)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getVibrator(context: Context): Vibrator? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (_: Exception) {
            null
        }
    }
}
