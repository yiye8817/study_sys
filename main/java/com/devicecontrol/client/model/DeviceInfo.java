package com.devicecontrol.client.model;

import com.google.gson.annotations.SerializedName;
import java.util.Map;

public class DeviceInfo {
    @SerializedName("device_id")
    public String deviceId;
    
    @SerializedName("device_name")
    public String deviceName;
    
    @SerializedName("device_type")
    public String deviceType;
    
    @SerializedName("ip_address")
    public String ipAddress;
    
    @SerializedName("mac_address")
    public String macAddress;
    
    @SerializedName("status")
    public String status;
    
    @SerializedName("software_version")
    public String softwareVersion;
    
    @SerializedName("cpu")
    public String cpu;
    
    @SerializedName("memory")
    public String memory;
    
    @SerializedName("disk")
    public String disk;
    
    @SerializedName("gpu")
    public String gpu;
    
    @SerializedName("display")
    public String display;
    
    @SerializedName("network")
    public String network;
    
    @SerializedName("extra_info")
    public Map<String, Object> extraInfo;
    
    public DeviceInfo() {
        // 默认构造函数
    }
}