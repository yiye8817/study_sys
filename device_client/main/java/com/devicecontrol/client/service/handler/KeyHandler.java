package com.devicecontrol.client.service.handler;

//package com.devicecontrol.client.handler;

import android.accessibilityservice.AccessibilityService;
import android.os.Build;
import android.util.Log;
import android.view.View;

import com.devicecontrol.client.service.handler.CommandHandler;
import com.devicecontrol.client.service.DeviceAccessibilityService;

import org.json.JSONObject;

/**
 * 按键处理器
 * 处理各种按键命令，包括：
 * - 系统键：Home、Back、Recent、Notifications等
 * - 导航键：上下左右方向键、Tab、Enter
 * - 编辑键：Delete、Backspace、End
 * - 翻页键：PageUp、PageDown
 * - 功能键：F1-F12、Esc
 */
public class KeyHandler {
    private static final String TAG = "KeyHandler";

    private DeviceAccessibilityService accessibilityService;

    // 按键类型常量
    public static class KeyType {
        public static final String HOME = "home";
        public static final String BACK = "back";
        public static final String BACKSPACE = "backspace";
        public static final String DELETE = "delete";
        public static final String DEL = "del";
        public static final String UP = "up";
        public static final String DOWN = "down";
        public static final String LEFT = "left";
        public static final String RIGHT = "right";
        public static final String ARROW_UP = "arrow_up";
        public static final String ARROW_DOWN = "arrow_down";
        public static final String ARROW_LEFT = "arrow_left";
        public static final String ARROW_RIGHT = "arrow_right";
        public static final String TAB = "tab";
        public static final String ENTER = "enter";
        public static final String RETURN = "return";
        public static final String ESC = "esc";
        public static final String ESCAPE = "escape";
        public static final String RECENTS = "recents";
        public static final String RECENT = "recent";
        public static final String NOTIFICATIONS = "notifications";
        public static final String NOTIFICATION = "notification";
        public static final String SETTINGS = "settings";
        public static final String QUICK_SETTINGS = "quick_settings";
        public static final String POWER = "power";
        public static final String END = "end";
        public static final String PAGE_UP = "pageup";
        public static final String PAGE_DOWN = "pagedown";
        public static final String PGUP = "pgup";
        public static final String PGDN = "pgdn";
    }

    /**
     * 构造函数
     */
    public KeyHandler(DeviceAccessibilityService accessibilityService) {
        this.accessibilityService = accessibilityService;
    }

    /**
     * 设置AccessibilityService实例
     */
    public void setAccessibilityService(DeviceAccessibilityService service) {
        this.accessibilityService = service;
    }

