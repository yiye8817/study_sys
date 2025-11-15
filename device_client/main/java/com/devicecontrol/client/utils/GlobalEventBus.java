package com.devicecontrol.client.utils;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GlobalEventBus {
    private static final String TAG = "GlobalEventBus";
    private static volatile GlobalEventBus instance;

    private final Map<String, List<EventListener>> listeners = new ConcurrentHashMap<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface EventListener {
        void onEvent(String event, Object data);
    }

    public static GlobalEventBus getInstance() {
        if (instance == null) {
            synchronized (GlobalEventBus.class) {
                if (instance == null) {
                    instance = new GlobalEventBus();
                }
            }
        }
        return instance;
    }

    private GlobalEventBus() {
        Log.d(TAG, "GlobalEventBus created");
    }

    /**
     * 注册事件监听器
     */
    public void register(String event, EventListener listener) {
        if (event == null || listener == null) {
            return;
        }

        synchronized (listeners) {
            List<EventListener> eventListeners = listeners.get(event);
            if (eventListeners == null) {
                eventListeners = new ArrayList<>();
                listeners.put(event, eventListeners);
            }
            if (!eventListeners.contains(listener)) {
                eventListeners.add(listener);
                Log.d(TAG, "Registered listener for event: " + event);
            }
        }
    }

    /**
     * 取消注册事件监听器
     */
    public void unregister(String event, EventListener listener) {
        if (event == null || listener == null) {
            return;
        }

        synchronized (listeners) {
            List<EventListener> eventListeners = listeners.get(event);
            if (eventListeners != null) {
                eventListeners.remove(listener);
                Log.d(TAG, "Unregistered listener for event: " + event);

                if (eventListeners.isEmpty()) {
                    listeners.remove(event);
                }
            }
        }
    }

    /**
     * 取消所有监听器
     */
    public void unregisterAll(EventListener listener) {
        synchronized (listeners) {
            for (List<EventListener> eventListeners : listeners.values()) {
                eventListeners.remove(listener);
            }
        }
    }

    /**
     * 发送事件（在主线程）
     */
    public void post(String event, Object data) {
        if (event == null) {
            return;
        }

        Log.d(TAG, "Posting event: " + event);

        mainHandler.post(() -> {
            List<EventListener> eventListeners;
            synchronized (listeners) {
                eventListeners = listeners.get(event);
                if (eventListeners != null) {
                    eventListeners = new ArrayList<>(eventListeners); // 复制以避免并发修改
                }
            }

            if (eventListeners != null) {
                for (EventListener listener : eventListeners) {
                    try {
                        listener.onEvent(event, data);
                    } catch (Exception e) {
                        Log.e(TAG, "Error dispatching event: " + event, e);
                    }
                }
                Log.d(TAG, "Event dispatched to " + eventListeners.size() + " listeners");
            } else {
                Log.d(TAG, "No listeners for event: " + event);
            }
        });
    }

    /**
     * 立即发送事件（在当前线程）
     */
    public void postImmediate(String event, Object data) {
        if (event == null) {
            return;
        }

        List<EventListener> eventListeners;
        synchronized (listeners) {
            eventListeners = listeners.get(event);
            if (eventListeners != null) {
                eventListeners = new ArrayList<>(eventListeners);
            }
        }

        if (eventListeners != null) {
            for (EventListener listener : eventListeners) {
                try {
                    listener.onEvent(event, data);
                } catch (Exception e) {
                    Log.e(TAG, "Error dispatching event: " + event, e);
                }
            }
        }
    }

    /**
     * 检查是否有监听器
     */
    public boolean hasListeners(String event) {
        synchronized (listeners) {
            List<EventListener> eventListeners = listeners.get(event);
            return eventListeners != null && !eventListeners.isEmpty();
        }
    }
}