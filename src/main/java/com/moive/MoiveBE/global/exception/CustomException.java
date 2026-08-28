package com.moive.MoiveBE.global.exception;

import lombok.Getter;

@Getter
public class CustomException extends RuntimeException {

    private final CustomErrorCode customErrorCode;

    public CustomException(CustomErrorCode customErrorCode) {
        super(customErrorCode.getMessage());
        this.customErrorCode = customErrorCode;
    }

    public CustomException(CustomErrorCode customErrorCode, String message){
        super(message);
        this.customErrorCode = customErrorCode;
    }
}
