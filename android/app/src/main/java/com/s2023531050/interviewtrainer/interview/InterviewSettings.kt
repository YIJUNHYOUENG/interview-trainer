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

    val typeLabel: String
        get() = when (type) {
            "cs" -> "CS"
            "cover" -> "지원서"
            else -> "혼합"
        }
}
