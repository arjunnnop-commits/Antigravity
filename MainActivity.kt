package com.fairy.agypc

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var env: Env
    private lateinit var panel: LinearLayout
    private lateinit var web: WebView
    private lateinit var logView: TextView
    private lateinit var scroll: ScrollView
    private lateinit var bar: ProgressBar
    private lateinit var urlEdit: EditText
    private lateinit var mainBtn: Button
    private lateinit var agBtn: Button
    private lateinit var stopBtn: Button
    private lateinit var resetBtn: Button
    private val ui = Handler(Looper.getMainLooper())
    private val lines = ArrayDeque<String>()
    private var renderPending = false
    @Volatile private var busy = false

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun btn(t: String, onClick: () -> Unit) = Button(this).apply {
        text = t; isAllCaps = false; setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(6) }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        env = Env(this)

        panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(24), dp(16), dp(16)) }
        panel.addView(TextView(this).apply { text = "Antigravity PC"; textSize = 24f; setTypeface(null, Typeface.BOLD) })
        panel.addView(TextView(this).apply { text = "Phone par poora Linux desktop + Google Antigravity"; textSize = 13f })
        bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        panel.addView(bar)
        urlEdit = EditText(this).apply { hint = "Antigravity Linux ARM64 .tar.gz URL (optional)"; setSingleLine(true); textSize = 12f }
        panel.addView(urlEdit)
        mainBtn = btn("") { onMain() }; panel.addView(mainBtn)
        agBtn = btn("Antigravity install/update") { onAg() }; panel.addView(agBtn)
        stopBtn = btn("Desktop band karo") { stopService(Intent(this, DesktopService::class.java)); log("Desktop band kiya."); refresh() }
        panel.addView(stopBtn)
        resetBtn = btn("Sab kuch delete (reset)") { if (!busy && !DesktopService.isRunning()) { env.reset(); log("Reset ho gaya."); refresh() } }
        panel.addView(resetBtn)
        logView = TextView(this).apply { textSize = 11f; typeface = Typeface.MONOSPACE; setTextIsSelectable(true) }
        scroll = ScrollView(this).apply {
            addView(logView)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(8) }
        }
        panel.addView(scroll)

        web = WebView(this).apply {
            visibility = View.GONE; setBackgroundColor(Color.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            webViewClient = WebViewClient(); webChromeClient = WebChromeClient()
            isFocusableInTouchMode = true
        }
        setContentView(FrameLayout(this).apply { addView(panel); addView(web) })
        refresh()
    }

    private fun log(s: String) {
        ui.post {
            lines.addLast(s); while (lines.size > 400) lines.removeFirst()
            if (!renderPending) {
                renderPending = true
                ui.postDelayed({
                    renderPending = false
                    logView.text = lines.joinToString("\n")
                    scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
                }, 250)
            }
        }
    }

    private fun refresh() {
        val running = DesktopService.isRunning()
        mainBtn.isEnabled = !busy
        mainBtn.text = when {
            !env.installed -> "Setup shuru karo"
            running -> "Desktop kholo"
            else -> "Desktop start karo"
        }
        urlEdit.visibility = View.VISIBLE
        agBtn.visibility = if (env.installed) View.VISIBLE else View.GONE
        agBtn.isEnabled = !busy
        stopBtn.visibility = if (running) View.VISIBLE else View.GONE
        resetBtn.visibility = if (env.installed && !running) View.VISIBLE else View.GONE
        if (!busy) bar.progress = 0
    }

    private fun onMain() {
        if (busy) return
        when {
            !env.installed -> runBg { env.setup(urlEdit.text.toString().trim(), ::log) { p -> ui.post { bar.progress = p } }; log("Setup complete! Ab 'Desktop start karo' dabao.") }
            DesktopService.isRunning() && portOpen() -> showDesktop()
            else -> startDesktop()
        }
    }

    private fun onAg() {
        if (busy) return
        runBg { env.installAntigravity(urlEdit.text.toString().trim(), ::log) { p -> ui.post { bar.progress = p } } }
    }

    private fun runBg(job: () -> Unit) {
        busy = true; refresh()
        thread {
            try { job() } catch (e: Throwable) { log("ERROR: ${e.message}") }
            busy = false; ui.post { refresh() }
        }
    }

    private fun startDesktop() {
        startForegroundService(Intent(this, DesktopService::class.java))
        log("Desktop start ho raha hai (pehli baar 30-60 sec lag sakte hain)...")
        busy = true; refresh()
        thread {
            var ok = false
            for (i in 0 until 180) {
                DesktopService.drain().forEach { log(it) }
                if (portOpen()) { ok = true; break }
                if (!DesktopService.isRunning() && i > 5) break
                Thread.sleep(1000)
            }
            busy = false
            ui.post { refresh(); if (ok) showDesktop() else log("Desktop start nahi hua, upar ke logs dekho.") }
        }
    }

    private fun portOpen() = try {
        Socket().use { it.connect(InetSocketAddress("127.0.0.1", Cfg.PORT), 500); true }
    } catch (e: Exception) { false }

    private fun showDesktop() {
        web.loadUrl("http://127.0.0.1:${Cfg.PORT}/vnc.html?autoconnect=true&resize=remote&reconnect=true&password=${Cfg.VNC_PASS}")
        panel.visibility = View.GONE; web.visibility = View.VISIBLE; web.requestFocus()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (web.visibility == View.VISIBLE) { web.visibility = View.GONE; panel.visibility = View.VISIBLE; refresh() }
        else super.onBackPressed()
    }
}
