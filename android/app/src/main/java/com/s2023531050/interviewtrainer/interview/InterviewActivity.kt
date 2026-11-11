package com.s2023531050.interviewtrainer.interview

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
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
import com.s2023531050.interviewtrainer.db.AppDatabase
import com.s2023531050.interviewtrainer.db.InterviewRecord
import com.s2023531050.interviewtrainer.net.ApiClient
import com.s2023531050.interviewtrainer.net.ApiException
import com.s2023531050.interviewtrainer.voice.QuestionReader
import com.s2023531050.interviewtrainer.voice.WavRecorder
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * 8. AI 모의 면접 - 진행 (텍스트 답변).
 * 시작 → 질문 표시 → 답변 전송 → 꼬리질문/다음 주제 → 끝나면 리포트.
 * 질문 수와 꼬리질문 횟수는 서버가 센다. 앱은 서버가 알려 준 대로 화면만 바꾼다.
 * 설정이 "음성"이면 질문을 소리로 읽어 주고, "누르고 말하기" 버튼으로 답한다(녹음은 서버가 글자로 바꾼다).
 * 음성 모드에서도 "글자로 답하기"로 키보드 입력을 쓸 수 있다.
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

    private lateinit var rowText: View
    private lateinit var rowVoice: View
    private lateinit var btnRecord: MaterialButton
    private lateinit var tvVoiceHint: TextView
    private lateinit var btnToVoice: View

    private val voiceMode by lazy { settings.answerMode == "voice" }
    private var usedVoice = false // 음성으로 한 번이라도 답했는지 (기록에 남긴다)
    private var reader: QuestionReader? = null
    private val recorder = WavRecorder()

    private var sessionId: String? = null
    private var total = 0
    private var answered = 0
    private var busy = false
    private var thinking: View? = null

    // Android 17(API 37)부터는 PC 서버 같은 로컬 네트워크에 붙으려면 사용자 허용이 필요하다.
    private val localNetworkPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { afterNetworkPermission() }

    private val micPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(this, "마이크 권한이 없어 글자로 답해야 해요.", Toast.LENGTH_LONG).show()
            showVoiceInput(false)
        }
        startInterview()
    }

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

        rowText = findViewById(R.id.rowText)
        rowVoice = findViewById(R.id.rowVoice)
        btnRecord = findViewById(R.id.btnRecord)
        tvVoiceHint = findViewById(R.id.tvVoiceHint)
        btnToVoice = findViewById(R.id.btnToVoice)

        btnSend.setOnClickListener { sendAnswer() }
        btnEnd.setOnClickListener { confirmEnd() }

        if (voiceMode) {
            reader = QuestionReader(this) {
                Toast.makeText(this, "질문을 읽어 줄 수 없어요. (한국어 음성 데이터가 없음) 글자로 확인해 주세요.", Toast.LENGTH_LONG).show()
            }
            showVoiceInput(true)
            setupRecordButton()
            btnToKeyboard().setOnClickListener { showVoiceInput(false) }
            btnToVoice.setOnClickListener { showVoiceInput(true) }
        }

        if (savedInstanceState == null) requestPermissionThenStart()
    }

    private fun requestPermissionThenStart() {
        val permission = "android.permission.ACCESS_LOCAL_NETWORK"
        val needed = Build.VERSION.SDK_INT >= 37 &&
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        if (needed) localNetworkPermission.launch(permission) else afterNetworkPermission()
    }

    /** 음성 모드면 마이크 권한도 받는다. 거절하면 글자 입력으로 진행한다. */
    private fun afterNetworkPermission() {
        val mic = android.Manifest.permission.RECORD_AUDIO
        if (voiceMode && ContextCompat.checkSelfPermission(this, mic) != PackageManager.PERMISSION_GRANTED) {
            micPermission.launch(mic)
        } else {
            startInterview()
        }
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
        // 분석이 끝난 GitHub 프로젝트가 있으면 서버가 그 내용으로 프로젝트 질문을 섞는다.
        if (settings.repoState == "done" && settings.repoJobId.isNotEmpty() && !intent.getBooleanExtra(EXTRA_SKIP_REPO, false)) {
            body.put("repoAnalysisId", settings.repoJobId)
        }

        call(
            request = { ApiClient.post("/interview/start", body) },
            onError = { msg -> showError(msg, retry = ::startInterview, closeOnCancel = true) },
        ) { r ->
            sessionId = r.getString("sessionId")
            total = r.getInt("total")
            updateHeader(1, r.getString("topic"), 0)
            addQuestion(r.getString("question"))
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
        ) { r -> onAnswerResult(r) }
    }

    /** 녹음(WAV)을 서버에 보낸다. 서버가 글자로 바꿔 알려 주면 내 답변 말풍선으로 보여 준다. */
    private fun sendVoice(wav: ByteArray) {
        val id = sessionId ?: return
        if (busy) return
        call(
            request = { ApiClient.postBytes("/voice/answer?sessionId=$id", wav, "audio/wav") },
            onError = { msg ->
                AlertDialog.Builder(this)
                    .setTitle("문제가 생겼어요")
                    .setMessage(msg)
                    .setCancelable(false)
                    .setPositiveButton("다시 말하기") { _, _ -> setInputEnabled(true) }
                    .setNeutralButton("같은 녹음 다시 보내기") { _, _ -> sendVoice(wav) }
                    .show()
            },
        ) { r ->
            usedVoice = true
            answered++
            addMine(r.getString("text"))
            onAnswerResult(r)
        }
    }

    /** /interview/answer 와 /voice/answer 가 같은 모양으로 돌려주는 결과를 화면에 반영한다. */
    private fun onAnswerResult(r: JSONObject) {
        addEval(r.getInt("score"), r.getString("comment"))
        val type = r.getString("type")
        if (type == "end") {
            addAi("면접이 끝났습니다. 결과 리포트를 만들고 있어요.")
            finishInterview()
        } else {
            updateHeader(r.getInt("topicNo"), r.getString("topic"), r.getInt("followups"))
            addQuestion(r.getString("question"))
            setInputEnabled(true)
        }
    }

    // ---------- 음성 입력 ----------

    private fun btnToKeyboard() = findViewById<View>(R.id.btnToKeyboard)

    /** true 면 "누르고 말하기", false 면 키보드 입력. 음성 모드가 아니면 늘 키보드다. */
    private fun showVoiceInput(voice: Boolean) {
        val useVoice = voice && voiceMode
        rowVoice.visibility = if (useVoice) View.VISIBLE else View.GONE
        rowText.visibility = if (useVoice) View.GONE else View.VISIBLE
        btnToVoice.visibility = if (!useVoice && voiceMode) View.VISIBLE else View.GONE
        if (!useVoice) reader?.stop()
    }

    private val voiceInputShown get() = rowVoice.visibility == View.VISIBLE

    @SuppressLint("ClickableViewAccessibility")
    private fun setupRecordButton() {
        btnRecord.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (!btnRecord.isEnabled) return@setOnTouchListener false
                    reader?.stop() // 질문 읽는 소리가 녹음에 섞이지 않게
                    if (!recorder.start()) {
                        Toast.makeText(this, "마이크를 사용할 수 없어요.", Toast.LENGTH_SHORT).show()
                    } else {
                        v.isPressed = true
                        btnRecord.text = "말하는 중… 끝나면 손을 떼세요"
                        tvVoiceHint.text = "녹음 중"
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    v.isPressed = false
                    btnRecord.text = "누르고 말하기"
                    if (recorder.isRecording) {
                        val wav = recorder.stop()
                        if (wav == null) {
                            Toast.makeText(this, "너무 짧아요. 버튼을 누른 채로 말해 주세요.", Toast.LENGTH_SHORT).show()
                            tvVoiceHint.text = VOICE_HINT
                        } else {
                            tvVoiceHint.text = ""
                            sendVoice(wav)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    btnRecord.text = "누르고 말하기"
                    recorder.cancel()
                    tvVoiceHint.text = VOICE_HINT
                    true
                }
                else -> true
            }
        }
    }

    /** 면접관 질문을 말풍선으로 보이고, 음성 모드에서 음성 입력 중이면 소리로도 읽어 준다. */
    private fun addQuestion(text: String) {
        addAi(text)
        if (voiceMode && voiceInputShown) reader?.speak(text)
    }

    override fun onStop() {
        super.onStop()
        if (recorder.isRecording) {
            recorder.cancel()
            btnRecord.text = "누르고 말하기"
        }
        reader?.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        recorder.cancel()
        reader?.shutdown()
    }

    private fun confirmEnd() {
        if (busy) return
        if (answered == 0) {
            Toast.makeText(this, "답변한 내용이 없어 리포트 없이 종료합니다.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        reader?.stop()
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
        ) { report -> saveAndShowReport(report) }
    }

    /** 리포트를 기기에 저장하고(히스토리용) 리포트 화면을 연다. 저장이 실패해도 방금 리포트는 보여 준다. */
    private fun saveAndShowReport(report: JSONObject) {
        lifecycleScope.launch {
            val intent = Intent(this@InterviewActivity, ReportActivity::class.java)
            try {
                val title = listOf(settings.company, settings.role).filter { it.isNotBlank() }
                    .joinToString(" / ").ifEmpty { "${settings.typeLabel} 면접" }
                val id = AppDatabase.get(this@InterviewActivity).interviewRecords().insert(
                    InterviewRecord(
                        title = title,
                        mode = settings.type,
                        answerMode = if (usedVoice) "voice" else "text",
                        startedAt = System.currentTimeMillis(),
                        overallScore = report.getInt("overallScore"),
                        reportJson = report.toString(),
                    )
                )
                intent.putExtra(ReportActivity.EXTRA_RECORD_ID, id)
            } catch (e: Exception) {
                Toast.makeText(this@InterviewActivity, "기록을 저장하지 못했어요.", Toast.LENGTH_SHORT).show()
                intent.putExtra(ReportActivity.EXTRA_REPORT, report.toString())
                    .putExtra(ReportActivity.EXTRA_MODE_LABEL, settings.typeLabel)
            }
            startActivity(intent)
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
        btnRecord.isEnabled = enabled
        if (voiceInputShown) tvVoiceHint.text = if (enabled) VOICE_HINT else ""
        if (enabled && !voiceInputShown) etAnswer.requestFocus()
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

    companion object {
        /** 면접 홈에서 "분석 없이 시작"을 고르면 true. GitHub 분석 결과를 이번 면접에만 쓰지 않는다. */
        const val EXTRA_SKIP_REPO = "skipRepo"
        private const val VOICE_HINT = "버튼을 누른 채로 말하고, 끝나면 손을 떼세요"
    }
}
