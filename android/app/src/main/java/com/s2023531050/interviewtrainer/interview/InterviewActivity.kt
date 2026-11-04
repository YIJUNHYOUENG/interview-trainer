package com.s2023531050.interviewtrainer.interview

import android.os.Bundle
import com.s2023531050.interviewtrainer.BaseActivity
import com.s2023531050.interviewtrainer.R

/** 8. AI 모의 면접 - 진행. 대화 진행이 없으므로 "종료"를 누르면 바로 리포트로 간다. */
class InterviewActivity : BaseActivity(R.layout.activity_interview) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        openAndFinish(R.id.btnEnd, ReportActivity::class.java)
    }
}
