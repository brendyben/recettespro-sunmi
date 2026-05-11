package com.kapdatalabs.recettespro

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.sunmi.printerx.PrinterSdk
import com.sunmi.printerx.SdkCallback
import com.sunmi.printerx.enums.Align
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    @Volatile private var printer: PrinterSdk.Printer? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize the modern Sunmi PrinterX SDK
        try {
            PrinterSdk.getInstance().getPrinter(this, object : SdkCallback {
                override fun onConnect(p: PrinterSdk.Printer?) {
                    printer = p
                    Log.i(TAG, "PrinterX: connected, printer=" + (p != null))
                }

                override fun onFailed(p: PrinterSdk.Printer?, errorCode: Int, msg: String?) {
                    Log.e(TAG, "PrinterX: failed code=$errorCode msg=$msg")
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "PrinterX init failed", e)
        }

        webView = findViewById(R.id.webview)
        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true

        webView.webViewClient = WebViewClient()

        webView.webChromeClient = object : WebChromeClient() {
            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("RecettesPro")
                    .setMessage(message)
                    .setPositiveButton("OK") { _, _ -> result?.confirm() }
                    .setCancelable(false)
                    .show()
                return true
            }

            override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("RecettesPro")
                    .setMessage(message)
                    .setPositiveButton("OK") { _, _ -> result?.confirm() }
                    .setNegativeButton("Annuler") { _, _ -> result?.cancel() }
                    .setCancelable(false)
                    .show()
                return true
            }
        }

        webView.addJavascriptInterface(SunmiPrintBridge(), "SunmiPrint")

        webView.loadUrl("https://rpro.bakapdatalabs.com")
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    inner class SunmiPrintBridge {

        @JavascriptInterface
        fun isAvailable(): Boolean = printer != null

        @JavascriptInterface
        fun printTicket(jsonString: String): String {
            val p = printer ?: return "ERROR: printer not connected"

            return try {
                val ticket = JSONObject(jsonString)

                val titre   = ticket.optString("titre", "")
                val numero  = ticket.optString("numero", "")
                val agent   = ticket.optString("agent", "")
                val commune = ticket.optString("commune", "")
                val marche  = ticket.optString("marche", "")
                val date    = ticket.optString("date", "")
                val heure   = ticket.optString("heure", "")
                val montant = ticket.optString("montant", "")
                val libelle = ticket.optString("libelle", "Montant collecte")

                val lineApi = p.lineApi()

                // Titre
                if (titre.isNotEmpty()) {
                    lineApi.initLine(com.sunmi.printerx.style.BaseStyle.getStyle().setAlign(Align.CENTER))
                    lineApi.printText(titre, com.sunmi.printerx.style.TextStyle.getStyle().enableBold(true))
                }

                // Numéro de ticket
                if (numero.isNotEmpty()) {
                    lineApi.initLine(com.sunmi.printerx.style.BaseStyle.getStyle().setAlign(Align.CENTER))
                    lineApi.printText(numero, com.sunmi.printerx.style.TextStyle.getStyle().setTextSize(30).enableBold(true))
                }

                lineApi.printText("\n--------------------------------\n", null)

                // Infos en 2 colonnes
                lineApi.initLine(com.sunmi.printerx.style.BaseStyle.getStyle().setAlign(Align.LEFT))
                if (agent.isNotEmpty())   lineApi.printText(formatRow("Agent", agent) + "\n", null)
                if (commune.isNotEmpty()) lineApi.printText(formatRow("Commune", commune) + "\n", null)
                if (marche.isNotEmpty())  lineApi.printText(formatRow("Marche", marche) + "\n", null)
                if (date.isNotEmpty())    lineApi.printText(formatRow("Date", date) + "\n", null)
                if (heure.isNotEmpty())   lineApi.printText(formatRow("Heure", heure) + "\n", null)

                lineApi.printText("--------------------------------\n\n", null)

                // Montant
                lineApi.initLine(com.sunmi.printerx.style.BaseStyle.getStyle().setAlign(Align.CENTER))
                lineApi.printText(montant + "\n", com.sunmi.printerx.style.TextStyle.getStyle().setTextSize(42).enableBold(true))
                lineApi.printText(libelle + "\n", com.sunmi.printerx.style.TextStyle.getStyle().setTextSize(20))

                lineApi.printText("\n\nMerci !\n\n\n\n", null)

                "OK"
            } catch (e: Exception) {
                Log.e(TAG, "Print failed", e)
                "ERROR: " + e.message
            }
        }

        @JavascriptInterface
        fun printRaw(text: String): String {
            val p = printer ?: return "ERROR: printer not connected"
            return try {
                p.lineApi().printText(text + "\n\n\n", null)
                "OK"
            } catch (e: Exception) {
                "ERROR: " + e.message
            }
        }

        @JavascriptInterface
        fun toast(message: String) {
            runOnUiThread {
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun formatRow(label: String, value: String): String {
        val totalWidth = 32
        val spaces = totalWidth - label.length - value.length
        return if (spaces > 0) label + " ".repeat(spaces) + value
               else "$label  $value"
    }

    companion object {
        private const val TAG = "RecettesPro"
    }
}
