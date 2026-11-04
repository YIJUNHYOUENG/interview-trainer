package com.s2023531050.interviewtrainer

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.MaterialToolbar

/**
 * 모든 화면의 공통 부모.
 * - 시스템 바(상태바·내비게이션 바·키보드)에 화면이 가려지지 않게 여백을 준다.
 * - 레이아웃에 `toolbar` 가 있으면 왼쪽 버튼을 누를 때 화면을 닫는다.
 * - 버튼을 눌렀을 때 다른 화면으로 가는 코드를 한 줄로 줄여 준다.
 */
abstract class BaseActivity(@LayoutRes layoutRes: Int) : AppCompatActivity(layoutRes) {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

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
