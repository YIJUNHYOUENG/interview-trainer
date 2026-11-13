package com.s2023531050.interviewtrainer

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.s2023531050.interviewtrainer.db.AppDatabase
import kotlinx.coroutines.launch
import com.s2023531050.interviewtrainer.concept.ConceptListActivity
import com.s2023531050.interviewtrainer.concept.ConceptRepository
import com.s2023531050.interviewtrainer.concept.LearningRepository
import com.s2023531050.interviewtrainer.concept.LearningRules
import com.google.android.material.button.MaterialButton
import com.s2023531050.interviewtrainer.concept.QuizActivity
import com.s2023531050.interviewtrainer.concept.WrongNoteActivity
import com.s2023531050.interviewtrainer.interview.HistoryActivity
import com.s2023531050.interviewtrainer.interview.InterviewHomeActivity

/** 1. 홈 */
class MainActivity : BaseActivity(R.layout.activity_main) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        open(R.id.cardConcept, ConceptListActivity::class.java)
        open(R.id.cardInterview, InterviewHomeActivity::class.java)
        open(R.id.cardQuiz, QuizActivity::class.java)
        open(R.id.btnQuiz, QuizActivity::class.java)
        open(R.id.cardWrong, WrongNoteActivity::class.java)
        findViewById<View>(R.id.cardReview).setOnClickListener {
            startActivity(Intent(this, ConceptListActivity::class.java).putExtra(ConceptListActivity.EXTRA_DUE_ONLY, true))
        }
        open(R.id.cardHistory, HistoryActivity::class.java)
    }

    /** "면접 히스토리" 카드를 저장된 기록으로 채운다. */
    override fun onResume() {
        super.onResume()
        refreshServerStatus(R.id.tvServerStatus)
        renderLearning()
        lifecycleScope.launch {
            val dao = AppDatabase.get(this@MainActivity).interviewRecords()
            val all = dao.all()
            val latest = all.firstOrNull()
            findViewById<TextView>(R.id.tvHistoryCount).text = "총 ${all.size}회 ›"
            findViewById<TextView>(R.id.tvHistoryScore).text = latest?.let { "${it.overallScore}점" } ?: "-"
            findViewById<TextView>(R.id.tvHistoryWhen).text =
                latest?.let { "최근 · " + daysAgo(it.startedAt) } ?: "아직 기록이 없어요"
            // 점수 추이 그림은 아직 실제 점수로 그리지 않으므로 숨긴다.
            findViewById<View>(R.id.ivHistoryTrend).visibility = View.GONE
        }
    }

    private fun daysAgo(time: Long): String {
        val days = ((System.currentTimeMillis() - time) / 86_400_000L).toInt()
        return if (days <= 0) "오늘" else "${days}일 전"
    }

    /** 학습 통계(개념 수·평균 숙련도, 오늘의 퀴즈, 오답, 오늘 복습)를 저장된 진도로 채운다. 퀴즈를 풀고 돌아오면 바로 갱신된다. */
    private fun renderLearning() {
        lifecycleScope.launch {
            val data = ConceptRepository.load(this@MainActivity)
            val stats = LearningRepository(this@MainActivity, data).stats()

            findViewById<TextView>(R.id.tvConceptInfo).text =
                if (stats.studiedCount == 0) "${data.all.size}개 개념 · 아직 시작 전"
                else "${data.all.size}개 개념 · 평균 Lv.${"%.1f".format(stats.averageLevel)}"

            val status = findViewById<TextView>(R.id.tvQuizStatus)
            val dots = findViewById<TextView>(R.id.tvQuizDots)
            val button = findViewById<MaterialButton>(R.id.btnQuiz)
            val total = LearningRules.DAILY_COUNT
            if (stats.dailyCompleted) {
                status.text = "오늘 완료 · ${stats.dailyCorrect}/$total 정답"
                status.setBackgroundResource(R.drawable.bg_chip_ok)
                status.setTextColor(getColor(R.color.ok))
                dots.text = "●".repeat(total).chunked(1).joinToString(" ") + "   내일 새 퀴즈가 열려요"
                button.text = "결과 보기"
            } else {
                status.text = if (stats.dailyAnswered > 0) "${stats.dailyAnswered}/$total 푸는 중" else "아직 안 풀었어요"
                status.setBackgroundResource(R.drawable.bg_chip_bad)
                status.setTextColor(getColor(R.color.bad))
                dots.text = (1..total).joinToString(" ") { if (it <= stats.dailyAnswered) "●" else "○" } + "   ${total}문제 · 약 2분"
                button.text = if (stats.dailyAnswered > 0) "이어 풀기" else "풀기"
            }

            findViewById<TextView>(R.id.tvWrongCount).text = "${stats.wrongUnresolved}개"
            findViewById<TextView>(R.id.tvDueCount).text = "${stats.dueCount}개념"
        }
    }
}
