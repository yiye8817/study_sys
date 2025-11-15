package com.devicecontrol.client.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

import com.devicecontrol.client.MainActivity;
import com.devicecontrol.client.model.DeviceInfo;
import com.devicecontrol.client.model.EventData;
import com.devicecontrol.client.network.ApiClient;
import com.devicecontrol.client.network.SocketIOManager;
import com.devicecontrol.client.service.handler.CommandHandler;
import com.devicecontrol.client.service.handler.EnhancedCommandHandler;
import com.devicecontrol.client.utils.Constants;
import com.devicecontrol.client.utils.DeviceInfoCollector;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DeviceControlService extends Service {
    private static final String TAG = "DeviceControlService";
    private static final String CHANNEL_ID = "device_control_channel";
    private static final int NOTIFICATION_ID = 1001;

    private PowerManager.WakeLock wakeLock;
    private SocketIOManager socketManager;
    private ApiClient apiClient;
    private CommandHandler commandHandler;
    private EnhancedCommandHandler enhancedCommandHandler;
    private HeartbeatManager heartbeatManager;
    private DeviceInfoCollector deviceInfoCollector;
    private ScheduledExecutorService scheduler;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service onCreate");

        // 初始化组件
        initializeComponents();

        // 创建前台服务通知
        startForegroundService();

        // 获取WakeLock保持服务运行
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "DeviceControl:WakeLock"
        );
        wakeLock.acquire(10*60*1000L /*10 minutes*/);
    }

    private void startForegroundService() {
        // 创建通知渠道
        createNotificationChannel();

        // 创建通知
        Notification notification = createNotification();

        // 根据Android版本启动前台服务
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+
            int foregroundServiceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;

            // 检查是否有蓝牙权限，如果有则添加CONNECTED_DEVICE类型
            if (ContextCompat.checkSelfPermission(this, "android.permission.BLUETOOTH_CONNECT")
                    == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, "android.permission.CHANGE_WIFI_STATE")
                            == PackageManager.PERMISSION_GRANTED) {
                // 只有在有相关权限时才使用CONNECTED_DEVICE类型
                // foregroundServiceType |= ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE;
            }

            // 使用 ServiceCompat 来兼容不同版本
            ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    foregroundServiceType
            );
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10-13
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            // Android 9及以下
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Device Control Service",
                    NotificationManager.IMPORTANCE_LOW  // 改为LOW以减少干扰
            );
            channel.setDescription("Device control background service");
            channel.setShowBadge(false);
            channel.enableLights(false);
            channel.enableVibration(false);
            channel.setSound(null, null);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Device Control")
                .setContentText("Service is running")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setSilent(true)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET);

        // 添加停止服务的动作
        Intent stopIntent = new Intent(this, DeviceControlService.class);
        stopIntent.setAction("STOP_SERVICE");
        PendingIntent stopPendingIntent = PendingIntent.getService(
                this,
                0,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        builder.addAction(android.R.drawable.ic_delete, "Stop", stopPendingIntent);

        return builder.build();
    }

    private void initializeComponents() {
        // 初始化设备信息收集器
        deviceInfoCollector = new DeviceInfoCollector(this);

        // 初始化API客户端
        apiClient = new ApiClient(Constants.SERVER_URL);

        // 初始化命令处理器
        commandHandler = new CommandHandler(this);
        enhancedCommandHandler = new EnhancedCommandHandler(this, apiClient);

        // 初始化或获取SocketIO管理器（移除setApplicationContext）
        socketManager = SocketIOManager.getInstance();
        socketManager.setEventListener(new SocketIOManager.EventListener() {
            @Override
            public void onConnected() {
                Log.d(TAG, "Socket connected");
                registerDevice();
                sendEvent(Constants.EventTypes.STARTUP, "Device started");

                // 发送连接状态广播
                Intent intent = new Intent("com.devicecontrol.SERVICE_STATUS");
                intent.putExtra("connected", true);
                sendBroadcast(intent);
            }

            @Override
            public void onDisconnected() {
                Log.d(TAG, "Socket disconnected");

                // 发送断开状态广播
                Intent intent = new Intent("com.devicecontrol.SERVICE_STATUS");
                intent.putExtra("connected", false);
                sendBroadcast(intent);
            }

            @Override
            public void onCommandReceived(JSONObject command) {
                handleCommand(command);
            }
        });

        // 初始化心跳管理器
        heartbeatManager = new HeartbeatManager(this, apiClient, socketManager);

        // 初始化调度器
        scheduler = Executors.newScheduledThreadPool(2);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service onStartCommand");

        // 检查是否是停止服务的请求
        if (intent != null && "STOP_SERVICE".equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }

        // 连接服务器
        connectToServer();

        // 启动心跳
        startHeartbeat();

        return START_STICKY;
    }

    private void connectToServer() {
        // 注册设备到服务器
        DeviceInfo deviceInfo = deviceInfoCollector.collectDeviceInfo();
        apiClient.registerDevice(deviceInfo, new ApiClient.Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                Log.d(TAG, "Device registered successfully");
                // 连接SocketIO
                socketManager.connect();
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Failed to register device: " + error);
                // 重试连接
                scheduler.schedule(() -> connectToServer(), 5, TimeUnit.SECONDS);
            }
        });
    }

    private void registerDevice() {
        JSONObject deviceData = new JSONObject();
        try {
            deviceData.put("device_id", Constants.DEVICE_ID);
            socketManager.emit("register_device", deviceData);
        } catch (Exception e) {
            Log.e(TAG, "Failed to register device via socket", e);
        }
    }

    private void startHeartbeat() {
        heartbeatManager.start();
    }

    private void sendEvent(int eventType, String message) {
        EventData eventData = new EventData();
        eventData.deviceType = "mobile";
        eventData.deviceId = Constants.DEVICE_ID;
        eventData.eventId = "EVENT_" + eventType;
        eventData.eventValue = String.valueOf(eventType);
        eventData.location = Build.MODEL;
        eventData.timestamp = System.currentTimeMillis();

        Map<String, Object> extraFields = new HashMap<>();
        extraFields.put("message", message);
        eventData.extraFields = extraFields;

        apiClient.sendEvent(eventData, new ApiClient.Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                Log.d(TAG, "Event sent: " + eventType);
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Failed to send event: " + error);
            }
        });
    }
    // 在 DeviceControlService.java 中添加广播接收器注册的修复

