package com.s2023531050.interviewtrainer

import com.s2023531050.interviewtrainer.concept.LearningRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class LearningRulesTest {
    private val zone = TimeZone.getTimeZone("Asia/Seoul")

    private fun time(y: Int, m: Int, d: Int, h: Int = 12): Long =
        Calendar.getInstance(zone).apply { clear(); set(y, m - 1, d, h, 30, 0) }.timeInMillis

    @Test fun 복습일수는_숙련도별로_정해진다() {
        assertEquals(listOf(1, 2, 4, 7, 15), (1..5).map { LearningRules.reviewDays(it) })
    }

    @Test fun 정답이면_올라가고_최대는_5() {
        assertEquals(2, LearningRules.nextLevel(1, true))
        assertEquals(5, LearningRules.nextLevel(4, true))
        assertEquals(5, LearningRules.nextLevel(5, true))
    }

    @Test fun 오답이면_내려가고_최소는_1() {
        assertEquals(2, LearningRules.nextLevel(3, false))
        assertEquals(1, LearningRules.nextLevel(2, false))
        assertEquals(1, LearningRules.nextLevel(1, false))
    }

    @Test fun 미학습은_정답이든_오답이든_1로_시작() {
        assertEquals(1, LearningRules.nextLevel(0, true))
        assertEquals(1, LearningRules.nextLevel(0, false))
    }

    @Test fun 복습일은_그날_0시로_맞춘다() {
        val now = time(2026, 11, 13, 15)
        val at = LearningRules.reviewAt(now, 3, zone) // Lv.3 → 4일 뒤
        assertEquals(time(2026, 11, 17, 0) - 30 * 60_000, at) // 17일 0시 0분
        assertEquals("2026-11-17", LearningRules.dateKey(at, zone))
    }

    @Test fun 달이_바뀌어도_일수가_맞다() {
        val at = LearningRules.reviewAt(time(2026, 11, 25), 5, zone) // 15일 뒤
        assertEquals("2026-12-10", LearningRules.dateKey(at, zone))
    }

    @Test fun 복습_대상_판정() {
        val now = time(2026, 11, 13, 9)
        val today = LearningRules.startOfDay(now, zone)
        assertTrue(LearningRules.isDue(today, now, zone))                       // 오늘
        assertTrue(LearningRules.isDue(today - 3 * 86_400_000L, now, zone))     // 지남
        assertFalse(LearningRules.isDue(LearningRules.startOfDay(now, zone, 1), now, zone)) // 내일
        assertFalse(LearningRules.isDue(null, now, zone))                       // 안 푼 개념
    }

    @Test fun 복습_문구() {
        val now = time(2026, 11, 13, 20)
        assertEquals("아직 안 봄", LearningRules.reviewLabel(null, now, zone))
        assertEquals("복습 오늘", LearningRules.reviewLabel(LearningRules.startOfDay(now, zone), now, zone))
        assertEquals("복습 오늘", LearningRules.reviewLabel(LearningRules.startOfDay(now, zone, -2), now, zone))
        assertEquals("1일 뒤", LearningRules.reviewLabel(LearningRules.startOfDay(now, zone, 1), now, zone))
        assertEquals("7일 뒤", LearningRules.reviewLabel(LearningRules.startOfDay(now, zone, 7), now, zone))
    }

    @Test fun 날짜_키는_자정에_바뀐다() {
        assertEquals("2026-11-13", LearningRules.dateKey(time(2026, 11, 13, 23), zone))
        assertEquals("2026-11-14", LearningRules.dateKey(time(2026, 11, 14, 0), zone))
    }
}

class DailyQuizPlannerTest {
    private val zone = java.util.TimeZone.getTimeZone("Asia/Seoul")
    private val now = java.util.Calendar.getInstance(zone).apply { clear(); set(2026, 10, 13, 12, 0, 0) }.timeInMillis
    private val day = 86_400_000L

    private fun concept(id: String) = com.s2023531050.interviewtrainer.concept.Concept(
        id, "c", "카테고리", id, "요약", emptyList(), "설명", emptyList(),
        List(3) { com.s2023531050.interviewtrainer.concept.Quiz("q", listOf("a", "b", "c", "d"), 0, "e") },
    )

    private fun progress(id: String, level: Int, next: Long?, attempts: Int = 0) =
        com.s2023531050.interviewtrainer.db.ConceptProgress(id, level, null, attempts, 0, next)

    private val concepts = (1..10).map { concept("c-$it") }

    private fun pick(progress: Map<String, com.s2023531050.interviewtrainer.db.ConceptProgress>, unresolved: Set<String> = emptySet()) =
        com.s2023531050.interviewtrainer.concept.DailyQuizPlanner.pick(concepts, progress, unresolved, now, "2026-11-13", zone)
            .map { it.conceptId }

    @Test fun 처음에는_서로_다른_개념_3개() {
        val ids = pick(emptyMap())
        assertEquals(3, ids.size)
        assertEquals(3, ids.toSet().size)
    }

    @Test fun 같은_날짜면_같은_문제() {
        assertEquals(pick(emptyMap()), pick(emptyMap()))
    }

    @Test fun 복습일이_지난_개념이_먼저고_오래된_순() {
        val start = LearningRules.startOfDay(now, zone)
        val p = mapOf(
            "c-5" to progress("c-5", 3, start - 1 * day),   // 하루 지남
            "c-7" to progress("c-7", 3, start - 5 * day),   // 5일 지남 (가장 오래됨)
            "c-2" to progress("c-2", 1, start + 3 * day),   // 아직 안 됨
        )
        assertEquals(listOf("c-7", "c-5"), pick(p).take(2))
    }

    @Test fun 복습_대상_다음은_풀지_못한_오답이_있는_개념() {
        val start = LearningRules.startOfDay(now, zone)
        val p = mapOf("c-4" to progress("c-4", 3, start))
        val ids = pick(p, unresolved = setOf("c-9"))
        assertEquals(listOf("c-4", "c-9"), ids.take(2))
    }

    @Test fun 나머지는_숙련도가_낮은_개념부터() {
        val start = LearningRules.startOfDay(now, zone)
        // 모든 개념을 학습했고 복습일은 아직인 상태: Lv.1 개념이 먼저 뽑혀야 한다
        val p = concepts.associate { it.id to progress(it.id, 4, start + 10 * day) } + mapOf(
            "c-3" to progress("c-3", 1, start + 10 * day),
            "c-8" to progress("c-8", 2, start + 10 * day),
        )
        val ids = pick(p)
        assertEquals("c-3", ids[0])
        assertEquals("c-8", ids[1])
    }

    @Test fun 개념_안에서는_출제_횟수로_문제_번호를_돌린다() {
        val p = concepts.associate { it.id to progress(it.id, 1, null, attempts = 4) }
        val items = com.s2023531050.interviewtrainer.concept.DailyQuizPlanner.pick(concepts, p, emptySet(), now, "x", zone)
        assertTrue(items.all { it.quizIndex == 1 }) // 4 % 3
    }

    @Test fun 개념이_3개_미만이면_있는_만큼() {
        val few = concepts.take(2)
        val items = com.s2023531050.interviewtrainer.concept.DailyQuizPlanner.pick(few, emptyMap(), emptySet(), now, "x", zone)
        assertEquals(2, items.size)
    }
}
