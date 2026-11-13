package io.github.wumisc.woilaibangni.core.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.concurrent.thread

/**
 * 16kHz / 单声道 / PCM16 采集。
 *
 * 只负责"把声音变成 short 数组"，不做 VAD、不做识别、不碰 UI。
 * 权限由调用方保证（RECORD_AUDIO）。
 */
class PcmRecorder(
    private val sampleRate: Int = 16_000,
    private val frameSize: Int = 512,
) {
    private var record: AudioRecord? = null
    private var worker: Thread? = null

    @Volatile
    private var running = false

    val isRunning: Boolean get() = running

    fun start(onFrame: (ShortArray) -> Unit): Boolean {
        if (running) return true
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuf <= 0) return false

        val rec = try {
            AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf * 2)
                .build()
        } catch (t: Throwable) {
            null
        } ?: return false

        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return false
        }

        record = rec
        running = true
        rec.startRecording()

        worker = thread(name = "pcm-recorder", isDaemon = true) {
            val buf = ShortArray(frameSize)
            while (running) {
                val n = rec.read(buf, 0, buf.size)
                if (n > 0) onFrame(if (n == buf.size) buf.copyOf() else buf.copyOf(n))
            }
        }
        return true
    }

    fun stop() {
        running = false
        worker?.join(300)
        worker = null
        record?.let {
            runCatching { it.stop() }
            it.release()
        }
        record = null
    }
}
