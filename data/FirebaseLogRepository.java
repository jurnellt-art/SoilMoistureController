package com.anthurium.soilcontroller.data;

import androidx.annotation.NonNull;

import com.anthurium.soilcontroller.model.SensorReading;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class FirebaseLogRepository {

    private static final String NODE_LOGS = "sensor_logs";

    public interface WriteCallback {
        void onSuccess();
        void onError(String message);
    }

    public interface HistoryListener {
        void onDataChanged(List<SensorReading> readings);
        void onError(String message);
    }

    private final DatabaseReference logsRef;
    private ValueEventListener activeListener;

    public FirebaseLogRepository() {
        FirebaseDatabase db;
        try {
            db = FirebaseDatabase.getInstance("https://soil-moisture-controller-default-rtdb.firebaseio.com");
        } catch (Exception e) {
            db = FirebaseDatabase.getInstance();
        }
        logsRef = db.getReference(NODE_LOGS);
    }

    public void saveReading(SensorReading reading, final WriteCallback callback) {
        if (reading.getTimestamp() == null) {
            reading.setTimestamp(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));
        }

        String key = logsRef.push().getKey();
        if (key == null) {
            callback.onError("Could not generate log id");
            return;
        }

        Map<String, Object> value = new HashMap<>();
        value.put("timestamp", reading.getTimestamp());
        value.put("soil", reading.getSoilMoistureLevels());
        value.put("temp", reading.getTemperatures());
        value.put("humidity", reading.getHumidities());
        value.put("ph", reading.getPhLevel());
        value.put("pumps", reading.getPumpStates());
        value.put("valves", reading.getValveStates());
        value.put("mist", reading.getMistStates());
        value.put("mode", reading.getMode());

        logsRef.child(key).setValue(value)
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public void listenForHistory(final HistoryListener listener) {
        stopListening();
        activeListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<SensorReading> readings = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    SensorReading r = new SensorReading();
                    r.setTimestamp(child.child("timestamp").getValue(String.class));
                    r.setSoilMoistureLevels(getFloatList(child, "soil"));
                    r.setTemperatures(getFloatList(child, "temp"));
                    r.setHumidities(getFloatList(child, "humidity"));
                    r.setPhLevel(getFloat(child, "ph"));
                    r.setPumpStates(getBoolList(child, "pumps"));
                    r.setValveStates(getBoolList(child, "valves"));
                    r.setMistStates(getBoolList(child, "mist"));
                    r.setMode(child.child("mode").getValue(String.class));
                    readings.add(r);
                }
                Collections.sort(readings, (a, b) -> {
                    String ta = a.getTimestamp() == null ? "" : a.getTimestamp();
                    String tb = b.getTimestamp() == null ? "" : b.getTimestamp();
                    return tb.compareTo(ta);
                });
                listener.onDataChanged(readings);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                listener.onError(error.getMessage());
            }
        };
        logsRef.addValueEventListener(activeListener);
    }

    public void stopListening() {
        if (activeListener != null) {
            logsRef.removeEventListener(activeListener);
            activeListener = null;
        }
    }

    public void clearAll(final WriteCallback callback) {
        logsRef.removeValue()
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    private float getFloat(DataSnapshot snapshot, String key) {
        Double d = snapshot.child(key).getValue(Double.class);
        return d == null ? 0f : d.floatValue();
    }

    private List<Float> getFloatList(DataSnapshot snapshot, String key) {
        List<Float> list = new ArrayList<>();
        DataSnapshot listNode = snapshot.child(key);
        for (DataSnapshot item : listNode.getChildren()) {
            Double d = item.getValue(Double.class);
            if (d != null) list.add(d.floatValue());
        }
        return list;
    }

    private List<Boolean> getBoolList(DataSnapshot snapshot, String key) {
        List<Boolean> list = new ArrayList<>();
        DataSnapshot listNode = snapshot.child(key);
        for (DataSnapshot item : listNode.getChildren()) {
            Boolean b = item.getValue(Boolean.class);
            if (b != null) list.add(b);
        }
        return list;
    }
}
