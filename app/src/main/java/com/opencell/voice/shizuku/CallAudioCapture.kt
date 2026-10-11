package com.opencell.voice.shizuku

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.ParcelFileDescriptor
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread
import kotlin.math.sqrt

/**
 * Captures call audio with an AudioRecord. Only works with shell (Shizuku) or root privileges, which hold
 * CAPTURE_AUDIO_OUTPUT; a normal app gets an uninitialized recorder. Pure framework code so it runs in app_process.
 */
class CallAudioCapture {
    @Volatile private var running = false
    private var worker: Thread? = null

    /** Starts capture and returns a pipe of raw PCM, or throws if the recorder cannot start. */
    fun start(source: Int): ParcelFileDescriptor {
        stop()
        val record = open(source)
        val (read, write) = ParcelFileDescriptor.createPipe()
        running = true
        worker = thread(name = "call-audio") {
            ParcelFileDescriptor.AutoCloseOutputStream(write).use { out -> pump(record, out) }
        }
        return read
    }

    fun stop() {
        running = false
        worker?.join(1_000)
        worker = null
    }

    private fun pump(record: AudioRecord, out: OutputStream) {
        val frame = ShortArray(FRAME_SAMPLES)
        val bytes = ByteBuffer.allocate(FRAME_SAMPLES * 2).order(ByteOrder.LITTLE_ENDIAN)
        try {
            record.startRecording()
            while (running) {
                val n = record.read(frame, 0, frame.size)
                if (n <= 0) break
                bytes.clear()
                for (i in 0 until n) bytes.putShort(frame[i])
                out.write(bytes.array(), 0, n * 2)
            }
        } catch (_: java.io.IOException) {
            // Reader closed the pipe: the session ended.
        } finally {
            running = false
            runCatching { record.stop() }
            record.release()
        }
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        const val FRAME_SAMPLES = 320

        @SuppressLint("MissingPermission")
        fun open(source: Int): AudioRecord {
            val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val record = AudioRecord(source, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, FRAME_SAMPLES * 16))
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                throw IllegalStateException("AudioRecord(source=$source) not initialized: this device or privilege level cannot capture call audio")
            }
            return record
        }
    }
}

/**
 * Diagnostic entry point, run as the shell user during a call:
 * `adb shell CLASSPATH=$(pm path com.opencell | cut -d: -f2) app_process /system/bin com.opencell.voice.shizuku.CallAudioProbe 3`
 * Prints, per audio source, whether capture starts and how loud it is.
 */
object CallAudioProbe {
    @JvmStatic
    fun main(args: Array<String>) {
        val seconds = args.firstOrNull()?.toIntOrNull() ?: 2
        val sources = mapOf("VOICE_CALL" to MediaRecorder.AudioSource.VOICE_CALL, "VOICE_DOWNLINK" to MediaRecorder.AudioSource.VOICE_DOWNLINK, "VOICE_UPLINK" to MediaRecorder.AudioSource.VOICE_UPLINK)
        for ((name, source) in sources) {
            val result = runCatching {
                val record = CallAudioCapture.open(source)
                try {
                    record.startRecording()
                    val frame = ShortArray(CallAudioCapture.FRAME_SAMPLES)
                    var frames = 0
                    var energy = 0.0
                    repeat(seconds * 50) {
                        val n = record.read(frame, 0, frame.size)
                        if (n > 0) {
                            frames++
                            var sum = 0.0
                            for (i in 0 until n) sum += frame[i].toDouble() * frame[i]
                            energy += sqrt(sum / n)
                        }
                    }
                    "ok frames=$frames avgRms=${if (frames > 0) (energy / frames).toInt() else 0}"
                } finally {
                    record.stop()
                    record.release()
                }
            }.getOrElse { "FAILED ${it.message}" }
            println("$name: $result")
        }
    }
}
