package com.s2023531050.interviewtrainer.interview

import com.s2023531050.interviewtrainer.db.InterviewRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 기록을 화면에 보여 줄 때 쓰는 문구. 히스토리, 홈의 최근 면접, 리포트 부제가 같은 형식을 쓴다. */
fun modeLabel(mode: String): String = when (mode) {
    "cs" -> "CS"
    "cover" -> "지원서"
    else -> "혼합"
}

fun InterviewRecord.meta(): String {
    val date = SimpleDateFormat("MM.dd", Locale.KOREA).format(Date(startedAt))
    val answer = if (answerMode == "voice") "음성" else "텍스트"
    return "$date · ${modeLabel(mode)} · $answer"
}
