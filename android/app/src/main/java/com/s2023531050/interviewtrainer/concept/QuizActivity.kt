package com.s2023531050.interviewtrainer.concept

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.s2023531050.interviewtrainer.BaseActivity
import com.s2023531050.interviewtrainer.R
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * 4. CS 개념 학습 - 퀴즈 풀이.
 * - 기본: 오늘의 퀴즈(하루 3문제). 이미 끝낸 날이면 결과 화면을 바로 연다. 중간에 나갔다 오면 푼 다음 문제부터 이어 푼다.
 * - [EXTRA_RETRY]: 오답노트의 "오답 다시 풀기". 맞히면 해결함으로 옮기고 숙련도는 바꾸지 않는다.
 * - [EXTRA_CONCEPT_ID]: 개념 상세의 "이 개념 퀴즈 풀기". 그 개념의 문제를 모두 풀고, 끝나면 결과(맞힌 수)로 숙련도·복습일을 정한다.
 * 보기를 누르면 바로 채점하고 그 결과를 저장한다(중간에 그만둬도 푼 문제까지는 반영).
 */
class QuizActivity : BaseActivity(R.layout.activity_quiz) {

    /** 지금 푸는 문제 한 개 */
    private data class Question(val conceptId: String, val quizIndex: Int)

    private lateinit var data: ConceptData
    private lateinit var repo: LearningRepository
    private var questions: List<Question> = emptyList()
    private var index = 0
    private var answered = false
    private enum class Mode { DAILY, RETRY, CONCEPT }

    private var mode = Mode.DAILY
    private var conceptId: String? = null

