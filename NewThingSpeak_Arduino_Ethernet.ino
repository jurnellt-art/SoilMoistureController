/*
  Soil Moisture Controller For Potted Anthurium with ThingSpeak IoT
  Arduino Mega 2560 + Ethernet Shield (W5100)

  ThingSpeak Channel Fields:
    Field 1: Sensor 1 Soil Moisture (%)
    Field 2: Sensor 2 Soil Moisture (%)
    Field 3: Sensor 3 Soil Moisture (%)
    Field 4: Sensor 4 Soil Moisture (%)
    Field 5: Sensor 5 Soil Moisture (%)
    Field 6: Sensor 6 Soil Moisture (%)
    Field 7: Average Temperature (°C)
    Field 8: pH Level
    Status text: "Mode: ... | Pumps: n/2 | Valves Open: n/6 | Mist: n/4"

  Control mode (MANUAL / AUTOMATIC) is remembered across resets. A blank board
  starts in MANUAL. Changing mode switches every pump, valve and mist maker OFF
  first, so the new mode always starts from a known state.
*/

#include <SPI.h>
#include <Ethernet.h>
#include <DHT.h>
#include <Wire.h>
#include <LiquidCrystal_I2C.h>
#include <ThingSpeak.h>
#include <EEPROM.h>

// ---- Network config ----
byte mac[] = { 0xDE, 0xAD, 0xBE, 0xEF, 0xFE, 0xED };
IPAddress ip(192, 168, 0, 50);      // Static IP for local network
IPAddress myDns(192, 168, 0, 1);
IPAddress gateway(192, 168, 0, 1);
IPAddress subnet(255, 255, 255, 0);
EthernetServer server(80);
EthernetClient tsClient;

// ---- ThingSpeak Config ----
unsigned long myChannelNumber = 3532877;
const char * myWriteAPIKey = "4EJFZQ1GQBA2BUMA";

unsigned long lastThingSpeakSync = 0;
const unsigned long THINGSPEAK_INTERVAL_MS = 20000; // ThingSpeak free tier requires >=15s delay
unsigned long thingSpeakInterval = THINGSPEAK_INTERVAL_MS;  // grows after failed uploads

// ---- Sensor pins ----
const uint8_t PIN_SOIL[6]   = { A0, A1, A2, A3, A4, A5 };
const uint8_t PIN_PH        = A7;
const uint8_t PIN_DHT[3]    = { 46, 48, 9 };

// ---- Relay pins ----
const uint8_t PIN_PUMP[2]   = { 34, 36 };
const uint8_t PIN_VALVE[6]  = { 22, 24, 26, 28, 30, 32 };
const uint8_t PIN_MIST[4]   = { 38, 40, 42, 44 };
const uint8_t PIN_BUZZER    = 8;

const bool RELAY_ACTIVE_LOW = true;
const uint8_t RELAY_ON  = RELAY_ACTIVE_LOW ? LOW  : HIGH;
const uint8_t RELAY_OFF = RELAY_ACTIVE_LOW ? HIGH : LOW;

// The 4-channel mist relay board is a separate module and may trigger on the
// opposite level. true = LOW turns the mist maker ON.
const bool MIST_RELAY_ACTIVE_LOW = true;
const uint8_t MIST_ON  = MIST_RELAY_ACTIVE_LOW ? LOW  : HIGH;
const uint8_t MIST_OFF = MIST_RELAY_ACTIVE_LOW ? HIGH : LOW;

DHT dht0(PIN_DHT[0], DHT22);
DHT dht1(PIN_DHT[1], DHT22);
DHT dht2(PIN_DHT[2], DHT22);
DHT* dhtSensors[3] = { &dht0, &dht1, &dht2 };

LiquidCrystal_I2C lcd(0x27, 20, 4);

unsigned long lastLcdUpdate = 0;
const unsigned long LCD_UPDATE_INTERVAL_MS = 3000;

bool pumpState[2]  = { false, false };
bool valveState[6] = { false, false, false, false, false, false };
bool mistState[4]  = { false, false, false, false };
String mode = "MANUAL";   // "AUTOMATIC" or "MANUAL"; restored from EEPROM in setup()
const int EEPROM_MODE_ADDR = 0;

const float AUTO_SOIL_THRESHOLD = 35.0;
const float AUTO_SOIL_STOP_ABOVE = 45.0;
const uint8_t VALVE_PUMP[6] = { 0, 0, 0, 1, 1, 1 };

const float MIST_ON_BELOW   = 55.0;
const float MIST_OFF_ABOVE  = 70.0;
bool mistGroupOn = false;

const int SOIL_DRY_RAW = 590;
const int SOIL_WET_RAW = 280;

