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
        // 음성 탭: 음성으로 답한 면접일 때만 보여 준다.
        val voice = report.optJSONObject("voice")
        if (voice != null) {
            bindVoice(voice)
        } else {
            tabs.removeTabAt(2)
            findViewById<View>(R.id.sectionVoice).visibility = View.GONE
        }

        val box = findViewById<LinearLayout>(R.id.sectionQuestions)
        box.removeAllViews()
        val questions = report.getJSONArray("questions")
        for (i in 0 until questions.length()) {
            box.addView(questionCard(box, i + 1, questions.getJSONObject(i)))
        }
    }

    // ---------- 음성 탭 ----------

    private enum class Level(val bg: Int, val color: Int) {
        OK(R.drawable.bg_chip_ok, R.color.ok),
        WARN(R.drawable.bg_chip_warn, R.color.warn),
        BAD(R.drawable.bg_chip_bad, R.color.bad),
    }

    /** 서버가 계산한 지표를 기준값(docs/DESIGN.md 4.3)과 견줘 음성 탭에 채운다. 판정은 앱에서 한다. */
    private fun bindVoice(v: JSONObject) {
        // 목소리 (음량): 눈금은 -60dB(아주 작음) ~ 0dB(최대), 적당한 범위는 -39 ~ -18dB
        val volDb = if (v.isNull("volumeDb")) null else v.getDouble("volumeDb")
        val fading = v.optInt("fadingAnswers")
        if (volDb == null) {
            tag(R.id.tvVolTag, "측정 불가", Level.WARN)
            findViewById<TextView>(R.id.tvVolDesc).text = "목소리가 너무 작아 음량을 재지 못했어요."
        } else {
            setWeight(R.id.vVolMark, ((volDb + 60) / 60 * 100).coerceIn(2.0, 96.0))
            val (label, level) = when {
                volDb < -39 -> "작음" to Level.WARN
                volDb > -18 -> "큼" to Level.WARN
                else -> "좋음" to Level.OK
            }
            tag(R.id.tvVolTag, if (level == Level.OK && fading > 0) "보통" else label, if (fading > 0) Level.WARN else level)
            val size = when (label) {
                "작음" -> "평균 크기가 작아요."
                "큼" -> "평균 크기가 커요."
                else -> "평균 크기는 적당해요."
            }
            findViewById<TextView>(R.id.tvVolDesc).text =
                if (fading > 0) "$size 끝으로 갈수록 작아진 답변이 ${fading}개 있어요." else size
        }

        // 발성: 말 속도(분당 음절 250~350), 멈춤(답변당 3회 이하), 군말(분당 4회 이하)
        val spm = v.getInt("spm")
        val pauses = v.getDouble("pausesPerAnswer")
        val fillers = v.getDouble("fillersPerMin")
        findViewById<TextView>(R.id.tvSpeedBig).text = "$spm 음절/분"
        findViewById<TextView>(R.id.tvSpeedDetail).text =
            "멈춤 답변당 ${trim(pauses)}회 (기준 3회 이하)\n군말 분당 ${trim(fillers)}회 (기준 4회 이하)"
        setWeight(R.id.vSpeedMark, (spm / 500.0 * 100).coerceIn(2.0, 96.0))
        val rateOk = spm in 250..350
        val habitsOk = pauses <= 3 && fillers <= 4
        when {
            spm < 250 -> tag(R.id.tvSpeedTag, "느림", Level.WARN)
            spm > 350 -> tag(R.id.tvSpeedTag, "빠름", Level.WARN)
            rateOk && habitsOk -> tag(R.id.tvSpeedTag, "좋음", Level.OK)
            else -> tag(R.id.tvSpeedTag, "보통", Level.WARN)
        }

        // 발음: 단어 인식 신뢰도 평균 (참고값)
        val clarity = v.getDouble("clarity")
        tag(R.id.tvClarityTag, "%.2f".format(clarity), if (clarity >= 0.8) Level.OK else Level.WARN)
        val low = v.optJSONArray("lowWords")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
        findViewById<TextView>(R.id.tvClarityDesc).text =
            if (low.isEmpty()) "참고값이에요. 알아듣기 어려웠던 단어는 없었어요."
            else "참고값이에요. 인식이 낮았던 단어: " + low.joinToString(", ") { "“$it”" }

        // 문맥: AI 가 글자로 바뀐 답변을 보고 쓴 평가
        val comment = v.optString("comment")
        findViewById<View>(R.id.tvContextTag).visibility = View.GONE
        findViewById<TextView>(R.id.tvContextDesc).text =
            comment.ifBlank { "문장 평가를 받지 못했어요." }

        // 답변별 말 속도 막대
        val bars = findViewById<LinearLayout>(R.id.llSpeedBars)
        bars.removeAllViews()
        val list = v.optJSONArray("spmList")
        val unit = resources.displayMetrics.density
        if (list == null || list.length() == 0) {
            bars.visibility = View.GONE
            findViewById<View>(R.id.tvBarsTitle).visibility = View.GONE
        } else {
            for (i in 0 until list.length()) {
                val h = (list.getInt(i) / 500.0 * 60).coerceIn(6.0, 60.0)
                val bar = View(this).apply {
                    setBackgroundResource(R.drawable.bg_bar)
                    layoutParams = LinearLayout.LayoutParams(0, (h * unit).toInt(), 1f).apply {
                        if (i < list.length() - 1) marginEnd = (6 * unit).toInt()
                    }
                }
                bars.addView(bar)
            }
        }
    }

    private fun tag(id: Int, text: String, level: Level) {
        val view = findViewById<TextView>(id)
        val (l, t, r, b) = listOf(view.paddingLeft, view.paddingTop, view.paddingRight, view.paddingBottom)
        view.text = text
        view.setBackgroundResource(level.bg)
        view.setTextColor(getColor(level.color))
        view.setPadding(l, t, r, b)
    }

    private fun setWeight(id: Int, weight: Double) {
        val view = findViewById<View>(id)
        val lp = view.layoutParams as LinearLayout.LayoutParams
        lp.weight = weight.toFloat()
        view.layoutParams = lp
    }

    private fun trim(x: Double) = if (x == x.toInt().toDouble()) x.toInt().toString() else "%.1f".format(x)

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
