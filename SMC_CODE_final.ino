/*
  Soil Moisture Controller For Potted Anthurium with IoT
  Arduino Mega 2560 + Ethernet Shield (W5100)

  Hardware on this sketch:
    - 6x Capacitive soil moisture sensors -> A0-A5 (one per valve/plant)
    - 1x pH sensor (PH-4502C)            -> A7
    - 3x DHT22 temp/humidity sensors     -> pins 46, 48, 9
      (NOTE: pin 50 was originally used here but conflicts with the
      Ethernet shield's SPI bus on the Mega — pins 50-53 are reserved
      for SPI and cannot be reused for anything else. Moved to pin 9.)
    - 8-channel relay module:
        IN1 -> pin 34  = Pump 1 (submersible lift pump)
        IN2 -> pin 36  = Pump 2 (diaphragm high pressure pump)
        IN3 -> pin 22  = Valve 1
        IN4 -> pin 24  = Valve 2
        IN5 -> pin 26  = Valve 3
        IN6 -> pin 28  = Valve 4
        IN7 -> pin 30  = Valve 5
        IN8 -> pin 32  = Valve 6
    - 4-channel relay module (mist makers):
        IN1 -> pin 38  = Mist maker 1
        IN2 -> pin 40  = Mist maker 2
        IN3 -> pin 42  = Mist maker 3
        IN4 -> pin 44  = Mist maker 4
    - Buzzer                              -> pin 8
    - 20x4 I2C LCD Display                -> SDA = pin 20, SCL = pin 21 (Mega's dedicated I2C pins)
      Steady layout (no page rotation): all 6 soil moisture readings +
      average temperature across the 3 DHT22 sensors, refreshed every 3s.
      Humidity, pH, and pump/valve/mode status are not shown on the LCD
      (still available via the app's Sensor Data / Pump Control screens).
    - Ethernet shield SPI                 -> pins 50-53 (reserved, do not reuse)

  HTTP API exposed to the Android app:
    GET /sensors
      -> {"soil":[..6 values..],"temp":[..3..],"humidity":[..3..],"ph":x,
          "pumps":[p1,p2],"valves":[v1..v6],"mist":[m1..m4],"mode":"MANUAL"}
    GET /pump?id=1|2&state=on|off
    GET /valve?id=1-6&state=on|off
    GET /mist?id=1-4&state=on|off
    GET /mode?value=manual|automatic
*/

#include <SPI.h>
#include <Ethernet.h>
#include <DHT.h>
#include <Wire.h>
#include <LiquidCrystal_I2C.h>

// ---- Network config ----
byte mac[] = { 0xDE, 0xAD, 0xBE, 0xEF, 0xFE, 0xED };
IPAddress ip(192, 168, 0, 50);      // Static IP for COAG FACULTY network (192.168.0.x)
IPAddress myDns(192, 168, 0, 1);
IPAddress gateway(192, 168, 0, 1);
IPAddress subnet(255, 255, 255, 0);
EthernetServer server(80);

// ---- Sensor pins ----
const uint8_t PIN_SOIL[6]   = { A0, A1, A2, A3, A4, A5 };
const uint8_t PIN_PH        = A7;
const uint8_t PIN_DHT[3]    = { 46, 48, 9 }; // pin 9 used instead of 50 (SPI conflict)

// ---- Relay pins (8-channel module: 2 pumps + 6 valves) ----
const uint8_t PIN_PUMP[2]   = { 34, 36 };
const uint8_t PIN_VALVE[6]  = { 22, 24, 26, 28, 30, 32 };

// ---- Relay pins (4-channel module: mist makers) ----
const uint8_t PIN_MIST[4]   = { 38, 40, 42, 44 };

const uint8_t PIN_BUZZER    = 8;

DHT dht0(PIN_DHT[0], DHT22);
DHT dht1(PIN_DHT[1], DHT22);
DHT dht2(PIN_DHT[2], DHT22);
DHT* dhtSensors[3] = { &dht0, &dht1, &dht2 };

// 20x4 I2C LCD - default address is usually 0x27 or 0x3F depending on the
// backpack chip; if the display shows nothing/garbage, try 0x3F instead.
LiquidCrystal_I2C lcd(0x27, 20, 4);