void setup() {
  Serial.begin(9600);

  // Initialize SPI Select pins explicitly
  pinMode(4, OUTPUT);  digitalWrite(4, HIGH);
  pinMode(10, OUTPUT); digitalWrite(10, HIGH);
  pinMode(53, OUTPUT); digitalWrite(53, HIGH);
  delay(100);

  for (uint8_t i = 0; i < 2; i++) {
    digitalWrite(PIN_PUMP[i], RELAY_OFF);
    pinMode(PIN_PUMP[i], OUTPUT);
  }
  for (uint8_t i = 0; i < 6; i++) {
    digitalWrite(PIN_VALVE[i], RELAY_OFF);
    pinMode(PIN_VALVE[i], OUTPUT);
  }
  for (uint8_t i = 0; i < 4; i++) {
    digitalWrite(PIN_MIST[i], MIST_OFF);
    pinMode(PIN_MIST[i], OUTPUT);
  }
  pinMode(PIN_BUZZER, OUTPUT);
  digitalWrite(PIN_BUZZER, LOW);

  // Restore the mode you last chose. A blank EEPROM counts as MANUAL.
  mode = (EEPROM.read(EEPROM_MODE_ADDR) == 1) ? "AUTOMATIC" : "MANUAL";

  for (uint8_t i = 0; i < 3; i++) dhtSensors[i]->begin();

  Wire.begin();
  lcd.init();
  lcd.backlight();
  lcd.setCursor(0, 0);
  lcd.print("Anthurium ThingSpeak");
  lcd.setCursor(0, 1);
  lcd.print("Starting up...");

  Ethernet.begin(mac, ip, myDns, gateway, subnet);
  server.begin();
  ThingSpeak.begin(tsClient);

  Serial.print("Server started at ");
  Serial.println(Ethernet.localIP());

  // Ethernet.begin() with a fixed IP never reports a failure, so the IP below
  // does NOT prove the shield works. Ask the chip itself.
  bool ethOk = (Ethernet.hardwareStatus() != EthernetNoHardware);
  Serial.println(ethOk ? "Ethernet chip found" : "ERROR: Ethernet shield NOT detected");
  Serial.print("Mode: ");
  Serial.println(mode);

  lcd.clear();
  lcd.setCursor(0, 0);
  lcd.print("IP:");
  lcd.setCursor(0, 1);
  lcd.print(Ethernet.localIP());
  lcd.setCursor(0, 2);
  lcd.print(ethOk ? "Ethernet chip: OK" : "NO ETHERNET CHIP!");
  lcd.setCursor(0, 3);
  lcd.print("Mode: ");
  lcd.print(mode);
  delay(3000);
}

void loop() {
  if (mode == "AUTOMATIC") {
    runAutomaticControl();
  }

  checkLowMoistureAlert();
  syncWithThingSpeakCloud();

  if (millis() - lastLcdUpdate >= LCD_UPDATE_INTERVAL_MS) {
    lastLcdUpdate = millis();
    updateLcd();
  }

  EthernetClient client = server.available();
  if (client) {
    String request = client.readStringUntil('\r');
    client.flush();
    Serial.print("REQ: ");
    Serial.println(request);

    if (request.indexOf("GET /sensors") >= 0) {
      handleSensors(client);
    } else if (request.indexOf("GET /pump") >= 0) {
      handlePumpCommand(client, request);
    } else if (request.indexOf("GET /valve") >= 0) {
      handleValveCommand(client, request);
    } else if (request.indexOf("GET /mist") >= 0) {
      handleMistCommand(client, request);
    } else if (request.indexOf("GET /mode") >= 0) {
      if (request.indexOf("value=automatic") >= 0 || request.indexOf("automatic") >= 0) {
        setControlMode("AUTOMATIC");
        sendPlainText(client, "mode automatic");
      } else {
        setControlMode("MANUAL");
        sendPlainText(client, "mode manual");
      }
    } else {
      sendPlainText(client, "not found");
    }

    delay(1);
    client.stop();
  }
}

