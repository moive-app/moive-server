package com.moive.MoiveBE.global.common;

import com.moive.MoiveBE.global.exception.CustomErrorCode;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import lombok.Getter;

@Getter
@JsonPropertyOrder({"success", "code", "message", "data"})
public class BaseResponse<T> {

    private final boolean success;
    private final int code;
    private final String message;
    private final T data;

    private BaseResponse(boolean success, int code, String message, T data) {
        this.success = success;
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> BaseResponse<T> success(T data) {
        return new BaseResponse<>(true, 200, "요청에 성공했습니다.", data);
    }

    public static <T> BaseResponse<T> fail(CustomErrorCode customErrorCode) {
        return new BaseResponse<>(false, customErrorCode.getCode(), customErrorCode.getMessage(), null);
    }

    public static <T> BaseResponse<T> fail(CustomErrorCode customErrorCode, T data) {
        return new BaseResponse<>(false, customErrorCode.getCode(), customErrorCode.getMessage(), data);
    }

}
