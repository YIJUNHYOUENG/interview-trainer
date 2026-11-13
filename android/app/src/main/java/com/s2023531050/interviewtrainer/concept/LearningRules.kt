package com.s2023531050.interviewtrainer.concept

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 학습 진도 계산 규칙 (docs/SCREENS.md 6장, DESIGN.md 4.1). 화면이나 DB 와 무관한 순수 계산이라 단위 시험이 쉽다.
 * 날짜가 바뀌는 시각은 자정(기기 시간)이다.
 */
object LearningRules {
    const val MAX_LEVEL = 5
    const val DAILY_COUNT = 3

    /** 숙련도별 다음 복습까지의 일수: Lv.1 → 1일, Lv.2 → 2일, Lv.3 → 4일, Lv.4 → 7일, Lv.5 → 15일 */
    fun reviewDays(level: Int): Int = when (level) {
        1 -> 1
        2 -> 2
        3 -> 4
        4 -> 7
        else -> 15
    }

    /**
     * 문제 하나를 푼 뒤의 숙련도. 정답이면 +1(최대 Lv.5), 오답이면 −1(최소 Lv.1).
     * 기록이 없는 개념(Lv.0)은 정답이든 오답이든 Lv.1 로 시작한다.
     */
    fun nextLevel(level: Int, correct: Boolean): Int = when {
        level <= 0 -> 1
        correct -> minOf(MAX_LEVEL, level + 1)
        else -> maxOf(1, level - 1)
    }

    /** 숙련도 [level] 인 개념의 다음 복습일(그날 0시). [now] 가 속한 날을 기준으로 일수만큼 뒤다. */
    fun reviewAt(now: Long, level: Int, zone: TimeZone = TimeZone.getDefault()): Long =
        startOfDay(now, zone, plusDays = reviewDays(level))

    /** [time] 이 속한 날의 0시(+ [plusDays] 일). */
    fun startOfDay(time: Long, zone: TimeZone = TimeZone.getDefault(), plusDays: Int = 0): Long =
        Calendar.getInstance(zone).apply {
            timeInMillis = time
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, plusDays)
        }.timeInMillis

    /** 오늘의 퀴즈 키 (yyyy-MM-dd) */
    fun dateKey(time: Long, zone: TimeZone = TimeZone.getDefault()): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = zone }.format(Date(time))

    /** 복습일이 오늘이거나 지났는가: 내일 0시보다 이르면 해당 */
    fun isDue(nextReviewAt: Long?, now: Long, zone: TimeZone = TimeZone.getDefault()): Boolean =
        nextReviewAt != null && nextReviewAt < startOfDay(now, zone, plusDays = 1)

    /** 목록에 보일 복습 문구: "복습 오늘"(지났어도), "N일 뒤", 퀴즈를 푼 적이 없으면 "아직 안 봄" */
    fun reviewLabel(nextReviewAt: Long?, now: Long, zone: TimeZone = TimeZone.getDefault()): String {
        if (nextReviewAt == null) return "아직 안 봄"
        if (isDue(nextReviewAt, now, zone)) return "복습 오늘"
        val days = Math.round((nextReviewAt - startOfDay(now, zone)) / 86_400_000.0).toInt()
        return "${days}일 뒤"
    }
}
