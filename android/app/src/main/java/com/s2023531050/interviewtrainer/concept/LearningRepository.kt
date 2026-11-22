package com.s2023531050.interviewtrainer.concept

import android.content.Context
import androidx.room.withTransaction
import com.s2023531050.interviewtrainer.db.AppDatabase
import com.s2023531050.interviewtrainer.db.ConceptProgress
import com.s2023531050.interviewtrainer.db.DailyQuiz
import com.s2023531050.interviewtrainer.db.WrongAnswer
import org.json.JSONArray
import org.json.JSONObject
import java.util.Random
import java.util.TimeZone

/** 오늘의 퀴즈 한 문제. [chosen] 이 -1 이면 아직 안 푼 문제이고, 푼 뒤에는 숙련도 변화를 함께 남겨 결과 화면에 쓴다. */
data class DailyItem(
    val conceptId: String,
    val quizIndex: Int,
    val chosen: Int = -1,
    val levelBefore: Int = 0,
    val levelAfter: Int = 0,
    val nextReviewAt: Long? = null,
) {
    val answered get() = chosen >= 0

    fun toJson(): JSONObject = JSONObject()
        .put("c", conceptId).put("q", quizIndex).put("a", chosen)
        .put("lb", levelBefore).put("la", levelAfter)
        .put("nr", nextReviewAt ?: JSONObject.NULL)

    companion object {
        fun fromJson(o: JSONObject) = DailyItem(
            o.getString("c"), o.getInt("q"), o.optInt("a", -1), o.optInt("lb"), o.optInt("la"),
            if (o.isNull("nr")) null else o.getLong("nr"),
        )

        fun listToJson(items: List<DailyItem>): String = JSONArray(items.map { it.toJson() }).toString()

        fun listFromJson(text: String): List<DailyItem> {
            val a = JSONArray(text)
            return (0 until a.length()).map { fromJson(a.getJSONObject(it)) }
        }
    }
}

/** 한 문제를 푼 결과. 화면은 이것으로 정오와 해설을 보여 준다. */
data class AnswerResult(
    val correct: Boolean,
    val levelBefore: Int,
    val levelAfter: Int,
    val nextReviewAt: Long?,
)

/** 개념 퀴즈 결과. [applied] 가 false 면 복습일 전이라 연습만 한 것이고 숙련도·복습일은 그대로다. */
data class ConceptQuizResult(
    val correct: Int,
    val total: Int,
    val levelBefore: Int,
    val levelAfter: Int,
    val nextReviewAt: Long?,
    val applied: Boolean,
    /** 같이 오늘의 퀴즈 답으로 반영된 문제 수 */
    val dailyLinked: Int = 0,
)

data class LearningStats(
    val dailyCompleted: Boolean,
    val dailyAnswered: Int,
    val dailyCorrect: Int,
    val wrongUnresolved: Int,
    val dueCount: Int,
    val studiedCount: Int,
    val completedCount: Int,   // 이수 완료한 개념 수
)

/** 오늘의 퀴즈로 낼 문제를 고르는 규칙 (docs/SCREENS.md 6장). */
object DailyQuizPlanner {
    /**
     * 개념 하나에서 한 문제씩, 최대 [LearningRules.DAILY_COUNT] 개를 고른다. 개념 고르는 순서는
     * ① 복습일이 된(지난) 개념, 오래된 순 ② 풀지 못한 오답이 있는 개념 ③ 숙련도 낮은 순(미학습 포함).
     * 같은 날짜로 다시 계산해도 같은 결과가 나오도록 [seed](날짜)로 섞는다. 개념 안에서는 출제 횟수로 돌려 가며 고른다.
     */
    fun pick(
        concepts: List<Concept>,
        progress: Map<String, ConceptProgress>,
        unresolvedConceptIds: Set<String>,
        now: Long,
        seed: String,
        zone: TimeZone = TimeZone.getDefault(),
    ): List<DailyItem> {
        val chosen = LinkedHashSet<Concept>()
        fun add(list: List<Concept>) {
            for (c in list) if (chosen.size < LearningRules.DAILY_COUNT) chosen.add(c)
        }
        val rnd = Random(seed.hashCode().toLong())

        add(concepts.filter { LearningRules.isDue(progress[it.id]?.nextReviewAt, now, zone) }
            .sortedBy { progress[it.id]?.nextReviewAt })
        add(concepts.filter { it.id in unresolvedConceptIds && it !in chosen }.shuffled(rnd))
        add(concepts.filter { it !in chosen }.shuffled(rnd).sortedBy { progress[it.id]?.level ?: 0 })

        return chosen.map { c ->
            val attempts = progress[c.id]?.quizAttempts ?: 0
            DailyItem(c.id, attempts % c.quizzes.size)
        }
    }
}

