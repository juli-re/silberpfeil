package com.project.silberpfeil

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import android.net.NetworkSpecifier
import android.net.wifi.WifiNetworkSpecifier
import android.net.wifi.WifiNetworkSuggestion
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var gaugeRpm: DashboardGauge
    private lateinit var gaugeTemp: DashboardGauge
    private lateinit var viewButtonLed: View
    private lateinit var viewUdpLed: View

    private var socket: DatagramSocket? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastPacketTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Kiosk-Modus & Keep Screen On
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN)

        // Views initialisieren
        gaugeRpm = findViewById(R.id.gaugeRpm)
        gaugeTemp = findViewById(R.id.gaugeTemp)
        viewButtonLed = findViewById(R.id.viewButtonLed)
        viewUdpLed = findViewById(R.id.viewUdpLed)

        // Gauges konfigurieren
        gaugeRpm.minVal = 0f
        gaugeRpm.maxVal = 8000f
        gaugeRpm.title = "DREHZAHL"
        gaugeRpm.unit = "U/min"
        gaugeRpm.arcColor = Color.parseColor("#00FFCC")

        gaugeTemp.minVal = 20f
        gaugeTemp.maxVal = 120f
        gaugeTemp.title = "MOTOR"
        gaugeTemp.unit = "°C"
        gaugeTemp.arcColor = Color.parseColor("#FF3333")

        // LEDs rund machen
        setLedColor(viewButtonLed, Color.GREEN)
        setLedColor(viewUdpLed, Color.RED)

        // 1. Automatisch mit "Silberpfeil" WLAN verbinden
        connectToSilberpfeilWifi()

        // 2. UDP Empfänger starten
        startUdpReceiver()

        // 3. Watchdog für Verbindungs-Abbruch (alle 500ms prüfen)
        startUdpWatchdog()
    }

    private fun setLedColor(view: View, color: Int) {
        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
        view.background = drawable
    }

    fun connectToSilberpfeilWifi() {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        // Wir erstellen einen festen Netzwerkvorschlag
        val suggestion = WifiNetworkSuggestion.Builder()
            .setSsid("Silberpfeil") // Hier deine ESP32-SSID eintragen
            // Falls dein ESP32 ein Passwort hat, nimm stattdessen:
            // .setWpa2Passphrase("dein_passwort")
            .setIsAppInteractionRequired(false) // Verhindert, dass die App den Nutzer fragen muss
            .build()

        val suggestionsList = listOf(suggestion)

        // Den Vorschlag im System registrieren
        val status = wifiManager.addNetworkSuggestions(suggestionsList)
        if (status == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS) {
            // Das System weiß jetzt Bescheid und verbindet sich künftig vollautomatisch,
            // sobald der ESP32 in Reichweite ist!
        }
    }
    private fun startUdpReceiver() {
        thread {
            try {
                socket = DatagramSocket(8092)
                val buffer = ByteArray(256)
                val packet = DatagramPacket(buffer, buffer.size)

                while (!Thread.currentThread().isInterrupted) {
                    socket?.receive(packet)

                    lastPacketTime = System.currentTimeMillis()
                    val jsonString = String(packet.data, 0, packet.length)
                    val json = JSONObject(jsonString)

                    val rpm = json.getInt("rpm").toFloat()
                    val temp = json.getDouble("temp").toFloat()
                    val buttonState = json.optInt("button", 0)

                    runOnUiThread {
                        // Gauges aktualisieren
                        gaugeRpm.currentVal = rpm
                        gaugeTemp.currentVal = temp

                        // UDP Status auf GRÜN setzen
                        setLedColor(viewUdpLed, Color.GREEN)

                        // Taster-LED auswerten
                        if (buttonState == 1) {
                            setLedColor(viewButtonLed, Color.RED)
                        } else {
                            setLedColor(viewButtonLed, Color.GREEN)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun startUdpWatchdog() {
        mainHandler.postDelayed(object : Runnable {
            override fun run() {
                // Wenn länger als 1500ms kein Paket kam -> UDP LED Rot
                if (System.currentTimeMillis() - lastPacketTime > 1500) {
                    setLedColor(viewUdpLed, Color.RED)
                }
                mainHandler.postDelayed(this, 500)
            }
        }, 500)
    }

    override fun onDestroy() {
        super.onDestroy()
        socket?.close()
        mainHandler.removeCallbacksAndMessages(null)
    }
}