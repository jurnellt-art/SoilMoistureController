package com.anthurium.soilcontroller.data;

import android.os.Handler;
import android.os.Looper;

import com.anthurium.soilcontroller.model.SensorReading;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ThingSpeakRepository {

    // Default placeholders - replace with your ThingSpeak Channel ID & Read API Key if private
    private static final String DEFAULT_CHANNEL_ID = "3532877"; 
    private static final String DEFAULT_READ_API_KEY = "KJJ14FK6KM3LCH3D";

    public interface FeedCallback {
        void onSuccess(List<SensorReading> readings);
        void onError(String message);
    }

    public interface LatestFeedCallback {
        void onSuccess(SensorReading reading);
        void onError(String message);
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private String channelId;
    private String readApiKey;

    public ThingSpeakRepository() {
        this(DEFAULT_CHANNEL_ID, DEFAULT_READ_API_KEY);
    }

    public ThingSpeakRepository(String channelId, String readApiKey) {
        this.channelId = (channelId == null || channelId.trim().isEmpty()) ? DEFAULT_CHANNEL_ID : channelId.trim();
        this.readApiKey = readApiKey == null ? "" : readApiKey.trim();
    }

    public void setChannelConfig(String channelId, String readApiKey) {
        if (channelId != null && !channelId.trim().isEmpty()) {
            this.channelId = channelId.trim();
        }
        if (readApiKey != null) {
            this.readApiKey = readApiKey.trim();
        }
    }

    public void fetchLatestFeed(final LatestFeedCallback callback) {
        executor.execute(() -> {
            try {
                String urlStr = "https://api.thingspeak.com/channels/" + channelId + "/feeds/last.json";
                if (!readApiKey.isEmpty()) {
                    urlStr += "?api_key=" + readApiKey;
                }
                String jsonStr = httpGet(urlStr);
                JSONObject obj = new JSONObject(jsonStr);
                SensorReading reading = parseSingleFeed(obj);
                mainHandler.post(() -> callback.onSuccess(reading));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
            }
        });
    }

    public void fetchFeedHistory(final int resultsCount, final FeedCallback callback) {
        executor.execute(() -> {
            try {
                String urlStr = "https://api.thingspeak.com/channels/" + channelId + "/feeds.json?results=" + resultsCount;
                if (!readApiKey.isEmpty()) {
                    urlStr += "&api_key=" + readApiKey;
                }
                String jsonStr = httpGet(urlStr);
                JSONObject root = new JSONObject(jsonStr);
                JSONArray feeds = root.optJSONArray("feeds");
                List<SensorReading> readings = new ArrayList<>();
                if (feeds != null) {
                    for (int i = feeds.length() - 1; i >= 0; i--) { // Reverse for newest first
                        JSONObject item = feeds.getJSONObject(i);
                        readings.add(parseSingleFeed(item));
                    }
                }
                mainHandler.post(() -> callback.onSuccess(readings));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
            }
        });
    }

    private SensorReading parseSingleFeed(JSONObject obj) {
        SensorReading r = new SensorReading();
        r.setTimestamp(toLocalTime(obj.optString("created_at", "")));

        // Fields 1..6: Soil Moisture Sensors 1..6
        List<Float> soilList = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            soilList.add((float) obj.optDouble("field" + i, 0.0));
        }
        r.setSoilMoistureLevels(soilList);

        // Field 7: Average Temperature
        List<Float> tempList = new ArrayList<>();
        tempList.add((float) obj.optDouble("field7", 0.0));
        r.setTemperatures(tempList);

        // Field 8: pH Level
        r.setPhLevel((float) obj.optDouble("field8", 0.0));

        applyStatus(obj.optString("status", ""), r);
        return r;
    }

    // The sketch sends a status text with every update, for example
    // "Mode: AUTOMATIC | Pumps: 1/2 | Valves Open: 2/6 | Mist: 0/4".
    // Older entries without it get mode "UNKNOWN".
    private static void applyStatus(String status, SensorReading r) {
        r.setMode("UNKNOWN");
        if (status == null || status.isEmpty() || status.equals("null")) return;

        Matcher mode = Pattern.compile("Mode:\\s*(\\w+)").matcher(status);
        if (mode.find() && mode.group(1) != null) {
            r.setMode(mode.group(1).toUpperCase(Locale.US));
        }

        r.setPumpStates(countList(status, "Pumps:\\s*(\\d+)/(\\d+)"));
        r.setValveStates(countList(status, "Valves Open:\\s*(\\d+)/(\\d+)"));
        r.setMistStates(countList(status, "Mist:\\s*(\\d+)/(\\d+)"));
    }

    // "2/6" becomes [true, true, false, false, false, false]. Only the COUNT is known,
    // not which pump or valve it is.
    private static List<Boolean> countList(String status, String regex) {
        List<Boolean> list = new ArrayList<>();
        Matcher m = Pattern.compile(regex).matcher(status);
        if (m.find() && m.group(1) != null && m.group(2) != null) {
            int on = Integer.parseInt(m.group(1));
            int total = Integer.parseInt(m.group(2));
            for (int i = 0; i < total; i++) list.add(i < on);
        }
        return list;
    }

    // ThingSpeak timestamps are UTC ("2026-10-14T08:15:30Z"); show them in the phone's time zone.
    private static String toLocalTime(String createdAt) {
        try {
            SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            in.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date d = in.parse(createdAt);
            if (d == null) return createdAt;
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(d);
        } catch (Exception e) {
            return createdAt;
        }
    }

    private String httpGet(String urlString) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            int code = conn.getResponseCode();
            InputStream stream = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            if (stream == null) throw new Exception("HTTP " + code);
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
            }
            if (code < 200 || code >= 300) throw new Exception("HTTP " + code + ": " + sb);
            return sb.toString();
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
