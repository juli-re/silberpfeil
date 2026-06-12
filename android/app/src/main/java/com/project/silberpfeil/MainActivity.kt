package com.project.silberpfeil

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private val TAG = "SilberpfeilDashboard"

    private lateinit var gaugeRpm: DashboardGauge
    private lateinit var gaugeTemp: DashboardGauge
    private lateinit var viewButtonLed: View
    private lateinit var viewUdpLed: View
    private lateinit var textPacketLoss: TextView // Neue View für die Statistik

    private var socket: DatagramSocket? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastPacketTime = 0L

    // Variablen für die Packet-Loss-Berechnung
    private var packetCounter = 0
    private val EXPECTED_PACKETS_PER_SECOND = 50 // Weil Intervall = 20ms (1000ms / 20ms = 50)

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
        textPacketLoss = findViewById(R.id.textPacketLoss) // Musst du noch im XML anlegen!

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

        // Start-Zustand für LEDs (Rund machen)
        setLedColor(viewButtonLed, Color.GREEN)
        setLedColor(viewUdpLed, Color.RED)
        textPacketLoss.text = "0% Loss (0/50)"

        // 1. Automatisch mit "Silberpfeil" WLAN verbinden
        connectToSilberpfeilWifi()

        // 2. Binären UDP Empfänger starten
        startUdpReceiver()

        // 3. Watchdog & Statistik-Timer starten (jetzt kombiniert)
        startUdpWatchdogAndStats()
    }

    private fun setLedColor(view: View, color: Int) {
        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
        view.background = drawable
    }

    fun connectToSilberpfeilWifi() {
        try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

            val suggestion = WifiNetworkSuggestion.Builder()
                .setSsid("Silberpfeil")
                .setIsAppInteractionRequired(false)
                .build()

            val suggestionsList = listOf(suggestion)
            val status = wifiManager.addNetworkSuggestions(suggestionsList)
            if (status == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS) {
                Log.d(TAG, "WLAN-Vorschlag erfolgreich im System registriert.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fehler bei WLAN-Suggestion: ${e.message}")
        }
    }

    private fun startUdpReceiver() {
        thread {
            try {
                socket = DatagramSocket(8092)
                val buffer = ByteArray(32)
                val packet = DatagramPacket(buffer, buffer.size)

                Log.i(TAG, "Binärer UDP-Receiver auf Port 8092 gestartet.")

                while (!Thread.currentThread().isInterrupted) {
                    socket?.receive(packet)

                    lastPacketTime = System.currentTimeMillis()
                    packetCounter++ // Jedes angekommene Paket hochzählen
                    val length = packet.length

                    if (length >= 7) {
                        val rawData = packet.data.clone()
                        runOnUiThread {
                            parseAndApplyBinaryTelemetry(rawData, length)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Fehler im UDP Receiver: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private fun parseAndApplyBinaryTelemetry(bytes: ByteArray, length: Int) {
        try {
            val buffer = ByteBuffer.wrap(bytes, 0, length)
            buffer.order(ByteOrder.LITTLE_ENDIAN)

            val rpmVal = buffer.short.toInt() and 0xFFFF
            val tempVal = buffer.float
            val buttonState = buffer.get().toInt()

            gaugeRpm.currentVal = rpmVal.toFloat()
            gaugeTemp.currentVal = tempVal

            setLedColor(viewUdpLed, Color.GREEN)

            if (buttonState == 1) {
                setLedColor(viewButtonLed, Color.RED)
            } else {
                setLedColor(viewButtonLed, Color.GREEN)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Dekodieren des Binärpakets: ${e.message}")
        }
    }

    private fun startUdpWatchdogAndStats() {
        mainHandler.postDelayed(object : Runnable {
            override fun run() {
                val now = System.currentTimeMillis()

                // 1. WATCHDOG: Wenn länger als 1500ms kein Paket kam -> UDP LED Rot & Statistik nullen
                if (now - lastPacketTime > 1500) {
                    setLedColor(viewUdpLed, Color.RED)
                    textPacketLoss.text = "100% Loss (0/$EXPECTED_PACKETS_PER_SECOND)"
                    packetCounter = 0
                } else {
                    // 2. STATISTIK: Jede Sekunde (1000ms) den Packet Loss berechnen
                    // Da der Handler alle 1000ms läuft, entspricht packetCounter den "empfangenen Paketen pro Sekunde"

                    val received = packetCounter
                    // Falls der ESP mal minimal schneller schießt, deckeln wir die empfangenen Pakete beim Erwartungswert
                    val lossPackets = (EXPECTED_PACKETS_PER_SECOND - received).coerceAtLeast(0)
                    val lossPercent = (lossPackets.toFloat() / EXPECTED_PACKETS_PER_SECOND * 100).toInt()

                    // Text aktualisieren (z.B. "2% Loss (49/50)")
                    textPacketLoss.text = "$lossPercent% Loss ($received/$EXPECTED_PACKETS_PER_SECOND)"

                    // Textfarbe je nach Qualität anpassen
                    if (lossPercent > 10) {
                        textPacketLoss.setTextColor(Color.RED)
                    } else if (lossPercent > 2) {
                        textPacketLoss.setTextColor(Color.YELLOW)
                    } else {
                        textPacketLoss.setTextColor(Color.WHITE)
                    }

                    // Zähler für die nächste Sekunde zurücksetzen
                    packetCounter = 0
                }

                // Den Handler exakt alle 1000ms ausführen, um ein sauberes 1-Sekunden-Fenster zu haben
                mainHandler.postDelayed(this, 1000)
            }
        }, 1000)
    }

    override fun onDestroy() {
        super.onDestroy()
        socket?.close()
        mainHandler.removeCallbacksAndMessages(null)
    }
}