package com.kapdatalabs.recettespro

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.os.Bundle
import android.os.RemoteException
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.sunmi.peripheral.printer.InnerPrinterCallback
import com.sunmi.peripheral.printer.InnerPrinterManager
import com.sunmi.peripheral.printer.SunmiPrinterService
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var printerService: SunmiPrinterService? = null

    private val printerCallback = object : InnerPrinterCallback() {
        override fun onConnected(service: SunmiPrinterService) {
            printerService = service
            Log.i(TAG, "Printer connected")
        }

        override fun onDisconnected() {
            printerService = null
            Log.i(TAG, "Printer disconnected")
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Bind to Sunmi printer service
        try {
            InnerPrinterManager.getInstance().bindService(this, printerCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind printer service", e)
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

        // Permet à window.alert(), confirm(), prompt() de s'afficher comme dialogues Android
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

    override fun onDestroy() {
        try {
            InnerPrinterManager.getInstance().unBindService(this, printerCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unbind printer service", e)
        }
        super.onDestroy()
    }

    /** JavaScript-callable bridge exposed as window.SunmiPrint */
    inner class SunmiPrintBridge {

        @JavascriptInterface
        fun isAvailable(): Boolean = printerService != null

        @JavascriptInterface
        fun printTicket(jsonString: String): String {
            val service = printerService ?: return "ERROR: printer not connected"

            return try {
                val ticket = JSONObject(jsonString)

                val titre = ticket.optString("titre", "")
                val numero = ticket.optString("numero", "")
                val agent = ticket.optString("agent", "")
                val commune = ticket.optString("commune", "")
                val marche = ticket.optString("marche", "")
                val date = ticket.optString("date", "")
                val heure = ticket.optString("heure", "")
                val montant = ticket.optString("montant", "")
                val libelle = ticket.optString("libelle", "Montant collecté")

                // Reset printer
                service.printerInit(null)

                // ===== HEADER =====
                service.setAlignment(1, null) // 0=left 1=center 2=right
                if (titre.isNotEmpty()) {
                    service.setFontSize(24f, null)
                    service.printTextWithFont(titre + "\n", null, 24f, null)
                }
                service.lineWrap(1, null)

                // ===== NUMERO =====
                if (numero.isNotEmpty()) {
                    service.setAlignment(1, null)
                    service.printTextWithFont(numero + "\n", null, 30f, null)
                }
                service.lineWrap(1, null)

                // ===== SEPARATEUR =====
                service.setAlignment(0, null)
                service.printText("--------------------------------\n", null)

                // ===== INFOS (label droite + valeur à droite, tableau 2 colonnes) =====
                service.setFontSize(24f, null)
                printRow(service, "Agent", agent)
                printRow(service, "Commune", commune)
                printRow(service, "Marche", marche)
                printRow(service, "Date", date)
                printRow(service, "Heure", heure)

                service.printText("--------------------------------\n", null)
                service.lineWrap(1, null)

                // ===== MONTANT =====
                service.setAlignment(1, null)
                service.printTextWithFont(montant + "\n", null, 42f, null)
                service.setFontSize(20f, null)
                service.printTextWithFont(libelle + "\n", null, 20f, null)

                // ===== FOOTER =====
                service.lineWrap(2, null)
                service.setAlignment(1, null)
                service.printTextWithFont("Merci !\n", null, 22f, null)
                service.lineWrap(4, null)

                // Cut paper if device supports it (V2s does not have a cutter, so this is harmless)
                try { service.cutPaper(null) } catch (_: Exception) {}

                "OK"
            } catch (e: RemoteException) {
                Log.e(TAG, "Print failed", e)
                "ERROR: " + e.message
            } catch (e: Exception) {
                Log.e(TAG, "Print failed", e)
                "ERROR: " + e.message
            }
        }

        @JavascriptInterface
        fun printRaw(text: String): String {
            val service = printerService ?: return "ERROR: printer not connected"
            return try {
                service.printerInit(null)
                service.printText(text, null)
                service.lineWrap(4, null)
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

    /** Print a row with label on the left and value on the right, padded to 32 chars (58mm = 32 chars) */
    private fun printRow(service: SunmiPrinterService, label: String, value: String) {
        if (value.isEmpty()) return
        val totalWidth = 32
        val labelPart = label
        val valuePart = value
        val spaces = totalWidth - labelPart.length - valuePart.length
        val line = if (spaces > 0) {
            labelPart + " ".repeat(spaces) + valuePart
        } else {
            "$labelPart  $valuePart"
        }
        service.printText(line + "\n", null)
    }

    companion object {
        private const val TAG = "RecettesPro"
    }
}
