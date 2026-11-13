package com.s2023531050.interviewtrainer.net

import android.content.Context

/**
 * PC 서버 주소 설정.
 * 에뮬레이터는 PC 를 10.0.2.2 로 부르고, 실제 폰은 같은 와이파이의 PC IP(예: 192.168.0.12)로 부른다.
 * 사용자가 입력한 주소는 [normalize] 로 http://호스트:포트 모양으로 정리해 저장한다.
 */
object ServerConfig {
    const val DEFAULT_URL = "http://10.0.2.2:8787"
    private const val DEFAULT_PORT = 8787
    private const val PREFS = "server_config"
    private const val KEY = "baseUrl"

    private val HOST = Regex("^[A-Za-z0-9]([A-Za-z0-9.-]{0,251}[A-Za-z0-9])?$")

    fun load(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: DEFAULT_URL

    fun save(context: Context, url: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, url).apply()
        ApiClient.baseUrl = url
    }

    fun reset(context: Context) = save(context, DEFAULT_URL)

    /**
     * "192.168.0.12", "192.168.0.12:8787", "http://192.168.0.12:8787/" 모두 "http://192.168.0.12:8787" 로 만든다.
     * 주소로 쓸 수 없는 입력이면 null.
     */
    fun normalize(input: String): String? {
        var text = input.trim().removePrefix("http://").trimEnd('/')
        if (text.isEmpty() || text.contains("/") || text.contains(" ") || text.startsWith("https://")) return null
        val host: String
        val port: Int
        val colon = text.lastIndexOf(':')
        if (colon >= 0) {
            host = text.substring(0, colon)
            port = text.substring(colon + 1).toIntOrNull() ?: return null
        } else {
            host = text
            port = DEFAULT_PORT
        }
        if (port !in 1..65535 || !HOST.matches(host)) return null
        return "http://$host:$port"
    }

    /** 대화상자에 보여 줄 때는 http:// 를 뺀다. */
    fun display(url: String): String = url.removePrefix("http://")
}
