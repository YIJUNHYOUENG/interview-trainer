package com.s2023531050.interviewtrainer.interview

import android.content.Context

/** 면접 설정 화면에서 저장한 값. 앱을 껐다 켜도 남도록 SharedPreferences 에 둔다. */
class InterviewSettings(context: Context) {
    private val sp = context.getSharedPreferences("interview_settings", Context.MODE_PRIVATE)

    var company: String
        get() = sp.getString("company", "").orEmpty()
        set(v) = sp.edit().putString("company", v).apply()

    var role: String
        get() = sp.getString("role", "").orEmpty()
        set(v) = sp.edit().putString("role", v).apply()

    /** 이력서·자기소개서 내용 (서버의 coverLetter) */
    var resume: String
        get() = sp.getString("resume", "").orEmpty()
        set(v) = sp.edit().putString("resume", v).apply()

    /** 채용 공고 본문 (붙여넣기, 최대 5,000자) */
    var jobPosting: String
        get() = sp.getString("jobPosting", "").orEmpty()
        set(v) = sp.edit().putString("jobPosting", v.take(MAX_JOB)).apply()

    /** 기타 요청 (최대 500자) */
    var extraRequest: String
        get() = sp.getString("extraRequest", "").orEmpty()
        set(v) = sp.edit().putString("extraRequest", v.take(MAX_EXTRA)).apply()

    /** 이력서·포트폴리오 파일에서 읽은 글자와 파일 이름. 직접 입력한 [resume] 이 있으면 그쪽이 우선이다. */
    var resumeFileName: String
        get() = sp.getString("resumeFileName", "").orEmpty()
        set(v) = sp.edit().putString("resumeFileName", v).apply()

    var resumeFileText: String
        get() = sp.getString("resumeFileText", "").orEmpty()
        set(v) = sp.edit().putString("resumeFileText", v).apply()

    /** 서버(coverLetter)로 보낼 이력서 글자: 직접 입력 > 파일 */
    val effectiveResume: String
        get() = resume.ifBlank { resumeFileText }

    /** 공고·이력서·GitHub 중 하나라도 입력했는지 (지원서/혼합 면접이 일반 질문으로 대체되는지 판단) */
    val hasApplicationInfo: Boolean
        get() = jobPosting.isNotBlank() || effectiveResume.isNotBlank() || repoState == "done"

    /** "cs" / "cover" / "mixed" */
    var type: String
        get() = sp.getString("type", "mixed").orEmpty()
        set(v) = sp.edit().putString("type", v).apply()

    var questionCount: Int
        get() = sp.getInt("questionCount", 5)
        set(v) = sp.edit().putInt("questionCount", v.coerceIn(1, 10)).apply()

    var maxFollowups: Int
        get() = sp.getInt("maxFollowups", 2)
        set(v) = sp.edit().putInt("maxFollowups", v.coerceIn(0, 3)).apply()

    /** "text" / "voice" (음성은 아직 동작하지 않는다) */
    var answerMode: String
        get() = sp.getString("answerMode", "text").orEmpty()
        set(v) = sp.edit().putString("answerMode", v).apply()

    // ---- GitHub 분석 (서버의 /repo/analyze 결과를 기억해 둔다) ----

    /** 분석을 요청한 레포 주소. 입력칸 값이 이것과 다르면 이전 분석은 쓰지 않는다. */
    var githubUrl: String
        get() = sp.getString("githubUrl", "").orEmpty()
        set(v) = sp.edit().putString("githubUrl", v).apply()

    /** "" (분석 안 함) / "running" / "done" / "failed" */
    var repoState: String
        get() = sp.getString("repoState", "").orEmpty()
        set(v) = sp.edit().putString("repoState", v).apply()

    /** 서버가 준 작업 id. 면접을 시작할 때 repoAnalysisId 로 보낸다. */
    var repoJobId: String
        get() = sp.getString("repoJobId", "").orEmpty()
        set(v) = sp.edit().putString("repoJobId", v).apply()

    /** 화면에 보일 요약: 기술 스택 / 실패 이유 */
    var repoTech: String
        get() = sp.getString("repoTech", "").orEmpty()
        set(v) = sp.edit().putString("repoTech", v).apply()

    var repoQuestionCount: Int
        get() = sp.getInt("repoQuestionCount", 0)
        set(v) = sp.edit().putInt("repoQuestionCount", v).apply()

    var repoError: String
        get() = sp.getString("repoError", "").orEmpty()
        set(v) = sp.edit().putString("repoError", v).apply()

    fun clearRepo() {
        githubUrl = ""
        repoState = ""
        repoJobId = ""
        repoTech = ""
        repoQuestionCount = 0
        repoError = ""
    }

    val typeLabel: String
        get() = when (type) {
            "cs" -> "CS"
            "cover" -> "지원서"
            else -> "혼합"
        }

    /** 면접 홈 "이번 면접 옵션" 한 줄 */
    val optionsLine: String
        get() = "$typeLabel · 주제 ${questionCount}개 · 꼬리질문 ${maxFollowups}회 · " +
            if (answerMode == "voice") "음성" else "텍스트"

    companion object {
        const val MAX_JOB = 5000
        const val MAX_EXTRA = 500
        const val MAX_RESUME = 12000
    }
}