/**
 * 개념 학습의 저장·갱신을 한곳에서 한다. 한 문제를 푸는 일은 진도·오답·오늘의 퀴즈를 함께 바꾸므로 트랜잭션으로 묶는다.
 */
class LearningRepository(context: Context, private val data: ConceptData) {
    private val db = AppDatabase.get(context)
    private val progressDao = db.conceptProgress()
    private val wrongDao = db.wrongAnswers()
    private val dailyDao = db.dailyQuiz()

    // ---------- 개념 열람 ----------

    /** 개념 상세를 처음 열면 학습 시각만 기록한다. 숙련도는 퀴즈로만 바뀐다. */
    suspend fun markStudied(conceptId: String, now: Long = System.currentTimeMillis()) {
        val p = progressDao.get(conceptId)
        if (p == null) {
            progressDao.upsert(ConceptProgress(conceptId, 0, now, 0, 0, null))
        } else if (p.studiedAt == null) {
            progressDao.upsert(p.copy(studiedAt = now))
        }
    }

    suspend fun progressMap(): Map<String, ConceptProgress> = progressDao.all().associateBy { it.conceptId }

    // ---------- 오늘의 퀴즈 ----------

    /** 오늘 세트를 읽는다. 없으면 문제 3개를 골라 저장하고, 있으면(중간에 나갔다 온 경우) 그대로 이어 준다. */
    suspend fun todayQuiz(now: Long = System.currentTimeMillis()): Pair<DailyQuiz, List<DailyItem>> {
        val key = LearningRules.dateKey(now)
        dailyDao.get(key)?.let { return it to DailyItem.listFromJson(it.items) }
        return db.withTransaction {
            dailyDao.get(key)?.let { return@withTransaction it to DailyItem.listFromJson(it.items) }
            val items = DailyQuizPlanner.pick(
                data.all, progressMap(), wrongDao.unresolvedConceptIds().toSet(), now, key,
            )
            val quiz = DailyQuiz(key, DailyItem.listToJson(items), null, 0)
            dailyDao.upsert(quiz)
            quiz to items
        }
    }

