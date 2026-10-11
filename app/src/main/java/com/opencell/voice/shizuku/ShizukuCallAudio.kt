package com.opencell.voice.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.ParcelFileDescriptor
import com.opencell.BuildConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku
import java.io.InputStream
import kotlin.system.exitProcess

/** Lives in Shizuku's shell-privileged process (or root via Sui). */
class CallAudioUserService : ICallAudioService.Stub() {
    private val capture = CallAudioCapture()

    override fun start(source: Int): ParcelFileDescriptor = capture.start(source)

    override fun stop() = capture.stop()

    override fun destroy() {
        capture.stop()
        exitProcess(0)
    }
}

/** App-side handle for Shizuku mode: availability, permission, and the call-audio stream. */
class ShizukuCallAudio(private val context: Context) {

    enum class Status { NOT_RUNNING, NEEDS_PERMISSION, READY }

    @Volatile private var service: ICallAudioService? = null

    fun status(): Status = when {
        !runCatching { Shizuku.pingBinder() }.getOrDefault(false) -> Status.NOT_RUNNING
        Shizuku.isPreV11() -> Status.NOT_RUNNING
        Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> Status.NEEDS_PERMISSION
        else -> Status.READY
    }

    fun requestPermission() {
        if (status() == Status.NEEDS_PERMISSION) Shizuku.requestPermission(REQUEST_CODE)
    }

    /** Opens a live PCM stream of the current call. Throws if Shizuku is not ready or the device refuses capture. */
    suspend fun openStream(source: Int): InputStream {
        check(status() == Status.READY) { "Shizuku is not running or not allowed" }
        val svc = service ?: bind()
        return ParcelFileDescriptor.AutoCloseInputStream(svc.start(source))
    }

    fun stop() {
        runCatching { service?.stop() }
    }

    private suspend fun bind(): ICallAudioService {
        val connected = CompletableDeferred<ICallAudioService>()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                if (binder != null && binder.pingBinder()) {
                    val svc = ICallAudioService.Stub.asInterface(binder)
                    service = svc
                    connected.complete(svc)
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                service = null
            }
        }
        val args = Shizuku.UserServiceArgs(ComponentName(context.packageName, CallAudioUserService::class.java.name))
            .daemon(false)
            .processNameSuffix("callaudio")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)
        Shizuku.bindUserService(args, connection)
        return withTimeout(BIND_TIMEOUT_MS) { connected.await() }
    }

    private companion object {
        const val REQUEST_CODE = 4242
        const val BIND_TIMEOUT_MS = 10_000L
    }
}
