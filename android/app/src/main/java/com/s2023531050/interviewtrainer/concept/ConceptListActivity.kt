package com.s2023531050.interviewtrainer.concept

import android.content.Intent
import android.os.Bundle
import androidx.recyclerview.widget.RecyclerView
import com.s2023531050.interviewtrainer.BaseActivity
import com.s2023531050.interviewtrainer.R
import com.s2023531050.interviewtrainer.SampleData

/** 2. CS 개념 학습 - 목록 */
class ConceptListActivity : BaseActivity(R.layout.activity_concept_list) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        open(R.id.btnQuiz, QuizActivity::class.java)
        open(R.id.btnWrong, WrongNoteActivity::class.java)

        findViewById<RecyclerView>(R.id.rvConcepts).adapter = ConceptAdapter(SampleData.osConcepts) {
            startActivity(Intent(this, ConceptDetailActivity::class.java))
        }
    }
}
