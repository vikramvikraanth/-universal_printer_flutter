package com.universalprinter.html

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Renders receipt HTML to a bitmap via an offscreen [WebView]. Hardened against the three known
 * failure modes of "WebView → bitmap":
 *  - **Blank output on hardware-accelerated WebViews:** forced [View.LAYER_TYPE_SOFTWARE] so
 *    `draw(Canvas)` reliably captures pixels.
 *  - **Screenshot fires before data-URI images (logo, barcode/QR) decode:** instead of a fixed delay,
 *    it polls `contentHeight` until it has been non-zero and **stable** for [stableReads] reads (the
 *    reflow when images finish decoding changes the height, so a stable height ⇒ everything painted).
 *  - **Density-dependent width:** the HTML carries `<meta viewport width=widthPx>`, so layout is
 *    density-independent and the bitmap is paper-width-correct.
 * WebView must run on the main thread, so this bridges via a main-looper Handler and suspends until
 * done (bounded by [timeoutMs]). Not unit-testable — device-verified.
 */
internal class HtmlReceiptRasterizer(
    context: Context,
    private val pollMs: Long = 50,
    private val stableReads: Int = 3,
    private val minSettleMs: Long = 80,
    private val timeoutMs: Long = 15_000,
) {
    private val appContext = context.applicationContext

    suspend fun toBitmap(html: String, widthPx: Int): Bitmap = withTimeout(timeoutMs) {
        suspendCancellableCoroutine { cont ->
            val main = Handler(Looper.getMainLooper())
            main.post {
                try {
                    WebView.enableSlowWholeDocumentDraw()
                    val webView = WebView(appContext).apply {
                        setInitialScale(100)
                        isVerticalScrollBarEnabled = false
                        isHorizontalScrollBarEnabled = false
                        setLayerType(View.LAYER_TYPE_SOFTWARE, null) // reliable draw(Canvas) capture
                        settings.javaScriptEnabled = false           // static receipt HTML
                        settings.useWideViewPort = true              // honor the viewport width meta
                        settings.loadWithOverviewMode = true
                        settings.blockNetworkLoads = true            // data-URIs only; never hit the network
                    }
                    val widthSpec = View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY)

                    fun drawAndFinish() {
                        webView.measure(widthSpec, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                        webView.layout(0, 0, webView.measuredWidth, webView.measuredHeight)
                        val bitmap = Bitmap.createBitmap(widthPx, webView.measuredHeight.coerceAtLeast(1), Bitmap.Config.RGB_565)
                        Canvas(bitmap).apply { drawColor(Color.WHITE); webView.draw(this) }
                        webView.destroy()
                        if (cont.isActive) cont.resume(bitmap)
                    }

                    webView.webViewClient = object : WebViewClient() {
                        private var lastHeight = -1
                        private var stable = 0
                        private var waited = 0L
                        override fun onPageFinished(view: WebView, url: String?) {
                            val poll = object : Runnable {
                                override fun run() {
                                    if (!cont.isActive) { runCatching { view.destroy() }; return }
                                    val h = view.contentHeight
                                    if (h > 0 && h == lastHeight) stable++ else { stable = 0; lastHeight = h }
                                    waited += pollMs
                                    if (h > 0 && stable >= stableReads && waited >= minSettleMs) {
                                        runCatching { drawAndFinish() }.onFailure { if (cont.isActive) cont.resumeWithException(it) }
                                    } else {
                                        main.postDelayed(this, pollMs)
                                    }
                                }
                            }
                            main.postDelayed(poll, pollMs)
                        }
                    }

                    webView.layout(0, 0, widthPx, 1) // give layout a starting width
                    webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
                    cont.invokeOnCancellation { main.post { runCatching { webView.destroy() } } }
                } catch (e: Throwable) {
                    if (cont.isActive) cont.resumeWithException(e)
                }
            }
        }
    }
}
