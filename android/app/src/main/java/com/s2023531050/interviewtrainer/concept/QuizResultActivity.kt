package com.s2023531050.interviewtrainer.concept

import android.os.Bundle
import com.s2023531050.interviewtrainer.BaseActivity
import com.s2023531050.interviewtrainer.MainActivity
import com.s2023531050.interviewtrainer.R

/** 4. CS 개념 학습 - 퀴즈 결과 */
class QuizResultActivity : BaseActivity(R.layout.activity_quiz_result) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        open(R.id.btnWrong, WrongNoteActivity::class.java)
        openClearTop(R.id.btnHome, MainActivity::class.java)
    }
}
