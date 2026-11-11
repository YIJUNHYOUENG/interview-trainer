package com.s2023531050.interviewtrainer.interview

import com.s2023531050.interviewtrainer.net.ApiClient
import com.s2023531050.interviewtrainer.net.ApiException
import org.json.JSONObject

/**
 * GitHub 레포 분석(docs/DESIGN.md 4.4) 요청과 진행 확인.
 * 분석은 몇 분 걸릴 수 있어서, 요청하면 서버가 작업 id 를 주고 앱이 [refresh] 로 다시 묻는다.
 * 상태는 [InterviewSettings] 에 저장해 화면을 나갔다 와도 이어진다.
 */
object RepoAnalysis {
    private val URL_FORMAT = Regex("^https://github\\.com/[A-Za-z0-9][A-Za-z0-9-]{0,38}/[A-Za-z0-9._-]{1,100}$")

    /** 앱에서 하는 1차 형식 검사. 맞으면 정리한 주소(끝의 / 와 .git 제거), 아니면 null. 서버가 다시 검사한다. */
    fun normalize(input: String): String? {
        var url = input.trim().trimEnd('/')
        if (url.endsWith(".git")) url = url.removeSuffix(".git")
        return if (URL_FORMAT.matches(url) && !url.endsWith("/..") && !url.endsWith(".")) url else null
    }

    /** 분석을 시작한다. 서버가 거절하면 [ApiException] (문장은 그대로 보여 줘도 된다). */
    suspend fun start(settings: InterviewSettings, url: String) {
        val r = ApiClient.post("/repo/analyze", JSONObject().put("url", url))
        settings.githubUrl = url
        settings.repoJobId = r.getString("jobId")
        settings.repoState = "running"
        settings.repoTech = ""
        settings.repoError = ""
        settings.repoQuestionCount = 0
        if (r.optString("status") == "done") refresh(settings) // 같은 레포·같은 커밋은 바로 끝나 있다
    }

    /**
     * 진행 상황을 서버에 물어 설정에 반영하고 지금 상태를 돌려준다.
     * 연결 문제는 상태를 바꾸지 않고 그대로 [ApiException] 으로 던진다(다음에 다시 묻는다).
     */
    suspend fun refresh(settings: InterviewSettings): String {
        val id = settings.repoJobId
        if (id.isEmpty()) return ""
        try {
            val r = ApiClient.get("/repo/$id")
            when (r.getString("status")) {
                "done" -> {
                    settings.repoState = "done"
                    val stack = r.optJSONArray("techStack")
                    settings.repoTech = (0 until (stack?.length() ?: 0)).joinToString(", ") { stack!!.getString(it) }
                    settings.repoQuestionCount = r.optJSONArray("questions")?.length() ?: 0
                }
                "failed" -> {
                    settings.repoState = "failed"
                    settings.repoError = r.optString("error").ifBlank { "분석에 실패했어요." }
                }
                else -> settings.repoState = "running"
            }
        } catch (e: ApiException) {
            // 서버가 재시작되어 작업을 잃었으면(404) 실패로 돌려 다시 분석하게 한다.
            if (e.message?.contains("찾을 수 없") == true) {
                settings.repoState = "failed"
                settings.repoError = "분석 결과가 사라졌어요. 다시 분석해 주세요."
            } else {
                throw e
            }
        }
        return settings.repoState
    }

    /** 면접 홈 카드에 보일 한 줄 */
    fun summaryLine(settings: InterviewSettings): String = when (settings.repoState) {
        "running" -> "분석 중…"
        "done" -> "✓ 분석 완료" + settings.repoTech.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
        "failed" -> "✗ 분석 실패"
        else -> "미입력"
    }
}
