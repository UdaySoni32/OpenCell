package com.opencell.voice

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Minimal 16-bit PCM WAV encode/decode for talking to speech APIs. */
object Wav {
    fun encode(pcm: Pcm): ByteArray {
        val data = ByteBuffer.allocate(pcm.samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        pcm.samples.forEach { data.putShort(it) }
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + data.capacity()); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
            putInt(pcm.sampleRate); putInt(pcm.sampleRate * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(data.capacity())
        }
        return ByteArrayOutputStream().apply { write(header.array()); write(data.array()) }.toByteArray()
    }

    /** Decodes mono or stereo 16-bit PCM WAV (stereo is downmixed). Walks chunks, so extra chunks are fine. */
    fun decode(bytes: ByteArray): Pcm {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(bytes.size >= 12 && String(bytes, 0, 4) == "RIFF" && String(bytes, 8, 4) == "WAVE") { "not a WAV file" }
        var pos = 12
        var channels = 1
        var rate = Pcm.SAMPLE_RATE
        var bits = 16
        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4)
            // Streaming encoders write 0 or -1 as the data size; treat that as "rest of file".
            val declared = buf.getInt(pos + 4)
            val size = if (declared <= 0 || pos + 8 + declared > bytes.size) bytes.size - pos - 8 else declared
            if (id == "fmt ") {
                channels = buf.getShort(pos + 10).toInt()
                rate = buf.getInt(pos + 12)
                bits = buf.getShort(pos + 22).toInt()
            } else if (id == "data") {
                require(bits == 16) { "only 16-bit WAV is supported" }
                val frames = size / 2 / channels
                val out = ShortArray(frames) { i ->
                    if (channels == 1) buf.getShort(pos + 8 + i * 2)
                    else ((buf.getShort(pos + 8 + i * 4).toInt() + buf.getShort(pos + 10 + i * 4).toInt()) / 2).toShort()
                }
                return Pcm(out, rate)
            }
            pos += 8 + size + (size and 1)
        }
        throw IllegalArgumentException("WAV has no data chunk")
    }
}