    /**
     * 오늘의 퀴즈 [index] 번 문제에 [chosen] 을 골랐을 때 채점하고 숙련도·오답·복습일을 바꾼다.
     * 이미 푼 문제면 아무것도 바꾸지 않고 null.
     */
    suspend fun answerDaily(index: Int, chosen: Int, now: Long = System.currentTimeMillis()): AnswerResult? =
        db.withTransaction {
            val key = LearningRules.dateKey(now)
            val quiz = dailyDao.get(key) ?: return@withTransaction null
            val items = DailyItem.listFromJson(quiz.items).toMutableList()
            val item = items.getOrNull(index) ?: return@withTransaction null
            if (item.answered) return@withTransaction null

            val q = data.get(item.conceptId)?.quizzes?.getOrNull(item.quizIndex) ?: return@withTransaction null
            val correct = chosen == q.answer

            val old = progressDao.get(item.conceptId)
            val before = old?.level ?: 0
            val after = LearningRules.nextLevel(before, correct)
            val review = LearningRules.reviewAt(now, after)
            progressDao.upsert(
                ConceptProgress(
                    conceptId = item.conceptId,
                    level = after,
                    studiedAt = old?.studiedAt,
                    quizAttempts = (old?.quizAttempts ?: 0) + 1,
                    quizCorrect = (old?.quizCorrect ?: 0) + if (correct) 1 else 0,
                    nextReviewAt = review,
                    correctMask = old?.correctMask ?: 0,
                )
            )
            recordWrongOrSolved(item.conceptId, item.quizIndex, chosen, correct, now)

            items[index] = item.copy(chosen = chosen, levelBefore = before, levelAfter = after, nextReviewAt = review)
            val done = items.all { it.answered }
            dailyDao.upsert(
                quiz.copy(
                    items = DailyItem.listToJson(items),
                    completedAt = if (done) now else null,
                    correctCount = items.count { it.answered && data.get(it.conceptId)?.quizzes?.getOrNull(it.quizIndex)?.answer == it.chosen },
                )
            )
            AnswerResult(correct, before, after, review)
        }

    // ---------- 개념 퀴즈 (개념 상세에서 그 개념의 문제를 모두 푼다) ----------

    /**
     * 개념 퀴즈를 마치고 숙련도·복습일을 정한다([LearningRules.levelAfterConceptQuiz]).
     * 이미 퀴즈를 푼 개념의 복습일이 아직 안 됐으면(같은 날 반복 등) 연습으로만 치고 숙련도는 바꾸지 않는다.
     * 문제별 오답 기록은 풀 때마다 [answerRetry] 가 이미 처리했다.
     */
    suspend fun finishConceptQuiz(
        conceptId: String, answers: List<Triple<String, Int, Int>>, now: Long = System.currentTimeMillis(),
    ): ConceptQuizResult = db.withTransaction {
        val total = answers.size
        val correct = answers.count { (c, q, a) -> data.get(c)?.quizzes?.getOrNull(q)?.answer == a }
        val old = progressDao.get(conceptId)
        val before = old?.level ?: 0
        val practiceOnly = old?.nextReviewAt != null && !LearningRules.isDue(old.nextReviewAt, now)
        val result = if (practiceOnly) {
            progressDao.upsert(old!!.copy(quizAttempts = old.quizAttempts + total, quizCorrect = old.quizCorrect + correct))
            ConceptQuizResult(correct, total, before, before, old.nextReviewAt, applied = false)
        } else {
            val after = LearningRules.levelAfterConceptQuiz(before, correct, total)
            val review = LearningRules.reviewAt(now, after)
            progressDao.upsert(
                ConceptProgress(
                    conceptId = conceptId,
                    level = after,
                    studiedAt = old?.studiedAt ?: now,
                    quizAttempts = (old?.quizAttempts ?: 0) + total,
                    quizCorrect = (old?.quizCorrect ?: 0) + correct,
                    nextReviewAt = review,
                    correctMask = old?.correctMask ?: 0,
                )
            )
            ConceptQuizResult(correct, total, before, after, review, applied = true)
        }
        result.copy(dailyLinked = linkToDaily(conceptId, answers, result, now))
    }

