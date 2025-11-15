package com.devicecontrol.client.aidl.handler;

import android.os.Bundle;
import android.os.RemoteException;

/**
 * 命令处理器接口
 */
public interface ICommandHandler {
    /**
     * 同步执行命令
     * @param arg0 整型参数
     * @param arg1 字符串参数
     * @param arg2 Bundle参数
     * @return 返回结果Bundle
     */
    Bundle executeSync(int arg0, String arg1, Bundle arg2);

    /**
     * 异步执行命令
     * @param arg0 整型参数
     * @param arg1 字符串参数
     * @param arg2 Bundle参数
     * @param callback 结果回调
     */
    void executeAsync(int arg0, String arg1, Bundle arg2, AsyncCallback callback) throws RemoteException;

    /**
     * 获取命令描述
     */
    String getDescription();

    /**
     * 异步回调接口
     */
    interface AsyncCallback {
        void onSuccess(Bundle result) throws RemoteException;
        void onError(int errorCode, String errorMessage) throws RemoteException;
        void onProgress(int progress, String message) throws RemoteException;
    }
}