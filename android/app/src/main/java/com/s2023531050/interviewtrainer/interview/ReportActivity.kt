package com.s2023531050.interviewtrainer.interview

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.tabs.TabLayout
import com.s2023531050.interviewtrainer.BaseActivity
import com.s2023531050.interviewtrainer.R
import com.s2023531050.interviewtrainer.concept.ConceptDetailActivity
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 9. AI 모의 면접 - 결과 리포트. 탭(종합 / 질문별 / 음성)에 따라 보이는 영역만 바꾼다.
 * 면접 직후에는 서버가 만든 리포트(JSON)를 [EXTRA_REPORT] 로 받아 보여 준다.
 * 지난 면접(히스토리)에서 열 때는 아직 저장 기능이 없어 예시 화면 그대로다.
 */
class ReportActivity : BaseActivity(R.layout.activity_report) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val tabs = findViewById<TabLayout>(R.id.tabs)
        val sections = listOf(R.id.sectionSummary, R.id.sectionQuestions, R.id.sectionVoice)
            .map { findViewById<View>(it) }

        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                sections.forEachIndexed { index, view ->
                    view.visibility = if (index == tab.position) View.VISIBLE else View.GONE
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        intent.getStringExtra(EXTRA_REPORT)?.let { json ->
            bind(JSONObject(json), intent.getStringExtra(EXTRA_MODE_LABEL).orEmpty(), tabs)
        }

        open(R.id.rowConcept1, ConceptDetailActivity::class.java)
        open(R.id.rowConcept2, ConceptDetailActivity::class.java)
        open(R.id.btnHistory, HistoryActivity::class.java)
        openClearTop(R.id.btnAgain, InterviewHomeActivity::class.java)
    }

    /** 서버 리포트를 화면에 채운다. 아직 데이터가 없는 영역(음성, 낮은 개념)은 숨긴다. */
    private fun bind(report: JSONObject, modeLabel: String, tabs: TabLayout) {
        val score = report.getInt("overallScore")

        val date = SimpleDateFormat("MM.dd", Locale.KOREA).format(Date())
        findViewById<MaterialToolbar>(R.id.toolbar).subtitle = "$date · $modeLabel · 텍스트"

        findViewById<CircularProgressIndicator>(R.id.progressScore).setProgressCompat(score, true)
        findViewById<TextView>(R.id.tvScore).text = score.toString()
        findViewById<TextView>(R.id.tvVerdict).text = when {
            score >= 80 -> "아주 좋아요"
            score >= 60 -> "전반적으로 안정적"
            score >= 40 -> "조금 더 연습해요"
            else -> "기초부터 다져요"
        }
        findViewById<TextView>(R.id.tvSummary).text = report.getString("summary")
        findViewById<TextView>(R.id.tvStrengths).text = bullets(report.getJSONArray("strengths"))
        findViewById<TextView>(R.id.tvImprovements).text = bullets(report.getJSONArray("improvements"))

        // 연결된 개념 추천과 음성 평가는 아직 만들지 않았다.
        listOf(R.id.tvLowTitle, R.id.rowConcept1, R.id.rowConcept2).forEach {
            findViewById<View>(it).visibility = View.GONE
        }
        tabs.removeTabAt(2)
        findViewById<View>(R.id.sectionVoice).visibility = View.GONE

        val box = findViewById<LinearLayout>(R.id.sectionQuestions)
        box.removeAllViews()
        val questions = report.getJSONArray("questions")
        for (i in 0 until questions.length()) {
            box.addView(questionCard(box, i + 1, questions.getJSONObject(i)))
        }
    }

    private fun questionCard(parent: LinearLayout, no: Int, q: JSONObject): View {
        val card = LayoutInflater.from(this).inflate(R.layout.item_report_question, parent, false)
        val score = q.getInt("score").coerceIn(0, 5)
        card.findViewById<TextView>(R.id.tvTopic).text = "질문 $no · ${q.getString("topic")}"
        card.findViewById<TextView>(R.id.tvStars).text = "★".repeat(score) + "☆".repeat(5 - score)
        card.findViewById<TextView>(R.id.tvQuestion).text = q.getString("question")
        card.findViewById<TextView>(R.id.tvAnswer).text = "내 답변: ${q.getString("answer")}"
        card.findViewById<TextView>(R.id.tvFeedback).text = q.getString("feedback")
        card.findViewById<TextView>(R.id.tvModel).text = q.getString("modelAnswer")

        val model = card.findViewById<View>(R.id.cardModel)
        val toggle = card.findViewById<TextView>(R.id.tvToggleModel)
        toggle.setOnClickListener {
            val show = model.visibility != View.VISIBLE
            model.visibility = if (show) View.VISIBLE else View.GONE
            toggle.text = if (show) "모범 답안 접기 ⌃" else "모범 답안 보기 ⌄"
        }
        return card
    }

    private fun bullets(array: JSONArray): String =
        (0 until array.length()).joinToString("\n") { "• ${array.getString(it)}" }

    companion object {
        const val EXTRA_REPORT = "report"
        const val EXTRA_MODE_LABEL = "modeLabel"
    }
}
