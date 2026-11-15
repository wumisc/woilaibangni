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

        // 有些系统（HyperOS / 部分定制 ROM）会拒绝 VOICE_RECOGNITION，退到 MIC 再试一次
        val sources = listOf(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC,
        )
        val rec = sources.firstNotNullOfOrNull { source -> build(source, minBuf) } ?: return false

        record = rec
        running = true
        runCatching { rec.startRecording() }
            .onFailure {
                running = false
                record = null
                rec.release()
                return false
            }

        worker = thread(name = "pcm-recorder", isDaemon = true) {
            val buf = ShortArray(frameSize)
            while (running) {
                val n = runCatching { rec.read(buf, 0, buf.size) }.getOrDefault(-1)
                if (n < 0) {
                    // 麦克风被系统收回（权限撤销、通话抢占）时 read 会持续返回错误
                    running = false
                    break
                }
                if (n > 0) onFrame(if (n == buf.size) buf.copyOf() else buf.copyOf(n))
            }
        }
        return true
    }

    private fun build(source: Int, minBuf: Int): AudioRecord? = try {
        AudioRecord.Builder()
            .setAudioSource(source)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuf * 2)
            .build()
            .takeIf { it.state == AudioRecord.STATE_INITIALIZED }
            ?: return null
    } catch (t: Throwable) {
        null
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