//    private void registerBroadcastReceivers() {
//        // 注册截屏权限结果接收器
//        BroadcastReceiver screenCaptureReceiver = new BroadcastReceiver() {
//            @Override
//            public void onReceive(Context context, Intent intent) {
//                if ("com.devicecontrol.SCREEN_CAPTURE_PERMISSION".equals(intent.getAction())) {
//                    int resultCode = intent.getIntExtra("resultCode", -1);
//                    Intent data = intent.getParcelableExtra("data");
//                    handleScreenCapturePermission(resultCode, data);
//                }
//            }
//        };
//
//        IntentFilter filter = new IntentFilter("com.devicecontrol.SCREEN_CAPTURE_PERMISSION");
//
//        // Android 14及以上需要指定标志
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
//            registerReceiver(screenCaptureReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
//        } else {
//            registerReceiver(screenCaptureReceiver, filter);
//        }
//    }
    private void handleCommand(JSONObject commandData) {
        try {
            String command = commandData.getString("command");
            JSONObject params = commandData.optJSONObject("params");

            // 优先使用增强命令处理器
            enhancedCommandHandler.execute(command, params, result -> {
                // 发送响应
                JSONObject response = new JSONObject();
                try {
                    response.put("device_id", Constants.DEVICE_ID);
                    response.put("command", command);
                    response.put("result", result);
                    socketManager.emit("command_response", response);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to send command response", e);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Failed to handle command", e);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service onDestroy");

        // 发送关机事件
        sendEvent(Constants.EventTypes.SHUTDOWN, "Service stopping");

        // 停止心跳
        heartbeatManager.stop();

        // 断开连接
        socketManager.disconnect();

        // 释放WakeLock
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }

        // 关闭调度器
        scheduler.shutdown();

        // 移除通知
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}