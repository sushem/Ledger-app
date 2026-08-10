package com.sushem.ledger

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var googleSignInClient: GoogleSignInClient

    // ---- Google Sign-In result handling ----
    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            firebaseAuth.signInWithCredential(credential).addOnCompleteListener(this) { authTask ->
                if (authTask.isSuccessful) {
                    upsertUserProfile()
                    notifyAuthState()
                } else {
                    webView.evaluateJavascript("onSyncError('Sign-in failed, please try again')", null)
                }
            }
        } catch (e: ApiException) {
            webView.evaluateJavascript("onSyncError('Google sign-in was cancelled or failed')", null)
        }
    }

    // ---- backup import: user picks a .json file ----
    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data ?: return@registerForActivityResult
        try {
            val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: return@registerForActivityResult
            val escaped = JSONObject.quote(text)
            webView.post { webView.evaluateJavascript("onNativeImport($escaped)", null) }
        } catch (e: Exception) {
            webView.post { webView.evaluateJavascript("showToast('Could not read that file')", null) }
        }
    }

    private fun upsertUserProfile() {
        val user = firebaseAuth.currentUser ?: return
        val profile = hashMapOf(
            "uid" to user.uid,
            "displayName" to (user.displayName ?: ""),
            "email" to (user.email ?: ""),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        firestore.collection("users").document(user.uid).set(profile, SetOptions.merge())
    }

    private fun notifyAuthState() {
        val user = firebaseAuth.currentUser
        val js = if (user != null) {
            "onAuthStateChanged(${JSONObject.quote(user.uid)}, ${JSONObject.quote(user.displayName ?: "")}, " +
                "${JSONObject.quote(user.email ?: "")}, ${JSONObject.quote(user.photoUrl?.toString() ?: "")})"
        } else {
            "onAuthStateChanged(null, null, null, null)"
        }
        webView.post { webView.evaluateJavascript(js, null) }
    }

    inner class WebAppInterface {

        // ---------- backup export/import ----------
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

        // ---------- Google sign-in / sign-out ----------
        @JavascriptInterface
        fun signIn() {
            runOnUiThread {
                googleSignInLauncher.launch(googleSignInClient.signInIntent)
            }
        }

        @JavascriptInterface
        fun signOut() {
            runOnUiThread {
                firebaseAuth.signOut()
                googleSignInClient.signOut().addOnCompleteListener { notifyAuthState() }
            }
        }

        // ---------- Firestore sync ----------
        // Two separate collections, related only by uid:
        //   users/{uid}       -> profile (uid, displayName, email, updatedAt)
        //   ledgerData/{uid}  -> budget data (uid, ledgerData json, updatedAt)
        @JavascriptInterface
        fun syncToCloud(json: String) {
            val user = firebaseAuth.currentUser
            if (user == null) {
                webView.post { webView.evaluateJavascript("onSyncError('Sign in first')", null) }
                return
            }
            val payload = hashMapOf(
                "uid" to user.uid,
                "ledgerData" to json,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            firestore.collection("ledgerData").document(user.uid)
                .set(payload, SetOptions.merge())
                .addOnSuccessListener {
                    webView.post { webView.evaluateJavascript("onSyncComplete()", null) }
                }
                .addOnFailureListener { e ->
                    val msg = JSONObject.quote(e.message ?: "Sync failed")
                    webView.post { webView.evaluateJavascript("onSyncError($msg)", null) }
                }
        }

        @JavascriptInterface
        fun loadFromCloud() {
            val user = firebaseAuth.currentUser
            if (user == null) {
                webView.post { webView.evaluateJavascript("onCloudDataLoaded(null)", null) }
                return
            }
            firestore.collection("ledgerData").document(user.uid).get()
                .addOnSuccessListener { doc ->
                    val json = doc.getString("ledgerData")
                    val js = if (json != null) "onCloudDataLoaded(${JSONObject.quote(json)})" else "onCloudDataLoaded(null)"
                    webView.post { webView.evaluateJavascript(js, null) }
                }
                .addOnFailureListener {
                    webView.post { webView.evaluateJavascript("onCloudDataLoaded(null)", null) }
                }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        firebaseAuth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id)) // auto-generated from google-services.json
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

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

        // Tell the page the current sign-in state once it's actually loaded and ready
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                notifyAuthState()
            }
        }

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