    /**
     * 处理按键命令
     * @param params 包含按键信息的JSON对象
     * @param callback 命令回调
     */
    public void handleKey(JSONObject params, CommandHandler.CommandCallback callback) {
        JSONObject result = new JSONObject();

        if (accessibilityService == null) {
            sendErrorResponse(result, "Accessibility service not connected", callback);
            return;
        }

        try {
            String key = params.optString("key", "").toLowerCase();
            boolean shiftPressed = params.optBoolean("shift", false);
            boolean ctrlPressed = params.optBoolean("ctrl", false);
            boolean altPressed = params.optBoolean("alt", false);

            Log.d(TAG, String.format("Processing key: %s, shift=%b, ctrl=%b, alt=%b",
                    key, shiftPressed, ctrlPressed, altPressed));

            // 处理组合键
            if (ctrlPressed || altPressed) {
                handleModifierKey(key, shiftPressed, ctrlPressed, altPressed, result, callback);
                return;
            }

            // 处理单个按键
            switch (key) {
                case KeyType.HOME:
                    handleHomeKey(result, callback);
                    break;

                case KeyType.BACK:
                case KeyType.BACKSPACE:
                    handleBackKey(params, result, callback);
                    break;

                case KeyType.DELETE:
                case KeyType.DEL:
                    handleDeleteKey(result, callback);
                    break;

                case KeyType.UP:
                case KeyType.ARROW_UP:
                    handleArrowKey(View.FOCUS_UP, "Up", result, callback);
                    break;

                case KeyType.DOWN:
                case KeyType.ARROW_DOWN:
                    handleArrowKey(View.FOCUS_DOWN, "Down", result, callback);
                    break;

                case KeyType.LEFT:
                case KeyType.ARROW_LEFT:
                    handleArrowKey(View.FOCUS_LEFT, "Left", result, callback);
                    break;

                case KeyType.RIGHT:
                case KeyType.ARROW_RIGHT:
                    handleArrowKey(View.FOCUS_RIGHT, "Right", result, callback);
                    break;

                case KeyType.TAB:
                    handleTabKey(shiftPressed, result, callback);
                    break;

                case KeyType.ENTER:
                case KeyType.RETURN:
                    handleEnterKey(result, callback);
                    break;

                case KeyType.ESC:
                case KeyType.ESCAPE:
                    handleEscapeKey(result, callback);
                    break;

                case KeyType.RECENTS:
                case KeyType.RECENT:
                    handleRecentsKey(result, callback);
                    break;

                case KeyType.NOTIFICATIONS:
                case KeyType.NOTIFICATION:
                    handleNotificationsKey(result, callback);
                    break;

                case KeyType.SETTINGS:
                case KeyType.QUICK_SETTINGS:
                    handleQuickSettingsKey(result, callback);
                    break;

                case KeyType.POWER:
                    handlePowerKey(result, callback);
                    break;

                case KeyType.END:
                    handleEndKey(result, callback);
                    break;

                case KeyType.PAGE_UP:
                case KeyType.PGUP:
                    handlePageKey(true, result, callback);
                    break;

                case KeyType.PAGE_DOWN:
                case KeyType.PGDN:
                    handlePageKey(false, result, callback);
                    break;

                default:
                    // 检查是否是功能键
                    if (key.startsWith("f") && key.length() <= 3) {
                        handleFunctionKey(key, result, callback);
                    } else {
                        sendErrorResponse(result, "Unknown key: " + key, callback);
                    }
                    break;
            }

        } catch (Exception e) {
            Log.e(TAG, "Error handling key command", e);
            sendErrorResponse(result, "Error handling key: " + e.getMessage(), callback);
        }
    }

    /**
     * 处理Home键
     */
    private void handleHomeKey(JSONObject result, CommandHandler.CommandCallback callback) {
        performGlobalAction(
                AccessibilityService.GLOBAL_ACTION_HOME,
                "Home",
                result,
                callback
        );
    }

    /**
     * 处理Back/Backspace键
     */
    private void handleBackKey(JSONObject params, JSONObject result, CommandHandler.CommandCallback callback) {
        boolean inTextField = params.optBoolean("in_text_field", false);

        if (inTextField) {
            // 在文本框中，删除字符
            deleteText(1, result, callback);
        } else {
            // 执行返回操作
            performGlobalAction(
                    AccessibilityService.GLOBAL_ACTION_BACK,
                    "Back",
                    result,
                    callback
            );
        }
    }

    /**
     * 处理Delete键
     */
    private void handleDeleteKey(JSONObject result, CommandHandler.CommandCallback callback) {
        deleteText(1, result, callback);
    }

    /**
     * 处理方向键
     */
    private void handleArrowKey(int direction, String keyName, JSONObject result, CommandHandler.CommandCallback callback) {
//        accessibilityService.moveFocusAsync(direction, new DeviceAccessibilityService.FocusCallback() {
//            @Override
//            public void onSuccess() {
//                sendSuccessResponse(result, keyName + " key pressed", keyName.toLowerCase(), callback);
//            }
//
//            @Override
//            public void onFailure(String error) {
//                sendErrorResponse(result, keyName + " key failed: " + error, callback);
//            }
//        });
    }

    /**
     * 处理Tab键
     */
    private void handleTabKey(boolean shiftPressed, JSONObject result, CommandHandler.CommandCallback callback) {
        int direction = shiftPressed ? View.FOCUS_BACKWARD : View.FOCUS_FORWARD;

//        accessibilityService.moveFocusAsync(direction, new DeviceAccessibilityService.FocusCallback() {
//            @Override
//            public void onSuccess() {
//                String message = "Tab key pressed" + (shiftPressed ? " with shift" : "");
//                sendSuccessResponse(result, message, "tab", callback);
//            }
//
//            @Override
//            public void onFailure(String error) {
//                sendErrorResponse(result, "Tab key failed: " + error, callback);
//            }
//        });
    }

