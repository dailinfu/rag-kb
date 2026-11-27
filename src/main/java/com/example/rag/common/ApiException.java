package com.example.rag.common;

/**
 * 业务异常：统一携带 HTTP 状态码与提示信息。
 */
public class ApiException extends RuntimeException {

    private final int code;

    public ApiException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    /** 参数类异常（400） */
    public static ApiException badRequest(String message) {
        return new ApiException(400, message);
    }

    /** 资源不存在（404） */
    public static ApiException notFound(String message) {
        return new ApiException(404, message);
    }

    /** 请求过于频繁（429） */
    public static ApiException tooManyRequests(String message) {
        return new ApiException(429, message);
    }
}
