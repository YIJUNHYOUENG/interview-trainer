package com.s2023531050.interviewtrainer.concept

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.s2023531050.interviewtrainer.BaseActivity
import com.s2023531050.interviewtrainer.MainActivity
import com.s2023531050.interviewtrainer.R
import kotlinx.coroutines.launch
import org.json.JSONArray

/**
 * 4. CS 개념 학습 - 퀴즈 결과.
 * - 오늘의 퀴즈: 저장된 오늘 세트를 읽어 문제별 정오, 숙련도 변화, 다음 복습일을 보여 준다.
 * - 오답 다시 풀기([EXTRA_RETRY_RESULT]): 방금 푼 문제의 정오와 해결 여부를 보여 준다(숙련도는 바뀌지 않는다).
 */
class QuizResultActivity : BaseActivity(R.layout.activity_quiz_result) {

    /** 화면에 그릴 한 줄 */
    private data class Row(
        val conceptId: String,
        val correct: Boolean,
        val question: String,
        val change: String,
        val note: String?,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { goHome() }
        findViewById<View>(R.id.btnHome).setOnClickListener { goHome() }
        findViewById<View>(R.id.btnWrong).setOnClickListener {
            startActivity(Intent(this, WrongNoteActivity::class.java))
        }

        lifecycleScope.launch {
            val data = ConceptRepository.load(this@QuizResultActivity)
            val retryJson = intent.getStringExtra(EXTRA_RETRY_RESULT)
            val rows = if (retryJson != null) retryRows(data, retryJson) else dailyRows(data)
            render(data, rows, retry = retryJson != null)
        }
    }

    private suspend fun dailyRows(data: ConceptData): List<Row> {
        val now = System.currentTimeMillis()
        val (_, items) = LearningRepository(this, data).todayQuiz(now)
        return items.filter { it.answered }.map { item ->
            val quiz = data.get(item.conceptId)!!.quizzes[item.quizIndex]
            val correct = item.chosen == quiz.answer
            val level = if (item.levelBefore == item.levelAfter) "Lv.${item.levelAfter} 유지"
            else "Lv.${item.levelBefore} → Lv.${item.levelAfter}"
            val review = LearningRules.reviewLabel(item.nextReviewAt, now)
            Row(
                item.conceptId, correct, quiz.question, "$level · 다음 복습 ${review.removePrefix("복습 ")}",
                if (correct) null else "오답노트에 추가됨",
            )
        }
    }

    private fun retryRows(data: ConceptData, json: String): List<Row> {
        val a = JSONArray(json)
        return (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            val concept = data.get(o.getString("c"))!!
            val quiz = concept.quizzes[o.getInt("q")]
            val correct = o.getInt("a") == quiz.answer
            Row(
                concept.id, correct, quiz.question, if (correct) "해결함으로 옮겼어요" else "아직 풀어야 할 것으로 남아 있어요",
                null,
            )
        }
    }

    private fun render(data: ConceptData, rows: List<Row>, retry: Boolean) {
        val right = rows.count { it.correct }
        findViewById<TextView>(R.id.tvScore).text = "$right/${rows.size}"
        findViewById<TextView>(R.id.tvMessage).text = when {
            rows.isEmpty() -> "푼 문제가 없어요"
            right == rows.size -> "모두 맞혔어요!"
            right == 0 -> "오답노트에서 다시 확인해 보세요"
            else -> "잘했어요. 틀린 문제를 확인해 보세요"
        }
        findViewById<TextView>(R.id.tvNotice).text = if (retry) "숙련도는 바뀌지 않아요" else "내일 새 퀴즈가 열려요"

        val box = findViewById<LinearLayout>(R.id.llRows)
        box.removeAllViews()
        rows.forEach { row ->
            val view = layoutInflater.inflate(R.layout.item_quiz_result, box, false)
            val concept = data.get(row.conceptId)!!
            view.findViewById<TextView>(R.id.tvMark).apply {
                text = if (row.correct) "✓" else "✗"
                setTextColor(ContextCompat.getColor(this@QuizResultActivity, if (row.correct) R.color.ok else R.color.bad))
            }
            view.findViewById<TextView>(R.id.tvConcept).text = "${concept.categoryName} · ${concept.title}"
            view.findViewById<TextView>(R.id.tvQuestion).text = row.question
            view.findViewById<TextView>(R.id.tvChange).text = row.change
            view.findViewById<TextView>(R.id.tvNote).apply {
                visibility = if (row.note != null) View.VISIBLE else View.GONE
                text = row.note.orEmpty()
            }
            view.setOnClickListener {
                startActivity(
                    Intent(this, ConceptDetailActivity::class.java)
                        .putExtra(ConceptDetailActivity.EXTRA_CONCEPT_ID, row.conceptId)
                )
            }
            box.addView(view)
        }
    }

    private fun goHome() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        finish()
    }

    companion object {
        /** 오답 다시 풀기 결과: [{"c": conceptId, "q": quizIndex, "a": chosen}, ...] */
        const val EXTRA_RETRY_RESULT = "retryResult"
    }
}