unsigned long lastLcdUpdate = 0;
const unsigned long LCD_UPDATE_INTERVAL_MS = 3000; // how often the LCD readout refreshes

// ---- State ----
bool pumpState[2]  = { false, false };
bool valveState[6] = { false, false, false, false, false, false };
bool mistState[4]  = { false, false, false, false };
String mode = "MANUAL"; // or "AUTOMATIC"

// Soil moisture threshold (%) below which Automatic mode opens that valve
const float AUTO_SOIL_THRESHOLD = 35.0;

// Humidity hysteresis for the mist makers, tuned for Anthurium andraeanum
// (Flamingo Flower), which prefers ~60-80% ambient humidity.
// Mist turns ON once the average of all 3 DHT22 readings drops below
// MIST_ON_BELOW, and stays on until it climbs back above MIST_OFF_ABOVE.
// The gap between the two prevents rapid on/off flicker at one boundary.
const float MIST_ON_BELOW   = 55.0;
const float MIST_OFF_ABOVE  = 70.0;
bool mistGroupOn = false; // tracks current state for the hysteresis band

void setup() {
  Serial.begin(9600);

  // Initialize SPI Select pins explicitly before starting Ethernet
  pinMode(4, OUTPUT);
  digitalWrite(4, HIGH);  // Disable SD card SPI

  pinMode(10, OUTPUT);
  digitalWrite(10, HIGH); // Disable W5100 CS temporarily to reset SPI bus

  pinMode(53, OUTPUT);    // Hardware SS pin on Arduino Mega MUST be OUTPUT
  digitalWrite(53, HIGH);

  delay(100);             // Power-on delay for W5100 chip startup

  for (uint8_t i = 0; i < 2; i++) {
    pinMode(PIN_PUMP[i], OUTPUT);
    digitalWrite(PIN_PUMP[i], LOW);
  }
  for (uint8_t i = 0; i < 6; i++) {
    pinMode(PIN_VALVE[i], OUTPUT);
    digitalWrite(PIN_VALVE[i], LOW);
  }
  for (uint8_t i = 0; i < 4; i++) {
    pinMode(PIN_MIST[i], OUTPUT);
    digitalWrite(PIN_MIST[i], LOW);
  }
  pinMode(PIN_BUZZER, OUTPUT);
  digitalWrite(PIN_BUZZER, LOW);

  for (uint8_t i = 0; i < 3; i++) {
    dhtSensors[i]->begin();
  }

  Wire.begin();
  lcd.init();
  lcd.backlight();
  lcd.setCursor(0, 0);
  lcd.print("Anthurium IoT");
  lcd.setCursor(0, 1);
  lcd.print("Starting up...");

  Ethernet.begin(mac, ip, myDns, gateway, subnet);
  server.begin();

  Serial.print("Server started at ");
  Serial.println(Ethernet.localIP());

  lcd.clear();
  lcd.setCursor(0, 0);
  lcd.print("IP:");
  lcd.setCursor(0, 1);
  lcd.print(Ethernet.localIP());
  delay(2000); // give you time to read the IP before it switches to sensor pages
}

void loop() {
  if (mode == "AUTOMATIC") {
    runAutomaticControl();
  }

  // Low-moisture alert runs in both Manual and Automatic mode, since in
  // Manual mode nothing waters a dry plant automatically — the buzzer is
  // the only thing that tells you a plant needs attention.
  checkLowMoistureAlert();

  if (millis() - lastLcdUpdate >= LCD_UPDATE_INTERVAL_MS) {
    lastLcdUpdate = millis();
    updateLcd();
  }

  EthernetClient client = server.available();
  if (client) {
    String request = client.readStringUntil('\r');
    client.flush();

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
        mode = "AUTOMATIC";
        sendPlainText(client, "mode automatic");
      } else {
        mode = "MANUAL";
        sendPlainText(client, "mode manual");
      }
    } else {
      sendPlainText(client, "not found");
    }

    delay(1);
    client.stop();
  }
}

// ---------------- Automatic control ----------------

