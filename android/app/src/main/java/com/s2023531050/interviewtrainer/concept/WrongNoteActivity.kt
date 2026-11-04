package com.s2023531050.interviewtrainer.concept

import android.content.Intent
import android.os.Bundle
import androidx.recyclerview.widget.RecyclerView
import com.s2023531050.interviewtrainer.BaseActivity
import com.s2023531050.interviewtrainer.R
import com.s2023531050.interviewtrainer.SampleData

/** 5. CS 개념 학습 - 오답노트 */
class WrongNoteActivity : BaseActivity(R.layout.activity_wrong_note) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        open(R.id.btnRetry, QuizActivity::class.java)

        findViewById<RecyclerView>(R.id.rvWrong).adapter = WrongAdapter(SampleData.wrongItems) {
            startActivity(Intent(this, ConceptDetailActivity::class.java))
        }
    }
}
