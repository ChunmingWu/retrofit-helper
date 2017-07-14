package com.xcheng.okhttp.request;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.gson.reflect.TypeToken;
import com.xcheng.okhttp.EasyOkHttp;
import com.xcheng.okhttp.callback.OkCall;
import com.xcheng.okhttp.callback.ResponseParse;
import com.xcheng.okhttp.callback.UICallback;
import com.xcheng.okhttp.error.BaseError;
import com.xcheng.okhttp.utils.OkExceptions;
import com.xcheng.okhttp.utils.ParamHelper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;

/**
 * Created by cx on 17/6/22.
 * 发起http请求的封装类
 */
public final class OkHttpCall<T> implements OkCall<T> {

    private final OkRequest okRequest;
    private final int id;

    //发起请求 解析相关
    private final Request originalRequest;
    private final ResponseParse<T> responseParse;

    private TypeToken<T> typeToken;
    private Class<? extends UICallback> tokenClass;
    private ExecutorCallback<T> executorCallback;

    private okhttp3.Call rawCall;
    private volatile boolean canceled;
    private boolean executed;

    @SuppressWarnings("unchecked")
    public OkHttpCall(@NonNull OkRequest okRequest) {
        this.okRequest = okRequest;
        this.id = okRequest.id();
        this.typeToken = (TypeToken<T>) okRequest.typeToken();
        this.originalRequest = okRequest.createRequest();
        this.responseParse = createResponseParse();
    }

    @Override
    public Class<? extends ResponseParse> getParseClass() {
        Class<? extends ResponseParse> respParseClass = okRequest.parseClass();
        if (respParseClass == null) {
            //get default
            respParseClass = EasyOkHttp.getOkConfig().getParseClass();
        }
        return respParseClass;
    }

    @Override
    public int getId() {
        return id;
    }

    @Override
    public Request request() {
        return originalRequest;
    }

    private void buildCall() {
        Request request = originalRequest;
        RequestBody body = request.body();
        if (executorCallback != null && okRequest.inProgress() && body != null) {
            Request.Builder builder = originalRequest.newBuilder();
            RequestBody requestBody = new CountingRequestBody(body, new CountingRequestBody.Listener() {
                @Override
                public void onRequestProgress(final long bytesWritten, final long contentLength) {
                    executorCallback.inProgress(bytesWritten * 1.0f / contentLength, contentLength, id);
                }
            });
            builder.method(request.method(), requestBody);
            request = builder.build();
        }
        if (okRequest.okHttpClient() != null) {
            rawCall = okRequest.okHttpClient().newCall(request);
        } else {
            rawCall = EasyOkHttp.getOkConfig().getOkHttpClient().newCall(request);
        }
        addCall(this);
    }

    private void sendFailResult(BaseError error, @Nullable Response responseNoBody) {
        if (error == null) {
            error = BaseError.getNotFoundError("do not find defined error in " + getParseClass() + ".getError(IOException) method");
        }
        executorCallback.onError(error,responseNoBody, id);
        executorCallback.onAfter(id);
    }

    private void sendSuccessResult(T t) {
        executorCallback.onSuccess(t, id);
        executorCallback.onAfter(id);
    }


    @Override
    public OkResponse<T> execute() throws IOException {
        synchronized (this) {
            if (executed) throw new IllegalStateException("Already executed.");
            executed = true;
            buildCall();
        }
        if (canceled) {
            rawCall.cancel();
        }
        try {
            return responseParse.parseNetworkResponse(this, rawCall.execute(), id);
        } finally {
            finished(this);
        }
    }