void runAutomaticControl() {
  bool anyValveOpen = false;

  for (uint8_t i = 0; i < 6; i++) {
    float soil = readSoilMoisture(i);
    bool shouldOpen = soil < AUTO_SOIL_THRESHOLD;
    valveState[i] = shouldOpen;
    digitalWrite(PIN_VALVE[i], shouldOpen ? HIGH : LOW);
    if (shouldOpen) anyValveOpen = true;
  }

  // Run both pumps together whenever at least one valve is open.
  // Adjust this if you want the two pumps used differently
  // (e.g. pump 1 for lifting water, pump 2 only for higher-pressure lines).
  pumpState[0] = anyValveOpen;
  pumpState[1] = anyValveOpen;
  digitalWrite(PIN_PUMP[0], anyValveOpen ? HIGH : LOW);
  digitalWrite(PIN_PUMP[1], anyValveOpen ? HIGH : LOW);

  // Mist makers: averaged humidity across all 3 DHT22 sensors, all 4 mist
  // makers run as one group (misting raises ambient humidity broadly rather
  // than just near one sensor, so per-sensor mapping isn't meaningful here).
  float humiditySum = 0;
  for (uint8_t i = 0; i < 3; i++) {
    float h = dhtSensors[i]->readHumidity();
    humiditySum += isnan(h) ? 0 : h;
  }
  float avgHumidity = humiditySum / 3.0;

  if (mistGroupOn) {
    // Currently on: keep misting until humidity recovers above the upper bound
    if (avgHumidity > MIST_OFF_ABOVE) {
      mistGroupOn = false;
    }
  } else {
    // Currently off: start misting once humidity drops below the lower bound
    if (avgHumidity < MIST_ON_BELOW) {
      mistGroupOn = true;
    }
  }

  for (uint8_t i = 0; i < 4; i++) {
    mistState[i] = mistGroupOn;
    digitalWrite(PIN_MIST[i], mistGroupOn ? HIGH : LOW);
  }
}

// ---------------- Command handlers ----------------

void handlePumpCommand(EthernetClient &client, const String &request) {
  int id = getIntParam(request, "id");
  bool on = request.indexOf("state=on") >= 0;

  if (id < 1 || id > 2) {
    sendPlainText(client, "invalid pump id");
    return;
  }

  uint8_t idx = id - 1;
  pumpState[idx] = on;
  digitalWrite(PIN_PUMP[idx], on ? HIGH : LOW);
  sendPlainText(client, "pump " + String(id) + (on ? " on" : " off"));
}

void handleValveCommand(EthernetClient &client, const String &request) {
  int id = getIntParam(request, "id");
  bool on = request.indexOf("state=on") >= 0;

  if (id < 1 || id > 6) {
    sendPlainText(client, "invalid valve id");
    return;
  }

  uint8_t idx = id - 1;
  valveState[idx] = on;
  digitalWrite(PIN_VALVE[idx], on ? HIGH : LOW);
  sendPlainText(client, "valve " + String(id) + (on ? " on" : " off"));
}

void handleMistCommand(EthernetClient &client, const String &request) {
  int id = getIntParam(request, "id");
  bool on = request.indexOf("state=on") >= 0;

  if (id < 1 || id > 4) {
    sendPlainText(client, "invalid mist id");
    return;
  }

  uint8_t idx = id - 1;
  mistState[idx] = on;
  digitalWrite(PIN_MIST[idx], on ? HIGH : LOW);
  sendPlainText(client, "mist " + String(id) + (on ? " on" : " off"));
}

// Extracts an integer value for "id=" from a request like "GET /valve?id=3&state=on"
int getIntParam(const String &request, const String &key) {
  int keyIndex = request.indexOf(key + "=");
  if (keyIndex < 0) return -1;
  int start = keyIndex + key.length() + 1;
  int end = start;
  while (end < (int)request.length() && isDigit(request.charAt(end))) {
    end++;
  }
  if (end == start) return -1;
  return request.substring(start, end).toInt();
}

// ---------------- LCD display ----------------

