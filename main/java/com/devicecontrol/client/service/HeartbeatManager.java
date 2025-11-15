package com.devicecontrol.client.service;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.util.Log;

import com.devicecontrol.client.model.EventData;
import com.devicecontrol.client.network.ApiClient;
import com.devicecontrol.client.network.SocketIOManager;
import com.devicecontrol.client.utils.Constants;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class HeartbeatManager {
    private static final String TAG = "HeartbeatManager";

    private Context context;
    private ApiClient apiClient;
    private SocketIOManager socketManager;
    private ScheduledExecutorService scheduler;
    private boolean isRunning = false;

    public HeartbeatManager(Context context, ApiClient apiClient, SocketIOManager socketManager) {
        this.context = context;
        this.apiClient = apiClient;
        this.socketManager = socketManager;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    public void start() {
        if (isRunning) {
            return;
        }

        isRunning = true;
        scheduler.scheduleAtFixedRate(this::sendHeartbeat, 0, Constants.HEARTBEAT_INTERVAL, TimeUnit.SECONDS);
        Log.d(TAG, "Heartbeat started");
    }

    public void stop() {
        isRunning = false;
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
        Log.d(TAG, "Heartbeat stopped");
    }

    private void sendHeartbeat() {
        try {
            // 收集系统信息
            Map<String, Object> systemInfo = collectSystemInfo();

            // 创建心跳事件
            EventData eventData = new EventData();
            eventData.deviceType = "mobile";
            eventData.deviceId = Constants.DEVICE_ID;
            eventData.eventId = "EVENT_" + Constants.EventTypes.HEARTBEAT;
            eventData.eventValue = String.valueOf(Constants.EventTypes.HEARTBEAT);
            eventData.location = Build.MODEL;
            eventData.timestamp = System.currentTimeMillis();
            eventData.extraFields = systemInfo;

            // 通过API发送
            apiClient.sendEvent(eventData, null);

            // 通过Socket发送状态
            if (socketManager.isConnected()) {
                JSONObject status = new JSONObject();
                status.put("device_id", Constants.DEVICE_ID);
                status.put("status", systemInfo);
                socketManager.emit("device_status", status);
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to send heartbeat", e);
        }
    }

    private Map<String, Object> collectSystemInfo() {
        Map<String, Object> info = new HashMap<>();

        try {
            // CPU使用率
            info.put("cpu", getCpuUsage());

            // 内存使用
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(memInfo);

            long totalMem = memInfo.totalMem;
            long availMem = memInfo.availMem;
            long usedMem = totalMem - availMem;
            int memPercent = (int) ((usedMem * 100) / totalMem);

            info.put("memory", memPercent + "%");
            info.put("memory_used", usedMem / (1024 * 1024) + "MB");
            info.put("memory_total", totalMem / (1024 * 1024) + "MB");

            // 电池信息 - 修复Android 14兼容性
            IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = null;

            // Android 14需要特殊处理
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // 使用Context.RECEIVER_NOT_EXPORTED标志
                batteryStatus = context.registerReceiver(null, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                batteryStatus = context.registerReceiver(null, filter);
            }

            if (batteryStatus != null) {
                int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                if (level != -1 && scale != -1) {
                    int batteryPct = (int) ((level * 100) / (float) scale);
                    info.put("battery", batteryPct + "%");

                    int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                    boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                            status == BatteryManager.BATTERY_STATUS_FULL;
                    info.put("charging", isCharging);
                }
            }

            // 正常运行时间
            long uptime = android.os.SystemClock.elapsedRealtime();
            info.put("uptime", uptime / 1000); // 转换为秒

        } catch (Exception e) {
            Log.e(TAG, "Error collecting system info", e);
        }

        return info;
    }

    private String getCpuUsage() {
        try {
            Process process = Runtime.getRuntime().exec("top -n 1");
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream())
            );

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("CPU")) {
                    // 解析CPU使用率
                    String[] parts = line.split(",");
                    for (String part : parts) {
                        if (part.contains("idle")) {
                            String idle = part.replaceAll("[^0-9]", "");
                            if (!idle.isEmpty()) {
                                try {
                                    int idlePercent = Integer.parseInt(idle);
                                    return (100 - idlePercent) + "%";
                                } catch (NumberFormatException e) {
                                    // 忽略解析错误
                                }
                            }
                        }
                    }
                }
            }
            reader.close();
        } catch (Exception e) {
            Log.e(TAG, "Error getting CPU usage", e);
        }

        return "N/A";
    }
}