package com.s2023531050.interviewtrainer.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 마이크 녹음 → 16kHz 모노 16bit WAV (docs/DESIGN.md 4.3). 서버(Whisper)가 그대로 읽을 수 있는 형식이다.
 * [start] 로 시작하고 [stop] 이 WAV 바이트를 돌려준다. RECORD_AUDIO 권한은 호출하는 쪽이 먼저 받아야 한다.
 */
class WavRecorder {
    companion object {
        const val SAMPLE_RATE = 16_000
        const val MAX_SECONDS = 150 // 서버 제한(약 4분)보다 짧게
        private const val MIN_BYTES = SAMPLE_RATE * 2 / 2 // 0.5초보다 짧으면 실수로 본다
    }

    private var record: AudioRecord? = null
    private var thread: Thread? = null
    private val pcm = ByteArrayOutputStream()

    @Volatile private var recording = false

    val isRecording get() = recording

    /** 녹음을 시작한다. 마이크를 열지 못하면 false. */
    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (recording) return true
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) return false
        val rec = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, minBuf * 2
            )
        } catch (e: Exception) {
            return false
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return false
        }

        pcm.reset()
        record = rec
        recording = true
        rec.startRecording()
        thread = Thread {
            val buf = ByteArray(minBuf)
            val limit = SAMPLE_RATE * 2 * MAX_SECONDS
            while (recording && pcm.size() < limit) {
                val n = rec.read(buf, 0, buf.size)
                if (n > 0) synchronized(pcm) { pcm.write(buf, 0, n) } else if (n < 0) break
            }
        }.also { it.start() }
        return true
    }

    /** 녹음을 끝내고 WAV 를 돌려준다. 너무 짧으면 null. */
    fun stop(): ByteArray? {
        if (!recording && record == null) return null
        recording = false
        thread?.join(1000)
        record?.let {
            runCatching { it.stop() }
            it.release()
        }
        record = null
        thread = null
        val data = synchronized(pcm) { pcm.toByteArray() }
        return if (data.size < MIN_BYTES) null else wrap(data)
    }

    /** 녹음을 버린다(손가락이 버튼 밖으로 나가거나 화면을 떠날 때). */
    fun cancel() {
        stop()
    }

    private fun wrap(pcmData: ByteArray): ByteArray {
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()).putInt(36 + pcmData.size).put("WAVE".toByteArray())
        header.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
        header.putInt(SAMPLE_RATE).putInt(SAMPLE_RATE * 2).putShort(2).putShort(16)
        header.put("data".toByteArray()).putInt(pcmData.size)
        return header.array() + pcmData
    }
}
