#include <WiFi.h>
#include <AsyncUDP.h>

const char* ssid = "Silberpfeil";
const int BUTTON_PIN = 13;

AsyncUDP udp;
const int udpPort = 8092;

void setup() {
    WiFi.softAP(ssid);
    randomSeed(analogRead(0));
    pinMode(BUTTON_PIN, INPUT_PULLUP);
}

void loop() {
  int buttonPressed = (digitalRead(BUTTON_PIN) == LOW) ? 1 : 0;
  int rpm = random(1500, 9500);         
  float oil_temp = random(700, 1100) / 10.0; 
  String data = "{\"rpm\":" + String(rpm) +
                ",\"temp\":" + String(oil_temp) +
                ",\"button\":" + String(buttonPressed) +
                "}";
  udp.broadcastTo(data.c_str(), udpPort);
  delay(200); // 10 Hz
}
