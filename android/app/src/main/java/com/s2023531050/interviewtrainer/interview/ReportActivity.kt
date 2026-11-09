package com.s2023531050.interviewtrainer.interview

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.tabs.TabLayout
import com.s2023531050.interviewtrainer.BaseActivity
import com.s2023531050.interviewtrainer.R
import com.s2023531050.interviewtrainer.concept.ConceptDetailActivity
import com.s2023531050.interviewtrainer.db.AppDatabase
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 9. AI 모의 면접 - 결과 리포트. 탭(종합 / 질문별 / 음성)에 따라 보이는 영역만 바꾼다.
 * 저장된 기록의 id([EXTRA_RECORD_ID])를 받으면 DB 에서 리포트를 읽어 보여 준다.
 * 아무것도 받지 못하면 예시 화면 그대로다.
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

        val recordId = intent.getLongExtra(EXTRA_RECORD_ID, -1L)
        if (recordId >= 0) {
            // 저장된 기록(방금 끝난 면접, 히스토리에서 연 면접)
            lifecycleScope.launch {
                val record = AppDatabase.get(this@ReportActivity).interviewRecords().get(recordId)
                if (record == null) {
                    Toast.makeText(this@ReportActivity, "기록을 찾을 수 없어요.", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    // 제목이 "혼합 면접" 같은 기본값이면 종류가 meta 에 이미 있으니 meta 만 보여 준다.
                    val subtitle = if (record.title.endsWith(" 면접")) record.meta() else "${record.title} · ${record.meta()}"
                    bind(JSONObject(record.reportJson), subtitle, tabs)
                }
            }
        } else {
            intent.getStringExtra(EXTRA_REPORT)?.let { json ->
                val label = intent.getStringExtra(EXTRA_MODE_LABEL).orEmpty()
                val date = SimpleDateFormat("MM.dd", Locale.KOREA).format(Date())
                bind(JSONObject(json), "$date · $label · 텍스트", tabs)
            }
        }

        open(R.id.rowConcept1, ConceptDetailActivity::class.java)
        open(R.id.rowConcept2, ConceptDetailActivity::class.java)
        open(R.id.btnHistory, HistoryActivity::class.java)
        openClearTop(R.id.btnAgain, InterviewHomeActivity::class.java)
    }

    /** 서버 리포트를 화면에 채운다. 아직 데이터가 없는 영역(음성, 낮은 개념)은 숨긴다. */
    private fun bind(report: JSONObject, subtitle: String, tabs: TabLayout) {
        val score = report.getInt("overallScore")

        findViewById<MaterialToolbar>(R.id.toolbar).subtitle = subtitle

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
        /** 저장된 기록의 id. 있으면 DB 에서 읽어 보여 준다. */
        const val EXTRA_RECORD_ID = "recordId"
        /** 저장에 실패했을 때만 쓰는 대비책: 리포트 JSON 을 그대로 받는다. */
        const val EXTRA_REPORT = "report"
        const val EXTRA_MODE_LABEL = "modeLabel"
    }
}
