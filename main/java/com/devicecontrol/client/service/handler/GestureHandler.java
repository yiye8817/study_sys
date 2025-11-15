package com.devicecontrol.client.service.handler;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.graphics.Path;
import android.graphics.Point;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import androidx.annotation.RequiresApi;

import com.devicecontrol.client.service.handler.CommandHandler;
import com.devicecontrol.client.service.DeviceAccessibilityService;

import org.json.JSONObject;

/**
 * 手势处理器 - 改进版
 */
public class GestureHandler {
    private static final String TAG = "GestureHandler";

    private DeviceAccessibilityService accessibilityService;
    private Context context;
    private int screenWidth;
    private int screenHeight;

    // 手势类型常量
    public static class GestureType {
        public static final String SWIPE_UP = "swipe_up";
        public static final String SWIPE_DOWN = "swipe_down";
        public static final String SWIPE_LEFT = "swipe_left";
        public static final String SWIPE_RIGHT = "swipe_right";
        public static final String PINCH_IN = "pinch_in";
        public static final String PINCH_OUT = "pinch_out";
        public static final String CUSTOM = "custom";
    }

    // 调整后的默认参数
    private static final int DEFAULT_DURATION = 500; // 增加到500ms
    private static final float SWIPE_UP_START_RATIO = 0.75f; // 从75%开始
    private static final float SWIPE_UP_END_RATIO = 0.25f; // 到25%结束
    private static final float SWIPE_DOWN_START_RATIO = 0.25f;
    private static final float SWIPE_DOWN_END_RATIO = 0.75f;
    private static final float SWIPE_LEFT_START_RATIO = 0.75f;
    private static final float SWIPE_LEFT_END_RATIO = 0.25f;
    private static final float SWIPE_RIGHT_START_RATIO = 0.25f;
    private static final float SWIPE_RIGHT_END_RATIO = 0.75f;
    private static final float CENTER_RATIO = 0.5f;

    /**
     * 构造函数
     */
    public GestureHandler(Context context, DeviceAccessibilityService accessibilityService) {
        this.context = context;
        this.accessibilityService = accessibilityService;
        initScreenSize();
    }

    /**
     * 设置AccessibilityService实例
     */
    public void setAccessibilityService(DeviceAccessibilityService service) {
        this.accessibilityService = service;
        // 重新初始化屏幕尺寸
        initScreenSize();
    }

    /**
     * 初始化屏幕尺寸
     */
    private void initScreenSize() {
        try {
            WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            if (wm != null) {
                Display display = wm.getDefaultDisplay();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    Point size = new Point();
                    display.getRealSize(size);
                    screenWidth = size.x;
                    screenHeight = size.y;
                } else {
                    DisplayMetrics metrics = new DisplayMetrics();
                    display.getMetrics(metrics);
                    screenWidth = metrics.widthPixels;
                    screenHeight = metrics.heightPixels;
                }
                Log.d(TAG, String.format("Screen size initialized: %dx%d", screenWidth, screenHeight));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting screen size", e);
            // 使用默认值
            DisplayMetrics metrics = context.getResources().getDisplayMetrics();
            screenWidth = metrics.widthPixels;
            screenHeight = metrics.heightPixels;
            Log.d(TAG, String.format("Using fallback screen size: %dx%d", screenWidth, screenHeight));
        }
    }

