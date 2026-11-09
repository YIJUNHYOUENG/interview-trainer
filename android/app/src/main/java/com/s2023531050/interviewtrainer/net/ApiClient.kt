package com.s2023531050.interviewtrainer.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** PC 서버가 돌려준 오류. [message] 는 사용자에게 그대로 보여 줘도 되는 문장이다. */
class ApiException(message: String) : Exception(message)

/** PC 서버(docs/DESIGN.md 7장)와 JSON 으로 통신한다. 에뮬레이터에서 PC 는 10.0.2.2 다. */
object ApiClient {
    const val BASE_URL = "http://10.0.2.2:8787"

    private const val CONNECT_TIMEOUT_MS = 5_000
    private const val READ_TIMEOUT_MS = 90_000 // claude 응답이 느릴 수 있다 (리포트 20초 안팎)

    suspend fun get(path: String): JSONObject = request("GET", path, null)

    suspend fun post(path: String, body: JSONObject): JSONObject = request("POST", path, body)

    private suspend fun request(method: String, path: String, body: JSONObject?): JSONObject =
        withContext(Dispatchers.IO) {
            val conn = URL(BASE_URL + path).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = method
                conn.connectTimeout = CONNECT_TIMEOUT_MS
                conn.readTimeout = READ_TIMEOUT_MS
                conn.setRequestProperty("Accept", "application/json")
                if (body != null) {
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                }

                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (code !in 200..299) throw ApiException(errorMessage(text, code))
                JSONObject(text)
            } catch (e: IOException) {
                throw ApiException("서버에 연결할 수 없습니다. PC 서버가 켜져 있는지 확인해 주세요.")
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
