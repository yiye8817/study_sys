package com.devicecontrol.client.model;

import com.google.gson.annotations.SerializedName;

public class CommandResponse {
    @SerializedName("success")
    public boolean success;
    
    @SerializedName("message")
    public String message;
    
    @SerializedName("error")
    public String error;
    
    @SerializedName("data")
    public Object data;
    
    public CommandResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }
    
    public static CommandResponse success(String message) {
        return new CommandResponse(true, message);
    }
    
    public static CommandResponse error(String error) {
        CommandResponse response = new CommandResponse(false, null);
        response.error = error;
        return response;
    }
}