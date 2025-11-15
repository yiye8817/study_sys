package com.devicecontrol.client.network;

import android.util.Log;

import com.devicecontrol.client.utils.Constants;

import org.json.JSONObject;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

import java.net.URISyntaxException;

public class SocketIOManager {
    private static final String TAG = "SocketIOManager";

    private Socket socket;
    private String serverUrl;
    private EventListener eventListener;
    private boolean isConnected = false;

    // 单例实例
    private static volatile SocketIOManager instance;

    public interface EventListener {
        void onConnected();
        void onDisconnected();
        void onCommandReceived(JSONObject command);
    }

    /**
     * 获取单例实例
     */
    public static SocketIOManager getInstance() {
        if (instance == null) {
            synchronized (SocketIOManager.class) {
                if (instance == null) {
                    instance = new SocketIOManager(Constants.SERVER_URL);
                }
            }
        }
        return instance;
    }

    /**
     * 创建新实例（会替换现有实例）
     */
    public static SocketIOManager createInstance(String serverUrl) {
        synchronized (SocketIOManager.class) {
            if (instance != null) {
                instance.disconnect();
            }
            instance = new SocketIOManager(serverUrl);
            return instance;
        }
    }

    /**
     * 公开构造函数（用于非单例模式）
     */
    public SocketIOManager(String serverUrl) {
        this.serverUrl = serverUrl;
        initSocket();
    }

    private void initSocket() {
        try {
            IO.Options options = new IO.Options();
            options.reconnection = true;
            options.reconnectionAttempts = Integer.MAX_VALUE;
            options.reconnectionDelay = 1000;
            options.reconnectionDelayMax = 5000;
            options.timeout = 20000;
            options.transports = new String[]{"websocket"};

            socket = IO.socket(serverUrl, options);

            // 设置事件监听器
            socket.on(Socket.EVENT_CONNECT, onConnect);
            socket.on(Socket.EVENT_DISCONNECT, onDisconnect);
            socket.on(Socket.EVENT_CONNECT_ERROR, onConnectError);
            socket.on("execute_command", onCommandReceived);
            socket.on("device_registered", onDeviceRegistered);
            socket.on("screenshot_request", onScreenshotRequest);
            socket.on("stream_start", onStreamStart);
            socket.on("stream_stop", onStreamStop);

        } catch (URISyntaxException e) {
            Log.e(TAG, "Invalid server URL: " + serverUrl, e);
        }
    }

    private Emitter.Listener onConnect = args -> {
        Log.d(TAG, "Socket connected");
        isConnected = true;
        if (eventListener != null) {
            eventListener.onConnected();
        }
    };

    private Emitter.Listener onDisconnect = args -> {
        Log.d(TAG, "Socket disconnected");
        isConnected = false;
        if (eventListener != null) {
            eventListener.onDisconnected();
        }
    };

    private Emitter.Listener onConnectError = args -> {
        if (args.length > 0) {
            Log.e(TAG, "Socket connection error: " + args[0]);
        } else {
            Log.e(TAG, "Socket connection error");
        }
    };

    private Emitter.Listener onCommandReceived = args -> {
        try {
            JSONObject data = (JSONObject) args[0];
            Log.d(TAG, "Command received: " + data.toString());
            if (eventListener != null) {
                eventListener.onCommandReceived(data);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse command", e);
        }
    };

    private Emitter.Listener onDeviceRegistered = args -> {
        try {
            JSONObject data = (JSONObject) args[0];
            Log.d(TAG, "Device registered: " + data.toString());
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse registration response", e);
        }
    };

    private Emitter.Listener onScreenshotRequest = args -> {
        Log.d(TAG, "Screenshot requested from server");
        // 这里需要通过其他方式通知Service执行截图
        // 可以使用回调或EventBus等方式
    };

    private Emitter.Listener onStreamStart = args -> {
        try {
            JSONObject data = args.length > 0 ? (JSONObject) args[0] : new JSONObject();
            int fps = data.optInt("fps", 5);
            Log.d(TAG, "Stream start requested, fps: " + fps);
            // 通知Service开始流式传输
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse stream start", e);
        }
    };

    private Emitter.Listener onStreamStop = args -> {
        Log.d(TAG, "Stream stop requested");
        // 通知Service停止流式传输
    };

    public void setEventListener(EventListener listener) {
        this.eventListener = listener;
    }

    public void connect() {
        if (socket != null && !socket.connected()) {
            socket.connect();
            Log.d(TAG, "Attempting to connect to: " + serverUrl);
        }
    }

    public void disconnect() {
        if (socket != null && socket.connected()) {
            socket.disconnect();
            Log.d(TAG, "Disconnecting from server");
        }
    }

    /**
     * 发送事件到服务器
     */
    public void emit(String event, JSONObject data) {
        if (socket != null && socket.connected()) {
            socket.emit(event, data);
            Log.d(TAG, "Emitted event: " + event);
        } else {
            Log.w(TAG, "Socket not connected, cannot emit: " + event);
            // 尝试重连
            connect();
        }
    }

    /**
     * 发送截图数据
     */
    public void sendScreenshot(String deviceId, String base64Image) {
        try {
            JSONObject data = new JSONObject();
            data.put("device_id", deviceId);
            data.put("screenshot", base64Image);

            emit("screenshot_data", data);
            Log.d(TAG, "Screenshot data sent, size: " + base64Image.length());

        } catch (Exception e) {
            Log.e(TAG, "Failed to send screenshot", e);
        }
    }

    /**
     * 发送设备状态
     */
    public void sendDeviceStatus(JSONObject status) {
        emit("device_status", status);
    }

    /**
     * 注册设备
     */
    public void registerDevice(String deviceId) {
        try {
            JSONObject data = new JSONObject();
            data.put("device_id", deviceId);
            emit("register_device", data);
        } catch (Exception e) {
            Log.e(TAG, "Failed to register device", e);
        }
    }

    /**
     * 发送命令响应
     */
    public void sendCommandResponse(String deviceId, String command, JSONObject result) {
        try {
            JSONObject data = new JSONObject();
            data.put("device_id", deviceId);
            data.put("command", command);
            data.put("result", result);
            data.put("timestamp", System.currentTimeMillis());

            emit("command_response", data);
        } catch (Exception e) {
            Log.e(TAG, "Failed to send command response", e);
        }
    }

    public boolean isConnected() {
        return isConnected && socket != null && socket.connected();
    }

    /**
     * 销毁实例
     */
    public void destroy() {
        disconnect();
        if (socket != null) {
            socket.off();
            socket = null;
        }
        if (instance == this) {
            instance = null;
        }
    }
}