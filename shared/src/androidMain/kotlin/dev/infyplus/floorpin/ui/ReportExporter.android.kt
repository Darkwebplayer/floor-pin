package dev.infyplus.floorpin.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.print.PrintAttributes
import android.print.PrintManager
import android.util.Log
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

private const val TAG = "ReportExporter"

@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun rememberReportExporter(): (html: String, jobName: String, baseUrl: String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { html, jobName, baseUrl ->
            Log.d(TAG, "Starting PDF export — jobName=$jobName, htmlLength=${html.length}, baseUrl=$baseUrl")

            val webView = WebView(context).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    blockNetworkLoads = false
                    blockNetworkImage = false
                    cacheMode = WebSettings.LOAD_NO_CACHE
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    setSupportZoom(false)
                    builtInZoomControls = false
                    displayZoomControls = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        safeBrowsingEnabled = false
                    }
                }

                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun onImagesReady() {
                        Log.d(TAG, "onImagesReady callback from JS — initiating print")
                        post { doPrint(context, this@apply, jobName) }
                    }
                }, "Android")

                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest
                    ): WebResourceResponse? {
                        Log.d(TAG, "shouldInterceptRequest: ${request.method} ${request.url}")
                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun onLoadResource(view: WebView, url: String) {
                        Log.d(TAG, "onLoadResource: $url")
                    }

                    override fun onReceivedHttpError(
                        view: WebView,
                        request: WebResourceRequest,
                        errorResponse: WebResourceResponse
                    ) {
                        Log.e(TAG, "HTTP error ${errorResponse.statusCode} ${errorResponse.reasonPhrase} for ${request.url}")
                    }

                    override fun onReceivedError(
                        view: WebView,
                        request: WebResourceRequest,
                        error: android.webkit.WebResourceError
                    ) {
                        Log.e(TAG, "Resource error ${error.errorCode} ${error.description} for ${request.url}")
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        Log.d(TAG, "onPageFinished — injecting image-ready detection JS")
                        view.evaluateJavascript("""
                            (function(){
                                var imgs = document.querySelectorAll('img');
                                console.log('ReportExporter: found ' + imgs.length + ' images in HTML');
                                if (imgs.length === 0) { Android.onImagesReady(); return; }
                                var total = imgs.length, loaded = 0;
                                function check() {
                                    loaded++;
                                    console.log('ReportExporter: image ' + loaded + '/' + total +
                                        (loaded>=total ? ' ALL READY' : ''));
                                    if (loaded >= total) Android.onImagesReady();
                                }
                                imgs.forEach(function(img) {
                                    if (img.complete) {
                                        console.log('ReportExporter: img already settled (complete=' + img.complete +
                                            ' nw=' + img.naturalWidth + ' src=' + img.src.substring(0,60) + ')');
                                        check();
                                    } else {
                                        console.log('ReportExporter: waiting for img: ' + img.src.substring(0,60));
                                        img.onload = check;
                                        img.onerror = check;
                                    }
                                });
                            })();
                        """.trimIndent(), null)
                    }
                }

                webChromeClient = WebChromeClient()
            }

            // Attach to window — required for WebView to render images
            val decorView = (context as? android.app.Activity)?.window?.decorView as? ViewGroup
            decorView?.addView(webView, ViewGroup.LayoutParams(1, 1))
            Log.d(TAG, "WebView attached to window: ${decorView != null}")

            webView.loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null)
            Log.d(TAG, "loadDataWithBaseURL called with baseUrl=$baseUrl")
        }
    }
}

private fun doPrint(context: Context, webView: WebView, jobName: String) {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
    val adapter = webView.createPrintDocumentAdapter(jobName)
    printManager.print(jobName, adapter, PrintAttributes.Builder().build())
    Log.d(TAG, "Print job submitted: $jobName")

    // Detach from window after a short delay to let print capture complete
    webView.postDelayed({
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
        Log.d(TAG, "WebView detached and destroyed")
    }, 3000)
}
