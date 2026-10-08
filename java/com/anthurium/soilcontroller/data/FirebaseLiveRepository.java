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
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class FirebaseLiveRepository {

    private static final String NODE_ROOT = "soil_controller";
    private static final String NODE_LIVE_SENSORS = "live_sensors";
    private static final String NODE_CONTROL_COMMANDS = "control_commands";

    public interface LiveDataListener {
        void onDataReceived(SensorReading reading);
        void onError(String message);
    }

    public interface CommandCallback {
        void onSuccess();
        void onError(String message);
    }

    private final DatabaseReference liveSensorsRef;
    private final DatabaseReference controlCommandsRef;
    private ValueEventListener liveListener;

    public FirebaseLiveRepository() {
        FirebaseDatabase db = FirebaseDatabase.getInstance();
        liveSensorsRef = db.getReference(NODE_ROOT).child(NODE_LIVE_SENSORS);
        controlCommandsRef = db.getReference(NODE_ROOT).child(NODE_CONTROL_COMMANDS);
    }

    public void startListeningLiveData(final LiveDataListener listener) {
        stopListeningLiveData();
        liveListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    listener.onError("No sensor data in Firebase cloud yet");
                    return;
                }

                SensorReading r = new SensorReading();
                r.setTimestamp(snapshot.child("timestamp").getValue(String.class));
                r.setSoilMoistureLevels(getFloatList(snapshot, "soil"));
                r.setTemperatures(getFloatList(snapshot, "temp"));
                r.setHumidities(getFloatList(snapshot, "humidity"));
                r.setPhLevel(getFloat(snapshot, "ph"));
                r.setPumpStates(getBoolList(snapshot, "pumps"));
                r.setValveStates(getBoolList(snapshot, "valves"));
                r.setMistStates(getBoolList(snapshot, "mist"));
                
                String mode = snapshot.child("mode").getValue(String.class);
                r.setMode(mode != null ? mode : "MANUAL");

                listener.onDataReceived(r);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                listener.onError(error.getMessage());
            }
        };
        liveSensorsRef.addValueEventListener(liveListener);
    }

    public void stopListeningLiveData() {
        if (liveListener != null) {
            liveSensorsRef.removeEventListener(liveListener);
            liveListener = null;
        }
    }

    public void setMode(String mode, final CommandCallback callback) {
        Map<String, Object> update = new HashMap<>();
        update.put("mode", mode.toUpperCase(Locale.US));
        controlCommandsRef.updateChildren(update)
                .addOnSuccessListener(aVoid -> {
                    liveSensorsRef.child("mode").setValue(mode.toUpperCase(Locale.US));
                    if (callback != null) callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    if (callback != null) callback.onError(e.getMessage());
                });
    }

    public void setPumpState(int pumpIndex, boolean on, final CommandCallback callback) {
        controlCommandsRef.child("pumps").child(String.valueOf(pumpIndex)).setValue(on ? 1 : 0)
                .addOnSuccessListener(aVoid -> {
                    liveSensorsRef.child("pumps").child(String.valueOf(pumpIndex)).setValue(on ? 1 : 0);
                    if (callback != null) callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    if (callback != null) callback.onError(e.getMessage());
                });
    }

    public void setValveState(int valveIndex, boolean on, final CommandCallback callback) {
        controlCommandsRef.child("valves").child(String.valueOf(valveIndex)).setValue(on ? 1 : 0)
                .addOnSuccessListener(aVoid -> {
                    liveSensorsRef.child("valves").child(String.valueOf(valveIndex)).setValue(on ? 1 : 0);
                    if (callback != null) callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    if (callback != null) callback.onError(e.getMessage());
                });
    }

    public void setMistState(int mistIndex, boolean on, final CommandCallback callback) {
        controlCommandsRef.child("mist").child(String.valueOf(mistIndex)).setValue(on ? 1 : 0)
                .addOnSuccessListener(aVoid -> {
                    liveSensorsRef.child("mist").child(String.valueOf(mistIndex)).setValue(on ? 1 : 0);
                    if (callback != null) callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    if (callback != null) callback.onError(e.getMessage());
                });
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
            Long val = item.getValue(Long.class);
            if (val != null) {
                list.add(val == 1L);
            } else {
                Boolean b = item.getValue(Boolean.class);
                list.add(b != null && b);
            }
        }
        return list;
    }
}
