package com.s2023531050.interviewtrainer.interview

import android.os.Bundle
import com.s2023531050.interviewtrainer.BaseActivity
import com.s2023531050.interviewtrainer.R

/** 7. AI 모의 면접 - 홈 */
class InterviewHomeActivity : BaseActivity(R.layout.activity_interview_home) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        open(R.id.cardInfo, InterviewSettingsActivity::class.java)
        open(R.id.btnStart, InterviewActivity::class.java)
        open(R.id.rowSettings, InterviewSettingsActivity::class.java)
        open(R.id.rowHistory, HistoryActivity::class.java)
        open(R.id.cardRecent, ReportActivity::class.java)
    }
}
