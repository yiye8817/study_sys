package com.devicecontrol.client.network;

import android.util.Log;

import com.devicecontrol.client.model.DeviceInfo;
import com.devicecontrol.client.model.EventData;
import com.google.gson.Gson;

import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ApiClient {
    private static final String TAG = "ApiClient";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    
    private String baseUrl;
    private OkHttpClient client;
    private Gson gson;
    
    public interface Callback<T> {
        void onSuccess(T result);
        void onError(String error);
    }
    
    public ApiClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.gson = new Gson();
        
        this.client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
    }
    
    public void registerDevice(DeviceInfo deviceInfo, Callback<Void> callback) {
        String json = gson.toJson(deviceInfo);
        RequestBody body = RequestBody.create(json, JSON);
        
        Request request = new Request.Builder()
            .url(baseUrl + "/api/devices")
            .post(body)
            .build();
        
        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Failed to register device", e);
                if (callback != null) {
                    callback.onError(e.getMessage());
                }
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() || response.code() == 409) {
                    Log.d(TAG, "Device registered successfully");
                    if (callback != null) {
                        callback.onSuccess(null);
                    }
                } else {
                    String error = "Registration failed: " + response.code();
                    Log.e(TAG, error);
                    if (callback != null) {
                        callback.onError(error);
                    }
                }
                response.close();
            }
        });
    }
    
    public void sendEvent(EventData eventData, Callback<Void> callback) {
        String json = gson.toJson(eventData);
        RequestBody body = RequestBody.create(json, JSON);
        
        Request request = new Request.Builder()
            .url(baseUrl + "/api/events")
            .post(body)
            .build();
        
        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Failed to send event", e);
                if (callback != null) {
                    callback.onError(e.getMessage());
                }
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    if (callback != null) {
                        callback.onSuccess(null);
                    }
                } else {
                    String error = "Event send failed: " + response.code();
                    if (callback != null) {
                        callback.onError(error);
                    }
                }
                response.close();
            }
        });
    }
    
    public void uploadScreenshot(JSONObject data, Callback<String> callback) {
        String json = data.toString();
        RequestBody body = RequestBody.create(json, JSON);
        
        Request request = new Request.Builder()
            .url(baseUrl + "/api/screenshot")
            .post(body)
            .build();
        
        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Failed to upload screenshot", e);
                if (callback != null) {
                    callback.onError(e.getMessage());
                }
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String responseBody = response.body().string();
                    try {
                        JSONObject result = new JSONObject(responseBody);
                        String url = result.optString("url", "");
                        if (callback != null) {
                            callback.onSuccess(url);
                        }
                    } catch (Exception e) {
                        if (callback != null) {
                            callback.onError(e.getMessage());
                        }
                    }
                } else {
                    String error = "Upload failed: " + response.code();
                    if (callback != null) {
                        callback.onError(error);
                    }
                }
                response.close();
            }
        });
    }
    
    public void uploadFrame(JSONObject data, Callback<Void> callback) {
        String json = data.toString();
        RequestBody body = RequestBody.create(json, JSON);
        
        Request request = new Request.Builder()
            .url(baseUrl + "/api/stream/frame")
            .post(body)
            .build();
        
        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                // 忽略帧上传失败
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                response.close();
            }
        });
    }
}