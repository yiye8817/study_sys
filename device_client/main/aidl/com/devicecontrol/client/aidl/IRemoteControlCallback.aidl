// app/src/main/aidl/com/devicecontrol/client/aidl/IRemoteControlCallback.aidl
package com.devicecontrol.client.aidl;

import android.os.Bundle;


oneway  interface IRemoteControlCallback  {
    /**
     * 命令执行结果回调
     * @param cmd 命令类型
     * @param result 执行结果
     */
    void onCommandResult(int cmd, in Bundle  result);

    /**
     * 错误回调
     * @param cmd 命令类型
     * @param errorCode 错误代码
     * @param errorMessage 错误信息
     */
    void onError(int cmd, int errorCode, String errorMessage);

    /**
     * 进度回调
     * @param cmd 命令类型
     * @param progress 进度值(0-100)
     * @param message 进度信息
     */
    void onProgress(int cmd, int progress, String message);
}