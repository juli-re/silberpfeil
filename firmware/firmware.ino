#include <WiFi.h>
#include <AsyncUDP.h>

const char* ssid = "Silberpfeil";
const int BUTTON_PIN = 13;

AsyncUDP udp;
const int udpPort = 8092;

// Exakt dieselbe Struktur, die die Android-App (MainActivity.kt) erwartet!
struct __attribute__((packed)) TelemetryPacket {
    uint16_t rpm;        // 2 Bytes
    float temperature;   // 4 Bytes
    uint8_t buttonState; // 1 Byte
}; // Gesamtgröße: Genau 7 Bytes statt dem riesigen JSON-String

void setup() {
    WiFi.softAP(ssid);
    randomSeed(analogRead(0));
    pinMode(BUTTON_PIN, INPUT_PULLUP);
}

void loop() {
    // 1. Werte generieren / einlesen
    int buttonPressed = (digitalRead(BUTTON_PIN) == LOW) ? 1 : 0;
    int rpm = random(1500, 9500);         
    float oil_temp = random(700, 1100) / 10.0; 

    // 2. Binär-Paket mit den Werten befüllen
    TelemetryPacket packet;
    packet.rpm = (uint16_t)rpm;
    packet.temperature = oil_temp;
    packet.buttonState = (uint8_t)buttonPressed;

    // 3. Das Paket als rohen Byte-Stream senden
    // (uint8_t*)&packet zeigt auf die Speicheradresse der Struct, sizeof(packet) schickt exakt die 7 Bytes
    udp.broadcastTo((uint8_t*)&packet, sizeof(packet), udpPort);

    delay(20); 
}