void syncWithThingSpeakCloud() {
  if (millis() - lastThingSpeakSync < thingSpeakInterval) return;
  lastThingSpeakSync = millis();

  // Populate Fields 1 to 6 (Soil Sensors)
  for (uint8_t i = 0; i < 6; i++) {
    ThingSpeak.setField(i + 1, readSoilMoisture(i));
  }

  // Calculate Average Temperature
  float tempSum = 0;
  for (uint8_t i = 0; i < 3; i++) {
    float t = dhtSensors[i]->readTemperature();
    tempSum += isnan(t) ? 0 : t;
  }
  float avgTemp = tempSum / 3.0;

  // Field 7: Temperature, Field 8: pH Level
  ThingSpeak.setField(7, avgTemp);
  ThingSpeak.setField(8, readPh());

  // Count active relays for text status update
  uint8_t openValves = 0;
  for (uint8_t i = 0; i < 6; i++) if (valveState[i]) openValves++;
  uint8_t activePumps = 0;
  for (uint8_t i = 0; i < 2; i++) if (pumpState[i]) activePumps++;
  uint8_t activeMists = 0;
  for (uint8_t i = 0; i < 4; i++) if (mistState[i]) activeMists++;

  String statusMsg = "Mode: " + mode +
                     " | Pumps: " + String(activePumps) + "/2" +
                     " | Valves Open: " + String(openValves) + "/6" +
                     " | Mist: " + String(activeMists) + "/4";

  ThingSpeak.setStatus(statusMsg);

  int status = ThingSpeak.writeFields(myChannelNumber, myWriteAPIKey);
  if (status == 200) {
    Serial.println("ThingSpeak update successful!");
    thingSpeakInterval = THINGSPEAK_INTERVAL_MS;
  } else {
    Serial.print("ThingSpeak update failed HTTP error ");
    Serial.println(status);
    // Each failed attempt can freeze the Mega for a second or two (and the app
    // times out meanwhile), so try less often until uploads work again.
    thingSpeakInterval = min(thingSpeakInterval * 2, 120000UL);
  }
}

void runAutomaticControl() {
  bool pumpNeeded[2] = { false, false };

  for (uint8_t i = 0; i < 6; i++) {
    float soil = readSoilMoisture(i);
    if (valveState[i]) {
      if (soil >= AUTO_SOIL_STOP_ABOVE) valveState[i] = false;
    } else {
      if (soil < AUTO_SOIL_THRESHOLD) valveState[i] = true;
    }
    digitalWrite(PIN_VALVE[i], valveState[i] ? RELAY_ON : RELAY_OFF);
    if (valveState[i]) pumpNeeded[VALVE_PUMP[i]] = true;
  }

  for (uint8_t p = 0; p < 2; p++) {
    pumpState[p] = pumpNeeded[p];
    digitalWrite(PIN_PUMP[p], pumpNeeded[p] ? RELAY_ON : RELAY_OFF);
  }

  float humiditySum = 0;
  for (uint8_t i = 0; i < 3; i++) {
    float h = dhtSensors[i]->readHumidity();
    humiditySum += isnan(h) ? 0 : h;
  }
  float avgHumidity = humiditySum / 3.0;

  if (mistGroupOn) {
    if (avgHumidity > MIST_OFF_ABOVE) mistGroupOn = false;
  } else {
    if (avgHumidity < MIST_ON_BELOW) mistGroupOn = true;
  }

  for (uint8_t i = 0; i < 4; i++) {
    mistState[i] = mistGroupOn;
    digitalWrite(PIN_MIST[i], mistGroupOn ? MIST_ON : MIST_OFF);
  }
}

void handlePumpCommand(EthernetClient &client, const String &request) {
  int id = getIntParam(request, "id");
  bool on = request.indexOf("state=on") >= 0;
  if (id < 1 || id > 2) { sendPlainText(client, "invalid pump id"); return; }
  uint8_t idx = id - 1;
  pumpState[idx] = on;
  digitalWrite(PIN_PUMP[idx], on ? RELAY_ON : RELAY_OFF);
  sendPlainText(client, "pump " + String(id) + (on ? " on" : " off"));
}

void handleValveCommand(EthernetClient &client, const String &request) {
  int id = getIntParam(request, "id");
  bool on = request.indexOf("state=on") >= 0;
  if (id < 1 || id > 6) { sendPlainText(client, "invalid valve id"); return; }
  uint8_t idx = id - 1;
  valveState[idx] = on;
  digitalWrite(PIN_VALVE[idx], on ? RELAY_ON : RELAY_OFF);
  sendPlainText(client, "valve " + String(id) + (on ? " on" : " off"));
}

void handleMistCommand(EthernetClient &client, const String &request) {
  int id = getIntParam(request, "id");
  bool on = request.indexOf("state=on") >= 0;
  if (id < 1 || id > 4) { sendPlainText(client, "invalid mist id"); return; }
  uint8_t idx = id - 1;
  mistState[idx] = on;
  digitalWrite(PIN_MIST[idx], on ? MIST_ON : MIST_OFF);
  sendPlainText(client, "mist " + String(id) + (on ? " on" : " off"));
}

int getIntParam(const String &request, const String &key) {
  int keyIndex = request.indexOf(key + "=");
  if (keyIndex < 0) return -1;
  int start = keyIndex + key.length() + 1;
  int end = start;
  while (end < (int)request.length() && isDigit(request.charAt(end))) end++;
  if (end == start) return -1;
  return request.substring(start, end).toInt();
}

