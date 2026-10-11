package com.opencell.voice

import kotlinx.coroutines.flow.Flow

/** 16-bit mono PCM. The pipeline runs at [SAMPLE_RATE]; TTS output may use its own rate. */
class Pcm(val samples: ShortArray, val sampleRate: Int = SAMPLE_RATE) {
    val durationMs: Long get() = samples.size * 1000L / sampleRate

    companion object {
        const val SAMPLE_RATE = 16_000
        const val FRAME_MS = 20
        const val FRAME_SAMPLES = SAMPLE_RATE * FRAME_MS / 1000

        fun concat(frames: List<ShortArray>): Pcm {
            val out = ShortArray(frames.sumOf { it.size })
            var pos = 0
            frames.forEach { it.copyInto(out, pos); pos += it.size }
            return Pcm(out)
        }
    }
}

/** What the agent hears: a stream of [Pcm.FRAME_SAMPLES]-sample frames at 16 kHz. */
interface AudioInput {
    fun frames(): Flow<ShortArray>
}

/** What the agent says. [play] suspends until done and stops early when cancelled (barge-in). */
interface AudioOutput {
    suspend fun play(pcm: Pcm)
}

interface SttEngine {
    val label: String
    /** Blocking; run on IO. */
    fun transcribe(pcm: Pcm): String
}

interface TtsEngine {
    val label: String
    /** Blocking; run on IO. */
    fun synthesize(text: String): Pcm
}
