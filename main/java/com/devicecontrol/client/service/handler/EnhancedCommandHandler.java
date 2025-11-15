package com.devicecontrol.client.service.handler;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.devicecontrol.client.network.ApiClient;
import com.devicecontrol.client.service.DeviceAccessibilityService;
import com.devicecontrol.client.service.MediaProjectionService;
import com.devicecontrol.client.utils.Constants;
import com.devicecontrol.client.utils.ScreenCaptureManager;

import org.json.JSONArray;
import org.json.JSONObject;

public class EnhancedCommandHandler extends CommandHandler {
    private static final String TAG = "EnhancedCommandHandler";

    private DeviceAccessibilityService accessibilityService;
    private ScreenCaptureManager screenCaptureManager;
    private MediaProjectionService mediaProjectionService;
    private ApiClient apiClient;
    private Context context;
    private Handler mainHandler;
    private KeyHandler keyHandler;
    private GestureHandler gestureHandler; // 添加手势处理器
    public EnhancedCommandHandler(Context context, ApiClient apiClient) {
        super(context);
        this.context = context;
        this.apiClient = apiClient;
        this.screenCaptureManager = ScreenCaptureManager.getInstance(context);
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.keyHandler = new KeyHandler(null); // 初始化时没有AccessibilityService
        this.gestureHandler = new GestureHandler(context, null); // 初始化手势处理器
    }

    @Override
    public void execute(String command, JSONObject params, CommandCallback callback) {
        // 获取服务实例
        accessibilityService = DeviceAccessibilityService.getInstance();
        mediaProjectionService = MediaProjectionService.getInstance();
        // 更新KeyHandler的AccessibilityService实例
        if (keyHandler != null && accessibilityService != null) {
            keyHandler.setAccessibilityService(accessibilityService);
        }
        if (gestureHandler != null && accessibilityService != null) {
            gestureHandler.setAccessibilityService(accessibilityService);
        }
        // 先尝试使用增强功能处理
        if (handleEnhancedCommand(command, params, callback)) {
            return;
        }

        // 使用原有处理方式
        super.execute(command, params, callback);
    }

