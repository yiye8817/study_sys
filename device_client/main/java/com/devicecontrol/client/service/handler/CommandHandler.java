package com.devicecontrol.client.service.handler;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.devicecontrol.client.utils.CommandExecutor;
import com.devicecontrol.client.utils.Constants;

import org.json.JSONObject;

import java.io.DataOutputStream;
import java.util.HashMap;
import java.util.Map;

public class CommandHandler {
    private static final String TAG = "CommandHandler";

    protected Context context;
    private Handler mainHandler;
    private CommandExecutor commandExecutor;

    public interface CommandCallback {
        void onResult(JSONObject result);
    }

    public CommandHandler(Context context) {
        this.context = context;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.commandExecutor = new CommandExecutor(context);
    }

    public void execute(String command, JSONObject params, CommandCallback callback) {
        Log.d(TAG, "Executing command: " + command);

        JSONObject result = new JSONObject();

        try {
            switch (command) {
                case Constants.Commands.POWER:
                    handlePower(params, result);
                    break;

                case Constants.Commands.SCREENSHOT:
                    handleScreenshot(params, result);
                    break;

                case Constants.Commands.EXECUTE:
                    handleExecute(params, result);
                    break;

                case Constants.Commands.KEY:
                    handleKey(params, result);
                    break;

                case Constants.Commands.TOUCH:
                    handleTouch(params, result);
                    break;

                case Constants.Commands.GESTURE:
                    handleGesture(params, result);
                    break;

                case Constants.Commands.TYPE:
                    handleType(params, result);
                    break;

                default:
                    result.put("success", false);
                    result.put("error", "Unknown command: " + command);
            }
        } catch (Exception e) {
            Log.e(TAG, "Command execution failed", e);
            try {
                result.put("success", false);
                result.put("error", e.getMessage());
            } catch (Exception ex) {
                // Ignore
            }
        }

        if (callback != null) {
            callback.onResult(result);
        }
    }

    private void handlePower(JSONObject params, JSONObject result) throws Exception {
        String action = params.optString("action", "");

        switch (action) {
            case "shutdown":
                // 需要root权限
                executeRootCommand("reboot -p");
                result.put("success", true);
                result.put("message", "Shutdown initiated");
                break;

            case "restart":
                // 需要root权限
                executeRootCommand("reboot");
                result.put("success", true);
                result.put("message", "Restart initiated");
                break;

            case "sleep":
                // 模拟按电源键
                executeShellCommand("input keyevent KEYCODE_POWER");
                result.put("success", true);
                result.put("message", "Sleep initiated");
                break;

            default:
                result.put("success", false);
                result.put("error", "Unknown power action: " + action);
        }
    }

    private void handleScreenshot(JSONObject params, JSONObject result) throws Exception {
        // 这里需要MediaProjection权限或root权限
        // 简化示例：使用shell命令
        String screenshotPath = "/sdcard/screenshot.png";
        executeShellCommand("screencap -p " + screenshotPath);

        // 读取截图并转换为base64
        // 实际实现需要读取文件并编码
        result.put("success", true);
        result.put("message", "Screenshot taken");
        result.put("path", screenshotPath);
    }

    private void handleExecute(JSONObject params, JSONObject result) throws Exception {
        String cmd = params.optString("cmd", params.optString("command", ""));

        if (cmd.isEmpty()) {
            result.put("success", false);
            result.put("error", "No command provided");
            return;
        }

        // 安全限制：只允许某些命令
        String[] allowedCommands = {"ls", "pwd", "date", "uptime", "df", "free", "ps", "top", "whoami"};
        boolean isAllowed = false;
        for (String allowed : allowedCommands) {
            if (cmd.startsWith(allowed)) {
                isAllowed = true;
                break;
            }
        }

        if (!isAllowed) {
            result.put("success", false);
            result.put("error", "Command not allowed: " + cmd);
            return;
        }

        String output = executeShellCommand(cmd);
        result.put("success", true);
        result.put("output", output);
    }

