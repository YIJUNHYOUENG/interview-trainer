package com.s2023531050.interviewtrainer

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.s2023531050.interviewtrainer.net.ApiClient
import com.s2023531050.interviewtrainer.net.ServerConfig
import kotlinx.coroutines.launch

/**
 * 모든 화면의 공통 부모.
 * - 시스템 바(상태바·내비게이션 바·키보드)에 화면이 가려지지 않게 여백을 준다.
 * - 레이아웃에 `toolbar` 가 있으면 왼쪽 버튼을 누를 때 화면을 닫는다.
 * - 버튼을 눌렀을 때 다른 화면으로 가는 코드를 한 줄로 줄여 준다.
 */
abstract class BaseActivity(@LayoutRes layoutRes: Int) : AppCompatActivity(layoutRes) {

    private var pendingNetwork: (() -> Unit)? = null

    // Android 17(API 37)부터는 PC 서버 같은 로컬 네트워크에 붙으려면 사용자 허용이 필요하다.
    private val networkPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        pendingNetwork?.let {
            pendingNetwork = null
            it()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ApiClient.baseUrl = ServerConfig.load(this) // 저장된 서버 주소를 쓴다

        val content = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(content) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
            )
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        findViewById<MaterialToolbar>(R.id.toolbar)?.setNavigationOnClickListener { finish() }
    }

    /** 로컬 네트워크 권한이 있으면 바로, 없으면 받은 뒤(거절해도) [action] 을 실행한다. */
    protected fun whenNetworkReady(action: () -> Unit) {
        val permission = "android.permission.ACCESS_LOCAL_NETWORK"
        val needed = Build.VERSION.SDK_INT >= 37 &&
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        if (needed) {
            pendingNetwork = action
            networkPermission.launch(permission)
        } else {
            action()
        }
    }

    /**
     * 화면 위쪽의 "● 서버 연결됨" 표시([labelId])를 실제 서버 상태로 채운다.
     * 누르면 서버 주소를 바꾸는 대화상자가 열린다(실제 폰은 PC 의 IP 로 바꿔야 한다).
     */
    protected fun refreshServerStatus(@IdRes labelId: Int) {
        val label = findViewById<TextView>(labelId)
        label.setOnClickListener { showServerDialog(labelId) }
        label.text = "● 서버 확인 중…"
        label.setTextColor(getColor(R.color.mut))
        whenNetworkReady {
            lifecycleScope.launch {
                val health = ApiClient.health()
                if (health == null) {
                    label.text = "● 서버 연결 안 됨"
                    label.setTextColor(getColor(R.color.bad))
                } else {
                    val claude = health.optString("claude")
                    when {
                        claude == "fake" -> label.text = "● 서버 연결됨 (가짜 모드)"
                        claude == "missing" -> label.text = "● 서버 연결됨 (claude 없음)"
                        else -> label.text = "● 서버 연결됨"
                    }
                    label.setTextColor(getColor(if (claude == "missing") R.color.warn else R.color.ok))
                }
            }
        }
    }

    private fun showServerDialog(@IdRes labelId: Int) {
        val input = EditText(this).apply {
            setText(ServerConfig.display(ApiClient.baseUrl))
            hint = "예) 192.168.0.12:8787"
            inputType = InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine()
            selectAll() // 열자마자 전체 선택: 바로 새 주소를 입력하면 기존 주소가 바뀐다
            setSelectAllOnFocus(true)
        }
        val pad = (20 * resources.displayMetrics.density).toInt()
        val box = FrameLayout(this).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }
        AlertDialog.Builder(this)
            .setTitle("PC 서버 주소")
            .setMessage("에뮬레이터는 10.0.2.2:8787, 실제 폰은 같은 와이파이에 있는 PC 의 IP:8787 을 입력하세요.")
            .setView(box)
            .setPositiveButton("저장") { _, _ ->
                val url = ServerConfig.normalize(input.text.toString())
                if (url == null) {
                    Toast.makeText(this, "주소 형식이 맞지 않아요. 예) 192.168.0.12:8787", Toast.LENGTH_LONG).show()
                } else {
                    ServerConfig.save(this, url)
                    refreshServerStatus(labelId)
                }
            }
            .setNeutralButton("에뮬레이터용") { _, _ ->
                ServerConfig.reset(this)
                refreshServerStatus(labelId)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    /** [viewId] 를 누르면 [target] 화면을 연다. */
    protected fun open(@IdRes viewId: Int, target: Class<out Activity>) {
        findViewById<View>(viewId).setOnClickListener {
            startActivity(Intent(this, target))
        }
    }

    /** [viewId] 를 누르면 [target] 화면을 열고 지금 화면은 닫는다(뒤로가기로 돌아오지 않게). */
    protected fun openAndFinish(@IdRes viewId: Int, target: Class<out Activity>) {
        findViewById<View>(viewId).setOnClickListener {
            startActivity(Intent(this, target))
            finish()
        }
    }

    /** [viewId] 를 누르면 쌓여 있던 화면을 걷어내고 [target] 화면으로 돌아간다. */
    protected fun openClearTop(@IdRes viewId: Int, target: Class<out Activity>) {
        findViewById<View>(viewId).setOnClickListener {
            startActivity(
                Intent(this, target).addFlags(
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            )
        }
    }
}