    // 다시 풀기·개념 퀴즈에서 푼 답(결과 화면에 넘긴다): conceptId, quizIndex, chosen
    private val retryAnswers = mutableListOf<Triple<String, Int, Int>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { confirmExit() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = confirmExit()
        })

        val retryIds = intent.getStringArrayListExtra(EXTRA_RETRY)
        conceptId = intent.getStringExtra(EXTRA_CONCEPT_ID)
        mode = when {
            retryIds != null -> Mode.RETRY
            conceptId != null -> Mode.CONCEPT
            else -> Mode.DAILY
        }
        findViewById<MaterialToolbar>(R.id.toolbar).title = when (mode) {
            Mode.RETRY -> "오답 다시 풀기"
            Mode.CONCEPT -> "개념 퀴즈"
            Mode.DAILY -> "오늘의 퀴즈"
        }

        lifecycleScope.launch {
            data = ConceptRepository.load(this@QuizActivity)
            repo = LearningRepository(this@QuizActivity, data)

            if (mode == Mode.RETRY) {
                questions = retryIds.orEmpty().mapNotNull { s ->
                    val (c, q) = s.split(":").let { it[0] to it[1].toInt() }
                    if (data.get(c)?.quizzes?.getOrNull(q) != null) Question(c, q) else null
                }
                index = 0
            } else if (mode == Mode.CONCEPT) {
                // 그 개념의 퀴즈를 처음부터 끝까지 푼다(이어 풀기 없음)
                questions = data.get(conceptId!!)?.quizzes?.indices?.map { Question(conceptId!!, it) }.orEmpty()
                index = 0
            } else {
                val (quiz, items) = repo.todayQuiz()
                if (quiz.completedAt != null) { // 오늘은 이미 풀었다: 결과 화면이 바로 열린다
                    startActivity(Intent(this@QuizActivity, QuizResultActivity::class.java))
                    finish()
                    return@launch
                }
                questions = items.map { Question(it.conceptId, it.quizIndex) }
                index = items.indexOfFirst { !it.answered }.coerceAtLeast(0)
            }
            if (questions.isEmpty()) {
                finish()
                return@launch
            }
            showQuestion()
        }
    }

    private fun showQuestion() {
        answered = false
        val q = questions[index]
        val concept = data.get(q.conceptId)!!
        val quiz = concept.quizzes[q.quizIndex]

        findViewById<LinearProgressIndicator>(R.id.progress).apply {
            max = questions.size
            setProgressCompat(index, true)
        }
        findViewById<TextView>(R.id.tvProgress).text = "${index + 1} / ${questions.size}"
        findViewById<Chip>(R.id.chipCategory).text = concept.categoryName
        findViewById<TextView>(R.id.tvConcept).text = concept.title
        findViewById<TextView>(R.id.tvQuestion).text = quiz.question
        findViewById<View>(R.id.cardExplanation).visibility = View.GONE

        val box = findViewById<LinearLayout>(R.id.llOptions)
        box.removeAllViews()
        quiz.options.forEachIndexed { i, text ->
            val card = layoutInflater.inflate(R.layout.item_quiz_option, box, false) as MaterialCardView
            card.findViewById<TextView>(R.id.tvNumber).text = "${i + 1}"
            card.findViewById<TextView>(R.id.tvOption).text = text
            card.setOnClickListener { choose(i) }
            box.addView(card)
        }

        findViewById<MaterialButton>(R.id.btnNext).apply {
            isEnabled = false
            text = if (index == questions.size - 1) "결과 보기" else "다음 문제"
            setOnClickListener { next() }
        }
        findViewById<ScrollView>(R.id.scroll).scrollTo(0, 0)
    }

    /** 보기를 눌렀다: 한 번만 채점하고 저장한 뒤, 정답과 내가 고른 보기를 색으로 보여 준다. */
    private fun choose(chosen: Int) {
        if (answered) return
        answered = true // 저장이 끝나기 전에 또 눌러도 한 번만 처리한다
        val q = questions[index]
        val quiz = data.get(q.conceptId)!!.quizzes[q.quizIndex]

        lifecycleScope.launch {
            if (mode != Mode.DAILY) {
                // 다시 풀기와 개념 퀴즈: 문제별로 오답노트만 갱신한다. 숙련도는 개념 퀴즈가 끝난 뒤 한 번에 정한다.
                repo.answerRetry(q.conceptId, q.quizIndex, chosen)
                retryAnswers.add(Triple(q.conceptId, q.quizIndex, chosen))
            } else {
                repo.answerDaily(index, chosen)
            }
            paint(chosen, quiz)
        }
    }

    private fun paint(chosen: Int, quiz: Quiz) {
        val box = findViewById<LinearLayout>(R.id.llOptions)
        for (i in 0 until box.childCount) {
            val card = box.getChildAt(i) as MaterialCardView
            card.isClickable = false
            when {
                i == quiz.answer -> tint(card, R.color.okbg, R.color.okline)
                i == chosen -> tint(card, R.color.badbg, R.color.badline)
            }
        }
        val correct = chosen == quiz.answer
        findViewById<TextView>(R.id.tvVerdict).apply {
            text = if (correct) "정답이에요" else "아쉬워요. 정답은 ${quiz.answer + 1}번이에요"
            setTextColor(ContextCompat.getColor(this@QuizActivity, if (correct) R.color.ok else R.color.bad))
        }
        findViewById<TextView>(R.id.tvExplanation).text = quiz.explanation
        findViewById<View>(R.id.cardExplanation).visibility = View.VISIBLE
        findViewById<MaterialButton>(R.id.btnNext).isEnabled = true
        findViewById<ScrollView>(R.id.scroll).post { findViewById<ScrollView>(R.id.scroll).fullScroll(View.FOCUS_DOWN) }
    }

    private fun tint(card: MaterialCardView, bg: Int, line: Int) {
        card.setCardBackgroundColor(ContextCompat.getColor(this, bg))
        card.strokeColor = ContextCompat.getColor(this, line)
    }

    private fun next() {
        if (index < questions.size - 1) {
            index++
            showQuestion()
        } else {
            val result = Intent(this, QuizResultActivity::class.java)
            val arr = JSONArray()
            retryAnswers.forEach { (c, q, a) -> arr.put(JSONObject().put("c", c).put("q", q).put("a", a)) }
            when (mode) {
                Mode.DAILY -> {
                    startActivity(result)
                    finish()
                }
                Mode.RETRY -> {
                    startActivity(result.putExtra(QuizResultActivity.EXTRA_RETRY_RESULT, arr.toString()))
                    finish()
                }
                Mode.CONCEPT -> lifecycleScope.launch {
                    val id = conceptId!!
                    val r = repo.finishConceptQuiz(id, retryAnswers.toList())
                    val json = JSONObject().put("c", id).put("answers", arr)
                        .put("daily", r.dailyLinked)
                        .put("before", r.levelBefore).put("after", r.levelAfter)
                        .put("next", r.nextReviewAt ?: JSONObject.NULL).put("applied", r.applied)
                    startActivity(result.putExtra(QuizResultActivity.EXTRA_CONCEPT_RESULT, json.toString()))
                    finish()
                }
            }
        }
    }

    /** 푼 문제까지는 이미 저장되어 있다. 그만둬도 되는지만 묻는다. */
    private fun confirmExit() {
        AlertDialog.Builder(this)
            .setTitle("퀴즈를 그만할까요?")
            .setMessage(
                when (mode) {
                    Mode.DAILY -> "그만하면 지금까지 푼 문제까지만 반영돼요. 다시 들어오면 푼 다음 문제부터 이어서 풀 수 있어요."
                    Mode.RETRY -> "그만하면 지금까지 푼 문제까지만 반영돼요."
                    Mode.CONCEPT -> "그만하면 이 개념의 숙련도는 바뀌지 않아요. 틀린 문제는 오답노트에 남아요."
                }
            )
            .setPositiveButton("그만하기") { _, _ -> finish() }
            .setNegativeButton("계속 풀기", null)
            .show()
    }

    companion object {
        /** 오답 다시 풀기: "conceptId:quizIndex" 문자열 목록(최대 10개) */
        const val EXTRA_RETRY = "retry"

        /** 개념 퀴즈: 개념 id. 그 개념의 퀴즈를 모두 푼다. */
        const val EXTRA_CONCEPT_ID = "conceptId"
    }
}
