package com.anthurium.soilcontroller.net;

import android.os.Handler;
import android.os.Looper;

import com.anthurium.soilcontroller.model.SensorReading;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ArduinoApiClient {

    public interface SensorCallback {
        void onSuccess(SensorReading reading);
        void onError(String message);
    }

    public interface SimpleCallback {
        void onSuccess(String response);
        void onError(String message);
    }

    private static final int TIMEOUT_MS = 5000;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final String baseUrl;

    public ArduinoApiClient(String baseUrl) {
        if (baseUrl == null || baseUrl.isEmpty()) {
            this.baseUrl = "http://192.168.1.50";
        } else {
            this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        }
    }

    public void fetchSensorData(final SensorCallback callback) {
        executor.execute(() -> {
            try {
                String json = httpGet(baseUrl + "/sensors");
                JSONObject obj = new JSONObject(json);
                SensorReading reading = new SensorReading();
                
                reading.setSoilMoistureLevels(parseFloatArray(obj.optJSONArray("soil")));
                reading.setTemperatures(parseFloatArray(obj.optJSONArray("temp")));
                reading.setHumidities(parseFloatArray(obj.optJSONArray("humidity")));
                reading.setPhLevel((float) obj.optDouble("ph", 0));
                
                reading.setPumpStates(parseBoolArray(obj.optJSONArray("pumps")));
                reading.setValveStates(parseBoolArray(obj.optJSONArray("valves")));
                reading.setMistStates(parseBoolArray(obj.optJSONArray("mist")));
                
                reading.setMode(obj.optString("mode", "MANUAL"));
                postSuccess(callback, reading);
            } catch (IOException | JSONException e) {
                postError(callback, e.getMessage());
            }
        });
    }

    public void setPumpState(int id, boolean on, final SimpleCallback callback) {
        executor.execute(() -> {
            try {
                String response = httpGet(baseUrl + "/pump?id=" + id + "&state=" + (on ? "on" : "off"));
                postSuccess(callback, response);
            } catch (IOException e) {
                postError(callback, e.getMessage());
            }
        });
    }

    public void setValveState(int id, boolean on, final SimpleCallback callback) {
        executor.execute(() -> {
            try {
                String response = httpGet(baseUrl + "/valve?id=" + id + "&state=" + (on ? "on" : "off"));
                postSuccess(callback, response);
            } catch (IOException e) {
                postError(callback, e.getMessage());
            }
        });
    }

    public void setMistState(int id, boolean on, final SimpleCallback callback) {
        executor.execute(() -> {
            try {
                String response = httpGet(baseUrl + "/mist?id=" + id + "&state=" + (on ? "on" : "off"));
                postSuccess(callback, response);
            } catch (IOException e) {
                postError(callback, e.getMessage());
            }
        });
    }

    public void setMode(final String mode, final SimpleCallback callback) {
        executor.execute(() -> {
            try {
                String response = httpGet(baseUrl + "/mode?value=" + mode.toLowerCase());
                postSuccess(callback, response);
            } catch (IOException e) {
                postError(callback, e.getMessage());
            }
        });
    }

    private List<Float> parseFloatArray(JSONArray arr) {
        List<Float> list = new ArrayList<>();
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                list.add((float) arr.optDouble(i, 0));
            }
        }
        return list;
    }

    private List<Boolean> parseBoolArray(JSONArray arr) {
        List<Boolean> list = new ArrayList<>();
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                list.add(arr.optInt(i, 0) == 1);
            }
        }
        return list;
    }

    private String httpGet(String urlString) throws IOException {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            int code = conn.getResponseCode();
            InputStream stream = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
            }
            if (code < 200 || code >= 300) throw new IOException("HTTP " + code + ": " + sb);
            return sb.toString();
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private void postSuccess(SensorCallback cb, SensorReading r) { mainHandler.post(() -> cb.onSuccess(r)); }
    private void postError(SensorCallback cb, String msg) { mainHandler.post(() -> cb.onError(msg)); }
    private void postSuccess(SimpleCallback cb, String res) { mainHandler.post(() -> cb.onSuccess(res)); }
    private void postError(SimpleCallback cb, String msg) { mainHandler.post(() -> cb.onError(msg)); }
}
