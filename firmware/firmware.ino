#include <WiFi.h>
#include <AsyncUDP.h>

// wifi
const char* ssid = "Silberpfeil";

// pin setup
const int MAGNET_COUNT = 6;
const float wheelDiameter = 0.55; // in meters
const int HALL_SENSOR_PIN = 12;
const int SPEED_SENSOR_PIN = 14;
const int BUTTON_PIN = 13;

// --- RPM ISR variables ---
volatile uint32_t rpmPulseCount = 0;
volatile uint32_t rpmLastPulseTime = 0;
volatile uint32_t rpmCurrentPulseTime = 0;
portMUX_TYPE RPM_mux = portMUX_INITIALIZER_UNLOCKED;

// --- Speed ISR variables ---
volatile uint32_t speedPulseCount = 0;
volatile uint32_t speedLastPulseTime = 0;
volatile uint32_t speedCurrentPulseTime = 0;
portMUX_TYPE Speed_mux = portMUX_INITIALIZER_UNLOCKED;

AsyncUDP udp;
const int udpPort = 8092;

struct __attribute__((packed)) TelemetryPacket { // 11 Bytes
    uint16_t rpm;        // 2 Bytes
    float temperature;   // 4 Bytes
    float speed;         // 4 Bytes
    uint8_t buttonState; // 1 Byte
};

void IRAM_ATTR RPM_interrupt_handle (){
    portENTER_CRITICAL_ISR(&RPM_mux);
    rpmPulseCount++;
    rpmLastPulseTime = rpmCurrentPulseTime;
    rpmCurrentPulseTime = micros();
    portEXIT_CRITICAL_ISR(&RPM_mux);
}

void IRAM_ATTR SPEED_interrupt_handle (){
    portENTER_CRITICAL_ISR(&Speed_mux);
    speedPulseCount++;
    speedLastPulseTime = speedCurrentPulseTime;
    speedCurrentPulseTime = micros();
    portEXIT_CRITICAL_ISR(&Speed_mux);
}

uint16_t calculateRPM (){
    uint32_t lastTime;
    uint32_t currentTime;
    
    portENTER_CRITICAL(&RPM_mux);
    lastTime = rpmLastPulseTime;
    currentTime = rpmCurrentPulseTime;
    portEXIT_CRITICAL(&RPM_mux);
    
    uint32_t timeElapsed = micros() - currentTime;
    
    if (timeElapsed > 1000000) {
        return 0;
    }
    
    uint32_t pulsePeriod = currentTime - lastTime;
    if (pulsePeriod == 0) {
        return 0;
    }
    
    float calculatedRpm = (60000000.0 / pulsePeriod);
    
    if (calculatedRpm > 9000.0) {
        return 9000;
    }
    
    return (uint16_t)calculatedRpm;
}

float calculateSpeed (){
    uint32_t lastTime;
    uint32_t currentTime;
    
    portENTER_CRITICAL(&Speed_mux);
    lastTime = speedLastPulseTime;
    currentTime = speedCurrentPulseTime;
    portEXIT_CRITICAL(&Speed_mux);
    
    uint32_t timeElapsed = micros() - currentTime;
    
    if (timeElapsed > 1000000) {
        return 0.0;
    }
    
    uint32_t pulsePeriod = currentTime - lastTime;
    if (pulsePeriod == 0) {
        return 0.0;
    }
    
    float axleRpm = (60000000.0 / pulsePeriod) / MAGNET_COUNT;
    float currentSpeed = (axleRpm * 3.1415926535 * wheelDiameter * 60.0) / 1000.0;
    
    if (currentSpeed > 100.0) {
        return 100.0;
    }
    
    return currentSpeed;
}

void setup() {
    WiFi.softAP(ssid);
    randomSeed(analogRead(0));
    
    pinMode(BUTTON_PIN, INPUT_PULLUP);
    pinMode(HALL_SENSOR_PIN, INPUT_PULLUP);
    pinMode(SPEED_SENSOR_PIN, INPUT_PULLUP);
    
    attachInterrupt(digitalPinToInterrupt(HALL_SENSOR_PIN), RPM_interrupt_handle, FALLING);
    attachInterrupt(digitalPinToInterrupt(SPEED_SENSOR_PIN), SPEED_interrupt_handle, FALLING);
}

void loop() {
    int buttonPressed = (digitalRead(BUTTON_PIN) == LOW) ? 1 : 0;
    uint16_t rpm = calculateRPM();         
    float speed = calculateSpeed();
    float oil_temp = random(700, 1100) / 10.0; 

    TelemetryPacket packet;
    packet.rpm = rpm;
    packet.temperature = oil_temp;
    packet.speed = speed;
    packet.buttonState = (uint8_t)buttonPressed;

    udp.broadcastTo((uint8_t*)&packet, sizeof(packet), udpPort);

    delay(20); 
}