package com.example.equalizerapp.Services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.audiofx.Equalizer;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

public class EqualizerService extends Service {
    private Equalizer equalizer;
    private static final String TAG = "EqualizerService";
    SharedPreferences prefs;
    private static final String CHANNEL_ID = "EqualizerServiceChannel";
    public static final String ACTION_SET_BAND = "com.example.soundapp.SET_BAND";
    public static final String ACTION_READY = "com.example.soundapp.READY";
    public static final String ACTION_USE_PRESET = "com.example.soundapp.USE_PRESET";
    public static final String EXTRA_PRESET_POS = "preset_position";
    public static final String EXTRA_BAND = "band";
    public static final String EXTRA_LEVEL = "level";

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, createNotificationChannel());
        }
        equalizer = new Equalizer(0, 0);
        equalizer.setEnabled(true);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_SET_BAND.equals(intent.getAction())) {
            prefs = getSharedPreferences("EQ_PREFS", Context.MODE_PRIVATE);
            prefs.edit().putInt("selectedPreset", 0).apply();
            int band = intent.getIntExtra(EXTRA_BAND, -1);
            int level = intent.getIntExtra(EXTRA_LEVEL, 0);
            equalizer.setBandLevel((short) band, (short)level);
            prefs.edit().putInt("band" + band, level).commit();

            for(int i = 0; i < 5; i++){
                Log.d("band", "band" + i + ": " + prefs.getInt("band" + i, -9));
            }
        }
        else if(intent != null && ACTION_USE_PRESET.equals(intent.getAction())){
            int position = intent.getIntExtra(EXTRA_PRESET_POS, 0);
            prefs = getSharedPreferences("EQ_PREFS", Context.MODE_PRIVATE);
            prefs.edit().putInt("selectedPreset", position).apply(); // ← lưu lại preset hiện tại
            if (position == 0) {
                Log.d("ApplyPreset", "Custom selected, skip equalizer.usePreset");
            }else{
                int eqPresetIndex = position - 1;
                equalizer.usePreset((short) eqPresetIndex);
                short numberOfBands = equalizer.getNumberOfBands();
                SharedPreferences.Editor editor = prefs.edit();
                for (short band = 0; band < numberOfBands; band++) {
                    int level = equalizer.getBandLevel(band);
                    editor.putInt("band" + band, level);
                }

                editor.apply();
                Log.d("ApplyPreset", "ApplyPreset successfully, position = " + position);
            }

            Intent updateIntent = new Intent(ACTION_READY);
            sendBroadcast(updateIntent);
        }
        else {
            // Luôn apply lại preset khi service được khởi động lại
            applySavedEqualizer();
            LoadDataPresent();
            // Gửi ACTION_READY để Activity update UI
            Intent readyIntent = new Intent(ACTION_READY);
            sendBroadcast(readyIntent);
        }
        return START_STICKY;
    }
    private void applySavedEqualizer() {
        try {
            if (equalizer == null) return;
            prefs = getSharedPreferences("EQ_PREFS", Context.MODE_PRIVATE);
            short numberOfBands = equalizer.getNumberOfBands();
            prefs.edit().putInt("numberOfBands", numberOfBands).commit();

            short minLevel = equalizer.getBandLevelRange()[0];
            short maxLevel = equalizer.getBandLevelRange()[1];
            prefs.edit().putInt("minLevel", minLevel).apply();
            prefs.edit().putInt("maxLevel", maxLevel).apply();

            for (short band = 0; band < numberOfBands; band++) {
                int level = prefs.getInt("band" + band, 0);
                equalizer.setBandLevel(band, (short)level);
            }

            Log.d(TAG, "Equalizer applied successfully in service");

        } catch (Exception e) {
            Log.e(TAG, "Error applying equalizer: " + e.getMessage());
        }
    }
    public void LoadDataPresent(){
        if(equalizer == null) return;
        prefs = getSharedPreferences("EQ_PREFS", Context.MODE_PRIVATE);
        int numberOfPresets = equalizer.getNumberOfPresets();
        prefs.edit().putInt("numberOfPresets", numberOfPresets).commit();
        for (short i = 0; i < numberOfPresets; i++) {
            prefs.edit().putString("preset" + i, equalizer.getPresetName(i)).commit();
        }
    }
    private Notification createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Equalizer Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Audio Enhancement");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Equalizer Service")
                .setContentText("Equalizer is applied after boot")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}