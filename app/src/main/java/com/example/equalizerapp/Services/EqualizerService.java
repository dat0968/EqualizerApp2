package com.example.equalizerapp.Services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.LoudnessEnhancer;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.example.equalizerapp.MainActivity;
import com.example.equalizerapp.R;

public class EqualizerService extends Service {
    private Equalizer equalizer;
    private BassBoost bassBoost;
    private LoudnessEnhancer loudnessEnhancer;
    private static final String TAG = "EqualizerService";
    SharedPreferences prefs;
    private static final String CHANNEL_ID = "EqualizerServiceChannel";
    public static final String ACTION_SET_BAND = "com.example.soundapp.SET_BAND";
    public static final String ACTION_READY = "com.example.soundapp.READY";
    public static final String ACTION_USE_PRESET = "com.example.soundapp.USE_PRESET";
    public static final String EXTRA_PRESET_POS = "preset_position";
    public static final String EXTRA_BAND = "band";
    public static final String EXTRA_LEVEL = "level";
    public static final String ACTION_SET_BASSBOOST = "ACTION_SET_BASSBOOST";
    public static final String EXTRA_BASSBOOST_LEVEL = "EXTRA_BASSBOOST_LEVEL";
    public static final String ACTION_SET_BOOSTER = "ACTION_SET_BOOSTER";
    public static final String EXTRA_BOOSTER_LEVEL = "EXTRA_BOOSTER_LEVEL";

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, createNotificationChannel());
        }
        equalizer = new Equalizer(0, 0);
        equalizer.setEnabled(true);

        bassBoost = new BassBoost(0, 0);
        bassBoost.setEnabled(true);

        loudnessEnhancer  = new LoudnessEnhancer(0);
        loudnessEnhancer.setEnabled(true);
    }

    @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
        // Xử lý Booster
        if (intent != null && ACTION_SET_BOOSTER.equals(intent.getAction())) {
            int level = intent.getIntExtra(EXTRA_BOOSTER_LEVEL, 0);
            if (loudnessEnhancer != null) {
                loudnessEnhancer.setTargetGain(level);
            }
        }

        // Xử lý BassBoost
        if (intent != null && ACTION_SET_BASSBOOST.equals(intent.getAction())) {
            int level = intent.getIntExtra(EXTRA_BASSBOOST_LEVEL, 0);
            if (bassBoost != null) {
                bassBoost.setStrength((short) level);
            }
        }

        // Xử lý Equalizer
        if (intent != null && ACTION_SET_BAND.equals(intent.getAction())) {
            prefs = getSharedPreferences("EQ_PREFS", Context.MODE_PRIVATE);
            prefs.edit().putInt("selectedPreset", 0).commit();
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

            // Lưu vị trí preset được chọn
            prefs.edit().putInt("selectedPreset", position).commit();
            SharedPreferences.Editor editor = prefs.edit();

            int numberOfSystemPresets = equalizer.getNumberOfPresets();
            int numberOfUserPresets = prefs.getInt("numberOfUserPresets", 0);

            Log.d("USE_PRESET", "Position: " + position + ", System: " + numberOfSystemPresets + ", User: " + numberOfUserPresets);

            if (position == 0) {
                // Custom: áp dụng các band đang lưu trong SharedPreferences
                for (short band = 0; band < equalizer.getNumberOfBands(); band++) {
                    int level = prefs.getInt("band_custom" + band, 0);
                    //int level = prefs.getInt("band_custom" + band, 0);
                    prefs.edit().putInt("band" + band, level).commit();
                    Log.d("level_band_custom", "level_band_custom" + band + ": " + level);
                    equalizer.setBandLevel(band, (short) level);
                }
                Log.d("ApplyPreset", "Custom preset applied");
            } else if (position <= numberOfSystemPresets) {
                int eqPresetIndex = position - 1;
                equalizer.usePreset((short) eqPresetIndex);
                Log.d("ApplyPreset", "System preset " + eqPresetIndex + " applied");

                // Cập nhật UI với giá trị của preset hệ thống
                short numberOfBands = equalizer.getNumberOfBands();
                for (short band = 0; band < numberOfBands; band++) {
                    int level = equalizer.getBandLevel(band);
                    editor.putInt("band" + band, level);
                }
                editor.commit();
            } else {
                // Preset do user tạo
                int userPresetIndex = position - numberOfSystemPresets - 1;
                Log.d("ApplyPreset", "User preset " + userPresetIndex + " applied (position " + position + ")");

                for (short band = 0; band < equalizer.getNumberOfBands(); band++) {
                    int level = prefs.getInt("userPreset" + userPresetIndex + "_band" + band, 0);
                    equalizer.setBandLevel(band, (short) level);
                    // Cập nhật band hiện tại để UI hiển thị đúng
                    editor.putInt("band" + band, level);
                    Log.d("ApplyUserPreset", "Band " + band + " = " + level + " (from userPreset" + userPresetIndex + "_band" + band + ")");
                }
                editor.commit();
            }

            Intent readyIntent = new Intent(ACTION_READY);
            readyIntent.setPackage(getPackageName());
            sendBroadcast(readyIntent);
        }
        else {
            applySavedEqualizer();
            applySavedBassBoost();
            applySavedBooster();
            LoadDataPresent();
            //Gửi ACTION_READY để Activity update UI
            Intent readyIntent = new Intent(ACTION_READY);
            readyIntent.setPackage(getPackageName());
            sendBroadcast(readyIntent);
        }
        return START_STICKY;
    }
    private  void applySavedBooster(){
        if(bassBoost == null) return;
        prefs = getSharedPreferences("EQ_PREFS", Context.MODE_PRIVATE);
        int savedLevel = prefs.getInt("booster_level", 0);
        loudnessEnhancer.setTargetGain(savedLevel);
    }
    private  void applySavedBassBoost(){
        if(bassBoost == null) return;
        prefs = getSharedPreferences("EQ_PREFS", Context.MODE_PRIVATE);
        int savedLevel = prefs.getInt("bassboost_level", 0);
        bassBoost.setStrength((short) savedLevel);
    }
    private void applySavedEqualizer() {
        try {
            if (equalizer == null) return;
            prefs = getSharedPreferences("EQ_PREFS", Context.MODE_PRIVATE);
            short numberOfBands = equalizer.getNumberOfBands();
            prefs.edit().putInt("numberOfBands", numberOfBands).commit();

            short minLevel = equalizer.getBandLevelRange()[0];
            short maxLevel = equalizer.getBandLevelRange()[1];
            prefs.edit().putInt("minLevel", minLevel).commit();
            prefs.edit().putInt("maxLevel", maxLevel).commit();

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

        // Chỉ lưu thông tin preset hệ thống, KHÔNG ghi đè numberOfUserPresets
        int numberOfSystemPresets = equalizer.getNumberOfPresets();
        prefs.edit().putInt("numberOfSystemPresets", numberOfSystemPresets).commit();

        // Lưu tên các preset hệ thống với key khác
        for (short i = 0; i < numberOfSystemPresets; i++) {
            prefs.edit().putString("systemPreset" + i, equalizer.getPresetName(i)).commit();
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

        // Intent mở MainActivity
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        // PendingIntent để gắn vào notification
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.baseline_surround_sound_24)
                .setContentTitle("Equalizer Service")
                .setContentText("Equalizer is applied after boot")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(pendingIntent)
                .build();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}