    @Override
    public void enqueue(UICallback<T> uiCallback) {
        OkExceptions.checkNotNull(uiCallback, "uiCallback can not be null");
        this.tokenClass = uiCallback.getClass();
        this.executorCallback = new ExecutorCallback<>(uiCallback, this, responseParse);
        this.executorCallback.setOnAfterListener(new ExecutorCallback.OnAfterListener() {
            @Override
            public void onAfter(int id) {
                finished(OkHttpCall.this);
            }
        });
        synchronized (this) {
            if (executed) throw new IllegalStateException("Already executed.");
            executed = true;
            buildCall();
        }
        executorCallback.onBefore(id);
        if (canceled) {
            rawCall.cancel();
        }
        rawCall.enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, IOException e) {
                sendFailResult(responseParse.getError(e), null);
            }

            @Override
            public void onResponse(okhttp3.Call call, final Response response) {
                ResponseBody rawBody = response.body();
                // Remove the body's source (the only stateful object) so we can pass the response along.
                Response responseNoBody = response.newBuilder()
                        .body(new NoContentResponseBody(rawBody.contentType(), rawBody.contentLength()))
                        .build();
                try {
                    OkResponse<T> okResponse = responseParse.parseNetworkResponse(OkHttpCall.this, response, id);
                    BaseError responseError = null;
                    if (okResponse != null) {
                        if (okResponse.isSuccess()) {
                            sendSuccessResult(okResponse.getBody());
                            return;
                        }
                        responseError = okResponse.getError();
                    }
                    if (responseError == null) {
                        responseError = BaseError.getNotFoundError("do not find error in " + getParseClass() + ".parseNetworkResponse(OkCall<T> , Response , int ) , have you return it ?");
                    }
                    sendFailResult(responseError, responseNoBody);
                } catch (IOException e) {
                    e.printStackTrace();
                    sendFailResult(responseParse.getError(e), responseNoBody);
                } finally {
                    ResponseBody body = response.body();
                    if (body != null) {
                        body.close();
                    }
                }
            }
        });
    }

    @SuppressWarnings("unchecked")
    @Override
    public <V> V getExtra(String key) {
        Map<String, Object> extraMap = okRequest.extraMap();
        if (extraMap != null) {
            return (V) extraMap.get(key);
        }
        return null;
    }

    @Override
    public TypeToken<T> getTypeToken() {
        if (typeToken == null && tokenClass != null) {
            typeToken = ParamHelper.createTypeToken(tokenClass);
        }
        return typeToken;
    }

    @Override
    public synchronized boolean isExecuted() {
        return executed;
    }

    @Override
    public void cancel() {
        canceled = true;
        synchronized (this) {
            if (rawCall != null) {
                rawCall.cancel();
            }
        }

    }

    @Override
    public boolean isCanceled() {
        if (canceled) {
            return true;
        }
        synchronized (this) {
            return rawCall != null && rawCall.isCanceled();
        }
    }

    @Override
    public OkCall<T> clone() {
        OkHttpCall<T> okCall = new OkHttpCall<>(okRequest);
        okCall.typeToken = getTypeToken();
        return okCall;
    }

    private ResponseParse<T> createResponseParse() {
        try {
            return getParseClass().newInstance();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        OkExceptions.illegalState(getParseClass() + " must has a no zero argument constructor, class must be not private and abstract");
        return null;
    }

    private static final class NoContentResponseBody extends ResponseBody {
        private final MediaType contentType;
        private final long contentLength;

        NoContentResponseBody(MediaType contentType, long contentLength) {
            this.contentType = contentType;
            this.contentLength = contentLength;
        }

        @Override
        public MediaType contentType() {
            return contentType;
        }

        @Override
        public long contentLength() {
            return contentLength;
        }

        @Override
        public BufferedSource source() {
            throw new IllegalStateException("Cannot read raw response body of a converted body.");
        }
    }

    private static final List<OkHttpCall> ALLCALLS = new ArrayList<>();

    private static synchronized void addCall(OkHttpCall call) {
        ALLCALLS.add(call);
    }

    private static synchronized void finished(OkHttpCall call) {
        ALLCALLS.remove(call);
    }

    public static synchronized List<OkHttpCall> getCalls() {
        return Collections.unmodifiableList(ALLCALLS);
    }
}