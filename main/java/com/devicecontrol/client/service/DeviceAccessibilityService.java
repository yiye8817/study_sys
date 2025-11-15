package com.devicecontrol.client.service;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.RequiresApi;

import com.devicecontrol.client.utils.GlobalEventBus;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DeviceAccessibilityService extends AccessibilityService {
    private static final String TAG = "DeviceAccessibility";
    private static DeviceAccessibilityService instance;

    private Handler mainHandler;
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;
    private ExecutorService executorService;

    // 控制标志
    private boolean isProcessingCommand = false;
    private boolean isEnabled = true;
    private long lastEventTime = 0;
    private static final long EVENT_THROTTLE_MS = 50; // 事件节流

    public static DeviceAccessibilityService getInstance() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        // 创建处理器
        mainHandler = new Handler(Looper.getMainLooper());

        // 创建后台线程处理耗时操作
        backgroundThread = new HandlerThread("AccessibilityBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());

        // 创建线程池
        executorService = Executors.newSingleThreadExecutor();

        Log.d(TAG, "Accessibility Service created");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 如果服务被禁用或正在处理命令，忽略事件
        if (!isEnabled || isProcessingCommand) {
            return;
        }

        // 事件节流
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastEventTime < EVENT_THROTTLE_MS) {
            return;
        }
        lastEventTime = currentTime;

        // 只处理必要的事件
        int eventType = event.getEventType();
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
                eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {

            // 在后台线程处理事件
            backgroundHandler.post(() -> processEvent(event));
        }
    }

    private void processEvent(AccessibilityEvent event) {
        // 这里可以记录事件日志或做简单处理
        // 避免复杂操作
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "Accessibility Service interrupted");
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.d(TAG, "Accessibility Service connected");

        // 配置服务
        configureService();
        // 使用EventBus发送事件
        GlobalEventBus.getInstance().post("accessibility_connected", true);

        // 通知连接成功
        Intent intent = new Intent("com.devicecontrol.ACCESSIBILITY_CONNECTED");
        intent.setPackage(getPackageName());
        intent.putExtra("connected", true);
        sendBroadcast(intent);
        Log.d(TAG,"onServiceConnected:com.devicecontrol.ACCESSIBILITY_CONNECTED");
    }

    private void configureService() {
        // 动态配置服务，减少不必要的监听
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                // 可以在这里动态调整配置
            }
        } catch (Exception e) {
            Log.e(TAG, "Error configuring service", e);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;

        // 清理资源
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            backgroundThread = null;
        }

        if (executorService != null) {
            executorService.shutdown();
            executorService = null;
        }

        Log.d(TAG, "Accessibility Service destroyed");
    }

    /**
     * 设置是否启用服务
     */
    public void setEnabled(boolean enabled) {
        this.isEnabled = enabled;
        Log.d(TAG, "Service enabled: " + enabled);
    }

    /**
     * 执行点击操作（优化版）
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    public void performClickAsync(int x, int y, GestureCallback callback) {
        if (!isEnabled) {
            if (callback != null) callback.onFailure("Service disabled");
            return;
        }

        isProcessingCommand = true;

        backgroundHandler.post(() -> {
            try {
                Path path = new Path();
                path.moveTo(x, y);

                GestureDescription.Builder builder = new GestureDescription.Builder();
                builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 100));

                mainHandler.post(() -> {
                    dispatchGesture(builder.build(), new GestureResultCallback() {
                        @Override
                        public void onCompleted(GestureDescription gestureDescription) {
                            super.onCompleted(gestureDescription);
                            isProcessingCommand = false;
                            if (callback != null) {
                                callback.onSuccess();
                            }
                            Log.d(TAG, "Click completed at: " + x + ", " + y);
                        }

                        @Override
                        public void onCancelled(GestureDescription gestureDescription) {
                            super.onCancelled(gestureDescription);
                            isProcessingCommand = false;
                            if (callback != null) {
                                callback.onFailure("Gesture cancelled");
                            }
                            Log.e(TAG, "Click cancelled");
                        }
                    }, null);
                });
            } catch (Exception e) {
                isProcessingCommand = false;
                if (callback != null) {
                    callback.onFailure(e.getMessage());
                }
                Log.e(TAG, "Error performing click", e);
            }
        });
    }

    /**
     * 执行滑动操作（优化版）
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    public void performSwipeAsync(int startX, int startY, int endX, int endY,
                                  long duration, GestureCallback callback) {
        if (!isEnabled) {
            if (callback != null) callback.onFailure("Service disabled");
            return;
        }

        isProcessingCommand = true;

        backgroundHandler.post(() -> {
            try {
                Path path = new Path();
                path.moveTo(startX, startY);
                path.lineTo(endX, endY);

                GestureDescription.Builder builder = new GestureDescription.Builder();
                builder.addStroke(new GestureDescription.StrokeDescription(path, 0, duration));

                mainHandler.post(() -> {
                    dispatchGesture(builder.build(), new GestureResultCallback() {
                        @Override
                        public void onCompleted(GestureDescription gestureDescription) {
                            super.onCompleted(gestureDescription);
                            isProcessingCommand = false;
                            if (callback != null) {
                                callback.onSuccess();
                            }
                            Log.d(TAG, "Swipe completed");
                        }

                        @Override
                        public void onCancelled(GestureDescription gestureDescription) {
                            super.onCancelled(gestureDescription);
                            isProcessingCommand = false;
                            if (callback != null) {
                                callback.onFailure("Gesture cancelled");
                            }
                        }
                    }, null);
                });
            } catch (Exception e) {
                isProcessingCommand = false;
                if (callback != null) {
                    callback.onFailure(e.getMessage());
                }
                Log.e(TAG, "Error performing swipe", e);
            }
        });
    }

    /**
     * 输入文本（优化版）
     */
    public void inputTextAsync(String text, TextInputCallback callback) {
        if (!isEnabled) {
            if (callback != null) callback.onFailure("Service disabled");
            return;
        }

        executorService.execute(() -> {
            try {
                AccessibilityNodeInfo rootNode = getRootInActiveWindow();
                if (rootNode != null) {
                    // 查找焦点节点
                    AccessibilityNodeInfo focusNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);

                    if (focusNode != null && focusNode.isEditable()) {
                        Bundle arguments = new Bundle();
                        arguments.putCharSequence(
                                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                                text
                        );
                        boolean result = focusNode.performAction(
                                AccessibilityNodeInfo.ACTION_SET_TEXT,
                                arguments
                        );

                        if (callback != null) {
                            if (result) {
                                callback.onSuccess();
                            } else {
                                callback.onFailure("Failed to set text");
                            }
                        }
                        return;
                    }
                }

                if (callback != null) {
                    callback.onFailure("No editable field found");
                }
            } catch (Exception e) {
                if (callback != null) {
                    callback.onFailure(e.getMessage());
                }
                Log.e(TAG, "Error inputting text", e);
            }
        });
    }

    /**
     * 执行全局操作（优化版）
     */
    public void performGlobalActionAsync(int action, GlobalActionCallback callback) {
        if (!isEnabled) {
            if (callback != null) callback.onFailure("Service disabled");
            return;
        }

        mainHandler.post(() -> {
            boolean result = performGlobalAction(action);
            if (callback != null) {
                if (result) {
                    callback.onSuccess();
                } else {
                    callback.onFailure("Action failed");
                }
            }
        });
    }

    /**
     * 查找并点击文本（优化版）
     */
    public void clickByTextAsync(String text, ClickCallback callback) {
        if (!isEnabled) {
            if (callback != null) callback.onFailure("Service disabled");
            return;
        }

        executorService.execute(() -> {
            try {
                AccessibilityNodeInfo rootNode = getRootInActiveWindow();
                if (rootNode != null) {
                    List<AccessibilityNodeInfo> nodes = rootNode.findAccessibilityNodeInfosByText(text);

                    for (AccessibilityNodeInfo node : nodes) {
                        AccessibilityNodeInfo clickableNode = findClickableParent(node);
                        if (clickableNode != null) {
                            boolean result = clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                            if (result) {
                                if (callback != null) callback.onSuccess();
                                return;
                            }
                        }
                    }
                }

                if (callback != null) {
                    callback.onFailure("Text not found or not clickable");
                }
            } catch (Exception e) {
                if (callback != null) {
                    callback.onFailure(e.getMessage());
                }
                Log.e(TAG, "Error clicking by text", e);
            }
        });
    }

    /**
     * 查找可点击的父节点
     */
    private AccessibilityNodeInfo findClickableParent(AccessibilityNodeInfo node) {
        if (node == null) return null;

        if (node.isClickable()) {
            return node;
        }

        AccessibilityNodeInfo parent = node.getParent();
        int maxDepth = 5; // 限制搜索深度
        int depth = 0;

        while (parent != null && depth < maxDepth) {
            if (parent.isClickable()) {
                return parent;
            }
            parent = parent.getParent();
            depth++;
        }

        return null;
    }

    /**
     * 获取屏幕文本（限制数量）
     */
    public void getScreenTextsAsync(ScreenTextCallback callback) {
        if (!isEnabled) {
            if (callback != null) callback.onFailure("Service disabled");
            return;
        }

        executorService.execute(() -> {
            try {
                JSONArray texts = new JSONArray();
                AccessibilityNodeInfo rootNode = getRootInActiveWindow();

                if (rootNode != null) {
                    extractTextsLimited(rootNode, texts, 50); // 限制最多50个文本
                }

                if (callback != null) {
                    callback.onSuccess(texts);
                }
            } catch (Exception e) {
                if (callback != null) {
                    callback.onFailure(e.getMessage());
                }
                Log.e(TAG, "Error getting screen texts", e);
            }
        });
    }

    private void extractTextsLimited(AccessibilityNodeInfo node, JSONArray texts, int maxCount) {
        if (node == null || texts.length() >= maxCount) return;

        try {
            CharSequence text = node.getText();
            if (text != null && text.length() > 0) {
                JSONObject nodeInfo = new JSONObject();
                nodeInfo.put("text", text.toString());

                Rect rect = new Rect();
                node.getBoundsInScreen(rect);
                nodeInfo.put("x", rect.centerX());
                nodeInfo.put("y", rect.centerY());
                nodeInfo.put("clickable", node.isClickable());

                texts.put(nodeInfo);
            }

            // 递归处理子节点
            for (int i = 0; i < node.getChildCount() && texts.length() < maxCount; i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) {
                    extractTextsLimited(child, texts, maxCount);
                    child.recycle();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error extracting text from node", e);
        }
    }

    // 回调接口
    public interface GestureCallback {
        void onSuccess();
        void onFailure(String error);
    }

    public interface TextInputCallback {
        void onSuccess();
        void onFailure(String error);
    }

    public interface GlobalActionCallback {
        void onSuccess();
        void onFailure(String error);
    }

    public interface ClickCallback {
        void onSuccess();
        void onFailure(String error);
    }

    public interface ScreenTextCallback {
        void onSuccess(JSONArray texts);
        void onFailure(String error);
    }
}