    /**
     * 处理Enter键
     */
    private void handleEnterKey(JSONObject result, CommandHandler.CommandCallback callback) {
//        accessibilityService.performEnterAsync(new DeviceAccessibilityService.ActionCallback() {
//            @Override
//            public void onSuccess() {
//                sendSuccessResponse(result, "Enter key pressed", "enter", callback);
//            }
//
//            @Override
//            public void onFailure(String error) {
//                sendErrorResponse(result, "Enter key failed: " + error, callback);
//            }
//        });
    }

    /**
     * 处理Escape键
     */
    private void handleEscapeKey(JSONObject result, CommandHandler.CommandCallback callback) {
        performGlobalAction(
                AccessibilityService.GLOBAL_ACTION_BACK,
                "Escape",
                result,
                callback
        );
    }

    /**
     * 处理最近任务键
     */
    private void handleRecentsKey(JSONObject result, CommandHandler.CommandCallback callback) {
        performGlobalAction(
                AccessibilityService.GLOBAL_ACTION_RECENTS,
                "Recents",
                result,
                callback
        );
    }

    /**
     * 处理通知键
     */
    private void handleNotificationsKey(JSONObject result, CommandHandler.CommandCallback callback) {
        performGlobalAction(
                AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS,
                "Notifications",
                result,
                callback
        );
    }

    /**
     * 处理快速设置键
     */
    private void handleQuickSettingsKey(JSONObject result, CommandHandler.CommandCallback callback) {
        performGlobalAction(
                AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS,
                "Quick Settings",
                result,
                callback
        );
    }

