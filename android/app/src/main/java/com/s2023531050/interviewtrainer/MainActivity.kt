package com.s2023531050.interviewtrainer

import android.os.Bundle
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
}
