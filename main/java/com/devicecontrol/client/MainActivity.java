package com.devicecontrol.client;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.devicecontrol.client.service.DeviceAccessibilityService;
import com.devicecontrol.client.service.DeviceControlService;
import com.devicecontrol.client.service.MediaProjectionService;
import com.devicecontrol.client.utils.Constants;
import com.devicecontrol.client.utils.GlobalEventBus;
import com.devicecontrol.client.utils.ScreenCaptureManager;
import com.google.android.material.button.MaterialButton;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements EasyPermissions.PermissionCallbacks {
    private static final String TAG = "MainActivity";
    private static final int REQUEST_SCREEN_CAPTURE = 1001;
    private static final int REQUEST_OVERLAY = 1002;
    private static final int REQUEST_PERMISSIONS = 1003;
    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 1004;
    private static final int REQUEST_NOTIFICATION_PERMISSION = 1005;

    // UI组件
    private TextView tvStatus;
    private TextView tvDeviceId;
    private TextView tvServerUrl;
    private TextView tvLogs;
    private SwitchCompat switchAccessibility;
    private SwitchCompat switchScreenCapture;
    private SwitchCompat switchOverlay;
    private MaterialButton btnStartService;
    private MaterialButton btnStopService;
    private MaterialButton btnToggleAccessibility;
    private MaterialButton btnTestScreenshot;
    private MaterialButton btnTestClick;
    private MaterialButton btnRefresh;
    private MaterialButton btnClearLogs;
    private ScrollView scrollViewLogs;

    // 状态变量
    private boolean isServiceRunning = false;
    private boolean isAccessibilityPaused = false;
    private ScreenCaptureManager screenCaptureManager;
    private SharedPreferences prefs;
    private Handler uiHandler;
    private Handler refreshHandler;
    private Runnable refreshRunnable;
    private MediaProjectionManager mediaProjectionManager;
    private int screenCaptureResultCode;
    private Intent screenCaptureData;
    // EventBus监听器
    private GlobalEventBus.EventListener accessibilityListener = new GlobalEventBus.EventListener() {
        @Override
        public void onEvent(String event, Object data) {
            if ("accessibility_connected".equals(event)) {
                boolean connected = data instanceof Boolean ? (Boolean) data : false;
                Log.d(TAG, "Accessibility service connected: " + connected);
                prefs.edit().putBoolean("accessibility_service_enabled", connected).apply();
                runOnUiThread(() -> {
                    updateAccessibilitySwitch();
                    addLog("无障碍服务" + (connected ? "已连接" : "已断开"));
                });
            }else if ("screen_off".equals(event)){
                Log.d(TAG, "screen_capture_enabled " );
                mIsDialogShow = false;
                prefs.edit().putBoolean("screen_capture_enabled", false).apply();
            }
        }
    };
    // 广播接收器
    private BroadcastReceiver serviceReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG,"serviceReceiver:"+action);
            if ("com.devicecontrol.SERVICE_STATUS".equals(action)) {
                boolean connected = intent.getBooleanExtra("connected", false);
                updateStatus(connected);
                addLog("服务状态: " + (connected ? "已连接" : "已断开"));
            } else if ("com.devicecontrol.ACCESSIBILITY_CONNECTED".equals(action)) {
                updateAccessibilitySwitch();
                addLog("无障碍服务已连接");
            } else if ("com.devicecontrol.SCREENSHOT_UPLOADED".equals(action)) {
                String url = intent.getStringExtra("url");
                addLog("截图已上传: " + url);
            }
        }
    };
    private boolean mIsDialogShow = false;
    private BroadcastReceiver reauthorizationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.devicecontrol.NEED_REAUTHORIZATION".equals(intent.getAction())) {
                Log.d(TAG, "Received reauthorization request");
                runOnUiThread(() -> {
                    addLog("截屏权限已失效，需要重新授权");
                    if (!mIsDialogShow) {
                        // 显示对话框提示用户
                       new AlertDialog.Builder(MainActivity.this)
                                .setTitle("需要重新授权")
                                .setMessage("由于屏幕熄灭，截屏权限已失效。是否重新授权？")
                                .setPositiveButton("授权", (dialog, which) -> requestScreenCapture())
                                .setNegativeButton("取消", null).show();
                        mIsDialogShow = true;
                    }

                });
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // 初始化MediaProjectionManager
        mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        // 初始化
        prefs = getSharedPreferences("device_control_prefs", MODE_PRIVATE);
        uiHandler = new Handler(Looper.getMainLooper());
        refreshHandler = new Handler(Looper.getMainLooper());
        screenCaptureManager = ScreenCaptureManager.getInstance(this);

        // 初始化UI
        initViews();

        // 检查权限
        checkAndRequestPermissions();
        // 注册EventBus监听器
        registerEventBusListeners();
        // 注册广播接收器
        registerServiceReceiver();

        // 启动定时刷新
        startPeriodicRefresh();
        // 注册重新授权接收器
        IntentFilter filter = new IntentFilter("com.devicecontrol.NEED_REAUTHORIZATION");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(reauthorizationReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(reauthorizationReceiver, filter);
        }
        addLog("应用启动");
    }
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        // 处理从通知栏点击进入的请求
        if ("REQUEST_SCREEN_CAPTURE".equals(intent.getAction())) {
            requestScreenCapture();
        }
    }

    private void requestScreenCapture() {
        // 检查MediaProjectionService是否已经在运行
        if (MediaProjectionService.getInstance() != null && prefs.getBoolean("screen_capture_enabled",false)) {
            Toast.makeText(this, "截屏服务已经在运行", Toast.LENGTH_SHORT).show();
            switchScreenCapture.setChecked(true);
            return;
        }

        // 请求截屏权限
        Intent intent = mediaProjectionManager.createScreenCaptureIntent();
        startActivityForResult(intent, REQUEST_SCREEN_CAPTURE);
        addLog("请求截屏权限");
    }
    private void initViews() {
        // 查找视图
        tvStatus = findViewById(R.id.tv_status);
        tvDeviceId = findViewById(R.id.tv_device_id);
        tvServerUrl = findViewById(R.id.tv_server_url);
        tvLogs = findViewById(R.id.tv_logs);
        switchAccessibility = findViewById(R.id.switch_accessibility);
        switchScreenCapture = findViewById(R.id.switch_screen_capture);
        switchOverlay = findViewById(R.id.switch_overlay);
        btnStartService = findViewById(R.id.btn_start_service);
        btnStopService = findViewById(R.id.btn_stop_service);
        btnToggleAccessibility = findViewById(R.id.btn_toggle_accessibility);
        btnTestScreenshot = findViewById(R.id.btn_test_screenshot);
        btnTestClick = findViewById(R.id.btn_test_click);
        btnRefresh = findViewById(R.id.btn_refresh);
        btnClearLogs = findViewById(R.id.btn_clear_logs);
//        scrollViewLogs = findViewById(R.id.card_logs).findViewById(R.id.tv_logs).getParent().getParent();

        // 设置基本信息
        tvDeviceId.setText("Device ID: " + Constants.DEVICE_ID);
        tvServerUrl.setText("Server: " + Constants.SERVER_URL);

        // 设置按钮监听器
        setupButtonListeners();

        // 设置开关监听器
        setupSwitchListeners();

        // 初始更新UI状态
        updateAllSwitches();
        updateServiceButtons();
    }

    private void setupButtonListeners() {
        btnStartService.setOnClickListener(v -> startDeviceControlService());
        btnStopService.setOnClickListener(v -> stopDeviceControlService());

        btnToggleAccessibility.setOnClickListener(v -> toggleAccessibilityPause());

        btnTestScreenshot.setOnClickListener(v -> testScreenshot());
        btnTestClick.setOnClickListener(v -> testClick());
        btnRefresh.setOnClickListener(v -> refreshAllStatus());
        btnClearLogs.setOnClickListener(v -> clearLogs());
    }

    private void setupSwitchListeners() {
        // 无障碍服务开关
        switchAccessibility.setOnClickListener(v -> {
            boolean isChecked = switchAccessibility.isChecked();
            if (isChecked && !isAccessibilityServiceEnabled()) {
                openAccessibilitySettings();
                // 先设置为false，等实际开启后再更新
                switchAccessibility.setChecked(false);
            } else if (!isChecked && isAccessibilityServiceEnabled()) {
                // 提示用户需要在设置中关闭
                new AlertDialog.Builder(this)
                        .setTitle("提示")
                        .setMessage("请在系统设置中关闭无障碍服务")
                        .setPositiveButton("去设置", (dialog, which) -> openAccessibilitySettings())
                        .setNegativeButton("取消", (dialog, which) -> switchAccessibility.setChecked(true))
                        .show();
            }
        });

        // 截屏权限开关
        switchScreenCapture.setOnClickListener(v -> {
            boolean isChecked = switchScreenCapture.isChecked();
            if (isChecked && !hasScreenCapturePermission()) {
                requestScreenCapture();
                // 先设置为false，等实际授权后再更新
                switchScreenCapture.setChecked(false);
            } else if (!isChecked && hasScreenCapturePermission()) {
                // 停止截屏服务
                stopMediaProjectionService();
                prefs.edit().putBoolean("screen_capture_enabled", false).apply();
                addLog("截屏服务已停止");
            }
        });

        // 悬浮窗权限开关
        switchOverlay.setOnClickListener(v -> {
            boolean isChecked = switchOverlay.isChecked();
            if (isChecked && !hasOverlayPermission()) {
                requestOverlayPermission();
                // 先设置为false，等实际授权后再更新
                switchOverlay.setChecked(false);
            } else if (!isChecked && hasOverlayPermission()) {
                // 提示用户需要在设置中关闭
                new AlertDialog.Builder(this)
                        .setTitle("提示")
                        .setMessage("请在系统设置中关闭悬浮窗权限")
                        .setPositiveButton("去设置", (dialog, which) -> requestOverlayPermission())
                        .setNegativeButton("取消", (dialog, which) -> switchOverlay.setChecked(true))
                        .show();
            }
        });
    }

    private void startPeriodicRefresh() {
        refreshRunnable = new Runnable() {
            @Override
            public void run() {
                updateAllSwitches();
                refreshHandler.postDelayed(this, 2000); // 每2秒刷新一次
            }
        };
        refreshHandler.postDelayed(refreshRunnable, 1000);
    }

    private void stopPeriodicRefresh() {
        if (refreshHandler != null && refreshRunnable != null) {
            refreshHandler.removeCallbacks(refreshRunnable);
        }
    }

    private void registerServiceReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.devicecontrol.SERVICE_STATUS");
        filter.addAction("com.devicecontrol.ACCESSIBILITY_CONNECTED");
        filter.addAction("com.devicecontrol.SCREENSHOT_UPLOADED");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(serviceReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(serviceReceiver, filter);
        }
    }

    private void checkAndRequestPermissions() {
        List<String> permissions = new ArrayList<>();

        // 基础权限
        permissions.add(Manifest.permission.INTERNET);
        permissions.add(Manifest.permission.ACCESS_NETWORK_STATE);
        permissions.add(Manifest.permission.ACCESS_WIFI_STATE);
        permissions.add(Manifest.permission.CHANGE_WIFI_STATE);
        permissions.add(Manifest.permission.RECEIVE_BOOT_COMPLETED);

        // Android 12+ 蓝牙权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
            permissions.add(Manifest.permission.BLUETOOTH_SCAN);
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE);
        }

        // Android 13+ 通知权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        // 位置权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        } else {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }

        String[] permsArray = permissions.toArray(new String[0]);

        if (!EasyPermissions.hasPermissions(this, permsArray)) {
            EasyPermissions.requestPermissions(
                    this,
                    "应用需要这些权限才能正常工作",
                    REQUEST_PERMISSIONS,
                    permsArray
            );
        }
    }

    private void updateAllSwitches() {
        // 更新无障碍服务开关
        boolean accessibilityEnabled = isAccessibilityServiceEnabled();
        switchAccessibility.setChecked(accessibilityEnabled);

        // 更新截屏权限开关
        boolean screenCaptureEnabled = hasScreenCapturePermission();
        switchScreenCapture.setChecked(screenCaptureEnabled);

        // 更新悬浮窗权限开关
        boolean overlayEnabled = hasOverlayPermission();
        switchOverlay.setChecked(overlayEnabled);

        // 更新服务运行状态
        updateServiceStatus();
    }

    private boolean isAccessibilityServiceEnabled() {
//        try {
//            String service = getPackageName() + "/.service.DeviceAccessibilityService";
//            String enabledServices = Settings.Secure.getString(
//                    getContentResolver(),
//                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
//            );
//
//            if (TextUtils.isEmpty(enabledServices)) {
//                return false;
//            }
//
//            TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
//            splitter.setString(enabledServices);
//
//            while (splitter.hasNext()) {
//                String componentName = splitter.next();
//                if (componentName.equalsIgnoreCase(service)) {
//                    return true;
//                }
//            }
//        } catch (Exception e) {
//            Log.e(TAG, "Error checking accessibility service", e);
//        }
        return prefs.getBoolean("accessibility_service_enabled",false);

//        return false;
    }

    private boolean hasScreenCapturePermission() {
        // 检查SharedPreferences中的状态
        boolean enabled = prefs.getBoolean("screen_capture_enabled", false);

        // 检查服务是否在运行
        if (enabled && MediaProjectionService.getInstance() == null) {
            // 服务没有运行，可能是被系统杀死了
            prefs.edit().putBoolean("screen_capture_enabled", false).apply();
            return false;
        }

        return enabled;
    }

    private boolean hasOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(this);
        }
        return true;
    }

    private void updateAccessibilitySwitch() {
        uiHandler.post(() -> {
            boolean enabled = isAccessibilityServiceEnabled();
            switchAccessibility.setChecked(enabled);

            if (enabled && DeviceAccessibilityService.getInstance() != null) {
                btnToggleAccessibility.setEnabled(true);
                btnToggleAccessibility.setText(isAccessibilityPaused ? "恢复无障碍监听" : "暂停无障碍监听");
            } else {
                btnToggleAccessibility.setEnabled(false);
                btnToggleAccessibility.setText("无障碍服务未启动");
            }
        });
    }

    private void updateServiceStatus() {
        // 这里可以通过检查服务是否运行来更新状态
        // 简化处理，通过SharedPreferences判断
        isServiceRunning = prefs.getBoolean("service_running", false);
        updateServiceButtons();
    }

    private void updateServiceButtons() {
        btnStartService.setEnabled(!isServiceRunning);
        btnStopService.setEnabled(isServiceRunning);

        if (isServiceRunning) {
            tvStatus.setText("Status: Running");
            tvStatus.setTextColor(getColor(android.R.color.holo_green_dark));
        } else {
            tvStatus.setText("Status: Stopped");
            tvStatus.setTextColor(getColor(android.R.color.holo_red_dark));
        }
    }

    private void updateStatus(boolean connected) {
        runOnUiThread(() -> {
            if (connected) {
                tvStatus.setText("Status: Connected");
                tvStatus.setTextColor(getColor(android.R.color.holo_green_dark));
            } else if (isServiceRunning) {
                tvStatus.setText("Status: Connecting...");
                tvStatus.setTextColor(getColor(android.R.color.holo_orange_dark));
            } else {
                tvStatus.setText("Status: Stopped");
                tvStatus.setTextColor(getColor(android.R.color.holo_red_dark));
            }
        });
    }

    private void openAccessibilitySettings() {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        startActivity(intent);
        Toast.makeText(this, "请开启 Device Control Service", Toast.LENGTH_LONG).show();
        addLog("打开无障碍设置页面");
    }

