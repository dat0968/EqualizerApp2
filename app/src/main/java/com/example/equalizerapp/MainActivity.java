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
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
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

    @SuppressLint({"UnspecifiedRegisterReceiverFlag", "MissingInflatedId"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d("onCreate", "onCreate: chạy");
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        Intent myintent = new Intent(this, EqualizerService.class);
        // Kiểm tra và yêu cầu quyền nếu cần
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        100);
            } else {
                ContextCompat.startForegroundService(this, myintent);
            }
        } else {
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

            }
        });

        spnPreset = findViewById(R.id.spinnerPreset);
        IntentFilter filter = new IntentFilter(EqualizerService.ACTION_READY);
        registerReceiver(eqReadyReceiver, filter);

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
                // giả sử id theo tên seekBarBand1, seekBarBand2,... và txtBand1, txtBand2,...
                int seekId = getResources().getIdentifier("seekBarBand" + (i + 1), "id", getPackageName());
                int txtId = getResources().getIdentifier("txtBand" + (i + 1), "id", getPackageName());
                seekBars[i] = findViewById(seekId);
                txtBands[i] = findViewById(txtId);
            }
            setupSeekBars();
            setupPresent();
        }
    };
    private void setupPresent(){
        int numberOfPresets = prefs.getInt("numberOfPresets", 1);
        List<String> presetNamesRestored = new ArrayList<>();
        presetNamesRestored.add("Custom");
        for(int i = 0; i < numberOfPresets; i++){
            presetNamesRestored.add(prefs.getString("preset" + i, ""));
        }


        // gắn adapter cho mỗi spinner
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                presetNamesRestored
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        spnPreset.setAdapter(adapter);

        int savedPreset = prefs.getInt("selectedPreset", 0);
        spnPreset.setSelection(savedPreset, false);
        lastPreset = savedPreset;

        lastPreset = spnPreset.getSelectedItemPosition();
        // Xử lý sự kiện chọn item (nếu cần)
        spnPreset.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @SuppressLint("UnspecifiedRegisterReceiverFlag")
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == lastPreset) return;
                lastPreset = position;
                btnCreate.setVisibility(position == 0 ? View.VISIBLE : View.INVISIBLE);
                spnPreset.setSelection(position, false);
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
                    int position = prefs.getInt("selectedPreset", 0);
                    spnPreset.setSelection(position, false);
                    lastPreset = position;
                    btnCreate.setVisibility(View.VISIBLE);
                    short level = (short) (progress + minLevel);
                    txtBands[band].setText(level / 100 + " dB");
                    Intent intent = new Intent(MainActivity.this, EqualizerService.class);
                    intent.setAction(EqualizerService.ACTION_SET_BAND);
                    intent.putExtra(EqualizerService.EXTRA_BAND, (int) band);
                    Log.d("level", "level: " + (int)level);
                    intent.putExtra(EqualizerService.EXTRA_LEVEL, (int)level);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(intent);
                    } else {
                        startService(intent);
                    }
                }

                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }
    }
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(eqReadyReceiver);
    }
}