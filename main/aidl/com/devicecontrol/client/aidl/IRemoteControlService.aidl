// app/src/main/aidl/com/devicecontrol/client/aidl/IRemoteControlService.aidl
package com.devicecontrol.client.aidl;

import android.os.Bundle;
import com.devicecontrol.client.aidl.IRemoteControlCallback;
 interface IRemoteControlService {
    /**
     * 同步接口 - 立即返回结果
     * @param cmd 命令类型
     * @param arg0 整型参数
     * @param arg1 字符串参数
     * @param arg2 Bundle参数
     * @return 返回结果Bundle
     */
    Bundle executeCommandSync(int cmd, int arg0, String arg1, in Bundle arg2);

    /**
     * 异步接口 - 通过回调返回结果
     * @param cmd 命令类型
     * @param arg0 整型参数
     * @param arg1 字符串参数
     * @param arg2 Bundle参数
     * @param callback 回调接口
     */
    oneway void executeCommandAsync(int cmd, int arg0, String arg1, in Bundle arg2, IRemoteControlCallback callback);

    /**
     * 注册持久回调
     */
    void registerCallback(IRemoteControlCallback callback);

    /**
     * 取消注册回调
     */
    void unregisterCallback(IRemoteControlCallback callback);

    /**
     * 检查服务状态
     */
    boolean isServiceReady();

    /**
     * 获取服务版本
     */
    int getServiceVersion();
}