package com.opencell.voice.shizuku;

// Runs inside Shizuku's shell-privileged process. Streams 16 kHz mono PCM of the phone call.
interface ICallAudioService {
    // Shizuku calls this transaction id to tear the service down.
    void destroy() = 16777114;

    // source: MediaRecorder.AudioSource value (VOICE_CALL=4, VOICE_DOWNLINK=3, VOICE_UPLINK=2).
    // Returns the read end of a pipe; it closes when capture stops.
    ParcelFileDescriptor start(int source) = 1;

    void stop() = 2;
}
