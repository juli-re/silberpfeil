#include <WiFi.h>
#include <AsyncUDP.h>

// wifi

const char* ssid = "Silberpfeil";

// pin setup

const int MAGNET_COUNT = 6;
const int HALL_SENSOR_PIN = 12;
const int BUTTON_PIN = 13;

// isr variables

volatile uint32_t pulseCount = 0;
volatile uint32_t lastPulseTime = 0;
volatile uint32_t currentPulseTime = 0;

AsyncUDP udp;
const int udpPort = 8092;

struct __attribute__((packed)) TelemetryPacket {
    uint16_t rpm;        // 2 Bytes
    float temperature;   // 4 Bytes
    uint8_t buttonState; // 1 Byte
};

portMUX_TYPE mux = portMUX_INITIALIZER_UNLOCKED;

void IRAM_ATTR RPM_interrupt_handle (){
    portENTER_CRITICAL_ISR(&mux);
    pulseCount++;
    lastPulseTime = currentPulseTime;
    currentPulseTime = micros();
    portEXIT_CRITICAL_ISR(&mux);
}

uint16_t calculateRPM (){
    uint32_t lastTime;
    uint32_t currentTime;
    
    portENTER_CRITICAL(&mux);
    lastTime = lastPulseTime;
    currentTime = currentPulseTime;
    portEXIT_CRITICAL(&mux);
    
    uint32_t timeElapsed = micros() - currentTime;
    
    if (timeElapsed > 1000000) {
        return 0;
    }
    
    uint32_t pulsePeriod = currentTime - lastTime;
    if (pulsePeriod == 0) {
        return 0;
    }
    
    float calculatedRpm = (60000000.0 / pulsePeriod) / MAGNET_COUNT;
    
    if (calculatedRpm > 9000.0) {
        return 9000;
    }
    
    return (uint16_t)calculatedRpm;
}

void setup() {
    WiFi.softAP(ssid);
    randomSeed(analogRead(0));
    pinMode(BUTTON_PIN, INPUT_PULLUP);
    attachInterrupt(digitalPinToInterrupt(HALL_SENSOR_PIN), RPM_interrupt_handle, FALLING);
}

void loop() {

    int buttonPressed = (digitalRead(BUTTON_PIN) == LOW) ? 1 : 0;
    uint16_t rpm = calculateRPM();         
    float oil_temp = random(700, 1100) / 10.0; 

    TelemetryPacket packet;
    packet.rpm = rpm;
    packet.temperature = oil_temp;
    packet.buttonState = (uint8_t)buttonPressed;

    udp.broadcastTo((uint8_t*)&packet, sizeof(packet), udpPort);

    delay(20); 
} 
