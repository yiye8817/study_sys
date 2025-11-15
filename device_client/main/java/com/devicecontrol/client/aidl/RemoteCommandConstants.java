package com.devicecontrol.client.aidl;

public class RemoteCommandConstants {
    // 命令类型
    public static final int CMD_TAKE_SCREENSHOT = 1001;
    public static final int CMD_TOUCH = 1002;
    public static final int CMD_SWIPE = 1003;
    public static final int CMD_KEY_EVENT = 1004;
    public static final int CMD_INPUT_TEXT = 1005;
    public static final int CMD_GET_SCREEN_INFO = 1006;
    public static final int CMD_GET_DEVICE_INFO = 1007;
    public static final int CMD_EXECUTE_SHELL = 1008;
    public static final int CMD_START_RECORDING = 1009;
    public static final int CMD_STOP_RECORDING = 1010;
    public static final int CMD_GET_INSTALLED_APPS = 1011;
    public static final int CMD_LAUNCH_APP = 1012;
    public static final int CMD_KILL_APP = 1013;
    public static final int CMD_INSTALL_APK = 1014;
    public static final int CMD_UNINSTALL_APP = 1015;

    // 错误代码
    public static final int ERROR_NONE = 0;
    public static final int ERROR_UNKNOWN = -1;
    public static final int ERROR_PERMISSION_DENIED = -2;
    public static final int ERROR_SERVICE_NOT_READY = -3;
    public static final int ERROR_INVALID_PARAMETER = -4;
    public static final int ERROR_TIMEOUT = -5;
    public static final int ERROR_COMMAND_NOT_SUPPORTED = -6;

    // Bundle键值
    public static final String KEY_SUCCESS = "success";
    public static final String KEY_ERROR_CODE = "error_code";
    public static final String KEY_ERROR_MESSAGE = "error_message";
    public static final String KEY_RESULT = "result";
    public static final String KEY_DATA = "data";
    public static final String KEY_TIMESTAMP = "timestamp";

    // 服务版本
    public static final int SERVICE_VERSION = 1;
}