package com.devicecontrol.client.service;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;

import com.devicecontrol.client.MainActivity;
import com.devicecontrol.client.R;
import com.devicecontrol.client.network.SocketIOManager;
import com.devicecontrol.client.utils.Constants;
import com.devicecontrol.client.utils.GlobalEventBus;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.Queue;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class MediaProjectionService extends Service {
    private static final String TAG = "MediaProjectionService";
    private static final String CHANNEL_ID = "media_projection_channel";
    private static final int NOTIFICATION_ID = 2001;

    private MediaProjectionManager mediaProjectionManager;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private Handler backgroundHandler;
    private HandlerThread handlerThread;
    private SocketIOManager socketIOManager;

    // 屏幕状态管理
    private PowerManager powerManager;
    private PowerManager.WakeLock wakeLock;
    private boolean isScreenOn = true;

    private int screenWidth;
    private int screenHeight;
    private int screenDensity;

    // 状态标志
    private volatile boolean isProjectionReady = false;
    private volatile boolean isCapturing = false;
    private volatile boolean isInitializing = false;
    private volatile boolean needReauthorization = false;

    // 截图请求队列
    private Queue<ScreenshotRequest> screenshotQueue = new LinkedList<>();

    private static MediaProjectionService instance;

    // 保存权限数据（注意：熄屏后这些数据会失效）
    private int savedResultCode;
    private Intent savedData;
    private long permissionTimestamp; // 记录权限获取时间

    // 屏幕状态接收器
    private BroadcastReceiver screenStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "Screen state changed: " + action);

            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                isScreenOn = false;
                handleScreenOff();
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                isScreenOn = true;
                handleScreenOn();
            } else if (Intent.ACTION_USER_PRESENT.equals(action)) {
                // 用户解锁
                handleUserPresent();
            }
        }
    };

    // 命令接收器
    private BroadcastReceiver commandReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "Received broadcast: " + action);

            if ("com.devicecontrol.TAKE_SCREENSHOT".equals(action)) {
                takeScreenshot();
            } else if ("com.devicecontrol.REQUEST_REAUTHORIZATION".equals(action)) {
                requestReauthorization();
            }
        }
    };

    private static class ScreenshotRequest {
        long timestamp;
        int retryCount;

        ScreenshotRequest() {
            this.timestamp = System.currentTimeMillis();
            this.retryCount = 0;
        }
    }

    public static MediaProjectionService getInstance() {
        return instance;
    }

    public boolean isReady() {
        return isProjectionReady && mediaProjection != null && virtualDisplay != null &&
                imageReader != null && isScreenOn && !needReauthorization;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        Log.d(TAG, "MediaProjectionService onCreate");

        // 初始化
        mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);

        // 获取或创建SocketIO连接
        socketIOManager = SocketIOManager.getInstance();

        // 注册接收器
        registerReceivers();

        // 获取屏幕信息
        updateScreenMetrics();

        // 创建后台线程
        handlerThread = new HandlerThread("MediaProjection");
        handlerThread.start();
        backgroundHandler = new Handler(handlerThread.getLooper());

        // 启动前台服务
        startForegroundService();

        // 获取WakeLock（可选，防止CPU休眠）
        acquireWakeLock();
    }

    private void registerReceivers() {
        // 注册屏幕状态接收器
        IntentFilter screenFilter = new IntentFilter();
        screenFilter.addAction(Intent.ACTION_SCREEN_OFF);
        screenFilter.addAction(Intent.ACTION_SCREEN_ON);
        screenFilter.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(screenStateReceiver, screenFilter);

        // 注册命令接收器
        IntentFilter commandFilter = new IntentFilter();
        commandFilter.addAction("com.devicecontrol.TAKE_SCREENSHOT");
        commandFilter.addAction("com.devicecontrol.REQUEST_REAUTHORIZATION");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(commandReceiver, commandFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(commandReceiver, commandFilter);
        }

        Log.d(TAG, "Receivers registered");
    }

    private void acquireWakeLock() {
        if (wakeLock == null) {
            wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "DeviceControl:MediaProjection"
            );
        }

        if (!wakeLock.isHeld()) {
            wakeLock.acquire(10 * 60 * 1000L); // 10分钟
            Log.d(TAG, "WakeLock acquired");
        }
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            Log.d(TAG, "WakeLock released");
        }
    }

    private void updateScreenMetrics() {
        WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        Display display = windowManager.getDefaultDisplay();
        DisplayMetrics metrics = new DisplayMetrics();
        display.getRealMetrics(metrics);

        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;
        screenDensity = metrics.densityDpi;

        Log.d(TAG, String.format("Screen metrics: %dx%d, density=%d",
                screenWidth, screenHeight, screenDensity));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            Log.e(TAG, "onStartCommand with null intent");

            // 检查是否需要重新授权
            if (needReauthorization) {
                requestReauthorization();
            }

            return START_STICKY;
        }

        String action = intent.getAction();
        Log.d(TAG, "onStartCommand action: " + action);

        switch (action != null ? action : "") {
            case "START_PROJECTION":
                int resultCode = intent.getIntExtra("resultCode", Activity.RESULT_CANCELED);
                Intent data = intent.getParcelableExtra("data");

                if (resultCode == Activity.RESULT_OK && data != null) {
                    Log.d(TAG, "Starting projection with valid permission");
                    savedResultCode = resultCode;
                    savedData = data;
                    permissionTimestamp = System.currentTimeMillis();
                    needReauthorization = false;
                    initializeProjection(resultCode, data);
                } else {
                    Log.e(TAG, "Invalid permission data");
                    sendError("Invalid permission data. Please grant screen capture permission.");
                    needReauthorization = true;
                }
                break;

            case "STOP_PROJECTION":
                Log.d(TAG, "Stopping projection");
                stopProjection();
                stopSelf();
                break;

            case "TAKE_SCREENSHOT":
                Log.d(TAG, "Taking screenshot via intent");
                takeScreenshot();
                break;
        }

        return START_STICKY;
    }

    private synchronized void initializeProjection(int resultCode, Intent data) {
        if (isInitializing) {
            Log.w(TAG, "Already initializing, skipping");
            return;
        }

        isInitializing = true;

        backgroundHandler.post(() -> {
            try {
                // 先清理旧资源
                cleanup();

                // 创建MediaProjection
                Log.d(TAG, "Creating MediaProjection");
                mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data);

                if (mediaProjection == null) {
                    throw new Exception("Failed to create MediaProjection");
                }

                // 注册回调
                mediaProjection.registerCallback(new MediaProjection.Callback() {
                    @Override
                    public void onStop() {
                        Log.d(TAG, "MediaProjection stopped by system");
                        isProjectionReady = false;
                        cleanup();

                        // 如果屏幕是开着的，说明不是因为熄屏导致的停止
                        if (isScreenOn) {
                            Log.w(TAG, "MediaProjection stopped while screen is on, need reauthorization");
                            needReauthorization = true;
                            requestReauthorization();
                        }
                    }
                }, backgroundHandler);

                // 设置VirtualDisplay
                setupVirtualDisplay();

            } catch (Exception e) {
                Log.e(TAG, "Error initializing projection", e);
                sendError("Failed to initialize: " + e.getMessage());
                isInitializing = false;
                needReauthorization = true;
                requestReauthorization();
            }
        });
    }

    private void setupVirtualDisplay() {
        try {
            Log.d(TAG, "Setting up VirtualDisplay");

            if (imageReader != null) {
                imageReader.close();
            }

            imageReader = ImageReader.newInstance(
                    screenWidth,
                    screenHeight,
                    PixelFormat.RGBA_8888,
                    2
            );

            imageReader.setOnImageAvailableListener(reader -> {
                Log.v(TAG, "New image available");
            }, backgroundHandler);

            if (virtualDisplay != null) {
                virtualDisplay.release();
            }

            virtualDisplay = mediaProjection.createVirtualDisplay(
                    "ScreenCapture",
                    screenWidth,
                    screenHeight,
                    screenDensity,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.getSurface(),
                    null,
                    backgroundHandler
            );

            if (virtualDisplay != null) {
                isProjectionReady = true;
                isInitializing = false;
                needReauthorization = false;
                Log.d(TAG, "VirtualDisplay created successfully, service is ready");

                // 更新通知
                updateNotification(true);

                // 发送就绪广播
                Intent intent = new Intent("com.devicecontrol.PROJECTION_READY");
                sendBroadcast(intent);

                // 处理等待中的截图请求
                processQueuedScreenshots();

            } else {
                throw new Exception("Failed to create VirtualDisplay");
            }

        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException creating VirtualDisplay", e);
            sendError("Permission expired, need reauthorization");
            cleanup();
            isInitializing = false;
            needReauthorization = true;
            requestReauthorization();
        } catch (Exception e) {
            Log.e(TAG, "Error setting up VirtualDisplay", e);
            sendError("Error setting up capture: " + e.getMessage());
            cleanup();
            isInitializing = false;
        }
    }

    /**
     * 处理屏幕熄灭
     */
    private void handleScreenOff() {
        Log.d(TAG, "Screen turned off");
        isScreenOn = false;

        // 停止投影但不清理权限数据
        if (mediaProjection != null) {
            Log.d(TAG, "Stopping projection due to screen off");
            cleanup(); // 只清理资源，不清理savedData
        }
        // 使用EventBus发送事件
        GlobalEventBus.getInstance().post("screen_off", true);
        // 更新通知
        updateNotification(false);

        // 发送屏幕状态
        sendScreenStatus(false);
    }

    /**
     * 处理屏幕开启
     */
    private void handleScreenOn() {
        Log.d(TAG, "Screen turned on");
        isScreenOn = true;

        // 不自动重新初始化，需要用户重新授权
        needReauthorization = true;

        // 更新通知
        updateNotification(false);

        // 发送屏幕状态
        sendScreenStatus(true);
    }

    /**
     * 处理用户解锁
     */
    private void handleUserPresent() {
        Log.d(TAG, "User present (unlocked)");

        // 可以提示用户重新授权
        if (needReauthorization) {
            requestReauthorization();
        }
    }

    /**
     * 请求重新授权
     */
    private void requestReauthorization() {
        Log.d(TAG, "Requesting reauthorization");

        // 发送需要重新授权的广播
        Intent intent = new Intent("com.devicecontrol.NEED_REAUTHORIZATION");
        intent.setPackage(getPackageName());
        sendBroadcast(intent);

        // 更新通知，提示用户
        updateNotification(false);

        // 发送错误消息到服务器
        sendError("Screen capture permission expired, please reauthorize");
    }

    /**
     * 发送屏幕状态
     */
    private void sendScreenStatus(boolean screenOn) {
        try {
            if (socketIOManager != null && socketIOManager.isConnected()) {
                org.json.JSONObject status = new org.json.JSONObject();
                status.put("device_id", Constants.DEVICE_ID);
                status.put("screen_on", screenOn);
                status.put("projection_ready", isProjectionReady);
                status.put("timestamp", System.currentTimeMillis());
                socketIOManager.emit("screen_status", status);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to send screen status", e);
        }
    }

    private void startForegroundService() {
        createNotificationChannel();
        Notification notification = createNotification(isProjectionReady);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            );
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void updateNotification(boolean ready) {
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.notify(NOTIFICATION_ID, createNotification(ready));
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Screen Capture Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Screen capture service for remote control");
            channel.setShowBadge(false);
            channel.enableLights(false);
            channel.enableVibration(false);
            channel.setSound(null, null);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private Notification createNotification(boolean ready) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String statusText;
        if (!isScreenOn) {
            statusText = "Screen is off";
        } else if (needReauthorization) {
            statusText = "Need reauthorization - Click to grant";
        } else if (ready) {
            statusText = "Ready";
        } else {
            statusText = "Initializing...";
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Screen Capture Service")
                .setContentText(statusText)
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setSilent(true);

        // 如果需要重新授权，添加授权按钮
        if (needReauthorization && isScreenOn) {
            Intent authIntent = new Intent(this, MainActivity.class);
            authIntent.setAction("REQUEST_SCREEN_CAPTURE");
            PendingIntent authPendingIntent = PendingIntent.getActivity(
                    this,
                    1,
                    authIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            builder.addAction(android.R.drawable.ic_menu_manage, "Authorize", authPendingIntent);
        }

        // 停止按钮
        Intent stopIntent = new Intent(this, MediaProjectionService.class);
        stopIntent.setAction("STOP_PROJECTION");
        PendingIntent stopPendingIntent = PendingIntent.getService(
                this,
                2,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        builder.addAction(android.R.drawable.ic_delete, "Stop", stopPendingIntent);

        return builder.build();
    }

    public synchronized void takeScreenshot() {
        Log.d(TAG, String.format("takeScreenshot called, isReady=%b, isScreenOn=%b, needReauth=%b",
                isProjectionReady, isScreenOn, needReauthorization));

        if (!isScreenOn) {
            Log.e(TAG, "Cannot take screenshot while screen is off");
            sendError("Cannot take screenshot while screen is off");
            return;
        }

        if (needReauthorization) {
            Log.e(TAG, "Need reauthorization");
            sendError("Screen capture permission expired, please reauthorize");
            requestReauthorization();
            return;
        }

        if (!isProjectionReady) {
            if (isInitializing) {
                Log.w(TAG, "Service is initializing, queuing screenshot request");
                screenshotQueue.offer(new ScreenshotRequest());
                while (screenshotQueue.size() > 5) {
                    screenshotQueue.poll();
                }
                return;
            } else {
                Log.e(TAG, "Service not ready");
                sendError("Screen capture service not ready");
                requestReauthorization();
                return;
            }
        }

        if (imageReader == null || virtualDisplay == null) {
            Log.e(TAG, "ImageReader or VirtualDisplay is null");
            sendError("Capture components not ready");
            needReauthorization = true;
            requestReauthorization();
            return;
        }

        if (isCapturing) {
            Log.w(TAG, "Already capturing, skipping");
            return;
        }

        performScreenshot();
    }

    private void performScreenshot() {
        isCapturing = true;

        backgroundHandler.post(() -> {
            Image image = null;
            try {
                Thread.sleep(50);
                image = imageReader.acquireLatestImage();

                if (image == null) {
                    Thread.sleep(50);
                    image = imageReader.acquireLatestImage();
                }

                if (image != null) {
                    Log.d(TAG, "Image acquired, processing...");

                    Bitmap bitmap = imageToBitmap(image);
                    String base64 = bitmapToBase64(bitmap);

                    sendScreenshot(base64);

                    bitmap.recycle();
                } else {
                    Log.w(TAG, "No image available");
                    sendError("No image available");
                }

            } catch (Exception e) {
                Log.e(TAG, "Error capturing screenshot", e);
                sendError("Error capturing: " + e.getMessage());
            } finally {
                if (image != null) {
                    try {
                        image.close();
                    } catch (Exception e) {
                        Log.e(TAG, "Error closing image", e);
                    }
                }
                isCapturing = false;
            }
        });
    }

    private void processQueuedScreenshots() {
        if (!screenshotQueue.isEmpty() && isProjectionReady) {
            Log.d(TAG, "Processing " + screenshotQueue.size() + " queued screenshots");

            backgroundHandler.postDelayed(() -> {
                while (!screenshotQueue.isEmpty() && isProjectionReady) {
                    screenshotQueue.poll();
                    takeScreenshot();
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }, 500);
        }
    }

    private void sendScreenshot(String base64Image) {
        try {
            Log.d(TAG, "Sending screenshot, size: " + (base64Image.length() / 1024) + " KB");

            if (socketIOManager == null) {
                socketIOManager = SocketIOManager.getInstance();
            }

            if (!socketIOManager.isConnected()) {
                Log.w(TAG, "SocketIO not connected, connecting...");
                socketIOManager.connect();

                backgroundHandler.postDelayed(() -> sendScreenshot(base64Image), 2000);
                return;
            }

            socketIOManager.sendScreenshot(Constants.DEVICE_ID, base64Image);

            Intent intent = new Intent("com.devicecontrol.SCREENSHOT_SENT");
            intent.putExtra("success", true);
            intent.putExtra("size", base64Image.length());
            sendBroadcast(intent);

            Log.d(TAG, "Screenshot sent successfully");

        } catch (Exception e) {
            Log.e(TAG, "Error sending screenshot", e);
            sendError(e.getMessage());
        }
    }

    private void sendError(String error) {
        Log.e(TAG, "Error: " + error);

        Intent intent = new Intent("com.devicecontrol.SCREENSHOT_SENT");
        intent.putExtra("success", false);
        intent.putExtra("error", error);
        sendBroadcast(intent);

        try {
            if (socketIOManager != null && socketIOManager.isConnected()) {
                org.json.JSONObject errorData = new org.json.JSONObject();
                errorData.put("device_id", Constants.DEVICE_ID);
                errorData.put("error", error);
                errorData.put("timestamp", System.currentTimeMillis());
                socketIOManager.emit("screenshot_error", errorData);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to send error to server", e);
        }
    }

    private Bitmap imageToBitmap(Image image) {
        int width = image.getWidth();
        int height = image.getHeight();
        final Image.Plane[] planes = image.getPlanes();
        final ByteBuffer buffer = planes[0].getBuffer();
        int pixelStride = planes[0].getPixelStride();
        int rowStride = planes[0].getRowStride();
        int rowPadding = rowStride - pixelStride * width;

        Bitmap bitmap = Bitmap.createBitmap(
                width + rowPadding / pixelStride,
                height,
                Bitmap.Config.ARGB_8888
        );
        bitmap.copyPixelsFromBuffer(buffer);

        if (rowPadding != 0) {
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height);
        }

        return bitmap;
    }

    private String bitmapToBase64(Bitmap bitmap) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        int quality = 80;
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos);

        while (baos.toByteArray().length > 500 * 1024 && quality > 10) {
            baos.reset();
            quality -= 10;
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos);
        }

        byte[] bytes = baos.toByteArray();
        return Base64.encodeToString(bytes, Base64.NO_WRAP);
    }

    private synchronized void cleanup() {
        Log.d(TAG, "Cleaning up resources");

        isProjectionReady = false;
        isCapturing = false;

        if (virtualDisplay != null) {
            try {
                virtualDisplay.release();
            } catch (Exception e) {
                Log.e(TAG, "Error releasing VirtualDisplay", e);
            }
            virtualDisplay = null;
        }

        if (imageReader != null) {
            try {
                imageReader.close();
            } catch (Exception e) {
                Log.e(TAG, "Error closing ImageReader", e);
            }
            imageReader = null;
        }
    }

    private synchronized void stopProjection() {
        Log.d(TAG, "Stopping projection");

        cleanup();

        if (mediaProjection != null) {
            try {
                mediaProjection.stop();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping MediaProjection", e);
            }
            mediaProjection = null;
        }

        // 清除保存的权限数据
        savedResultCode = 0;
        savedData = null;
        permissionTimestamp = 0;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Service onDestroy");

        stopProjection();

        try {
            unregisterReceiver(screenStateReceiver);
            unregisterReceiver(commandReceiver);
        } catch (Exception e) {
            Log.e(TAG, "Error unregistering receivers", e);
        }

        releaseWakeLock();

        if (handlerThread != null) {
            handlerThread.quitSafely();
            handlerThread = null;
        }

        instance = null;

        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}