package com.xcheng.retrofit;

import android.arch.lifecycle.Lifecycle;
import android.support.annotation.NonNull;
import android.support.annotation.UiThread;

import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import retrofit2.Response;

@UiThread
public abstract class DefaultCallback<T> implements LifeCallback<T> {
    @NonNull
    public HttpError parseThrowable(LifeCall<T> call2, Throwable t) {
        if (t instanceof HttpError) {
            //用于convert函数直接抛出异常接收
            HttpError error = (HttpError) t;
            Response<?> response = error.response();
            if (response != null) {
                if (response.isSuccessful()) {
                    return new HttpError("暂无数据", response);
                }
                final String msg;
                switch (response.code()) {
                    case 400:
                        msg = "参数错误";
                        break;
                    case 401:
                        msg = "身份未授权";
                        break;
                    case 403:
                        msg = "禁止访问";
                        break;
                    case 404:
                        msg = "地址未找到";
                        break;
                    default:
                        msg = "服务异常";
                }
                return new HttpError(msg, response);
            }
            return error;
        } else if (t instanceof UnknownHostException) {
            return new HttpError("网络异常", t);
        } else if (t instanceof ConnectException) {
            return new HttpError("网络异常", t);
        } else if (t instanceof SocketException) {
            return new HttpError("服务异常", t);
        } else if (t instanceof SocketTimeoutException) {
            return new HttpError("响应超时", t);
        } else {
            return new HttpError("请求失败", t);
        }
    }

    @Override
    public void onDisposed(LifeCall<T> call, Lifecycle.Event event) {

    }
}