// Steady (non-rotating) layout: soil moisture for all 6 plants stays
// permanently visible, plus the average temperature across the 3 DHT22
// sensors. Humidity, pH, and pump/valve/mode status are no longer shown
// on the LCD (still available via the app) to make room for this.
// Called every LCD_UPDATE_INTERVAL_MS from loop().
void updateLcd() {
  lcd.clear();
  delay(2);

  lcd.setCursor(0, 0);
  lcd.print("Soil% 1-6:");

  lcd.setCursor(0, 1);
  for (uint8_t i = 0; i < 3; i++) {
    lcd.print((int)readSoilMoisture(i));
    lcd.print(i < 2 ? " " : "");
  }

  lcd.setCursor(0, 2);
  for (uint8_t i = 3; i < 6; i++) {
    lcd.print((int)readSoilMoisture(i));
    lcd.print(i < 5 ? " " : "");
  }

  float tempSum = 0;
  for (uint8_t i = 0; i < 3; i++) {
    float t = dhtSensors[i]->readTemperature();
    tempSum += isnan(t) ? 0 : t;
  }
  float avgTemp = tempSum / 3.0;

  lcd.setCursor(0, 3);
  lcd.print("Avg Temp: ");
  lcd.print(avgTemp, 1);
  lcd.print("C");
}

// ---------------- Low moisture buzzer alert ----------------

// Sounds the buzzer continuously while at least one plant's soil moisture
// is below AUTO_SOIL_THRESHOLD, and silences it once all plants recover.
// This is an active buzzer (per the parts list), so driving the pin HIGH
// is all that's needed — it produces its own tone/beep pattern internally.
void checkLowMoistureAlert() {
  bool anyPlantLow = false;
  for (uint8_t i = 0; i < 6; i++) {
    if (readSoilMoisture(i) < AUTO_SOIL_THRESHOLD) {
      anyPlantLow = true;
      break;
    }
  }
  digitalWrite(PIN_BUZZER, anyPlantLow ? HIGH : LOW);
}

// ---------------- Sensor reading ----------------

float readSoilMoisture(uint8_t index) {
  // TODO: calibrate raw ADC (0-1023) against dry/wet readings for each
  // Capacitive Soil Moisture Sensor V1.2 â€” they may not all read identically.
  int raw = analogRead(PIN_SOIL[index]);
  float percent = map(raw, 1023, 300, 0, 100); // adjust calibration values
  if (percent < 0) percent = 0;
  if (percent > 100) percent = 100;
  return percent;
}

float readPh() {
  // TODO: calibrate against the PH-4502C using pH 4/7/10 buffer solutions.
  int raw = analogRead(PIN_PH);
  float voltage = raw * (5.0 / 1023.0);
  float ph = 7 + ((2.5 - voltage) / 0.18); // placeholder formula, calibrate!
  return ph;
}

// ---------------- HTTP responses ----------------

void handleSensors(EthernetClient &client) {
  float soil[6];
  for (uint8_t i = 0; i < 6; i++) {
    soil[i] = readSoilMoisture(i);
  }

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
  for (uint8_t i = 0; i < 6; i++) {
    json += String(soil[i], 1);
    if (i < 5) json += ",";
  }
  json += "],";

  json += "\"temp\":[";
  for (uint8_t i = 0; i < 3; i++) {
    json += String(temp[i], 1);
    if (i < 2) json += ",";
  }
  json += "],";

  json += "\"humidity\":[";
  for (uint8_t i = 0; i < 3; i++) {
    json += String(hum[i], 1);
    if (i < 2) json += ",";
  }
  json += "],";

  json += "\"ph\":" + String(ph, 2) + ",";

  json += "\"pumps\":[";
  json += String(pumpState[0] ? 1 : 0) + "," + String(pumpState[1] ? 1 : 0);
  json += "],";

  json += "\"valves\":[";
  for (uint8_t i = 0; i < 6; i++) {
    json += String(valveState[i] ? 1 : 0);
    if (i < 5) json += ",";
  }
  json += "],";

  json += "\"mist\":[";
  for (uint8_t i = 0; i < 4; i++) {
    json += String(mistState[i] ? 1 : 0);
    if (i < 3) json += ",";
  }
  json += "],";

  json += "\"mode\":\"" + mode + "\"";
  json += "}";

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
