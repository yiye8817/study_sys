package com.devicecontrol.client.model;

import com.google.gson.annotations.SerializedName;
import java.util.Map;

public class EventData {
    @SerializedName("device_type")
    public String deviceType;
    
    @SerializedName("device_id")
    public String deviceId;
    
    @SerializedName("event_id")
    public String eventId;
    
    @SerializedName("event_value")
    public String eventValue;
    
    @SerializedName("location")
    public String location;
    
    @SerializedName("timestamp")
    public long timestamp;
    
    @SerializedName("extra_fields")
    public Map<String, Object> extraFields;
    
    public EventData() {
        // 默认构造函数
    }
}