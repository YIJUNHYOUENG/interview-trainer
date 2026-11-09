package com.s2023531050.interviewtrainer.interview

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.s2023531050.interviewtrainer.BaseActivity
import com.s2023531050.interviewtrainer.R
import com.s2023531050.interviewtrainer.db.AppDatabase
import kotlinx.coroutines.launch

/** 7. AI 모의 면접 - 홈 */
class InterviewHomeActivity : BaseActivity(R.layout.activity_interview_home) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        open(R.id.cardInfo, InterviewSettingsActivity::class.java)
        open(R.id.btnStart, InterviewActivity::class.java)
        open(R.id.rowSettings, InterviewSettingsActivity::class.java)
        open(R.id.rowHistory, HistoryActivity::class.java)
    }

    /** 가장 최근 면접을 "최근 리포트" 카드에 보여 준다. 기록이 없으면 카드를 숨긴다. */
    override fun onResume() {
        super.onResume()
        val card = findViewById<View>(R.id.cardRecent)
        val title = findViewById<View>(R.id.tvRecentTitle)
        lifecycleScope.launch {
            val dao = AppDatabase.get(this@InterviewHomeActivity).interviewRecords()
            findViewById<TextView>(R.id.tvHistoryCount).text = "${dao.count()}회 ›"
            val record = dao.latest()
            val visibility = if (record == null) View.GONE else View.VISIBLE
            card.visibility = visibility
            title.visibility = visibility
            if (record == null) return@launch

            findViewById<TextView>(R.id.tvRecentScore).text = record.overallScore.toString()
            findViewById<TextView>(R.id.tvRecentTitle2).text = record.title
            findViewById<TextView>(R.id.tvRecentMeta).text = record.meta()
            card.setOnClickListener {
                startActivity(
                    Intent(this@InterviewHomeActivity, ReportActivity::class.java)
                        .putExtra(ReportActivity.EXTRA_RECORD_ID, record.id)
                )
            }
        }
    }
}
