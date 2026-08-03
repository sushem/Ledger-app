package com.sushem.ledger

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    // launcher for "Import data" -> user picks a .json backup file
    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data ?: return@registerForActivityResult
        try {
            val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: return@registerForActivityResult
            // JSONObject.quote safely escapes the string for embedding in a JS call
            val escaped = JSONObject.quote(text)
            webView.post {
                webView.evaluateJavascript("onNativeImport($escaped)", null)
            }
        } catch (e: Exception) {
            webView.post {
                webView.evaluateJavascript("showToast('Could not read that file')", null)
            }
        }
    }

    inner class WebAppInterface {
        @JavascriptInterface
        fun exportData(json: String) {
            runOnUiThread {
                try {
                    val dir = File(cacheDir, "shared").apply { mkdirs() }
                    val file = File(dir, "ledger-backup-${System.currentTimeMillis()}.json")
                    FileOutputStream(file).use { it.write(json.toByteArray()) }
                    val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.fileprovider", file)

                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/json"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(intent, "Save Ledger backup"))
                    webView.evaluateJavascript("onNativeExportStarted()", null)
                } catch (e: Exception) {
                    webView.evaluateJavascript("showToast('Export failed')", null)
                }
            }
        }

        @JavascriptInterface
        fun importData() {
            runOnUiThread {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/json"
                }
                importLauncher.launch(intent)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        webView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true // required for localStorage persistence
        settings.allowFileAccess = true

        webView.addJavascriptInterface(WebAppInterface(), "Android")

        setContentView(webView)
        webView.loadUrl("file:///android_asset/index.html")
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