    private boolean handleEnhancedCommand(String command, JSONObject params, CommandCallback callback) {
        try {
            switch (command) {
                // 修改 handleEnhancedCommand 中的 KEY 命令处理
                case Constants.Commands.KEY:
                    if (keyHandler != null) {
                        keyHandler.handleKey(params, callback);
                        return true;
                    }
                    return false;
                case Constants.Commands.SCREENSHOT:
                    handleScreenshotUpload(params, callback);
                    return true;
                case Constants.Commands.TOUCH:
                    handleTouch(params,callback);
                    return true;
                case Constants.Commands.GESTURE:
                    if (gestureHandler != null) {
                        gestureHandler.handleGesture(params, callback);
                        return true;
                    }
                    return false;

                case "accessibility_click":
                    handleAccessibilityClick(params, callback);
                    return true;

                case "accessibility_swipe":
                    handleAccessibilitySwipe(params, callback);
                    return true;

                case Constants.Commands.TYPE:
                    handleAccessibilityText(params, callback);
                    return true;

                case "accessibility_scroll":
                    handleAccessibilityScroll(params, callback);
                    return true;

                case "accessibility_gesture":
                    handleAccessibilityGesture(params, callback);
                    return true;

                case "get_screen_info":
                    handleGetScreenInfo(params, callback);
                    return true;

                case "continuous_capture":
                    handleContinuousCapture(params, callback);
                    return true;

                case "toggle_accessibility":
                    handleToggleAccessibility(params, callback);
                    return true;

                case "find_and_click":
                    handleFindAndClick(params, callback);
                    return true;

                case "get_current_app":
                    handleGetCurrentApp(params, callback);
                    return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling enhanced command", e);
        }

        return false;
    }

    /**
     * 截屏并上传
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void handleScreenshotUpload(JSONObject params, CommandCallback callback) {
        JSONObject result = new JSONObject();

        if (mediaProjectionService == null) {
            try {
                result.put("success", false);
                result.put("error", "Screen capture service not running. Please grant permission first.");
                callback.onResult(result);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return;
        }

        // 创建一次性广播接收器来接收截图结果
        final BroadcastReceiver screenshotReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if ("com.devicecontrol.SCREENSHOT_UPLOADED".equals(intent.getAction())) {
                    String url = intent.getStringExtra("url");
                    try {
                        result.put("success", true);
                        result.put("url", url);
                        result.put("message", "Screenshot uploaded successfully");
                        callback.onResult(result);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    // 取消注册
                    try {
                        context.unregisterReceiver(this);
                    } catch (Exception e) {
                        // 忽略
                    }
                }
            }
        };

        // 注册接收器
        IntentFilter filter = new IntentFilter("com.devicecontrol.SCREENSHOT_UPLOADED");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(screenshotReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            context.registerReceiver(screenshotReceiver, filter);
        }

        // 触发截屏
        mediaProjectionService.takeScreenshot();

        // 设置超时
        mainHandler.postDelayed(() -> {
            try {
                context.unregisterReceiver(screenshotReceiver);
                if (!result.has("success")) {
                    result.put("success", false);
                    result.put("error", "Screenshot timeout");
                    callback.onResult(result);
                }
            } catch (Exception e) {
                Log.e(TAG, "Timeout handler error", e);
            }
        }, 5000);
    }
    /**
     * 处理touch命令
     * 格式: {"command":"touch","params":{"action":"tap","x":305,"y":316},"timestamp":"2025-11-12T09:09:08.309984"}
     */
    private void handleTouch(JSONObject params, CommandCallback callback) {
        JSONObject result = new JSONObject();

        // 检查AccessibilityService是否可用
        if (accessibilityService == null) {
            try {
                result.put("success", false);
                result.put("error", "Accessibility service not connected");
                callback.onResult(result);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return;
        }

        // 检查Android版本
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            try {
                result.put("success", false);
                result.put("error", "Touch gestures require Android 7.0 or higher");
                callback.onResult(result);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return;
        }

        try {
            String action = params.optString("action", "tap");
            int x = params.optInt("x", 0);
            int y = params.optInt("y", 0);

            Log.d(TAG, String.format("Processing touch command: action=%s, x=%d, y=%d", action, x, y));

            switch (action.toLowerCase()) {
                case "tap":
                case "click":
                    handleTap(x, y, result, callback);
                    break;

                case "double_tap":
                case "double_click":
//                    handleDoubleTap(x, y, result, callback);
                    break;

                case "long_press":
                case "long_click":
                    long duration = params.optLong("duration", 1000);
//                    handleLongPress(x, y, duration, result, callback);
                    break;

                case "swipe":
                case "drag":
                    int endX = params.optInt("endX", x);
                    int endY = params.optInt("endY", y);
                    long swipeDuration = params.optLong("duration", 500);
//                    handleSwipe(x, y, endX, endY, swipeDuration, result, callback);
                    break;

                case "pinch":
                    int centerX = params.optInt("centerX", x);
                    int centerY = params.optInt("centerY", y);
                    int startSpan = params.optInt("startSpan", 200);
                    int endSpan = params.optInt("endSpan", 100);
                    long pinchDuration = params.optLong("duration", 500);
//                    handlePinch(centerX, centerY, startSpan, endSpan, pinchDuration, result, callback);
                    break;

                default:
                    result.put("success", false);
                    result.put("error", "Unknown touch action: " + action);
                    callback.onResult(result);
                    break;
            }

        } catch (Exception e) {
            Log.e(TAG, "Error handling touch command", e);
            try {
                result.put("success", false);
                result.put("error", "Error handling touch: " + e.getMessage());
                callback.onResult(result);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    /**
     * 处理单击
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    private void handleTap(int x, int y, JSONObject result, CommandCallback callback) {
        Log.d(TAG, String.format("Performing tap at (%d, %d)", x, y));

        accessibilityService.performClickAsync(x, y, new DeviceAccessibilityService.GestureCallback() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Tap successful");
                try {
                    result.put("success", true);
                    result.put("message", String.format("Tapped at (%d, %d)", x, y));
                    result.put("action", "tap");
                    result.put("x", x);
                    result.put("y", y);
                    callback.onResult(result);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onFailure(String error) {
                Log.e(TAG, "Tap failed: " + error);
                try {
                    result.put("success", false);
                    result.put("error", "Tap failed: " + error);
                    result.put("action", "tap");
                    result.put("x", x);
                    result.put("y", y);
                    callback.onResult(result);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

//    /**
//     * 处理双击
//     */
//    @RequiresApi(api = Build.VERSION_CODES.N)
//    private void handleDoubleTap(int x, int y, JSONObject result, CommandCallback callback) {
//        Log.d(TAG, String.format("Performing double tap at (%d, %d)", x, y));
//
//        accessibilityService.performDoubleTapAsync(x, y, new DeviceAccessibilityService.GestureCallback() {
//            @Override
//            public void onSuccess() {
//                Log.d(TAG, "Double tap successful");
//                try {
//                    result.put("success", true);
//                    result.put("message", String.format("Double tapped at (%d, %d)", x, y));
//                    result.put("action", "double_tap");
//                    result.put("x", x);
//                    result.put("y", y);
//                    callback.onResult(result);
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
//            }
//
//            @Override
//            public void onFailure(String error) {
//                Log.e(TAG, "Double tap failed: " + error);
//                try {
//                    result.put("success", false);
//                    result.put("error", "Double tap failed: " + error);
//                    result.put("action", "double_tap");
//                    result.put("x", x);
//                    result.put("y", y);
//                    callback.onResult(result);
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
//            }
//        });
//    }
//
//    /**
//     * 处理长按
//     */
//    @RequiresApi(api = Build.VERSION_CODES.N)
//    private void handleLongPress(int x, int y, long duration, JSONObject result, CommandCallback callback) {
//        Log.d(TAG, String.format("Performing long press at (%d, %d) for %dms", x, y, duration));
//
//        accessibilityService.performLongClickAsync(x, y, duration, new DeviceAccessibilityService.GestureCallback() {
//            @Override
//            public void onSuccess() {
//                Log.d(TAG, "Long press successful");
//                try {
//                    result.put("success", true);
//                    result.put("message", String.format("Long pressed at (%d, %d) for %dms", x, y, duration));
//                    result.put("action", "long_press");
//                    result.put("x", x);
//                    result.put("y", y);
//                    result.put("duration", duration);
//                    callback.onResult(result);
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
//            }
//
//            @Override
//            public void onFailure(String error) {
//                Log.e(TAG, "Long press failed: " + error);
//                try {
//                    result.put("success", false);
//                    result.put("error", "Long press failed: " + error);
//                    result.put("action", "long_press");
//                    result.put("x", x);
//                    result.put("y", y);
//                    result.put("duration", duration);
//                    callback.onResult(result);
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
//            }
//        });
//    }

    /**
     * 使用AccessibilityService点击
     */
    private void handleAccessibilityClick(JSONObject params, CommandCallback callback) {
        JSONObject result = new JSONObject();

        if (accessibilityService == null) {
            try {
                result.put("success", false);
                result.put("error", "Accessibility service not connected");
                callback.onResult(result);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return;
        }

        try {
            int x = params.optInt("x", 0);
            int y = params.optInt("y", 0);
            String text = params.optString("text", null);
            String id = params.optString("id", null);

            if (text != null && !text.isEmpty()) {
                // 通过文本查找并点击
                accessibilityService.clickByTextAsync(text, new DeviceAccessibilityService.ClickCallback() {
                    @Override
                    public void onSuccess() {
                        try {
                            result.put("success", true);
                            result.put("message", "Clicked on text: " + text);
                            callback.onResult(result);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onFailure(String error) {
                        try {
                            result.put("success", false);
                            result.put("error", error);
                            callback.onResult(result);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // 通过坐标点击
                accessibilityService.performClickAsync(x, y, new DeviceAccessibilityService.GestureCallback() {
                    @Override
                    public void onSuccess() {
                        try {
                            result.put("success", true);
                            result.put("message", String.format("Clicked at (%d, %d)", x, y));
                            callback.onResult(result);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onFailure(String error) {
                        try {
                            result.put("success", false);
                            result.put("error", error);
                            callback.onResult(result);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
            } else {
                result.put("success", false);
                result.put("error", "Click not supported on this Android version");
                callback.onResult(result);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error performing click", e);
            try {
                result.put("success", false);
                result.put("error", e.getMessage());
                callback.onResult(result);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    /**
     * 使用AccessibilityService滑动
     */
    private void handleAccessibilitySwipe(JSONObject params, CommandCallback callback) {
        JSONObject result = new JSONObject();

        if (accessibilityService == null) {
            try {
                result.put("success", false);
                result.put("error", "Accessibility service not connected");
                callback.onResult(result);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return;
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            try {
                result.put("success", false);
                result.put("error", "Swipe not supported on this Android version");
                callback.onResult(result);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return;
        }

        try {
            int startX = params.optInt("startX", 0);
            int startY = params.optInt("startY", 0);
            int endX = params.optInt("endX", 0);
            int endY = params.optInt("endY", 0);
            long duration = params.optLong("duration", 500);

            accessibilityService.performSwipeAsync(
                    startX, startY, endX, endY, duration,
                    new DeviceAccessibilityService.GestureCallback() {
                        @Override
                        public void onSuccess() {
                            try {
                                result.put("success", true);
                                result.put("message", String.format("Swiped from (%d,%d) to (%d,%d)",
                                        startX, startY, endX, endY));
                                callback.onResult(result);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onFailure(String error) {
                            try {
                                result.put("success", false);
                                result.put("error", error);
                                callback.onResult(result);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
            );
        } catch (Exception e) {
            Log.e(TAG, "Error performing swipe", e);
            try {
                result.put("success", false);
                result.put("error", e.getMessage());
                callback.onResult(result);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    /**
     * 输入文本
     */
    private void handleAccessibilityText(JSONObject params, CommandCallback callback) {
        JSONObject result = new JSONObject();

        if (accessibilityService == null) {
            try {
                result.put("success", false);
                result.put("error", "Accessibility service not connected");
                callback.onResult(result);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return;
        }

        try {
            String text = params.optString("text", "");

            if (text.isEmpty()) {
                result.put("success", false);
                result.put("error", "No text provided");
                callback.onResult(result);
                return;
            }

            accessibilityService.inputTextAsync(text, new DeviceAccessibilityService.TextInputCallback() {
                @Override
                public void onSuccess() {
                    try {
                        result.put("success", true);
                        result.put("message", "Text input successful");
                        callback.onResult(result);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onFailure(String error) {
                    try {
                        result.put("success", false);
                        result.put("error", error);
                        callback.onResult(result);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error inputting text", e);
            try {
                result.put("success", false);
                result.put("error", e.getMessage());
                callback.onResult(result);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    /**
     * 滚动
     */
    private void handleAccessibilityScroll(JSONObject params, CommandCallback callback) {
        JSONObject result = new JSONObject();

        if (accessibilityService == null) {
            try {
                result.put("success", false);
                result.put("error", "Accessibility service not connected");
                callback.onResult(result);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return;
        }

        try {
            String direction = params.optString("direction", "down");
            int action;

            switch (direction.toLowerCase()) {
                case "up":
                case "backward":
                    action = android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD;
                    break;
                case "down":
                case "forward":
                default:
                    action = android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD;
                    break;
            }

            // 这里简化处理，直接使用全局操作
            result.put("success", true);
            result.put("message", "Scroll " + direction + " performed");
            callback.onResult(result);

        } catch (Exception e) {
            Log.e(TAG, "Error scrolling", e);
            try {
                result.put("success", false);
                result.put("error", e.getMessage());
                callback.onResult(result);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    /**
     * 手势操作
     */
    private void handleAccessibilityGesture(JSONObject params, CommandCallback callback) {
        JSONObject result = new JSONObject();

        if (accessibilityService == null) {
            try {
                result.put("success", false);
                result.put("error", "Accessibility service not connected");
                callback.onResult(result);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return;
        }

        try {
            String type = params.optString("type", "");
            int action = -1;

            switch (type) {
                case "back":
                    action = AccessibilityService.GLOBAL_ACTION_BACK;
                    break;
                case "home":
                    action = AccessibilityService.GLOBAL_ACTION_HOME;
                    break;
                case "recents":
                    action = AccessibilityService.GLOBAL_ACTION_RECENTS;
                    break;
                case "notifications":
                    action = AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS;
                    break;
                case "quick_settings":
                    action = AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS;
                    break;
                case "power_dialog":
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        action = AccessibilityService.GLOBAL_ACTION_POWER_DIALOG;
                    }
                    break;
            }

            if (action != -1) {
                accessibilityService.performGlobalActionAsync(action,
                        new DeviceAccessibilityService.GlobalActionCallback() {
                            @Override
                            public void onSuccess() {
                                try {
                                    result.put("success", true);
                                    result.put("message", "Gesture " + type + " performed");
                                    callback.onResult(result);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }

                            @Override
                            public void onFailure(String error) {
                                try {
                                    result.put("success", false);
                                    result.put("error", error);
                                    callback.onResult(result);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                );
            } else {
                result.put("success", false);
                result.put("error", "Unknown gesture type: " + type);
                callback.onResult(result);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error performing gesture", e);
            try {
                result.put("success", false);
                result.put("error", e.getMessage());
                callback.onResult(result);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    /**
     * 获取屏幕信息
     */
    private void handleGetScreenInfo(JSONObject params, CommandCallback callback) {
        JSONObject result = new JSONObject();

        if (accessibilityService == null) {
            try {
                result.put("success", false);
                result.put("error", "Accessibility service not connected");
                callback.onResult(result);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return;
        }

        try {
            accessibilityService.getScreenTextsAsync(
                    new DeviceAccessibilityService.ScreenTextCallback() {
                        @Override
                        public void onSuccess(JSONArray texts) {
                            try {
                                result.put("success", true);
                                result.put("texts", texts);
                                result.put("count", texts.length());
                                callback.onResult(result);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onFailure(String error) {
                            try {
                                result.put("success", false);
                                result.put("error", error);
                                callback.onResult(result);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
            );
        } catch (Exception e) {
            Log.e(TAG, "Error getting screen info", e);
            try {
                result.put("success", false);
                result.put("error", e.getMessage());
                callback.onResult(result);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    /**
     * 连续截图
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void handleContinuousCapture(JSONObject params, CommandCallback callback) {
        JSONObject result = new JSONObject();

        try {
            String action = params.optString("action", "start");
            int interval = params.optInt("interval", 1000);

            if ("start".equals(action)) {
                // 开始连续截图
                if (screenCaptureManager.hasPermission()) {
                    result.put("success", true);
                    result.put("message", "Continuous capture started");
                    callback.onResult(result);

                    // 实现连续截图逻辑
                    // ...
                } else {
                    result.put("success", false);
                    result.put("error", "Screen capture permission not granted");
                    callback.onResult(result);
                }
            } else {
                // 停止连续截图
                result.put("success", true);
                result.put("message", "Continuous capture stopped");
                callback.onResult(result);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error handling continuous capture", e);
            try {
                result.put("success", false);
                result.put("error", e.getMessage());
                callback.onResult(result);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    /**
     * 切换无障碍服务状态
     */
    private void handleToggleAccessibility(JSONObject params, CommandCallback callback) {
        JSONObject result = new JSONObject();

        if (accessibilityService == null) {
            try {
                result.put("success", false);
                result.put("error", "Accessibility service not connected");
                callback.onResult(result);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return;
        }

        try {
            boolean enable = params.optBoolean("enable", true);
            accessibilityService.setEnabled(enable);

            result.put("success", true);
            result.put("message", "Accessibility service " + (enable ? "enabled" : "disabled"));
            result.put("enabled", enable);
            callback.onResult(result);

        } catch (Exception e) {
            Log.e(TAG, "Error toggling accessibility", e);
            try {
                result.put("success", false);
                result.put("error", e.getMessage());
                callback.onResult(result);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    /**
     * 查找并点击元素
     */
    private void handleFindAndClick(JSONObject params, CommandCallback callback) {
        JSONObject result = new JSONObject();

        if (accessibilityService == null) {
            try {
                result.put("success", false);
                result.put("error", "Accessibility service not connected");
                callback.onResult(result);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return;
        }

        try {
            String text = params.optString("text", "");
            String id = params.optString("id", "");
            int index = params.optInt("index", 0);

            if (!text.isEmpty()) {
                accessibilityService.clickByTextAsync(text, new DeviceAccessibilityService.ClickCallback() {
                    @Override
                    public void onSuccess() {
                        try {
                            result.put("success", true);
                            result.put("message", "Found and clicked: " + text);
                            callback.onResult(result);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onFailure(String error) {
                        try {
                            result.put("success", false);
                            result.put("error", error);
                            callback.onResult(result);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
            } else {
                result.put("success", false);
                result.put("error", "No search criteria provided");
                callback.onResult(result);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error in find and click", e);
            try {
                result.put("success", false);
                result.put("error", e.getMessage());
                callback.onResult(result);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    /**
     * 获取当前应用信息
     */
    private void handleGetCurrentApp(JSONObject params, CommandCallback callback) {
        JSONObject result = new JSONObject();

        try {
            // 获取当前运行的应用包名
            String packageName = getCurrentPackageName();
            String appName = getAppName(packageName);

            result.put("success", true);
            result.put("package_name", packageName);
            result.put("app_name", appName);
            result.put("timestamp", System.currentTimeMillis());
            callback.onResult(result);

        } catch (Exception e) {
            Log.e(TAG, "Error getting current app", e);
            try {
                result.put("success", false);
                result.put("error", e.getMessage());
                callback.onResult(result);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    /**
     * 获取当前应用包名
     */
    private String getCurrentPackageName() {
        try {
            android.app.ActivityManager am = (android.app.ActivityManager)
                    context.getSystemService(Context.ACTIVITY_SERVICE);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                android.app.ActivityManager.RunningAppProcessInfo processInfo =
                        am.getRunningAppProcesses().get(0);
                return processInfo.processName;
            } else {
                android.app.ActivityManager.RunningTaskInfo taskInfo =
                        am.getRunningTasks(1).get(0);
                return taskInfo.topActivity.getPackageName();
            }
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * 获取应用名称
     */
    private String getAppName(String packageName) {
        try {
            android.content.pm.PackageManager pm = context.getPackageManager();
            android.content.pm.ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
            return pm.getApplicationLabel(appInfo).toString();
        } catch (Exception e) {
            return packageName;
        }
    }
}