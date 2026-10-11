package com.opencell.voice

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Speaker mode input: the phone's microphone hears both you (if near) and the caller on speakerphone. */
class MicInput(private val source: Int = MediaRecorder.AudioSource.VOICE_RECOGNITION) : AudioInput {
    @SuppressLint("MissingPermission") // RECORD_AUDIO is checked before a voice session starts
    override fun frames(): Flow<ShortArray> = flow {
        val minBuf = AudioRecord.getMinBufferSize(Pcm.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val record = AudioRecord(source, Pcm.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, Pcm.FRAME_SAMPLES * 8))
        check(record.state == AudioRecord.STATE_INITIALIZED) { "microphone is busy or unavailable during this call" }
        try {
            record.startRecording()
            while (currentCoroutineContext().isActive) {
                val frame = ShortArray(Pcm.FRAME_SAMPLES)
                val n = record.read(frame, 0, frame.size)
                if (n < 0) break
                if (n == frame.size) emit(frame)
            }
        } finally {
            record.stop()
            record.release()
        }
    }.flowOn(Dispatchers.IO)
}

/** Reads raw 16 kHz mono PCM from a stream (e.g. the Shizuku call-audio pipe). Ends when the stream closes. */
class StreamInput(private val open: suspend () -> InputStream) : AudioInput {
    override fun frames(): Flow<ShortArray> = flow {
        DataInputStream(open().buffered()).use { input ->
            val bytes = ByteArray(Pcm.FRAME_SAMPLES * 2)
            while (currentCoroutineContext().isActive) {
                try {
                    input.readFully(bytes)
                } catch (_: EOFException) {
                    break
                }
                val frame = ShortArray(Pcm.FRAME_SAMPLES)
                ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(frame)
                emit(frame)
            }
        }
    }.flowOn(Dispatchers.IO)
}

/** Plays speech out loud on the speaker so the far end hears it through the call (speakerphone must be on). */
class SpeakerOutput : AudioOutput {
    override suspend fun play(pcm: Pcm) {
        if (pcm.samples.isEmpty()) return
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(pcm.sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(pcm.samples.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        try {
            track.write(pcm.samples, 0, pcm.samples.size)
            track.play()
            // Poll so cancellation (barge-in) stops playback within ~50 ms.
            while (track.playbackHeadPosition < pcm.samples.size && track.playState == AudioTrack.PLAYSTATE_PLAYING) delay(50)
        } finally {
            track.stop()
            track.release()
        }
    }
}
