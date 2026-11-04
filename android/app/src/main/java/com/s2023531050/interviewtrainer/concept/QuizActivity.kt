package com.s2023531050.interviewtrainer.concept

import android.os.Bundle
import com.s2023531050.interviewtrainer.BaseActivity
import com.s2023531050.interviewtrainer.R

/** 4. CS 개념 학습 - 퀴즈 풀이. 문제 진행이 없으므로 "다음 문제"를 누르면 바로 결과 화면으로 간다. */
class QuizActivity : BaseActivity(R.layout.activity_quiz) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        openAndFinish(R.id.btnNext, QuizResultActivity::class.java)
    }
}
