package com.s2023531050.interviewtrainer.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

/** PC 서버가 돌려준 오류. [message] 는 사용자에게 그대로 보여 줘도 되는 문장이다. */
class ApiException(message: String) : Exception(message)

/** PC 서버(docs/DESIGN.md 7장)와 JSON 으로 통신한다. 에뮬레이터에서 PC 는 10.0.2.2 다. */
object ApiClient {
    /** PC 서버 주소. [ServerConfig] 에서 불러와 바꾼다. 기본값은 에뮬레이터가 PC 를 부르는 주소다. */
    @Volatile var baseUrl: String = ServerConfig.DEFAULT_URL

    private const val CONNECT_TIMEOUT_MS = 5_000
    private const val READ_TIMEOUT_MS = 90_000 // claude 응답(리포트 20초 안팎)과 음성 인식 시간을 넉넉히 기다린다
    private const val UNREACHABLE = "서버에 연결할 수 없습니다. PC 서버가 켜져 있는지 확인해 주세요."

    suspend fun get(path: String): JSONObject = request("GET", path, null, null)

    /** 서버가 켜져 있는지 짧게(3초) 확인한다. 응답을 못 받으면 null. */
    suspend fun health(): JSONObject? = withContext(Dispatchers.IO) {
        val conn = URL(baseUrl + "/health").openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 3_000
            conn.readTimeout = 5_000
            if (conn.responseCode in 200..299) JSONObject(conn.inputStream.bufferedReader().use { it.readText() }) else null
        } catch (e: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    suspend fun post(path: String, body: JSONObject): JSONObject =
        request("POST", path, body.toString().toByteArray(Charsets.UTF_8), "application/json; charset=utf-8")

    /** 파일 원본(녹음 WAV 등)을 본문 그대로 올린다. [readTimeoutMs] 로 기다리는 시간을 늘릴 수 있다(긴 녹음 인식 등). */
    suspend fun postBytes(path: String, bytes: ByteArray, contentType: String, readTimeoutMs: Int = READ_TIMEOUT_MS): JSONObject =
        request("POST", path, bytes, contentType, readTimeoutMs)

    private suspend fun request(
        method: String, path: String, body: ByteArray?, contentType: String?, readTimeoutMs: Int = READ_TIMEOUT_MS,
    ): JSONObject =
        withContext(Dispatchers.IO) {
            val conn = URL(baseUrl + path).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = method
                conn.connectTimeout = CONNECT_TIMEOUT_MS
                conn.readTimeout = readTimeoutMs
                conn.setRequestProperty("Accept", "application/json")
                if (body != null) {
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", contentType)
                    conn.setFixedLengthStreamingMode(body.size)
                }
                // 연결 단계의 실패(시간 초과 포함)는 "서버에 연결할 수 없음", 연결된 뒤의 시간 초과만 "응답이 늦음" 으로 알린다.
                try {
                    conn.connect()
                } catch (e: IOException) {
                    throw ApiException(UNREACHABLE)
                }
                if (body != null) conn.outputStream.use { it.write(body) }

                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (code !in 200..299) throw ApiException(errorMessage(text, code))
                JSONObject(text)
            } catch (e: SocketTimeoutException) {
                // 연결은 됐는데 응답이 늦다: 서버는 켜져 있으니 "연결할 수 없음" 과 구분해 알린다.
                throw ApiException("서버 응답이 너무 늦어요. 잠시 후 다시 시도해 주세요.")
            } catch (e: IOException) {
                throw ApiException(UNREACHABLE)
            } finally {
                conn.disconnect()
            }
        }

    /** 서버 오류 본문 {"detail": "..."} 에서 문장을 꺼낸다. */
    private fun errorMessage(text: String, code: Int): String {
        val detail = runCatching { JSONObject(text).opt("detail") }.getOrNull()
        return if (detail is String && detail.isNotBlank()) detail else "요청을 처리하지 못했습니다. ($code)"
    }
}
