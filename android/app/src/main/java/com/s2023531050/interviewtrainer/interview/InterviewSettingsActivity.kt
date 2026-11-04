package com.s2023531050.interviewtrainer.interview

import android.os.Bundle
import android.view.View
import com.google.android.material.button.MaterialButtonToggleGroup
import com.s2023531050.interviewtrainer.BaseActivity
import com.s2023531050.interviewtrainer.R

/** 6. AI 모의 면접 - 설정. 저장 기능은 아직 없고, 저장을 누르면 면접 홈으로 돌아간다. */
class InterviewSettingsActivity : BaseActivity(R.layout.activity_interview_settings) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        findViewById<MaterialButtonToggleGroup>(R.id.toggleType).check(R.id.btnTypeMixed)
        findViewById<MaterialButtonToggleGroup>(R.id.toggleMode).check(R.id.btnModeVoice)

        findViewById<View>(R.id.btnSave).setOnClickListener { finish() }
    }
}