    /**
     * 오늘의 퀴즈에 같은 개념·같은 문제가 아직 안 푼 채로 있으면, 개념 퀴즈에서 푼 답을 오늘의 퀴즈 답으로도 친다.
     * 숙련도는 개념 퀴즈 결과로 이미 정해졌으므로 여기서는 바꾸지 않고 그 변화를 문제 기록에만 남긴다. 반영한 문제 수를 돌려준다.
     */
    private suspend fun linkToDaily(conceptId: String, answers: List<Triple<String, Int, Int>>, r: ConceptQuizResult, now: Long): Int {
        val quiz = dailyDao.get(LearningRules.dateKey(now)) ?: return 0
        if (quiz.completedAt != null) return 0
        val items = DailyItem.listFromJson(quiz.items).toMutableList()
        var linked = 0
        items.forEachIndexed { i, item ->
            if (item.answered || item.conceptId != conceptId) return@forEachIndexed
            val chosen = answers.firstOrNull { it.first == conceptId && it.second == item.quizIndex }?.third ?: return@forEachIndexed
            items[i] = item.copy(chosen = chosen, levelBefore = r.levelBefore, levelAfter = r.levelAfter, nextReviewAt = r.nextReviewAt)
            linked++
        }
        if (linked == 0) return 0
        dailyDao.upsert(
            quiz.copy(
                items = DailyItem.listToJson(items),
                completedAt = if (items.all { it.answered }) now else null,
                correctCount = items.count { it.answered && data.get(it.conceptId)?.quizzes?.getOrNull(it.quizIndex)?.answer == it.chosen },
            )
        )
        return linked
    }

    // ---------- 오답 다시 풀기 ----------

    /**
     * 오답노트에서 다시 푼 한 문제(개념 퀴즈의 문제별 기록도 같다)를 채점한다. 맞히면 해결함으로 옮기고, 틀리면 틀린 횟수만 늘린다.
     * 오답을 낼 때 이미 숙련도를 내렸으므로 여기서는 숙련도를 바꾸지 않는다.
     */
    suspend fun answerRetry(conceptId: String, quizIndex: Int, chosen: Int, now: Long = System.currentTimeMillis()): Boolean =
        db.withTransaction {
            val q = data.get(conceptId)?.quizzes?.getOrNull(quizIndex) ?: return@withTransaction false
            val correct = chosen == q.answer
            recordWrongOrSolved(conceptId, quizIndex, chosen, correct, now)
            correct
        }

    /** 틀렸으면 오답노트에 쌓거나 횟수를 늘리고(다시 "풀어야 할 것"), 맞혔으면 이미 있는 오답을 해결 처리한다. */
    private suspend fun recordWrongOrSolved(conceptId: String, quizIndex: Int, chosen: Int, correct: Boolean, now: Long) {
        // 이수 완료 판정용: 이 문제를 가장 최근에 맞혔는지 기록한다(오늘의 퀴즈·개념 퀴즈·다시 풀기 모두 여기를 지난다)
        val p = progressDao.get(conceptId)
        val mask = LearningRules.withAnswer(p?.correctMask ?: 0, quizIndex, correct)
        progressDao.upsert(p?.copy(correctMask = mask) ?: ConceptProgress(conceptId, 0, null, 0, 0, null, mask))

        val w = wrongDao.find(conceptId, quizIndex)
        if (!correct) {
            wrongDao.upsert(
                w?.copy(chosen = chosen, wrongCount = w.wrongCount + 1, lastWrongAt = now, resolved = false)
                    ?: WrongAnswer(conceptId = conceptId, quizIndex = quizIndex, chosen = chosen, wrongCount = 1, lastWrongAt = now, resolved = false)
            )
        } else if (w != null && !w.resolved) {
            wrongDao.upsert(w.copy(resolved = true))
        }
    }

    // ---------- 홈·목록 통계 ----------

    suspend fun stats(now: Long = System.currentTimeMillis()): LearningStats {
        val daily = dailyDao.get(LearningRules.dateKey(now))
        val items = daily?.let { DailyItem.listFromJson(it.items) }.orEmpty()
        val studied = progressDao.all().filter { it.level > 0 }
        return LearningStats(
            dailyCompleted = daily?.completedAt != null,
            dailyAnswered = items.count { it.answered },
            dailyCorrect = daily?.correctCount ?: 0,
            wrongUnresolved = wrongDao.countUnresolved(),
            dueCount = progressDao.countDue(LearningRules.startOfDay(now, plusDays = 1)),
            studiedCount = studied.size,
            completedCount = progressDao.all().count {
                LearningRules.isCompleted(it.correctMask, data.get(it.conceptId)?.quizzes?.size ?: 0)
            },
        )
    }
}
