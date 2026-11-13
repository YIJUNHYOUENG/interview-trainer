package com.s2023531050.interviewtrainer

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.s2023531050.interviewtrainer.db.AppDatabase
import kotlinx.coroutines.launch
import com.s2023531050.interviewtrainer.concept.ConceptListActivity
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
        open(R.id.cardReview, ConceptListActivity::class.java) // "오늘 복습" 칩이 선택된 목록으로 가야 하지만 지금은 목록만 연다
        open(R.id.cardHistory, HistoryActivity::class.java)
    }

    /** "면접 히스토리" 카드를 저장된 기록으로 채운다. */
    override fun onResume() {
        super.onResume()
        refreshServerStatus(R.id.tvServerStatus)
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
}
