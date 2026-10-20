package com.opencell.voice

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.opencell.ai.LlmException
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** On-device text-to-speech (Android's TTS engine), rendered to PCM so the agent controls playback. */
class AndroidTts(private val context: Context) : TtsEngine {
    override val label = "on-device text-to-speech"

    @Volatile private var tts: TextToSpeech? = null
    @Volatile private var closed = false
    private val ready = CountDownLatch(1)

    init {
        Handler(Looper.getMainLooper()).post {
            val engine = TextToSpeech(context) { if (!closed) ready.countDown() }
            tts = engine
            if (closed) {
                engine.shutdown()
                tts = null
            }
        }
    }

    override fun synthesize(text: String): Pcm {
        if (!ready.await(INIT_TIMEOUT_S, TimeUnit.SECONDS)) throw LlmException("on-device TTS did not start")
        val engine = tts ?: throw LlmException("on-device TTS unavailable")
        val file = File.createTempFile("tts", ".wav", context.cacheDir)
        try {
            val id = UUID.randomUUID().toString()
            val done = CountDownLatch(1)
            val failed = AtomicReference<String?>(null)
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) { if (utteranceId == id) done.countDown() }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) { failed.set("TTS error"); done.countDown() }
                override fun onError(utteranceId: String?, errorCode: Int) { failed.set("TTS error $errorCode"); done.countDown() }
            })
            if (engine.synthesizeToFile(text, Bundle(), file, id) != TextToSpeech.SUCCESS) throw LlmException("on-device TTS rejected the text")
            if (!done.await(SYNTH_TIMEOUT_S, TimeUnit.SECONDS)) throw LlmException("on-device TTS timed out")
            failed.get()?.let { throw LlmException(it) }
            return Wav.decode(file.readBytes())
        } finally {
            file.delete()
        }
    }

    fun shutdown() {
        closed = true
        Handler(Looper.getMainLooper()).post {
            tts?.shutdown()
            tts = null
        }
    }

    private companion object {
        const val INIT_TIMEOUT_S = 10L
        const val SYNTH_TIMEOUT_S = 30L
    }
}

/**
 * On-device speech-to-text fed with our own PCM (Android 13+ `EXTRA_AUDIO_SOURCE`), so it works on call audio
 * captured by Shizuku as well as the mic. Needs the device's on-device recognizer (Google offline speech pack).
 */
class AndroidStt(private val context: Context) : SttEngine {
    override val label = "on-device speech-to-text"

    override fun transcribe(pcm: Pcm): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) throw LlmException("on-device speech-to-text needs Android 13+")
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) throw LlmException("no on-device speech recognizer installed")
        val (read, write) = ParcelFileDescriptor.createPipe()
        val result = AtomicReference<Result<String>?>(null)
        val done = CountDownLatch(1)
        val main = Handler(Looper.getMainLooper())
        var recognizer: SpeechRecognizer? = null
        main.post {
            recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onResults(results: Bundle) {
                        val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                        result.set(Result.success(text)); done.countDown()
                    }
                    override fun onError(error: Int) {
                        // "No match" just means silence or mumbling.
                        result.set(if (error == SpeechRecognizer.ERROR_NO_MATCH) Result.success("") else Result.failure(LlmException("speech recognizer error $error")))
                        done.countDown()
                    }
                    override fun onReadyForSpeech(params: Bundle?) = Unit
                    override fun onBeginningOfSpeech() = Unit
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() = Unit
                    override fun onPartialResults(partialResults: Bundle?) = Unit
                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                })
                startListening(
                    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, read)
                        .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                        .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                        .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, pcm.sampleRate),
                )
            }
        }
        try {
            ParcelFileDescriptor.AutoCloseOutputStream(write).use { out ->
                val bytes = ByteBuffer.allocate(pcm.samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                pcm.samples.forEach { bytes.putShort(it) }
                out.write(bytes.array())
            }
            if (!done.await(TIMEOUT_S, TimeUnit.SECONDS)) throw LlmException("speech recognizer timed out")
            return result.get()?.getOrThrow().orEmpty()
        } finally {
            main.post { recognizer?.destroy() }
            read.close()
        }
    }

    private companion object {
        const val TIMEOUT_S = 20L
    }
}