void updateLcd() {
  lcd.clear();
  delay(2);
  lcd.setCursor(0, 0); lcd.print("Soil% 1-6:");
  lcd.setCursor(0, 1);
  for (uint8_t i = 0; i < 3; i++) { lcd.print((int)readSoilMoisture(i)); lcd.print(i < 2 ? " " : ""); }
  lcd.setCursor(0, 2);
  for (uint8_t i = 3; i < 6; i++) { lcd.print((int)readSoilMoisture(i)); lcd.print(i < 5 ? " " : ""); }
  float tempSum = 0;
  for (uint8_t i = 0; i < 3; i++) { float t = dhtSensors[i]->readTemperature(); tempSum += isnan(t) ? 0 : t; }
  lcd.setCursor(0, 3); lcd.print("Avg Temp: "); lcd.print(tempSum / 3.0, 1); lcd.print("C");
}

void checkLowMoistureAlert() {
  bool anyPlantLow = false;
  for (uint8_t i = 0; i < 6; i++) {
    if (readSoilMoisture(i) < AUTO_SOIL_THRESHOLD) { anyPlantLow = true; break; }
  }
  digitalWrite(PIN_BUZZER, anyPlantLow ? HIGH : LOW);
}

float readSoilMoisture(uint8_t index) {
  int raw = analogRead(PIN_SOIL[index]);
  float percent = (float)(SOIL_DRY_RAW - raw) * 100.0 / (SOIL_DRY_RAW - SOIL_WET_RAW);
  if (percent < 0) percent = 0;
  if (percent > 100) percent = 100;
  return percent;
}

float readPh() {
  int raw = analogRead(PIN_PH);
  float voltage = raw * (5.0 / 1023.0);
  return 7 + ((2.5 - voltage) / 0.18);
}

void handleSensors(EthernetClient &client) {
  float soil[6]; for (uint8_t i = 0; i < 6; i++) soil[i] = readSoilMoisture(i);
  float temp[3], hum[3];
  for (uint8_t i = 0; i < 3; i++) {
    float t = dhtSensors[i]->readTemperature();
    float h = dhtSensors[i]->readHumidity();
    temp[i] = isnan(t) ? 0 : t;
    hum[i] = isnan(h) ? 0 : h;
  }
  float ph = readPh();

  String json = "{";
  json += "\"soil\":[";
  for (uint8_t i = 0; i < 6; i++) { json += String(soil[i], 1); if (i < 5) json += ","; }
  json += "],\"temp\":[";
  for (uint8_t i = 0; i < 3; i++) { json += String(temp[i], 1); if (i < 2) json += ","; }
  json += "],\"humidity\":[";
  for (uint8_t i = 0; i < 3; i++) { json += String(hum[i], 1); if (i < 2) json += ","; }
  json += "],\"ph\":" + String(ph, 2) + ",";
  json += "\"pumps\":[" + String(pumpState[0]?1:0) + "," + String(pumpState[1]?1:0) + "],";
  json += "\"valves\":[";
  for (uint8_t i = 0; i < 6; i++) { json += String(valveState[i]?1:0); if (i < 5) json += ","; }
  json += "],\"mist\":[";
  for (uint8_t i = 0; i < 4; i++) { json += String(mistState[i]?1:0); if (i < 3) json += ","; }
  json += "],\"mode\":\"" + mode + "\"}";

  client.println("HTTP/1.1 200 OK");
  client.println("Content-Type: application/json");
  client.println("Access-Control-Allow-Origin: *");
  client.println("Connection: close");
  client.println();
  client.println(json);
}

void sendPlainText(EthernetClient &client, const String &msg) {
  client.println("HTTP/1.1 200 OK");
  client.println("Content-Type: text/plain");
  client.println("Access-Control-Allow-Origin: *");
  client.println("Connection: close");
  client.println();
  client.println(msg);
}

// ---------------- Control mode ----------------

// Everything OFF: used whenever the mode changes so the new mode starts clean.
// (Without this, switching to MANUAL while watering would leave the pumps and
// valves running with nothing to stop them.)
void allOutputsOff() {
  for (uint8_t i = 0; i < 2; i++) { pumpState[i]  = false; digitalWrite(PIN_PUMP[i],  RELAY_OFF); }
  for (uint8_t i = 0; i < 6; i++) { valveState[i] = false; digitalWrite(PIN_VALVE[i], RELAY_OFF); }
  for (uint8_t i = 0; i < 4; i++) { mistState[i]  = false; digitalWrite(PIN_MIST[i],  MIST_OFF);  }
  mistGroupOn = false;
}

void setControlMode(const String &newMode) {
  if (newMode == mode) return;
  mode = newMode;
  EEPROM.update(EEPROM_MODE_ADDR, mode == "AUTOMATIC" ? 1 : 0);   // only writes when it changed
  allOutputsOff();
  Serial.print("Mode changed to ");
  Serial.println(mode);
}
