package com.s2023531050.interviewtrainer.interview

import android.content.Intent
import android.os.Bundle
import androidx.recyclerview.widget.RecyclerView
import com.s2023531050.interviewtrainer.BaseActivity
import com.s2023531050.interviewtrainer.R
import com.s2023531050.interviewtrainer.SampleData

/** 10. AI 모의 면접 - 히스토리 */
class HistoryActivity : BaseActivity(R.layout.activity_history) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        findViewById<RecyclerView>(R.id.rvHistory).adapter = HistoryAdapter(SampleData.history) {
            startActivity(Intent(this, ReportActivity::class.java))
        }
    }
}
