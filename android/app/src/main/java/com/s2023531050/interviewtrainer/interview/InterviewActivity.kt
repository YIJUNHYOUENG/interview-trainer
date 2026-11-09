package com.s2023531050.interviewtrainer.interview

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.s2023531050.interviewtrainer.BaseActivity
import com.s2023531050.interviewtrainer.R
import com.s2023531050.interviewtrainer.net.ApiClient
import com.s2023531050.interviewtrainer.net.ApiException
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * 8. AI 모의 면접 - 진행 (텍스트 답변).
 * 시작 → 질문 표시 → 답변 전송 → 꼬리질문/다음 주제 → 끝나면 리포트.
 * 질문 수와 꼬리질문 횟수는 서버가 센다. 앱은 서버가 알려 준 대로 화면만 바꾼다.
 */
class InterviewActivity : BaseActivity(R.layout.activity_interview) {

    private lateinit var settings: InterviewSettings
    private lateinit var chat: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var etAnswer: EditText
    private lateinit var btnSend: MaterialButton
    private lateinit var btnEnd: MaterialButton
    private lateinit var tvProgress: TextView
    private lateinit var tvFollow: TextView

    private var sessionId: String? = null
    private var total = 0
    private var answered = 0
    private var busy = false
    private var thinking: View? = null

    // Android 17(API 37)부터는 PC 서버 같은 로컬 네트워크에 붙으려면 사용자 허용이 필요하다.
    private val localNetworkPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { startInterview() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        settings = InterviewSettings(this)
        chat = findViewById(R.id.chat)
        scroll = findViewById(R.id.scroll)
        etAnswer = findViewById(R.id.etAnswer)
        btnSend = findViewById(R.id.btnSend)
        btnEnd = findViewById(R.id.btnEnd)
        tvProgress = findViewById(R.id.tvProgress)
        tvFollow = findViewById(R.id.tvFollow)

        btnSend.setOnClickListener { sendAnswer() }
        btnEnd.setOnClickListener { confirmEnd() }

        if (savedInstanceState == null) requestPermissionThenStart()
    }

    private fun requestPermissionThenStart() {
        val permission = "android.permission.ACCESS_LOCAL_NETWORK"
        val needed = Build.VERSION.SDK_INT >= 37 &&
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        if (needed) localNetworkPermission.launch(permission) else startInterview()
    }

    // ---------- 서버 호출 ----------

    private fun startInterview() {
        val body = JSONObject()
            .put("mode", settings.type)
            .put("company", settings.company)
            .put("role", settings.role)
            .put("coverLetter", settings.resume)
            .put("questionCount", settings.questionCount)
            .put("maxFollowups", settings.maxFollowups)

        call(
            request = { ApiClient.post("/interview/start", body) },
            onError = { msg -> showError(msg, retry = ::startInterview, closeOnCancel = true) },
        ) { r ->
            sessionId = r.getString("sessionId")
            total = r.getInt("total")
            updateHeader(1, r.getString("topic"), 0)
            addAi(r.getString("question"))
            setInputEnabled(true)
        }
    }

    private fun sendAnswer() {
        val id = sessionId ?: return
        val text = etAnswer.text.toString().trim()
        if (text.isEmpty() || busy) return

        etAnswer.text.clear()
        addMine(text)
        answered++

        val body = JSONObject().put("sessionId", id).put("answer", text)
        call(
            request = { ApiClient.post("/interview/answer", body) },
            onError = { msg ->
                answered--
                etAnswer.setText(text) // 보낸 답변을 되살려서 다시 보낼 수 있게 한다
                showError(msg, retry = null, closeOnCancel = false)
            },
        ) { r ->
            addEval(r.getInt("score"), r.getString("comment"))
            val type = r.getString("type")
            if (type == "end") {
                addAi("면접이 끝났습니다. 결과 리포트를 만들고 있어요.")
                finishInterview()
            } else {
                updateHeader(r.getInt("topicNo"), r.getString("topic"), r.getInt("followups"))
                addAi(r.getString("question"))
                setInputEnabled(true)
            }
        }
    }

