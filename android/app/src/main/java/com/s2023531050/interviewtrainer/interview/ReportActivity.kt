package com.s2023531050.interviewtrainer.interview

import android.os.Bundle
import android.view.View
import com.google.android.material.tabs.TabLayout
import com.s2023531050.interviewtrainer.BaseActivity
import com.s2023531050.interviewtrainer.R
import com.s2023531050.interviewtrainer.concept.ConceptDetailActivity

/** 9. AI 모의 면접 - 결과 리포트. 탭(종합 / 질문별 / 음성)에 따라 보이는 영역만 바꾼다. */
class ReportActivity : BaseActivity(R.layout.activity_report) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sections = listOf(R.id.sectionSummary, R.id.sectionQuestions, R.id.sectionVoice)
            .map { findViewById<View>(it) }

        findViewById<TabLayout>(R.id.tabs).addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                sections.forEachIndexed { index, view ->
                    view.visibility = if (index == tab.position) View.VISIBLE else View.GONE
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        open(R.id.rowConcept1, ConceptDetailActivity::class.java)
        open(R.id.rowConcept2, ConceptDetailActivity::class.java)
        open(R.id.btnHistory, HistoryActivity::class.java)
        openClearTop(R.id.btnAgain, InterviewHomeActivity::class.java)
    }
}