    /**
     * 处理手势命令
     */
    public void handleGesture(JSONObject params, CommandHandler.CommandCallback callback) {
        JSONObject result = new JSONObject();

        if (accessibilityService == null) {
            sendErrorResponse(result, "Accessibility service not connected", callback);
            return;
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            sendErrorResponse(result, "Gestures require Android 7.0 or higher", callback);
            return;
        }

        // 确保屏幕尺寸已初始化
        if (screenWidth == 0 || screenHeight == 0) {
            initScreenSize();
            if (screenWidth == 0 || screenHeight == 0) {
                sendErrorResponse(result, "Failed to get screen dimensions", callback);
                return;
            }
        }

        try {
            String type = params.optString("type", "").toLowerCase();
            int duration = params.optInt("duration", DEFAULT_DURATION);

            Log.d(TAG, String.format("Processing gesture: type=%s, duration=%d, screen=%dx%d",
                    type, duration, screenWidth, screenHeight));

            switch (type) {
                case GestureType.SWIPE_UP:
                    handleSwipeUp(duration, params, result, callback);
                    break;

                case GestureType.SWIPE_DOWN:
                    handleSwipeDown(duration, params, result, callback);
                    break;

                case GestureType.SWIPE_LEFT:
                    handleSwipeLeft(duration, params, result, callback);
                    break;

                case GestureType.SWIPE_RIGHT:
                    handleSwipeRight(duration, params, result, callback);
                    break;

                case GestureType.PINCH_IN:
                    handlePinchIn(duration, params, result, callback);
                    break;

                case GestureType.PINCH_OUT:
                    handlePinchOut(duration, params, result, callback);
                    break;

                case GestureType.CUSTOM:
                    handleCustomGesture(params, result, callback);
                    break;

                default:
                    sendErrorResponse(result, "Unknown gesture type: " + type, callback);
                    break;
            }

        } catch (Exception e) {
            Log.e(TAG, "Error handling gesture command", e);
            sendErrorResponse(result, "Error handling gesture: " + e.getMessage(), callback);
        }
    }