    private fun confirmEnd() {
        if (busy) return
        if (answered == 0) {
            Toast.makeText(this, "답변한 내용이 없어 리포트 없이 종료합니다.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("면접을 종료할까요?")
            .setMessage("지금까지 답변한 내용으로 리포트를 만듭니다.")
            .setPositiveButton("종료") { _, _ -> finishInterview() }
            .setNegativeButton("계속하기", null)
            .show()
    }

    private fun finishInterview() {
        val id = sessionId ?: return
        setInputEnabled(false)
        val body = JSONObject().put("sessionId", id)
        call(
            request = { ApiClient.post("/interview/finish", body) },
            onError = { msg -> showError(msg, retry = ::finishInterview, closeOnCancel = true) },
        ) { report ->
            startActivity(
                Intent(this, ReportActivity::class.java)
                    .putExtra(ReportActivity.EXTRA_REPORT, report.toString())
                    .putExtra(ReportActivity.EXTRA_MODE_LABEL, settings.typeLabel)
            )
            finish()
        }
    }

    /** 서버 호출 한 번. 기다리는 동안 "생각 중" 표시를 보여 주고, 성공/실패를 나눠 처리한다. */
    private fun call(
        request: suspend () -> JSONObject,
        onError: (String) -> Unit,
        onSuccess: (JSONObject) -> Unit,
    ) {
        setBusy(true)
        lifecycleScope.launch {
            try {
                val result = request()
                setBusy(false)
                onSuccess(result)
            } catch (e: ApiException) {
                setBusy(false)
                onError(e.message.orEmpty())
            } catch (e: org.json.JSONException) {
                setBusy(false)
                onError("서버 응답을 읽지 못했습니다.")
            }
        }
    }

    // ---------- 화면 ----------

    private fun setBusy(value: Boolean) {
        busy = value
        btnEnd.isEnabled = !value
        if (value) {
            setInputEnabled(false)
            thinking = addBubble(R.layout.item_bubble_ai, "면접관이 생각 중…")
        } else {
            thinking?.let { chat.removeView(it) }
            thinking = null
        }
    }

    private fun setInputEnabled(enabled: Boolean) {
        etAnswer.isEnabled = enabled
        btnSend.isEnabled = enabled
        if (enabled) etAnswer.requestFocus()
    }

    private fun updateHeader(topicNo: Int, topic: String, followups: Int) {
        tvProgress.text = "주제 $topicNo/$total"
        tvFollow.text = "· $topic · 꼬리질문 $followups/${settings.maxFollowups}"
    }

    private fun addAi(text: String) = addBubble(R.layout.item_bubble_ai, text)

    private fun addMine(text: String) = addBubble(R.layout.item_bubble_me, text)

    private fun addEval(score: Int, comment: String) {
        val stars = "★".repeat(score.coerceIn(0, 5)) + "☆".repeat(5 - score.coerceIn(0, 5))
        addBubble(R.layout.item_bubble_eval, "$stars  $comment")
    }

    private fun addBubble(layout: Int, text: String): View {
        val view = LayoutInflater.from(this).inflate(layout, chat, false) as TextView
        view.text = text
        chat.addView(view)
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        return view
    }

    private fun showError(message: String, retry: (() -> Unit)?, closeOnCancel: Boolean) {
        val builder = AlertDialog.Builder(this)
            .setTitle("문제가 생겼어요")
            .setMessage(message)
            .setCancelable(false)
        if (retry != null) builder.setPositiveButton("다시 시도") { _, _ -> retry() }
        builder.setNegativeButton(if (closeOnCancel) "닫기" else "확인") { _, _ ->
            if (closeOnCancel) finish() else setInputEnabled(true)
        }
        builder.show()
    }
}
