package com.example.equalizerapp;
import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.equalizerapp.Services.EqualizerService;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private SeekBar[] seekBars;
    private TextView[] txtBands;
    private Spinner spnPreset;
    private int numberOfBands;
    Button btnCreate;
    SharedPreferences prefs;
    private int minLevel, maxLevel;
    private static int lastPreset = -1;
    private static List<String> presetNamesRestored;

    @SuppressLint({"UnspecifiedRegisterReceiverFlag", "MissingInflatedId"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d("onCreate", "onCreate: chạy");
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        100);

            } else {
                Intent myintent = new Intent(this, EqualizerService.class);
                ContextCompat.startForegroundService(this, myintent);
            }
        } else {
            Intent myintent = new Intent(this, EqualizerService.class);
            ContextCompat.startForegroundService(this, myintent);
        }
        btnCreate = findViewById(R.id.btnCreate);
        prefs = getSharedPreferences("EQ_PREFS", MODE_PRIVATE);
        int selectedPreset = prefs.getInt("selectedPreset", 0);
        if(selectedPreset != 0){
            btnCreate.setVisibility(View.INVISIBLE);
        }else{
            btnCreate.setVisibility(View.VISIBLE);
        }
        btnCreate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Tạo EditText để nhập tên preset
                final EditText input = new EditText(MainActivity.this);
                input.setHint("Nhập tên preset");

                // Tạo AlertDialog
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Tạo preset mới")
                        .setView(input)
                        .setPositiveButton("OK", (dialog, which) -> {
                            String presetName = input.getText().toString().trim();
                            if (!presetName.isEmpty()) {
                                saveNewPreset(presetName);
                            } else {
                                btnCreate.setVisibility(View.INVISIBLE);
                                Toast.makeText(MainActivity.this, "Tên preset không được để trống", Toast.LENGTH_SHORT).show();
                            }
                        })
                        .setNegativeButton("Hủy", (dialog, which) -> dialog.dismiss())
                        .show();
            }
        });
        spnPreset = findViewById(R.id.spinnerPreset);
        IntentFilter filter = new IntentFilter(EqualizerService.ACTION_READY);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(eqReadyReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(eqReadyReceiver, filter);
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults, int deviceId) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults, deviceId);
        if (requestCode == 100) { // Code dùng khi request permission
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d("Permissions", "Notification permission granted");
                Intent myintent = new Intent(this, EqualizerService.class);
                ContextCompat.startForegroundService(this, myintent);
                recreate();
            } else {
                Log.d("Permissions", "Notification permission denied");
                Toast.makeText(this, "Bạn cần cho phép thông báo để ứng dụng hoạt động đầy đủ", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void saveNewPreset(String presetName) {
        prefs = getSharedPreferences("EQ_PREFS", MODE_PRIVATE);
        int numberOfUserPresets = prefs.getInt("numberOfUserPresets", 0);
        int numberOfSystemPresets = prefs.getInt("numberOfSystemPresets", 0);
        SharedPreferences.Editor editor = prefs.edit();

        // 1. Lưu tên preset mới với key riêng cho user preset
        editor.putString("userPreset" + numberOfUserPresets, presetName);
        // 2. Lưu cấu hình hiện tại của các band với key riêng
        for (int band = 0; band < seekBars.length; band++) {
            int level = seekBars[band].getProgress() + minLevel;
            editor.putInt("userPreset" + numberOfUserPresets + "_band" + band, level);
        }
        // 3. Tăng số lượng user preset
        editor.putInt("numberOfUserPresets", numberOfUserPresets + 1);
        // 4. Tính vị trí preset mới: Custom(0) + SystemPresets + UserPresets
        // Vị trí = 1 + numberOfSystemPresets + numberOfUserPresets (vị trí hiện tại chưa tăng)
        int newPresetPosition = 1 + numberOfSystemPresets + numberOfUserPresets;
        editor.putInt("selectedPreset", newPresetPosition);
        // 5. Apply tất cả thay đổi trước khi cập nhật UI
        editor.commit();
        // 6. Cập nhật UI
        setupPresent();
        // 7. Set selection để đảm bảo preset vừa tạo được chọn
        if (newPresetPosition < presetNamesRestored.size()) {
            spnPreset.setSelection(newPresetPosition, false);
            lastPreset = newPresetPosition;
        }
        Toast.makeText(this, "Preset \"" + presetName + "\" đã được lưu tại vị trí " + newPresetPosition + "!", Toast.LENGTH_SHORT).show();
    }

    private final BroadcastReceiver eqReadyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            minLevel = prefs.getInt("minLevel", -1500);
            maxLevel = prefs.getInt("maxLevel", 1500);
            numberOfBands = prefs.getInt("numberOfBands", 0);
            seekBars = new SeekBar[numberOfBands];
            txtBands = new TextView[numberOfBands];
            for (int i = 0; i < numberOfBands; i++) {
                int seekId = getResources().getIdentifier("seekBarBand" + (i + 1), "id", getPackageName());
                int txtId = getResources().getIdentifier("txtBand" + (i + 1), "id", getPackageName());
                seekBars[i] = findViewById(seekId);
                txtBands[i] = findViewById(txtId);
            }
            setupPresent();
            setupSeekBars();
            updateSeekBarsUI();
        }
    };

    // Method để cập nhật UI của seekbars mà không trigger sự kiện
    private void updateSeekBarsUI() {
        if (seekBars == null) return;
        for (short i = 0; i < numberOfBands; i++) {
            int savedLevel = prefs.getInt("band" + i, 0);
            seekBars[i].setProgress(savedLevel - minLevel);
            txtBands[i].setText(savedLevel / 100 + " dB");
        }
    }

    private void setupPresent(){
        int numberOfSystemPresets = prefs.getInt("numberOfSystemPresets", 0);
        int numberOfUserPresets = prefs.getInt("numberOfUserPresets", 0);

        presetNamesRestored = new ArrayList<>();
        presetNamesRestored.add("Custom");

        // Thêm system presets (vị trí 1 -> numberOfSystemPresets)
        for(int i = 0; i < numberOfSystemPresets; i++){
            String systemPresetName = prefs.getString("systemPreset" + i, "System Preset " + i);
            presetNamesRestored.add(systemPresetName);
        }

        // Thêm user presets (vị trí numberOfSystemPresets + 1 -> end)
        for(int i = 0; i < numberOfUserPresets; i++){
            String userPresetName = prefs.getString("userPreset" + i, "User Preset " + i);
            presetNamesRestored.add(userPresetName);
        }

        // Gắn adapter cho spinner
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                presetNamesRestored
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spnPreset.setAdapter(adapter);

        int savedPreset = prefs.getInt("selectedPreset", 0);

        // Đảm bảo selectedPreset không vượt quá số lượng preset có sẵn
        if (savedPreset >= presetNamesRestored.size()) {
            savedPreset = 0;
            prefs.edit().putInt("selectedPreset", 0).apply();
        }

        spnPreset.setSelection(savedPreset, false);
        lastPreset = savedPreset;

        // Xử lý sự kiện chọn item
        spnPreset.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @SuppressLint("UnspecifiedRegisterReceiverFlag")
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == lastPreset) return;

                lastPreset = position;
                btnCreate.setVisibility(position == 0 ? View.VISIBLE : View.INVISIBLE);

                // Lưu vị trí được chọn
                prefs.edit().putInt("selectedPreset", position).commit();

                Intent intent = new Intent(MainActivity.this, EqualizerService.class);
                intent.setAction(EqualizerService.ACTION_USE_PRESET);
                intent.putExtra(EqualizerService.EXTRA_PRESET_POS, position);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent);
                } else {
                    startService(intent);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });
    }

    private void setupSeekBars() {
        for (short i = 0; i < numberOfBands; i++) {
            final short band = i;
            seekBars[band].setMax(maxLevel - minLevel);
            int savedLevel = prefs.getInt("band" + band, 0);
            seekBars[band].setProgress(savedLevel - minLevel);
            txtBands[band].setText(savedLevel / 100 + " dB");
            seekBars[band].setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (!fromUser) return;

                    // Khi user thay đổi seekbar, chuyển về Custom mode
                    prefs.edit().putInt("selectedPreset", 0).commit();
                    spnPreset.setSelection(0, false);
                    lastPreset = 0;
                    btnCreate.setVisibility(View.VISIBLE);

                    short level = (short) (progress + minLevel);
                    txtBands[band].setText(level / 100 + " dB");

                    Intent intent = new Intent(MainActivity.this, EqualizerService.class);
                    intent.setAction(EqualizerService.ACTION_SET_BAND);
                    intent.putExtra(EqualizerService.EXTRA_BAND, (int) band);
                    intent.putExtra(EqualizerService.EXTRA_LEVEL, (int)level);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(intent);
                    } else {
                        startService(intent);
                    }
                }

                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                // Kết thúc thao tác kéo
                @Override public void onStopTrackingTouch(SeekBar seekBar) {
                    // Lưu TOÀN BỘ preset Custom vào band_custom (đầy đủ tất cả band)
                    SharedPreferences.Editor editor = prefs.edit();
                    for (int b = 0; b < numberOfBands; b++) {
                        short fullLevel = (short) (seekBars[b].getProgress() + minLevel);
                        editor.putInt("band_custom" + b, fullLevel);
                    }
                    editor.putInt("selectedPreset", 0).commit();
                }
            });
        }
    }

    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(eqReadyReceiver);
    }
}