    /**
     * 处理电源键
     */
    private void handlePowerKey(JSONObject result, CommandHandler.CommandCallback callback) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            performGlobalAction(
                    AccessibilityService.GLOBAL_ACTION_POWER_DIALOG,
                    "Power Dialog",
                    result,
                    callback
            );
        } else {
            sendErrorResponse(result, "Power dialog not supported on this Android version", callback);
        }
    }

    /**
     * 处理End键
     */
    private void handleEndKey(JSONObject result, CommandHandler.CommandCallback callback) {
//        accessibilityService.moveToEndAsync(new DeviceAccessibilityService.ActionCallback() {
//            @Override
//            public void onSuccess() {
//                sendSuccessResponse(result, "End key pressed", "end", callback);
//            }
//
//            @Override
//            public void onFailure(String error) {
//                sendErrorResponse(result, "End key failed: " + error, callback);
//            }
//        });
    }

    /**
     * 处理PageUp/PageDown键
     */
    private void handlePageKey(boolean isPageUp, JSONObject result, CommandHandler.CommandCallback callback) {
        int scrollAction = isPageUp ?
                android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD :
                android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD;

        String keyName = isPageUp ? "PageUp" : "PageDown";

//        accessibilityService.performScrollAsync(scrollAction, new DeviceAccessibilityService.ScrollCallback() {
//            @Override
//            public void onSuccess() {
//                sendSuccessResponse(result, keyName + " key pressed", keyName.toLowerCase(), callback);
//            }
//
//            @Override
//            public void onFailure(String error) {
//                sendErrorResponse(result, keyName + " key failed: " + error, callback);
//            }
//        });
    }

    /**
     * 处理功能键F1-F12
     */
    private void handleFunctionKey(String key, JSONObject result, CommandHandler.CommandCallback callback) {
        try {
            String numberStr = key.substring(1);
            int functionNumber = Integer.parseInt(numberStr);

//            if (functionNumber >= 1 && functionNumber <= 12) {
//                accessibilityService.sendFunctionKeyAsync(functionNumber, new DeviceAccessibilityService.ActionCallback() {
//                    @Override
//                    public void onSuccess() {
//                        sendSuccessResponse(result, key.toUpperCase() + " key pressed", key.toLowerCase(), callback);
//                    }
//
//                    @Override
//                    public void onFailure(String error) {
//                        sendErrorResponse(result, key.toUpperCase() + " key failed: " + error, callback);
//                    }
//                });
//            } else {
//                sendErrorResponse(result, "Invalid function key: " + key, callback);
//            }
        } catch (NumberFormatException e) {
            sendErrorResponse(result, "Invalid function key format: " + key, callback);
        }
    }

    /**
     * 处理组合键
     */
    private void handleModifierKey(String key, boolean shift, boolean ctrl, boolean alt,
                                   JSONObject result, CommandHandler.CommandCallback callback) {
        String modifiers = "";
        if (ctrl) modifiers += "Ctrl+";
        if (alt) modifiers += "Alt+";
        if (shift) modifiers += "Shift+";

        String combination = modifiers + key.toUpperCase();
        Log.d(TAG, "Processing key combination: " + combination);

//        // 处理常见的组合键
//        if (ctrl && key.equals("c")) {
//            // Ctrl+C 复制
//            accessibilityService.performCopyAsync(new DeviceAccessibilityService.ActionCallback() {
//                @Override
//                public void onSuccess() {
//                    sendSuccessResponse(result, "Copy performed", "ctrl+c", callback);
//                }
//
//                @Override
//                public void onFailure(String error) {
//                    sendErrorResponse(result, "Copy failed: " + error, callback);
//                }
//            });
//        } else if (ctrl && key.equals("v")) {
//            // Ctrl+V 粘贴
//            accessibilityService.performPasteAsync(new DeviceAccessibilityService.ActionCallback() {
//                @Override
//                public void onSuccess() {
//                    sendSuccessResponse(result, "Paste performed", "ctrl+v", callback);
//                }
//
//                @Override
//                public void onFailure(String error) {
//                    sendErrorResponse(result, "Paste failed: " + error, callback);
//                }
//            });
//        } else if (ctrl && key.equals("x")) {
//            // Ctrl+X 剪切
//            accessibilityService.performCutAsync(new DeviceAccessibilityService.ActionCallback() {
//                @Override
//                public void onSuccess() {
//                    sendSuccessResponse(result, "Cut performed", "ctrl+x", callback);
//                }
//
//                @Override
//                public void onFailure(String error) {
//                    sendErrorResponse(result, "Cut failed: " + error, callback);
//                }
//            });
//        } else if (ctrl && key.equals("a")) {
//            // Ctrl+A 全选
//            accessibilityService.performSelectAllAsync(new DeviceAccessibilityService.ActionCallback() {
//                @Override
//                public void onSuccess() {
//                    sendSuccessResponse(result, "Select all performed", "ctrl+a", callback);
//                }
//
//                @Override
//                public void onFailure(String error) {
//                    sendErrorResponse(result, "Select all failed: " + error, callback);
//                }
//            });
//        } else {
//            sendErrorResponse(result, "Unsupported key combination: " + combination, callback);
//        }
    }

    /**
     * 执行全局操作
     */
    private void performGlobalAction(int action, String actionName, JSONObject result,
                                     CommandHandler.CommandCallback callback) {
        accessibilityService.performGlobalActionAsync(action,
                new DeviceAccessibilityService.GlobalActionCallback() {
                    @Override
                    public void onSuccess() {
                        sendSuccessResponse(result, actionName + " key pressed", actionName.toLowerCase(), callback);
                    }

                    @Override
                    public void onFailure(String error) {
                        sendErrorResponse(result, actionName + " key failed: " + error, callback);
                    }
                }
        );
    }

    /**
     * 删除文本
     */
    private void deleteText(int count, JSONObject result, CommandHandler.CommandCallback callback) {
//        accessibilityService.deleteTextAsync(count, new DeviceAccessibilityService.TextInputCallback() {
//            @Override
//            public void onSuccess() {
//                sendSuccessResponse(result, "Delete key pressed", "delete", callback);
//            }
//
//            @Override
//            public void onFailure(String error) {
//                sendErrorResponse(result, "Delete key failed: " + error, callback);
//            }
//        });
    }

    /**
     * 发送成功响应
     */
    private void sendSuccessResponse(JSONObject result, String message, String key,
                                     CommandHandler.CommandCallback callback) {
        try {
            result.put("success", true);
            result.put("message", message);
            result.put("key", key);
            result.put("timestamp", System.currentTimeMillis());
            callback.onResult(result);
        } catch (Exception e) {
            Log.e(TAG, "Error sending success response", e);
        }
    }

    /**
     * 发送错误响应
     */
    private void sendErrorResponse(JSONObject result, String error, CommandHandler.CommandCallback callback) {
        try {
            result.put("success", false);
            result.put("error", error);
            result.put("timestamp", System.currentTimeMillis());
            callback.onResult(result);
        } catch (Exception e) {
            Log.e(TAG, "Error sending error response", e);
        }
    }
}