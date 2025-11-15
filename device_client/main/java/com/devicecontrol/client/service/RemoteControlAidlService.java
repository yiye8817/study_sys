package com.devicecontrol.client.service;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.util.Log;
import android.util.SparseArray;

import androidx.annotation.Nullable;

import com.devicecontrol.client.aidl.IRemoteControlCallback;
import com.devicecontrol.client.aidl.IRemoteControlService;
import com.devicecontrol.client.aidl.RemoteCommandConstants;
import com.devicecontrol.client.aidl.handler.ICommandHandler;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RemoteControlAidlService extends Service {
    private static final String TAG = "RemoteControlAidl";

    // 命令处理器映射表
    private final SparseArray<ICommandHandler> mCommandHandlers = new SparseArray<>();

    // 回调列表
    private final RemoteCallbackList<IRemoteControlCallback> mCallbacks = new RemoteCallbackList<>();

    // 处理器
    private Handler mMainHandler;
    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;
    private ExecutorService mExecutor;

    // 服务状态
    private volatile boolean mServiceReady = false;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service onCreate");

        // 初始化处理器
        initHandlers();

        // 注册命令处理器
        registerCommandHandlers();

        mServiceReady = true;
        Log.d(TAG, "Service initialized");
    }

    private void initHandlers() {
        mMainHandler = new Handler(Looper.getMainLooper());

        mBackgroundThread = new HandlerThread("RemoteControl-BG");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());

        mExecutor = Executors.newFixedThreadPool(4);
    }

    /**
     * 注册命令处理器
     */
    private void registerCommandHandlers() {
        // 注册各种命令处理器
        registerHandler(RemoteCommandConstants.CMD_GET_DEVICE_INFO, new GetDeviceInfoHandler());
//        registerHandler(RemoteCommandConstants.CMD_GET_SCREEN_INFO, new GetScreenInfoHandler());
//        registerHandler(RemoteCommandConstants.CMD_EXECUTE_SHELL, new ExecuteShellHandler());
//        registerHandler(RemoteCommandConstants.CMD_KEY_EVENT, new KeyEventHandler());
//        registerHandler(RemoteCommandConstants.CMD_INPUT_TEXT, new InputTextHandler());
//        registerHandler(RemoteCommandConstants.CMD_TOUCH, new TouchHandler());
//        registerHandler(RemoteCommandConstants.CMD_TAKE_SCREENSHOT, new ScreenshotHandler());

        Log.d(TAG, "Registered " + mCommandHandlers.size() + " command handlers");
    }

    /**
     * 注册命令处理器
     */
    public void registerHandler(int cmd, ICommandHandler handler) {
        if (handler != null) {
            mCommandHandlers.put(cmd, handler);
            Log.d(TAG, "Registered handler for cmd=" + cmd + ": " + handler.getDescription());
        }
    }

    /**
     * 注销命令处理器
     */
    public void unregisterHandler(int cmd) {
        mCommandHandlers.remove(cmd);
    }

    /**
     * AIDL接口实现
     */
    private final IRemoteControlService.Stub mBinder = new IRemoteControlService.Stub() {

        @Override
        public Bundle executeCommandSync(int cmd, int arg0, String arg1, Bundle arg2) throws RemoteException {
            Log.d(TAG, String.format("executeCommandSync: cmd=%d, arg0=%d, arg1=%s", cmd, arg0, arg1));

            // 权限检查
            if (!checkPermission()) {
                return createErrorBundle(RemoteCommandConstants.ERROR_PERMISSION_DENIED, "Permission denied");
            }

            // 获取命令处理器
            ICommandHandler handler = mCommandHandlers.get(cmd);
            if (handler == null) {
                Log.w(TAG, "No handler found for cmd=" + cmd);
                return createErrorBundle(RemoteCommandConstants.ERROR_COMMAND_NOT_SUPPORTED,
                        "Command not supported: " + cmd);
            }

            // 执行命令
            try {
                Bundle result = handler.executeSync(arg0, arg1, arg2);
                if (result == null) {
                    result = new Bundle();
                }
                result.putLong(RemoteCommandConstants.KEY_TIMESTAMP, System.currentTimeMillis());
                return result;

            } catch (Exception e) {
                Log.e(TAG, "Error executing sync command: " + cmd, e);
                return createErrorBundle(RemoteCommandConstants.ERROR_UNKNOWN, e.getMessage());
            }
        }

        @Override
        public void executeCommandAsync(int cmd, int arg0, String arg1, Bundle arg2,
                                        IRemoteControlCallback callback) throws RemoteException {
            Log.d(TAG, String.format("executeCommandAsync: cmd=%d, arg0=%d, arg1=%s", cmd, arg0, arg1));

            // 权限检查
            if (!checkPermission()) {
                if (callback != null) {
                    callback.onError(cmd, RemoteCommandConstants.ERROR_PERMISSION_DENIED, "Permission denied");
                }
                return;
            }

            // 获取命令处理器
            ICommandHandler handler = mCommandHandlers.get(cmd);
            if (handler == null) {
                Log.w(TAG, "No handler found for cmd=" + cmd);
                if (callback != null) {
                    callback.onError(cmd, RemoteCommandConstants.ERROR_COMMAND_NOT_SUPPORTED,
                            "Command not supported: " + cmd);
                }
                return;
            }

            // 在后台线程执行
            mExecutor.execute(() -> {
                try {
                    handler.executeAsync(arg0, arg1, arg2, new ICommandHandler.AsyncCallback() {
                        @Override
                        public void onSuccess(Bundle result) throws RemoteException {
                            if (result == null) {
                                result = new Bundle();
                            }
                            result.putLong(RemoteCommandConstants.KEY_TIMESTAMP, System.currentTimeMillis());

                            if (callback != null) {
                                callback.onCommandResult(cmd, result);
                            }
                        }

                        @Override
                        public void onError(int errorCode, String errorMessage) throws RemoteException {
                            if (callback != null) {
                                callback.onError(cmd, errorCode, errorMessage);
                            }
                        }

                        @Override
                        public void onProgress(int progress, String message) throws RemoteException {
                            if (callback != null) {
                                callback.onProgress(cmd, progress, message);
                            }
                        }
                    });

                } catch (Exception e) {
                    Log.e(TAG, "Error executing async command: " + cmd, e);
                    if (callback != null) {
                        try {
                            callback.onError(cmd, RemoteCommandConstants.ERROR_UNKNOWN, e.getMessage());
                        } catch (RemoteException ex) {
                            throw new RuntimeException(ex);
                        }
                    }
                }
            });
        }

        @Override
        public void registerCallback(IRemoteControlCallback callback) throws RemoteException {
            if (callback != null) {
                mCallbacks.register(callback);
                Log.d(TAG, "Callback registered");
            }
        }

        @Override
        public void unregisterCallback(IRemoteControlCallback callback) throws RemoteException {
            if (callback != null) {
                mCallbacks.unregister(callback);
                Log.d(TAG, "Callback unregistered");
            }
        }

        @Override
        public boolean isServiceReady() throws RemoteException {
            return mServiceReady;
        }

        @Override
        public int getServiceVersion() throws RemoteException {
            return RemoteCommandConstants.SERVICE_VERSION;
        }
    };

    /**
     * 权限检查
     */
    private boolean checkPermission() {
        int callingUid = Binder.getCallingUid();
        int callingPid = Binder.getCallingPid();

        Log.d(TAG, String.format("Permission check: UID=%d, PID=%d", callingUid, callingPid));

        // system_server (UID 1000) 或 root (UID 0) 允许
        if (callingUid == 1000 || callingUid == 0) {
            return true;
        }

        // 同一应用允许
        if (callingUid == Process.myUid()) {
            return true;
        }

        // 检查自定义权限（如果需要）
        // return checkCallingPermission("com.devicecontrol.permission.REMOTE_CONTROL")
        //        == PackageManager.PERMISSION_GRANTED;

        return true; // 暂时允许所有调用，实际使用时应该严格控制
    }

    /**
     * 创建错误Bundle
     */
    private Bundle createErrorBundle(int errorCode, String errorMessage) {
        Bundle bundle = new Bundle();
        bundle.putBoolean(RemoteCommandConstants.KEY_SUCCESS, false);
        bundle.putInt(RemoteCommandConstants.KEY_ERROR_CODE, errorCode);
        bundle.putString(RemoteCommandConstants.KEY_ERROR_MESSAGE, errorMessage);
        bundle.putLong(RemoteCommandConstants.KEY_TIMESTAMP, System.currentTimeMillis());
        return bundle;
    }

    /**
     * 广播给所有回调
     */
    protected void broadcastResult(int cmd, Bundle result) throws RemoteException {
        int n = mCallbacks.beginBroadcast();
        for (int i = 0; i < n; i++) {
            mCallbacks.getBroadcastItem(i).onCommandResult(cmd, result);
        }
        mCallbacks.finishBroadcast();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind: " + intent);
        return mBinder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");

        mServiceReady = false;

        if (mBackgroundThread != null) {
            mBackgroundThread.quitSafely();
        }

        if (mExecutor != null) {
            mExecutor.shutdown();
        }

        mCallbacks.kill();
        mCommandHandlers.clear();
    }

    // ========== 命令处理器实现示例 ==========

    /**
     * 获取设备信息处理器
     */
    private class GetDeviceInfoHandler implements ICommandHandler {
        @Override
        public Bundle executeSync(int arg0, String arg1, Bundle arg2) {
            Bundle result = new Bundle();
            result.putBoolean(RemoteCommandConstants.KEY_SUCCESS, true);
            result.putString("device_model", android.os.Build.MODEL);
            result.putString("device_brand", android.os.Build.BRAND);
            result.putString("android_version", android.os.Build.VERSION.RELEASE);
            result.putInt("sdk_version", android.os.Build.VERSION.SDK_INT);
            result.putString("device_id", android.os.Build.ID);
            return result;
        }

        @Override
        public void executeAsync(int arg0, String arg1, Bundle arg2, AsyncCallback callback) throws RemoteException {
            // 对于简单操作，异步版本可以直接调用同步版本
            Bundle result = executeSync(arg0, arg1, arg2);
            callback.onSuccess(result);
        }

        @Override
        public String getDescription() {
            return "Get device information";
        }
    }


}