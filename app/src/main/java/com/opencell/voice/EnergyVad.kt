package com.opencell.voice

import kotlin.math.sqrt

/**
 * Energy-based voice activity detector over 20 ms frames.
 * Speech starts after [startFrames] loud frames and ends after [endSilenceMs] of quiet.
 *
 * shortcut: a fixed RMS threshold, fine for phone-call audio; swap in WebRTC VAD / Silero if noisy rooms cause false starts.
 */
class EnergyVad(
    private val threshold: Double = 900.0,
    private val startFrames: Int = 3,
    endSilenceMs: Int = 700,
    maxUtteranceMs: Int = 15_000,
) {
    sealed interface Event {
        data object SpeechStarted : Event
        /** A finished utterance, including a little audio from before speech was detected. */
        data class Utterance(val pcm: Pcm) : Event
    }

    private val endSilenceFrames = endSilenceMs / Pcm.FRAME_MS
    private val maxFrames = maxUtteranceMs / Pcm.FRAME_MS
    private val preRoll = ArrayDeque<ShortArray>()
    private val speech = mutableListOf<ShortArray>()
    private var loudRun = 0
    private var quietRun = 0
    private var inSpeech = false

    fun reset() {
        preRoll.clear()
        speech.clear()
        loudRun = 0
        quietRun = 0
        inSpeech = false
    }

    /** Feed one frame; returns an event when speech starts or an utterance completes. */
    fun accept(frame: ShortArray): Event? {
        val loud = rms(frame) >= threshold
        if (!inSpeech) {
            preRoll.addLast(frame)
            if (preRoll.size > startFrames + PRE_ROLL_EXTRA) preRoll.removeFirst()
            loudRun = if (loud) loudRun + 1 else 0
            if (loudRun >= startFrames) {
                inSpeech = true
                quietRun = 0
                speech.addAll(preRoll)
                preRoll.clear()
                return Event.SpeechStarted
            }
            return null
        }
        speech += frame
        quietRun = if (loud) 0 else quietRun + 1
        if (quietRun >= endSilenceFrames || speech.size >= maxFrames) {
            val utterance = Pcm.concat(speech.dropLast(quietRun.coerceAtMost(speech.size)))
            reset()
            return Event.Utterance(utterance)
        }
        return null
    }

    companion object {
        private const val PRE_ROLL_EXTRA = 10

        fun rms(frame: ShortArray): Double {
            if (frame.isEmpty()) return 0.0
            var sum = 0.0
            for (s in frame) sum += s.toDouble() * s
            return sqrt(sum / frame.size)
        }
    }
}