//
    private void requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())
            );
            startActivityForResult(intent, REQUEST_OVERLAY);
            addLog("请求悬浮窗权限");
        }
    }

    private void startDeviceControlService() {
        // 检查必要权限
        if (!checkRequiredPermissions()) {
            new AlertDialog.Builder(this)
                    .setTitle("权限不足")
                    .setMessage("请授予所有必要权限后再启动服务")
                    .setPositiveButton("授予权限", (dialog, which) -> checkAndRequestPermissions())
                    .setNegativeButton("取消", null)
                    .show();
            return;
        }

        // 检查无障碍服务
//        if (!isAccessibilityServiceEnabled()) {
//            new AlertDialog.Builder(this)
//                    .setTitle("提示")
//                    .setMessage("建议开启无障碍服务以获得完整功能")
//                    .setPositiveButton("去开启", (dialog, which) -> openAccessibilitySettings())
//                    .setNegativeButton("跳过", (dialog, which) -> doStartService())
//                    .show();
//            return;
//        }

        doStartService();
    }

    private void doStartService() {
        Intent intent = new Intent(this, DeviceControlService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }

        isServiceRunning = true;
        prefs.edit().putBoolean("service_running", true).apply();
        updateServiceButtons();
        Toast.makeText(this, "服务已启动", Toast.LENGTH_SHORT).show();
        addLog("启动控制服务");
    }

    private void stopDeviceControlService() {
        Intent intent = new Intent(this, DeviceControlService.class);
        stopService(intent);

        isServiceRunning = false;
        prefs.edit().putBoolean("service_running", false).apply();
        updateServiceButtons();
        tvStatus.setText("Status: Stopped");
        Toast.makeText(this, "服务已停止", Toast.LENGTH_SHORT).show();
        addLog("停止控制服务");
    }

    private void stopMediaProjectionService() {
        Intent intent = new Intent(this, MediaProjectionService.class);
        intent.setAction("STOP_PROJECTION");
        startService(intent);
    }

    private boolean checkRequiredPermissions() {
        // 检查基础权限
        if (!EasyPermissions.hasPermissions(this,
                Manifest.permission.INTERNET,
                Manifest.permission.ACCESS_NETWORK_STATE,
                Manifest.permission.ACCESS_WIFI_STATE)) {
            return false;
        }

        // Android 13+ 检查通知权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!EasyPermissions.hasPermissions(this, Manifest.permission.POST_NOTIFICATIONS)) {
                return false;
            }
        }

        return true;
    }

    private void toggleAccessibilityPause() {
        DeviceAccessibilityService service = DeviceAccessibilityService.getInstance();
        if (service != null) {
            isAccessibilityPaused = !isAccessibilityPaused;
            service.setEnabled(!isAccessibilityPaused);

            String message = isAccessibilityPaused ? "无障碍监听已暂停" : "无障碍监听已恢复";
            btnToggleAccessibility.setText(isAccessibilityPaused ? "恢复无障碍监听" : "暂停无障碍监听");
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            addLog(message);
        } else {
            Toast.makeText(this, "无障碍服务未运行", Toast.LENGTH_SHORT).show();
        }
    }

    private void testScreenshot() {
        if (!hasScreenCapturePermission()) {
            Toast.makeText(this, "请先授予截屏权限", Toast.LENGTH_SHORT).show();
            return;
        }

        MediaProjectionService service = MediaProjectionService.getInstance();
        if (service != null) {
            service.takeScreenshot();
            Toast.makeText(this, "正在截图...", Toast.LENGTH_SHORT).show();
            addLog("执行测试截图");
        } else {
            Toast.makeText(this, "截屏服务未启动", Toast.LENGTH_SHORT).show();
        }
    }

    private void testClick() {
        DeviceAccessibilityService service = DeviceAccessibilityService.getInstance();
        if (service != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // 测试点击屏幕中心
            int centerX = getResources().getDisplayMetrics().widthPixels / 2;
            int centerY = getResources().getDisplayMetrics().heightPixels / 2;

            service.performClickAsync(centerX, centerY, new DeviceAccessibilityService.GestureCallback() {
                @Override
                public void onSuccess() {
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "点击成功", Toast.LENGTH_SHORT).show();
                        addLog("测试点击成功: (" + centerX + ", " + centerY + ")");
                    });
                }

                @Override
                public void onFailure(String error) {
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "点击失败: " + error, Toast.LENGTH_SHORT).show();
                        addLog("测试点击失败: " + error);
                    });
                }
            });
        } else {
            Toast.makeText(this, "无障碍服务未运行", Toast.LENGTH_SHORT).show();
        }
    }

    private void refreshAllStatus() {
        updateAllSwitches();
        Toast.makeText(this, "状态已刷新", Toast.LENGTH_SHORT).show();
        addLog("手动刷新状态");
    }

    private void addLog(String message) {
        runOnUiThread(() -> {
            String timestamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
            String currentText = tvLogs.getText().toString();
            String newText = timestamp + " - " + message + "\n" + currentText;

            // 限制日志长度
            String[] lines = newText.split("\n");
            if (lines.length > 100) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < 100; i++) {
                    sb.append(lines[i]).append("\n");
                }
                newText = sb.toString();
            }

            tvLogs.setText(newText);
        });
    }

    private void clearLogs() {
        tvLogs.setText("日志已清除\n");
        addLog("日志清除");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_SCREEN_CAPTURE) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                // 启动MediaProjectionService
                Intent serviceIntent = new Intent(this, MediaProjectionService.class);
                serviceIntent.setAction("START_PROJECTION");
                serviceIntent.putExtra("resultCode", resultCode);
                serviceIntent.putExtra("data", data);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent);
                } else {
                    startService(serviceIntent);
                }

                // 保存状态
                prefs.edit().putBoolean("screen_capture_enabled", true).apply();

                // 延迟更新UI，等待服务启动
                uiHandler.postDelayed(() -> {
                    switchScreenCapture.setChecked(true);
                    Toast.makeText(this, "截屏权限已获取", Toast.LENGTH_SHORT).show();
                    addLog("截屏权限已授予");
                }, 500);

            } else {
                switchScreenCapture.setChecked(false);
                Toast.makeText(this, "截屏权限被拒绝", Toast.LENGTH_SHORT).show();
                addLog("截屏权限被拒绝");
            }
        } else if (requestCode == REQUEST_OVERLAY) {
            // 延迟检查权限状态
            uiHandler.postDelayed(() -> {
                boolean hasPermission = hasOverlayPermission();
                switchOverlay.setChecked(hasPermission);
                if (hasPermission) {
                    Toast.makeText(this, "悬浮窗权限已获取", Toast.LENGTH_SHORT).show();
                    addLog("悬浮窗权限已授予");
                } else {
                    Toast.makeText(this, "悬浮窗权限被拒绝", Toast.LENGTH_SHORT).show();
                    addLog("悬浮窗权限被拒绝");
                }
            }, 500);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 从设置页面返回后刷新状态
        uiHandler.postDelayed(() -> {
            updateAllSwitches();

            // 检查无障碍服务是否新开启
            if (isAccessibilityServiceEnabled() && !switchAccessibility.isChecked()) {
                addLog("检测到无障碍服务已开启");
            }
        }, 500);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 暂停刷新，节省资源
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // 停止定时刷新
        stopPeriodicRefresh();
        // 取消注册EventBus
        GlobalEventBus.getInstance().unregisterAll(accessibilityListener);
        // 注销广播接收器
        try {
            unregisterReceiver(serviceReceiver);
        } catch (Exception e) {
            Log.e(TAG, "Error unregistering receiver", e);
        }
        try {
            unregisterReceiver(reauthorizationReceiver);
        } catch (Exception e) {
            Log.e(TAG, "Error unregistering receiver", e);
        }
    }
    private void registerEventBusListeners() {
        // 注册无障碍服务连接事件
        GlobalEventBus.getInstance().register("accessibility_connected", accessibilityListener);
        // 注册无障碍服务连接事件
        GlobalEventBus.getInstance().register("screen_off", accessibilityListener);

        // 注册其他事件
        GlobalEventBus.getInstance().register("service_status", (event, data) -> {
            if (data instanceof Boolean) {
                boolean connected = (Boolean) data;
                runOnUiThread(() -> {
                    updateStatus(connected);
                    addLog("服务状态更新: " + (connected ? "已连接" : "已断开"));
                });
            }
        });

        GlobalEventBus.getInstance().register("screenshot_result", (event, data) -> {
            runOnUiThread(() -> {
                if (data instanceof String) {
                    addLog("截图结果: " + data);
                }
            });
        });

        Log.d(TAG, "EventBus listeners registered");
    }
    @Override
    public void onPermissionsGranted(int requestCode, @NonNull List<String> perms) {
        Log.d(TAG, "Permissions granted: " + perms);
        addLog("权限已授予: " + perms.size() + "个");

        if (requestCode == REQUEST_PERMISSIONS) {
            Toast.makeText(this, "权限已授予", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onPermissionsDenied(int requestCode, @NonNull List<String> perms) {
        Log.d(TAG, "Permissions denied: " + perms);
        addLog("权限被拒绝: " + perms.size() + "个");

        if (EasyPermissions.somePermissionPermanentlyDenied(this, perms)) {
            new AlertDialog.Builder(this)
                    .setTitle("权限被拒绝")
                    .setMessage("某些权限被永久拒绝，请在设置中手动授予权限")
                    .setPositiveButton("去设置", (dialog, which) -> {
                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        Uri uri = Uri.fromParts("package", getPackageName(), null);
                        intent.setData(uri);
                        startActivity(intent);
                    })
                    .setNegativeButton("取消", null)
                    .show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }
}