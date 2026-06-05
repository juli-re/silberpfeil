#include <WiFi.h>
#include <AsyncUDP.h>

const char* ssid = "Silberpfeil";

AsyncUDP udp;
const int udpPort = 8092;

void setup() {
    WiFi.softAP(ssid);
    randomSeed(analogRead(0));
}

void loop() {
  int rpm = random(1500, 9500);         
  float oil_temp = random(700, 1100) / 10.0; 
  String data = "{\"rpm\":" + String(rpm) + ",\"temp\":" + String(oil_temp) + "}";
  udp.broadcastTo(data.c_str(), udpPort);
  delay(100); // 10 Hz
}
