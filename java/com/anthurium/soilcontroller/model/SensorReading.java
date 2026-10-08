package com.anthurium.soilcontroller.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Updated to support the 6-plant setup from the Mega 2560 sketch.
 */
public class SensorReading {

    private long id;
    private String timestamp;
    
    // Arrays matching the Arduino sketch arrays
    private List<Float> soilMoistureLevels = new ArrayList<>(); // 6 sensors
    private List<Float> temperatures = new ArrayList<>();       // 3 sensors
    private List<Float> humidities = new ArrayList<>();         // 3 sensors
    private float phLevel;
    
    private List<Boolean> pumpStates = new ArrayList<>();       // 2 pumps
    private List<Boolean> valveStates = new ArrayList<>();      // 6 valves
    private List<Boolean> mistStates = new ArrayList<>();       // 4 mist makers
    
    private String mode; // "MANUAL" or "AUTOMATIC"

    public SensorReading() {
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public List<Float> getSoilMoistureLevels() { return soilMoistureLevels; }
    public void setSoilMoistureLevels(List<Float> soilMoistureLevels) { this.soilMoistureLevels = soilMoistureLevels; }

    public List<Float> getTemperatures() { return temperatures; }
    public void setTemperatures(List<Float> temperatures) { this.temperatures = temperatures; }

    public List<Float> getHumidities() { return humidities; }
    public void setHumidities(List<Float> humidities) { this.humidities = humidities; }

    public float getPhLevel() { return phLevel; }
    public void setPhLevel(float phLevel) { this.phLevel = phLevel; }

    public List<Boolean> getPumpStates() { return pumpStates; }
    public void setPumpStates(List<Boolean> pumpStates) { this.pumpStates = pumpStates; }

    public List<Boolean> getValveStates() { return valveStates; }
    public void setValveStates(List<Boolean> valveStates) { this.valveStates = valveStates; }

    public List<Boolean> getMistStates() { return mistStates; }
    public void setMistStates(List<Boolean> mistStates) { this.mistStates = mistStates; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    
    // Legacy helper for single-value UI parts (uses first index)
    public float getSoilMoisturePercent() { 
        return soilMoistureLevels.isEmpty() ? 0 : soilMoistureLevels.get(0); 
    }
    public void setSoilMoisturePercent(float value) {
        if (soilMoistureLevels.isEmpty()) {
            soilMoistureLevels.add(value);
        } else {
            soilMoistureLevels.set(0, value);
        }
    }
    public float getTemperatureC() { 
        return temperatures.isEmpty() ? 0 : temperatures.get(0); 
    }
    public void setTemperatureC(float value) {
        if (temperatures.isEmpty()) {
            temperatures.add(value);
        } else {
            temperatures.set(0, value);
        }
    }
    public float getHumidityPercent() { 
        return humidities.isEmpty() ? 0 : humidities.get(0); 
    }
    public void setHumidityPercent(float value) {
        if (humidities.isEmpty()) {
            humidities.add(value);
        } else {
            humidities.set(0, value);
        }
    }
    public boolean isPumpOn() { 
        return pumpStates.contains(true); 
    }
}
