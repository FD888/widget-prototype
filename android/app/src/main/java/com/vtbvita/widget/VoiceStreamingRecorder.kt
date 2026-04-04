package com.vtbvita.widget

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.*
import okhttp3.*
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt

/**
 * Захватывает PCM-аудио с микрофона и стримит его на сервер через WebSocket.
 * Сервер проксирует в Яндекс SpeechKit gRPC и возвращает JSON:
 *   {"type":"partial","text":"..."}
 *   {"type":"final","text":"..."}
 *   {"type":"error","message":"..."}
 *
 * Параметры записи: 16kHz / 16-bit PCM / mono
 */
class VoiceStreamingRecorder(
    private val onPartial: (String) -> Unit,
    private val onFinal:   (String) -> Unit,
    private val onError:   (String) -> Unit,
) {

    companion object {
        private const val TAG              = "VoiceSTT"
        private const val SAMPLE_RATE      = 16_000
        private const val BYTES_PER_SAMPLE = 2
        private const val CHUNK_MS         = 100
        private val CHUNK_BYTES = SAMPLE_RATE * BYTES_PER_SAMPLE * CHUNK_MS / 1000
    }

    /** Нормализованная амплитуда 0.08..1.0 — читается из UI для вейвформы. */
    @Volatile var amplitude: Float = 0.08f
        private set

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)   // бесконечный таймаут для стриминга
        .build()

    @Volatile private var running = false
    private var webSocket:    WebSocket?  = null
    private var audioRecord:  AudioRecord? = null
    private var recordingJob: Job?         = null

    fun start(scope: CoroutineScope) {
        if (running) return
        running = true

        // Проверяем и инициализируем AudioRecord
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf == AudioRecord.ERROR_BAD_VALUE || minBuf == AudioRecord.ERROR) {
            Log.e(TAG, "getMinBufferSize failed: $minBuf")
            onError("Ошибка инициализации микрофона")
            running = false
            return
        }
        val internalBuf = maxOf(minBuf * 4, SAMPLE_RATE * BYTES_PER_SAMPLE * 2)
        Log.d(TAG, "minBuf=$minBuf internalBuf=$internalBuf")

        val rec = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            internalBuf
        )
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord init failed, state=${rec.state}")
            rec.release()
            onError("Не удалось открыть микрофон")
            running = false
            return
        }
        audioRecord = rec

        val wsUrl = BuildConfig.MOCK_API_BASE_URL
            .replace("https://", "wss://")
            .replace("http://", "ws://") + "/ws/stt"
        Log.d(TAG, "Connecting to $wsUrl")

        val request = Request.Builder().url(wsUrl).build()

        val listener = object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket opened")
                // Сохраняем ссылку сразу, чтобы recording loop её видел
                webSocket = ws
                rec.startRecording()
                Log.d(TAG, "AudioRecord started, state=${rec.recordingState}")
                recordingJob = scope.launch(Dispatchers.IO) {
                    val buf = ByteArray(CHUNK_BYTES)
                    var chunkCount = 0
                    while (running && isActive) {
                        val n = rec.read(buf, 0, buf.size)
                        if (n <= 0) { Log.w(TAG, "read returned $n"); continue }

                        // RMS для ripple-анимации
                        var sumSq = 0.0
                        for (i in 0 until n step 2) {
                            val lo = buf[i].toInt() and 0xFF
                            val hi = buf[i + 1].toInt()
                            val s  = ((hi shl 8) or lo).toShort().toInt()
                            sumSq += s.toDouble() * s
                        }
                        val rawRms = sqrt(sumSq / (n / 2)).toFloat()
                        amplitude = (rawRms / 32767f).coerceIn(0.08f, 1f)

                        ws.send(buf.copyOf(n).toByteString())
                        if (++chunkCount % 10 == 0) Log.d(TAG, "sent $chunkCount chunks, rawRms=$rawRms amp=$amplitude")
                    }
                    Log.d(TAG, "Recording loop ended")
                }
            }

            override fun onMessage(ws: WebSocket, text: String) {
                Log.d(TAG, "onMessage: $text")
                runCatching {
                    val j = JSONObject(text)
                    when (j.optString("type")) {
                        "partial" -> onPartial(j.optString("text", ""))
                        "final"   -> onFinal(j.optString("text", ""))
                        "error"   -> onError(j.optString("message", "STT error"))
                    }
                }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "onFailure: ${t.message}, response=${response?.code}")
                if (running) onError("Ошибка соединения: ${t.message}")
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "onClosing: $code $reason")
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "onClosed: $code $reason")
            }
        }

        webSocket = client.newWebSocket(request, listener)
    }

    /** Останавливает запись и закрывает WebSocket. Безопасно вызывать из любого потока. */
    fun stop() {
        running   = false
        amplitude = 0.08f
        recordingJob?.cancel()
        recordingJob = null
        try { audioRecord?.stop()    } catch (_: Exception) {}
        try { audioRecord?.release() } catch (_: Exception) {}
        audioRecord = null
        try { webSocket?.close(1000, "done") } catch (_: Exception) {}
        webSocket = null
    }
}
