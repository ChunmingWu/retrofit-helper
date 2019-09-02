package com.xcheng.retrofit;

import android.support.annotation.Nullable;

import static com.xcheng.retrofit.Utils.checkNotNull;

/**
 * An HTTP Result. like Retrofit
 */
public final class Result<T> {

    /**
     * @param body 请求成功返回的body
     * @throws NullPointerException if body==null
     */
    public static <T> Result<T> success(T body) {
        checkNotNull(body, "body==null");
        return new Result<>(body, null);
    }

    /**
     * @param error 请求失败返回的错误信息
     * @throws NullPointerException if error==null
     */
    public static <T> Result<T> error(HttpError error) {
        checkNotNull(error, "error==null");
        return new Result<>(null, error);
    }

    @Nullable
    private final HttpError error;
    @Nullable
    private final T body;

    private Result(@Nullable T body, @Nullable HttpError error) {
        this.error = error;
        this.body = body;
    }

    @Nullable
    public HttpError error() {
        return error;
    }

    @Nullable
    public T body() {
        return body;
    }

    /**
     * 判断http请求是否成功返回了body
     *
     * @return 是否成功
     */
    public boolean isSuccess() {
        return body != null;
    }

}
