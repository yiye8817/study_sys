package com.devicecontrol.client.utils;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import androidx.core.app.ActivityCompat;

import com.devicecontrol.client.model.DeviceInfo;

import java.io.File;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DeviceInfoCollector {
    String TAG = "DeviceInfoCollector";
    private Context context;

    public DeviceInfoCollector(Context context) {
        this.context = context;
    }

    @SuppressLint("HardwareIds")
    public DeviceInfo collectDeviceInfo() {
        DeviceInfo deviceInfo = new DeviceInfo();

        // 设备基本信息
        deviceInfo.deviceId = Constants.DEVICE_ID;
        deviceInfo.deviceName = Build.MODEL;
        deviceInfo.deviceType = "mobile";
        deviceInfo.status = "online";
        deviceInfo.softwareVersion = "Android " + Build.VERSION.RELEASE;

        // 获取IP地址
        deviceInfo.ipAddress = getIpAddress();

        // 获取MAC地址
        deviceInfo.macAddress = getMacAddress();

        // CPU信息
        deviceInfo.cpu = Build.HARDWARE + " " + Runtime.getRuntime().availableProcessors() + " cores";

        // 内存信息
        deviceInfo.memory = getMemoryInfo();

        // 存储信息
        deviceInfo.disk = getStorageInfo();

        // GPU信息
        deviceInfo.gpu = Build.BOARD;

        // 显示信息
        deviceInfo.display = getDisplayInfo();

        // 网络信息
        deviceInfo.network = getNetworkType();

        // 额外信息
        Map<String, Object> extraInfo = new HashMap<>();
        extraInfo.put("manufacturer", Build.MANUFACTURER);
        extraInfo.put("brand", Build.BRAND);
        extraInfo.put("sdk_version", Build.VERSION.SDK_INT);
        extraInfo.put("android_id", getAndroidId());
        extraInfo.put("battery_level", getBatteryLevel());
        deviceInfo.extraInfo = extraInfo;

        return deviceInfo;
    }

    private String getIpAddress() {
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        int ipAddress = wifiInfo.getIpAddress();

        return String.format("%d.%d.%d.%d",
                (ipAddress & 0xff),
                (ipAddress >> 8 & 0xff),
                (ipAddress >> 16 & 0xff),
                (ipAddress >> 24 & 0xff));
    }

    private String getMacAddress() {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                if (intf.getName().equalsIgnoreCase("wlan0")) {
                    byte[] mac = intf.getHardwareAddress();
                    if (mac == null) return "02:00:00:00:00:00";

                    StringBuilder buf = new StringBuilder();
                    for (byte aMac : mac) {
                        buf.append(String.format("%02X:", aMac));
                    }
                    if (buf.length() > 0) {
                        buf.deleteCharAt(buf.length() - 1);
                    }
                    return buf.toString();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "02:00:00:00:00:00";
    }

    private String getMemoryInfo() {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memoryInfo);

        long totalMemory = memoryInfo.totalMem / (1024 * 1024 * 1024);
        return totalMemory + " GB";
    }

    private String getStorageInfo() {
        StatFs stat = new StatFs(Environment.getExternalStorageDirectory().getPath());
        long totalBytes = stat.getTotalBytes();
        long totalGB = totalBytes / (1024 * 1024 * 1024);
        return totalGB + " GB";
    }

    private String getDisplayInfo() {
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(metrics);
        return metrics.widthPixels + "x" + metrics.heightPixels;
    }

    private String getNetworkType() {
        TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            Log.d(TAG,"getNetworkType not permission");
            return "TODO";
        }
        int networkType = telephonyManager.getNetworkType();

        switch (networkType) {
            case TelephonyManager.NETWORK_TYPE_LTE:
                return "4G";
            case TelephonyManager.NETWORK_TYPE_HSDPA:
            case TelephonyManager.NETWORK_TYPE_HSPA:
                return "3G";
            case TelephonyManager.NETWORK_TYPE_EDGE:
            case TelephonyManager.NETWORK_TYPE_GPRS:
                return "2G";
            default:
                return "WiFi";
        }
    }

    @SuppressLint("HardwareIds")
    private String getAndroidId() {
        return Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
    }

    private int getBatteryLevel() {
        // 这里简化处理，实际应该通过BroadcastReceiver获取
        return 50;
    }
}