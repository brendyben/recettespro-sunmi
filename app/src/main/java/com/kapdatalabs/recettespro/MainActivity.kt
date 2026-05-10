package com.kapdatalabs.recettespro

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
    @Volatile private var printerService: SunmiPrinterService? = null
    private var bindAttempts = 0

    private val printerCallback = object : InnerPrinterCallback() {
        override fun onConnected(service: SunmiPrinterService) {
            printerService = service
            Log.i(TAG, "Printer CONNECTED")
        }

        override fun onDisconnected() {
            printerService = null
            Log.i(TAG, "Printer DISCONNECTED - will rebind on next print")
        }
    }

    /** Bind to Sunmi printer service. Retries automatically if it fails. */
    private fun bindPrinter() {
        try {
            val ok = InnerPrinterManager.getInstance().bindService(this, printerCallback)
            Log.i(TAG, "bindService attempt ${bindAttempts + 1} returned $ok")
            if (!ok && bindAttempts < 5) {
                bindAttempts++
                Handler(Looper.getMainLooper()).postDelayed({ bindPrinter() }, 1000L)
            }
        } catch (e: Exception) {
            Log.e(TAG, "bindPrinter failed", e)
            if (bindAttempts < 5) {
                bindAttempts++
                Handler(Looper.getMainLooper()).postDelayed({ bindPrinter() }, 1000L)
            }
        }
    }

    /** Ensure we have a printer connection. Try to bind if not. Waits briefly. */
    private fun ensurePrinterReady(): SunmiPrinterService? {
        if (printerService != null) return printerService

        // Try to bind right now
        try {
            InnerPrinterManager.getInstance().bindService(this, printerCallback)
        } catch (e: Exception) {
            Log.e(TAG, "ensurePrinterReady bind failed", e)
        }

        // Wait up to 2 seconds for the binding to complete
        val start = System.currentTimeMillis()
        while (printerService == null && (System.currentTimeMillis() - start) < 2000) {
            try { Thread.sleep(50) } catch (_: InterruptedException) {}
        }

        return printerService
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Start binding to Sunmi printer service (with retries)
        bindPrinter()

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

        // Allow window.alert / confirm / prompt to show as Android dialogs
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

    override fun onResume() {
        super.onResume()
        // Re-attempt binding when app comes back to foreground
        if (printerService == null) {
            bindAttempts = 0
            bindPrinter()
        }
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
        fun isAvailable(): Boolean {
            // Try to ensure the service is ready when JS asks
            return ensurePrinterReady() != null
        }

        @JavascriptInterface
        fun printTicket(jsonString: String): String {
            val service = ensurePrinterReady() ?: return "ERROR: printer not connected"

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

                service.printerInit(null)

                service.setAlignment(1, null)
                if (titre.isNotEmpty()) {
                    service.printTextWithFont(titre + "\n", null, 24f, null)
                }
                service.lineWrap(1, null)

                if (numero.isNotEmpty()) {
                    service.setAlignment(1, null)
                    service.printTextWithFont(numero + "\n", null, 30f, null)
                }
                service.lineWrap(1, null)

                service.setAlignment(0, null)
                service.printText("--------------------------------\n", null)

                service.setFontSize(24f, null)
                printRow(service, "Agent", agent)
                printRow(service, "Commune", commune)
                printRow(service, "Marche", marche)
                printRow(service, "Date", date)
                printRow(service, "Heure", heure)

                service.printText("--------------------------------\n", null)
                service.lineWrap(1, null)

                service.setAlignment(1, null)
                service.printTextWithFont(montant + "\n", null, 42f, null)
                service.setFontSize(20f, null)
                service.printTextWithFont(libelle + "\n", null, 20f, null)

                service.lineWrap(2, null)
                service.setAlignment(1, null)
                service.printTextWithFont("Merci !\n", null, 22f, null)
                service.lineWrap(4, null)

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
            val service = ensurePrinterReady() ?: return "ERROR: printer not connected"
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
