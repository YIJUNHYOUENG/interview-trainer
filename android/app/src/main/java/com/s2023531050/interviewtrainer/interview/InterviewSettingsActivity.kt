package com.s2023531050.interviewtrainer.interview

import android.os.Bundle
import android.view.View
import android.widget.TextView
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputEditText
import com.s2023531050.interviewtrainer.BaseActivity
import com.s2023531050.interviewtrainer.R

/** 6. AI 모의 면접 - 설정. 저장하면 다음 면접부터 이 값으로 시작한다. */
class InterviewSettingsActivity : BaseActivity(R.layout.activity_interview_settings) {
    private var count = 5
    private var followups = 2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val settings = InterviewSettings(this)
        val etCompany = findViewById<TextInputEditText>(R.id.etCompany)
        val etRole = findViewById<TextInputEditText>(R.id.etRole)
        val etResume = findViewById<TextInputEditText>(R.id.etResume)
        val toggleType = findViewById<MaterialButtonToggleGroup>(R.id.toggleType)
        val toggleMode = findViewById<MaterialButtonToggleGroup>(R.id.toggleMode)
        val tvCount = findViewById<TextView>(R.id.tvCount)
        val tvFollow = findViewById<TextView>(R.id.tvFollow)

        // 저장된 값 불러오기
        etCompany.setText(settings.company)
        etRole.setText(settings.role)
        etResume.setText(settings.resume)
        toggleType.check(
            when (settings.type) {
                "cs" -> R.id.btnTypeCs
                "cover" -> R.id.btnTypeCover
                else -> R.id.btnTypeMixed
            }
        )
        toggleMode.check(if (settings.answerMode == "voice") R.id.btnModeVoice else R.id.btnModeText)
        count = settings.questionCount
        followups = settings.maxFollowups
        tvCount.text = count.toString()
        tvFollow.text = followups.toString()

        // 주제 수 1~10, 꼬리질문 0~3
        findViewById<View>(R.id.btnCountMinus).setOnClickListener {
            count = (count - 1).coerceAtLeast(1); tvCount.text = count.toString()
        }
        findViewById<View>(R.id.btnCountPlus).setOnClickListener {
            count = (count + 1).coerceAtMost(10); tvCount.text = count.toString()
        }
        findViewById<View>(R.id.btnFollowMinus).setOnClickListener {
            followups = (followups - 1).coerceAtLeast(0); tvFollow.text = followups.toString()
        }
        findViewById<View>(R.id.btnFollowPlus).setOnClickListener {
            followups = (followups + 1).coerceAtMost(3); tvFollow.text = followups.toString()
        }

        findViewById<View>(R.id.btnSave).setOnClickListener {
            settings.company = etCompany.text.toString().trim()
            settings.role = etRole.text.toString().trim()
            settings.resume = etResume.text.toString().trim()
            settings.type = when (toggleType.checkedButtonId) {
                R.id.btnTypeCs -> "cs"
                R.id.btnTypeCover -> "cover"
                else -> "mixed"
            }
            settings.answerMode = if (toggleMode.checkedButtonId == R.id.btnModeVoice) "voice" else "text"
            settings.questionCount = count
            settings.maxFollowups = followups
            finish()
        }
    }
}