    private void handleKey(JSONObject params, JSONObject result) throws Exception {
        String key = params.optString("key", "");

        Map<String, String> keyMapping = new HashMap<>();
        keyMapping.put("Enter", "KEYCODE_ENTER");
        keyMapping.put("Tab", "KEYCODE_TAB");
        keyMapping.put("Escape", "KEYCODE_ESCAPE");
        keyMapping.put("Backspace", "KEYCODE_DEL");
        keyMapping.put("Delete", "KEYCODE_FORWARD_DEL");
        keyMapping.put("Space", "KEYCODE_SPACE");
        keyMapping.put("Up", "KEYCODE_DPAD_UP");
        keyMapping.put("Down", "KEYCODE_DPAD_DOWN");
        keyMapping.put("Left", "KEYCODE_DPAD_LEFT");
        keyMapping.put("Right", "KEYCODE_DPAD_RIGHT");
        keyMapping.put("Home", "KEYCODE_HOME");
        keyMapping.put("Back", "KEYCODE_BACK");

        String keycode = keyMapping.get(key);
        if (keycode == null) {
            keycode = "KEYCODE_" + key.toUpperCase();
        }

        executeShellCommand("input keyevent " + keycode);
        result.put("success", true);
        result.put("message", "Key pressed: " + key);
    }

    private void handleTouch(JSONObject params, JSONObject result) throws Exception {
        String action = params.optString("action", "tap");
        int x = params.optInt("x", 0);
        int y = params.optInt("y", 0);

        switch (action) {
            case "tap":
                executeShellCommand(String.format("input tap %d %d", x, y));
                result.put("success", true);
                result.put("message", String.format("Tapped at (%d, %d)", x, y));
                break;

            case "swipe":
                int endX = params.optInt("endX", x);
                int endY = params.optInt("endY", y);
                int duration = params.optInt("duration", 300);
                executeShellCommand(String.format("input swipe %d %d %d %d %d",
                        x, y, endX, endY, duration));
                result.put("success", true);
                result.put("message", "Swipe performed");
                break;

            default:
                result.put("success", false);
                result.put("error", "Unknown touch action: " + action);
        }
    }

    private void handleGesture(JSONObject params, JSONObject result) throws Exception {
        String type = params.optString("type", "");

        switch (type) {
            case "swipe_up":
                executeShellCommand("input swipe 500 1000 500 200 300");
                break;

            case "swipe_down":
                executeShellCommand("input swipe 500 200 500 1000 300");
                break;

            case "swipe_left":
                executeShellCommand("input swipe 800 500 200 500 300");
                break;

            case "swipe_right":
                executeShellCommand("input swipe 200 500 800 500 300");
                break;

            case "tap":
                executeShellCommand("input tap 500 500");
                break;

            case "double_tap":
                executeShellCommand("input tap 500 500");
                Thread.sleep(50);
                executeShellCommand("input tap 500 500");
                break;

            case "long_press":
                executeShellCommand("input swipe 500 500 500 500 1000");
                break;

            default:
                result.put("success", false);
                result.put("error", "Unknown gesture: " + type);
                return;
        }

        result.put("success", true);
        result.put("message", "Gesture " + type + " executed");
    }

    private void handleType(JSONObject params, JSONObject result) throws Exception {
        String text = params.optString("text", "");

        if (text.isEmpty()) {
            result.put("success", false);
            result.put("error", "No text provided");
            return;
        }

        // 转义特殊字符
        text = text.replace(" ", "%s");
        text = text.replace("'", "\\'");
        text = text.replace("\"", "\\\"");

        executeShellCommand("input text \"" + text + "\"");
        result.put("success", true);
        result.put("message", "Text typed");
    }

    private String executeShellCommand(String command) {
        try {
            Process process = Runtime.getRuntime().exec(command);
            process.waitFor();

            // 读取输出
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream())
            );

            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            reader.close();

            return output.toString();
        } catch (Exception e) {
            Log.e(TAG, "Failed to execute shell command", e);
            return "";
        }
    }

    private void executeRootCommand(String command) {
        try {
            Process process = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(process.getOutputStream());
            os.writeBytes(command + "\n");
            os.writeBytes("exit\n");
            os.flush();
            os.close();
            process.waitFor();
        } catch (Exception e) {
            Log.e(TAG, "Failed to execute root command", e);
        }
    }
}