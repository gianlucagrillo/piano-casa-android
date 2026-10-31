package it.gianlucagrillo.pianocasa

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : Activity() {

    private lateinit var web: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()

        web = WebView(this)
        web.setBackgroundColor(0xFF04201A.toInt())
        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        web.isHapticFeedbackEnabled = false
        web.webViewClient = WebViewClient()
        web.webChromeClient = WebChromeClient()
        web.addJavascriptInterface(Haptics(this), "AndroidHaptics")
        setContentView(web)

        if (savedInstanceState != null && intent?.data == null) web.restoreState(savedInstanceState)
        else web.loadUrl(urlFor(intent))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.data != null) web.loadUrl(urlFor(intent))
    }

    /** pianocasa://connect#TOKEN passa il codice di accesso alla pagina. */
    private fun urlFor(i: Intent?): String {
        val d = i?.data
        val token = if (d?.scheme == "pianocasa") d.fragment else null
        return if (token.isNullOrEmpty()) URL else URL + "#connect=" + Uri.encode(token)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onResume() {
        super.onResume()
        // Ricarica il piano quando torni nell'app, così vedi sempre l'ultima versione e le ultime spunte.
        if (::web.isInitialized && web.url != null) web.evaluateJavascript("window.onAppResume && window.onAppResume()", null)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    private fun hideSystemBars() {
        val c = WindowInsetsControllerCompat(window, window.decorView)
        c.hide(WindowInsetsCompat.Type.systemBars())
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    /** Vibrazione con intensità regolabile, chiamata dalla pagina. */
    class Haptics(ctx: Context) {
        private val vib: Vibrator = if (Build.VERSION.SDK_INT >= 31)
            (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        else @Suppress("DEPRECATION") (ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)

        /** Vibrazione che sale da quasi zero al massimo in durationMs. */
        @JavascriptInterface
        fun ramp(durationMs: Int) {
            val steps = 20
            val step = (durationMs / steps).coerceAtLeast(10).toLong()
            val timings = LongArray(steps) { step }
            val amps = IntArray(steps) { i ->
                val p = (i + 1).toDouble() / steps
                (1 + 254 * p * p).toInt().coerceIn(1, 255)
            }
            if (vib.hasAmplitudeControl()) vib.vibrate(VibrationEffect.createWaveform(timings, amps, -1))
            else vib.vibrate(VibrationEffect.createWaveform(LongArray(steps * 2) { if (it % 2 == 0) step / 3 else step - step / 3 }, -1))
        }

        /** Colpo finale: level 1 = task, 2 = blocco, 3 = giornata. */
        @JavascriptInterface
        fun hit(level: Int) {
            val effect = when (level) {
                3 -> VibrationEffect.createWaveform(longArrayOf(0, 90, 60, 50, 50, 70), intArrayOf(0, 255, 0, 200, 0, 255), -1)
                2 -> VibrationEffect.createWaveform(longArrayOf(0, 80, 50, 50), intArrayOf(0, 255, 0, 220), -1)
                else -> VibrationEffect.createOneShot(70, 255)
            }
            vib.vibrate(effect)
        }

        @JavascriptInterface
        fun tick() { vib.vibrate(VibrationEffect.createOneShot(25, 120)) }

        @JavascriptInterface
        fun cancel() { vib.cancel() }
    }

    companion object {
        const val URL = "https://gianlucagrillo.github.io/piano-casa/?app=android"
    }
}