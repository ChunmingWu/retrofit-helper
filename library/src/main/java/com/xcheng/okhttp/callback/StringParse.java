package com.xcheng.okhttp.callback;

import android.support.annotation.NonNull;

import com.xcheng.okhttp.error.EasyError;
import com.xcheng.okhttp.request.OkResponse;

import java.io.IOException;

import okhttp3.Response;

/**
 * Created by chengxin on 2017/6/22.
 */
public class StringParse extends SimpleParse<String> {
    @NonNull
    @Override
    public OkResponse<String> parseNetworkResponse(OkCall<String> okCall, Response response) throws IOException {
        if (response.isSuccessful()) {
            return OkResponse.success(response.body().string());
        }
        return OkResponse.error(EasyError.create("response error"));
    }
}
