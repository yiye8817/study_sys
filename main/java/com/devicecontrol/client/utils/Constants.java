package com.devicecontrol.client.utils;

public class Constants {
    // 服务器配置
    public static final String SERVER_URL = "http://192.168.0.106:5000";
    public static final String DEVICE_ID = "ANDROID_" + android.os.Build.SERIAL;
    public static final int HEARTBEAT_INTERVAL = 30; // 秒

    // 事件类型
    public static class EventTypes {
        public static final int STARTUP = 1;
        public static final int SHUTDOWN = 2;
        public static final int HEARTBEAT = 6;
        public static final int STATUS = 10;
        public static final int ALERT = 11;
    }

    // 命令类型
    public static class Commands {
        public static final String POWER = "power";
        public static final String SCREENSHOT = "screenshot";
        public static final String EXECUTE = "execute";
        public static final String KEY = "key";
        public static final String MOUSE = "mouse";
        public static final String GESTURE = "gesture";
        public static final String TOUCH = "touch";
        public static final String TYPE = "type";
        public static final String TEXT = "text";
    }
}