    /**
     * 处理向上滑动手势
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    private void handleSwipeUp(int duration, JSONObject params, JSONObject result,
                               CommandHandler.CommandCallback callback) {
        try {
            // 获取自定义参数或使用默认值
            float startRatio = (float) params.optDouble("start_ratio", SWIPE_UP_START_RATIO);
            float endRatio = (float) params.optDouble("end_ratio", SWIPE_UP_END_RATIO);
            float horizontalRatio = (float) params.optDouble("horizontal_ratio", CENTER_RATIO);

            // 计算滑动坐标，确保在有效范围内
            int x = clamp((int) (screenWidth * horizontalRatio), 0, screenWidth - 1);
            int startY = clamp((int) (screenHeight * startRatio), 0, screenHeight - 1);
            int endY = clamp((int) (screenHeight * endRatio), 0, screenHeight - 1);

            // 确保滑动距离足够
            if (Math.abs(startY - endY) < 100) {
                Log.w(TAG, "Swipe distance too short, adjusting");
                startY = (int) (screenHeight * 0.8f);
                endY = (int) (screenHeight * 0.2f);
            }

            Log.d(TAG, String.format("Swipe up: from (%d,%d) to (%d,%d), duration=%d",
                    x, startY, x, endY, duration));

            // 创建滑动路径
            Path swipePath = new Path();
            swipePath.moveTo(x, startY);
            swipePath.lineTo(x, endY);

            // 执行手势
            performGesture(swipePath, duration, "Swipe Up", result, callback);

        } catch (Exception e) {
            Log.e(TAG, "Error performing swipe up", e);
            sendErrorResponse(result, "Error performing swipe up: " + e.getMessage(), callback);
        }
    }

    /**
     * 处理向下滑动手势
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    private void handleSwipeDown(int duration, JSONObject params, JSONObject result,
                                 CommandHandler.CommandCallback callback) {
        try {
            float startRatio = (float) params.optDouble("start_ratio", SWIPE_DOWN_START_RATIO);
            float endRatio = (float) params.optDouble("end_ratio", SWIPE_DOWN_END_RATIO);
            float horizontalRatio = (float) params.optDouble("horizontal_ratio", CENTER_RATIO);

            int x = clamp((int) (screenWidth * horizontalRatio), 0, screenWidth - 1);
            int startY = clamp((int) (screenHeight * startRatio), 0, screenHeight - 1);
            int endY = clamp((int) (screenHeight * endRatio), 0, screenHeight - 1);

            if (Math.abs(startY - endY) < 100) {
                Log.w(TAG, "Swipe distance too short, adjusting");
                startY = (int) (screenHeight * 0.2f);
                endY = (int) (screenHeight * 0.8f);
            }

            Log.d(TAG, String.format("Swipe down: from (%d,%d) to (%d,%d), duration=%d",
                    x, startY, x, endY, duration));

            Path swipePath = new Path();
            swipePath.moveTo(x, startY);
            swipePath.lineTo(x, endY);

            performGesture(swipePath, duration, "Swipe Down", result, callback);

        } catch (Exception e) {
            Log.e(TAG, "Error performing swipe down", e);
            sendErrorResponse(result, "Error performing swipe down: " + e.getMessage(), callback);
        }
    }

    /**
     * 处理向左滑动手势
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    private void handleSwipeLeft(int duration, JSONObject params, JSONObject result,
                                 CommandHandler.CommandCallback callback) {
        try {
            float startRatio = (float) params.optDouble("start_ratio", SWIPE_LEFT_START_RATIO);
            float endRatio = (float) params.optDouble("end_ratio", SWIPE_LEFT_END_RATIO);
            float verticalRatio = (float) params.optDouble("vertical_ratio", CENTER_RATIO);

            int startX = clamp((int) (screenWidth * startRatio), 0, screenWidth - 1);
            int endX = clamp((int) (screenWidth * endRatio), 0, screenWidth - 1);
            int y = clamp((int) (screenHeight * verticalRatio), 0, screenHeight - 1);

            if (Math.abs(startX - endX) < 100) {
                Log.w(TAG, "Swipe distance too short, adjusting");
                startX = (int) (screenWidth * 0.8f);
                endX = (int) (screenWidth * 0.2f);
            }

            Log.d(TAG, String.format("Swipe left: from (%d,%d) to (%d,%d), duration=%d",
                    startX, y, endX, y, duration));

            Path swipePath = new Path();
            swipePath.moveTo(startX, y);
            swipePath.lineTo(endX, y);

            performGesture(swipePath, duration, "Swipe Left", result, callback);

        } catch (Exception e) {
            Log.e(TAG, "Error performing swipe left", e);
            sendErrorResponse(result, "Error performing swipe left: " + e.getMessage(), callback);
        }
    }

    /**
     * 处理向右滑动手势
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    private void handleSwipeRight(int duration, JSONObject params, JSONObject result,
                                  CommandHandler.CommandCallback callback) {
        try {
            float startRatio = (float) params.optDouble("start_ratio", SWIPE_RIGHT_START_RATIO);
            float endRatio = (float) params.optDouble("end_ratio", SWIPE_RIGHT_END_RATIO);
            float verticalRatio = (float) params.optDouble("vertical_ratio", CENTER_RATIO);

            int startX = clamp((int) (screenWidth * startRatio), 0, screenWidth - 1);
            int endX = clamp((int) (screenWidth * endRatio), 0, screenWidth - 1);
            int y = clamp((int) (screenHeight * verticalRatio), 0, screenHeight - 1);

            if (Math.abs(startX - endX) < 100) {
                Log.w(TAG, "Swipe distance too short, adjusting");
                startX = (int) (screenWidth * 0.2f);
                endX = (int) (screenWidth * 0.8f);
            }

            Log.d(TAG, String.format("Swipe right: from (%d,%d) to (%d,%d), duration=%d",
                    startX, y, endX, y, duration));

            Path swipePath = new Path();
            swipePath.moveTo(startX, y);
            swipePath.lineTo(endX, y);

            performGesture(swipePath, duration, "Swipe Right", result, callback);

        } catch (Exception e) {
            Log.e(TAG, "Error performing swipe right", e);
            sendErrorResponse(result, "Error performing swipe right: " + e.getMessage(), callback);
        }
    }

    /**
     * 处理捏合手势（缩小）
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    private void handlePinchIn(int duration, JSONObject params, JSONObject result,
                               CommandHandler.CommandCallback callback) {
        try {
            int centerX = params.optInt("center_x", screenWidth / 2);
            int centerY = params.optInt("center_y", screenHeight / 2);
            int startSpan = params.optInt("start_span", Math.min(screenWidth, screenHeight) / 2);
            int endSpan = params.optInt("end_span", 100);

            Log.d(TAG, String.format("Pinch in: center=(%d,%d), span=%d->%d",
                    centerX, centerY, startSpan, endSpan));

            Path finger1 = new Path();
            Path finger2 = new Path();

            finger1.moveTo(centerX - startSpan / 2, centerY - startSpan / 2);
            finger1.lineTo(centerX - endSpan / 2, centerY - endSpan / 2);

            finger2.moveTo(centerX + startSpan / 2, centerY + startSpan / 2);
            finger2.lineTo(centerX + endSpan / 2, centerY + endSpan / 2);

            performMultiTouchGesture(new Path[]{finger1, finger2}, duration, "Pinch In", result, callback);

        } catch (Exception e) {
            Log.e(TAG, "Error performing pinch in", e);
            sendErrorResponse(result, "Error performing pinch in: " + e.getMessage(), callback);
        }
    }

    /**
     * 处理放大手势
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    private void handlePinchOut(int duration, JSONObject params, JSONObject result,
                                CommandHandler.CommandCallback callback) {
        try {
            int centerX = params.optInt("center_x", screenWidth / 2);
            int centerY = params.optInt("center_y", screenHeight / 2);
            int startSpan = params.optInt("start_span", 100);
            int endSpan = params.optInt("end_span", Math.min(screenWidth, screenHeight) / 2);

            Log.d(TAG, String.format("Pinch out: center=(%d,%d), span=%d->%d",
                    centerX, centerY, startSpan, endSpan));

            Path finger1 = new Path();
            Path finger2 = new Path();

            finger1.moveTo(centerX - startSpan / 2, centerY - startSpan / 2);
            finger1.lineTo(centerX - endSpan / 2, centerY - endSpan / 2);

            finger2.moveTo(centerX + startSpan / 2, centerY + startSpan / 2);
            finger2.lineTo(centerX + endSpan / 2, centerY + endSpan / 2);

            performMultiTouchGesture(new Path[]{finger1, finger2}, duration, "Pinch Out", result, callback);

        } catch (Exception e) {
            Log.e(TAG, "Error performing pinch out", e);
            sendErrorResponse(result, "Error performing pinch out: " + e.getMessage(), callback);
        }
    }

    /**
     * 处理自定义手势
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    private void handleCustomGesture(JSONObject params, JSONObject result,
                                     CommandHandler.CommandCallback callback) {
        try {
            if (!params.has("points")) {
                sendErrorResponse(result, "Custom gesture requires 'points' array", callback);
                return;
            }

            org.json.JSONArray pointsArray = params.getJSONArray("points");
            if (pointsArray.length() < 2) {
                sendErrorResponse(result, "Custom gesture requires at least 2 points", callback);
                return;
            }

            int duration = params.optInt("duration", DEFAULT_DURATION);

            Path gesturePath = new Path();
            org.json.JSONArray firstPoint = pointsArray.getJSONArray(0);
            int startX = clamp(firstPoint.getInt(0), 0, screenWidth - 1);
            int startY = clamp(firstPoint.getInt(1), 0, screenHeight - 1);
            gesturePath.moveTo(startX, startY);

            for (int i = 1; i < pointsArray.length(); i++) {
                org.json.JSONArray point = pointsArray.getJSONArray(i);
                int x = clamp(point.getInt(0), 0, screenWidth - 1);
                int y = clamp(point.getInt(1), 0, screenHeight - 1);
                gesturePath.lineTo(x, y);
            }

            Log.d(TAG, String.format("Custom gesture with %d points, duration=%d",
                    pointsArray.length(), duration));

            performGesture(gesturePath, duration, "Custom Gesture", result, callback);

        } catch (Exception e) {
            Log.e(TAG, "Error performing custom gesture", e);
            sendErrorResponse(result, "Error performing custom gesture: " + e.getMessage(), callback);
        }
    }

    /**
     * 执行单指手势
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    private void performGesture(Path path, long duration, String gestureName,
                                JSONObject result, CommandHandler.CommandCallback callback) {
        try {
            // 确保持续时间在有效范围内
            long validDuration = clamp(duration, 100, 60000);

            GestureDescription.StrokeDescription stroke =
                    new GestureDescription.StrokeDescription(path, 0, validDuration);

            GestureDescription.Builder builder = new GestureDescription.Builder();
            builder.addStroke(stroke);
            GestureDescription gesture = builder.build();

            Log.d(TAG, String.format("Dispatching %s gesture, duration=%d", gestureName, validDuration));

            boolean dispatched = accessibilityService.dispatchGesture(
                    gesture,
                    new AccessibilityService.GestureResultCallback() {
                        @Override
                        public void onCompleted(GestureDescription gestureDescription) {
                            super.onCompleted(gestureDescription);
                            Log.d(TAG, gestureName + " gesture completed successfully");
                            sendSuccessResponse(result, gestureName + " gesture completed",
                                    gestureName.toLowerCase().replace(" ", "_"), callback);
                        }

                        @Override
                        public void onCancelled(GestureDescription gestureDescription) {
                            super.onCancelled(gestureDescription);
                            Log.w(TAG, gestureName + " gesture was cancelled");
                            sendErrorResponse(result, gestureName + " gesture was cancelled", callback);
                        }
                    },
                    null
            );

            if (!dispatched) {
                Log.e(TAG, "Failed to dispatch " + gestureName + " gesture");
                sendErrorResponse(result, "Failed to dispatch " + gestureName + " gesture", callback);
            } else {
                Log.d(TAG, gestureName + " gesture dispatched successfully");
            }

        } catch (Exception e) {
            Log.e(TAG, "Error dispatching gesture", e);
            sendErrorResponse(result, "Error dispatching gesture: " + e.getMessage(), callback);
        }
    }

    /**
     * 执行多指手势
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    private void performMultiTouchGesture(Path[] paths, long duration, String gestureName,
                                          JSONObject result, CommandHandler.CommandCallback callback) {
        try {
            long validDuration = clamp(duration, 100, 60000);

            GestureDescription.Builder builder = new GestureDescription.Builder();

            for (Path path : paths) {
                GestureDescription.StrokeDescription stroke =
                        new GestureDescription.StrokeDescription(path, 0, validDuration);
                builder.addStroke(stroke);
            }

            GestureDescription gesture = builder.build();

            Log.d(TAG, String.format("Dispatching %s multi-touch gesture, %d fingers, duration=%d",
                    gestureName, paths.length, validDuration));

            boolean dispatched = accessibilityService.dispatchGesture(
                    gesture,
                    new AccessibilityService.GestureResultCallback() {
                        @Override
                        public void onCompleted(GestureDescription gestureDescription) {
                            super.onCompleted(gestureDescription);
                            Log.d(TAG, gestureName + " gesture completed successfully");
                            sendSuccessResponse(result, gestureName + " gesture completed",
                                    gestureName.toLowerCase().replace(" ", "_"), callback);
                        }

                        @Override
                        public void onCancelled(GestureDescription gestureDescription) {
                            super.onCancelled(gestureDescription);
                            Log.w(TAG, gestureName + " gesture was cancelled");
                            sendErrorResponse(result, gestureName + " gesture was cancelled", callback);
                        }
                    },
                    null
            );

            if (!dispatched) {
                Log.e(TAG, "Failed to dispatch " + gestureName + " gesture");
                sendErrorResponse(result, "Failed to dispatch " + gestureName + " gesture", callback);
            } else {
                Log.d(TAG, gestureName + " gesture dispatched successfully");
            }

        } catch (Exception e) {
            Log.e(TAG, "Error dispatching multi-touch gesture", e);
            sendErrorResponse(result, "Error dispatching gesture: " + e.getMessage(), callback);
        }
    }

    /**
     * 限制值在指定范围内
     */
    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * 限制值在指定范围内
     */
    private long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * 发送成功响应
     */
    private void sendSuccessResponse(JSONObject result, String message, String gestureType,
                                     CommandHandler.CommandCallback callback) {
        try {
            result.put("success", true);
            result.put("message", message);
            result.put("gesture_type", gestureType);
            result.put("screen_size", screenWidth + "x" + screenHeight);
            result.put("timestamp", System.currentTimeMillis());
            callback.onResult(result);
        } catch (Exception e) {
            Log.e(TAG, "Error sending success response", e);
        }
    }

    /**
     * 发送错误响应
     */
    private void sendErrorResponse(JSONObject result, String error,
                                   CommandHandler.CommandCallback callback) {
        try {
            result.put("success", false);
            result.put("error", error);
            result.put("screen_size", screenWidth + "x" + screenHeight);
            result.put("timestamp", System.currentTimeMillis());
            callback.onResult(result);
        } catch (Exception e) {
            Log.e(TAG, "Error sending error response", e);
        }
    }
}