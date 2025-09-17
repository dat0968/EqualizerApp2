package com.example.equalizerapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
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
    Button btnDelete;
    SharedPreferences prefs;
    private int minLevel, maxLevel;
    private static int lastPreset = -1;
    private SeekBar bassBoostSeekBar, boosterSeekBar;
    private TextView txtBassBoost, txtBooster;
    private int maxBassBoost = 1000;
    private static List<String> presetNamesRestored;

    @SuppressLint({"UnspecifiedRegisterReceiverFlag", "MissingInflatedId"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d("onCreatePlay", "onCreate: onCreate");
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        // Kiểm tra và yêu cầu quyền nếu cần
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                new AlertDialog.Builder(this)
                        .setTitle("XIN QUYỀN THÔNG BÁO")
                        .setMessage("Cho phép ứng dụng này gửi thông báo ?")
                        .setCancelable(false)
                        .setPositiveButton("Cho phép", (dialog, which) -> openNotificationSettings())
                        .setNegativeButton("Không cho phép", (dialog, which) -> finish())
                        .show();

            } else {
                Intent myintent = new Intent(this, EqualizerService.class);
                ContextCompat.startForegroundService(this, myintent);
            }
        } else {
            Intent myintent = new Intent(this, EqualizerService.class);
            ContextCompat.startForegroundService(this, myintent);
        }
        boosterSeekBar = findViewById(R.id.seekBarBooster);
        bassBoostSeekBar = findViewById(R.id.seekBarBass);
        txtBassBoost = findViewById(R.id.txtBassBoost);
        txtBooster = findViewById(R.id.txtBooster);
        btnCreate = findViewById(R.id.btnCreate);
        btnDelete = findViewById(R.id.btnDelete);
        prefs = getSharedPreferences("EQ_PREFS", MODE_PRIVATE);
        int selectedPreset = prefs.getInt("selectedPreset", 0);
        btnCreate.setVisibility(selectedPreset != 0 ? View.INVISIBLE : View.VISIBLE);
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
                                //Kiểm tra trùng preset
                                boolean exists = false;
                                int numberOfSystemPresets = prefs.getInt("numberOfSystemPresets", 0);
                                for (int i = 1; i <= numberOfSystemPresets; i++) {
                                    String sysName = presetNamesRestored.get(i);
                                    if (sysName.equalsIgnoreCase(presetName)) {
                                        exists = true;
                                        break;
                                    }
                                }
                                if (exists || presetName.equalsIgnoreCase("custom")) {
                                    Toast.makeText(MainActivity.this, "Không đặt tên preset trùng với tên preset hệ thống", Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                // Cập nhật preset nếu preset đó trùng tên với preset nào đó user tạo ra
                                int presetNeedtoDelete = -1;
                                for (int i = 0; i < spnPreset.getCount(); i++) {
                                    String item = spnPreset.getItemAtPosition(i).toString();
                                    if (item.equalsIgnoreCase(presetName)) {
                                        presetNeedtoDelete = i;
                                        break;
                                    }
                                }

                                if (presetNeedtoDelete >= 0) {
                                    deletePreset(presetNeedtoDelete); // xóa preset cũ
                                    // gọi lại saveNewPreset để lưu lại với cùng tên
                                    saveNewPreset(presetName);
                                    btnCreate.setVisibility(View.INVISIBLE);
                                    btnDelete.setVisibility(View.VISIBLE);
                                    Toast.makeText(MainActivity.this, "Preset \"" + presetName + "\" đã được cập nhật!", Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                btnCreate.setVisibility(View.INVISIBLE);
                                btnDelete.setVisibility(View.VISIBLE);
                                saveNewPreset(presetName);
                                Toast.makeText(MainActivity.this, "Preset đã được thêm mới", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(MainActivity.this, "Tên preset không được để trống", Toast.LENGTH_SHORT).show();
                            }
                        })
                        .setNegativeButton("Hủy", (dialog, which) -> dialog.dismiss())
                        .show();
            }
        });

        btnDelete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Xác nhận")
                        .setMessage("Bạn có chắc chắn muốn xóa không?")
                        .setPositiveButton("Có", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                int selectedPreset = prefs.getInt("selectedPreset", -1);
                                btnDelete.setVisibility(View.INVISIBLE);
                                btnCreate.setVisibility(View.VISIBLE);
                                deletePreset(selectedPreset);
                                Toast.makeText(MainActivity.this, "Đã xóa!", Toast.LENGTH_SHORT).show();
                            }
                        })
                        .setNegativeButton("Không", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss(); // Đóng dialog, không xóa
                            }
                        })
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
    private void openNotificationSettings() {
        Intent intent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Android 8.0 trở lên
            intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // Android 5.0 - 7.1
            intent = new Intent("android.settings.APP_NOTIFICATION_SETTINGS")
                    .putExtra("app_package", getPackageName())
                    .putExtra("app_uid", getApplicationInfo().uid);
        } else {
            // Android cũ hơn
            intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", getPackageName(), null));
        }
        startActivity(intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 100) {
            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Được cấp quyền
                Intent myintent = new Intent(this, EqualizerService.class);
                ContextCompat.startForegroundService(this, myintent);
            }
            else {
//                // Bị từ chối -> mở Settings
                new AlertDialog.Builder(this)
                        .setTitle("Cần quyền thông báo")
                        .setMessage("Ứng dụng cần quyền này để hoạt động đầy đủ. Vui lòng cấp trong Cài đặt.")
                        .setCancelable(false)
                        .setPositiveButton("Mở Cài đặt", (dialog, which) -> openNotificationSettings())
                        .setNegativeButton("Thoát", (dialog, which) -> finish())
                        .show();

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
    }
    private void deletePreset(int presetIndex) {
        prefs = getSharedPreferences("EQ_PREFS", MODE_PRIVATE);
        int numberOfUserPresets = prefs.getInt("numberOfUserPresets", 0);
        int numberOfSystemPresets = prefs.getInt("numberOfSystemPresets", 0);
        // presetIndex là vị trí trong Spinner (bao gồm cả Custom + System)
        // => Cần quy đổi sang index thật trong UserPresets
        // Custom (0) + SystemPresets (1..N) + UserPresets
        int userPresetIndex = presetIndex - (1 + numberOfSystemPresets);
        if (userPresetIndex < 0 || userPresetIndex >= numberOfUserPresets) {
            Toast.makeText(this, "Không thể xóa preset hệ thống!", Toast.LENGTH_SHORT).show();
            return;
        }
        SharedPreferences.Editor editor = prefs.edit();
        // 1. Dồn toàn bộ preset sau nó lên trước 1 bậc
        for (int i = userPresetIndex; i < numberOfUserPresets - 1; i++) {
            // Copy tên preset
            String nextName = prefs.getString("userPreset" + (i + 1), "");
            editor.putString("userPreset" + i, nextName);
            // Copy cấu hình các band
            for (int band = 0; band < seekBars.length; band++) {
                int level = prefs.getInt("userPreset" + (i + 1) + "_band" + band, 0);
                editor.putInt("userPreset" + i + "_band" + band, level);
            }
        }
        // 2. Xóa preset cuối cùng (vì nó đã được dồn lên trước)
        editor.remove("userPreset" + (numberOfUserPresets - 1));
        for (int band = 0; band < seekBars.length; band++) {
            editor.remove("userPreset" + (numberOfUserPresets - 1) + "_band" + band);
        }
        // 3. Giảm số lượng preset user
        editor.putInt("numberOfUserPresets", numberOfUserPresets - 1);

        // 4. Apply
        editor.commit();

        // 5. Cập nhật lại UI
        setupPresent();

        // Sau khi xóa, chọn về Custom preset cho an toàn
        spnPreset.setSelection(0, false);
        lastPreset = 0;
        Intent intent = new Intent(MainActivity.this, EqualizerService.class);
        intent.setAction(EqualizerService.ACTION_USE_PRESET);
        intent.putExtra(EqualizerService.EXTRA_PRESET_POS, lastPreset);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
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
            int numberOfUserPresets = prefs.getInt("numberOfUserPresets", 0);
            int numberOfSystemPresets = prefs.getInt("numberOfSystemPresets", 0);
            int selectedPreset = prefs.getInt("selectedPreset", 0);
            if (selectedPreset >= 1 + numberOfSystemPresets
                    && selectedPreset < 1 + numberOfSystemPresets + numberOfUserPresets) {
                // Đây là preset user -> cho phép xóa
                btnDelete.setVisibility(View.VISIBLE);
            } else {
                // Custom hoặc System preset -> không cho xóa
                btnDelete.setVisibility(View.INVISIBLE);
            }
            setupPresent();
            setupSeekBars();
            setupBassBoost();
            setupBooster();
            updateSeekBarsUI(); // Cập nhật UI sau khi preset thay đổi
        }
    };

    // Method để cập nhật UI của seekbars mà không trigger sự kiện
    private void updateSeekBarsUI() {
        if (seekBars == null) return;
        for (short i = 0; i < numberOfBands; i++) {
            int savedLevel = prefs.getInt("band" + i, 0);
            seekBars[i].setProgress(savedLevel - minLevel);
            txtBands[i].setText(savedLevel / 100 + " dB");
            Log.d("updateSeekBarsUI", "Band " + i + " set to " + savedLevel + " (progress: " + (savedLevel - minLevel) + ")");
        }
    }
    private  void setupBooster(){
        int boosterLevel = prefs.getInt("booster_level", 0);
        boosterSeekBar.setMax(2000);
        boosterSeekBar.setProgress(boosterLevel);
        txtBooster.setText(boosterLevel / 100 + " dB");
        boosterSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                prefs.edit().putInt("booster_level", progress).apply();
                txtBooster.setText(progress / 100 + " dB");

                Intent intent = new Intent(MainActivity.this, EqualizerService.class);
                intent.setAction(EqualizerService.ACTION_SET_BOOSTER);
                intent.putExtra(EqualizerService.EXTRA_BOOSTER_LEVEL, progress);
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
    private  void setupBassBoost(){
        // Khởi tạo progress từ prefs (hoặc mặc định 0)
        int bassLevel = prefs.getInt("bassboost_level", 0);
        bassBoostSeekBar.setMax(maxBassBoost);
        bassBoostSeekBar.setProgress(bassLevel);
        txtBassBoost.setText(bassLevel / 10 + " %");
        // Lắng nghe sự kiện thay đổi
        bassBoostSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                prefs.edit().putInt("bassboost_level", progress).apply();
                txtBassBoost.setText(progress / 10 + " %");

                // Gửi lệnh xuống service để apply bassboost
                Intent intent = new Intent(MainActivity.this, EqualizerService.class);
                intent.setAction(EqualizerService.ACTION_SET_BASSBOOST);
                intent.putExtra(EqualizerService.EXTRA_BASSBOOST_LEVEL, progress);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent);
                } else {
                    startService(intent);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) { }
        });

    }

    private void setupPresent(){
        int numberOfSystemPresets = prefs.getInt("numberOfSystemPresets", 0);
        int numberOfUserPresets = prefs.getInt("numberOfUserPresets", 0);
        presetNamesRestored = new ArrayList<>();
        presetNamesRestored.add("Custom"); // Vị trí 0
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
            savedPreset = 0; // Reset về Custom nếu vị trí không hợp lệ
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
                    spnPreset.setSelection(0, false); // Chuyển spinner về Custom
                    lastPreset = 0;
                    btnCreate.setVisibility(View.VISIBLE);
                    btnDelete.setVisibility(View.INVISIBLE);
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
                @Override public void onStopTrackingTouch(SeekBar seekBar) {
                    // Lưu TOÀN BỘ preset Custom vào band_custom (đầy đủ tất cả band)
                    SharedPreferences.Editor editor = prefs.edit();
                    for (int b = 0; b < numberOfBands; b++) {
                        short fullLevel = (short) (seekBars[b].getProgress() + minLevel);
                        editor.putInt("band_custom" + b, fullLevel);
                        Log.d("SaveCustomFull", "Saved band_custom" + b + ": " + fullLevel + " (from seekbar progress: " + seekBars[b].getProgress() + ")");
                    }
                    editor.putInt("selectedPreset", 0).commit(); // Đảm bảo ở Custom
                    Log.d("SaveCustomFull", "Full Custom preset saved on stop touch");
                }
            });
        }
    }
    @Override
    protected void onResume() {
        super.onResume();
        // Chỉ khởi động service nếu quyền đã được cấp hoặc trên Android 12 trở xuống
//        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
//                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
//                        == PackageManager.PERMISSION_GRANTED) {
//            Intent myintent = new Intent(this, EqualizerService.class);
//            ContextCompat.startForegroundService(this, myintent);
//        }
        Log.d("onResumePlay", "onResume: onResume");
    }

    protected void onDestroy() {
        super.onDestroy();
        Log.d("onDestroyPlay", "onDestroyPlay: onDestroyPlay");
        unregisterReceiver(eqReadyReceiver);
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        // Kiểm tra và yêu cầu quyền nếu cần
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
//                ActivityCompat.requestPermissions(this,
//                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
//                        100);
                new AlertDialog.Builder(this)
                        .setTitle("XIN QUYỀN THÔNG BÁO")
                        .setMessage("Cho phép ứng dụng này gửi thông báo ?")
                        .setCancelable(false)
                        .setPositiveButton("Cho phép", (dialog, which) -> openNotificationSettings())
                        .setNegativeButton("Không cho phép", (dialog, which) -> finish())
                        .show();

            } else {
                Intent myintent = new Intent(this, EqualizerService.class);
                ContextCompat.startForegroundService(this, myintent);
            }
        } else {
            Intent myintent = new Intent(this, EqualizerService.class);
            ContextCompat.startForegroundService(this, myintent);
        }
    }
}