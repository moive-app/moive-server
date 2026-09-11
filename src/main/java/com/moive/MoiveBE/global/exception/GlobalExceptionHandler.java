package com.moive.MoiveBE.global.exception;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.moive.MoiveBE.global.common.BaseResponse;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // Custom Exception
    @ExceptionHandler(CustomException.class)
    public ResponseEntity<BaseResponse<Void>> handleCustomException(CustomException e, HttpServletRequest request) {
        CustomErrorCode errorCode = e.getCustomErrorCode();

        log.error("*** Custom Exception - url: {} ({}), httpStatus: {}, errorCode: {}, errorMessage: {}",
                request.getRequestURL(), request.getMethod(), errorCode.getHttpStatus(), errorCode.getCode(), e.getMessage());

        // 별도 메시지가 지정된 경우 해당 메시지로 응답, 아니면 기존처럼 에러코드 기본 메시지 사용
        if (hasCustomMessage(e)) {
            return buildResponseEntity(errorCode, e.getMessage());
        }
        return buildResponseEntity(errorCode);
    }

    private boolean hasCustomMessage(CustomException e) {
        return e.getMessage() != null
                && !e.getMessage().equals(e.getCustomErrorCode().getMessage());
    }

    // @Valid Exception
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<BaseResponse<List<FieldErrorDetail>>> handleValidException(MethodArgumentNotValidException e, HttpServletRequest request) {
        log.error("*** Validation Exception - url: {} ({}), errorMessage: {}",
                request.getRequestURL(), request.getMethod(), e.getMessage());

        // 필드별 유효성 검증 실패 목록
        List<FieldErrorDetail> fieldErrors = e.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldErrorDetail(error.getField(), error.getDefaultMessage()))
                .collect(Collectors.toList());

        return buildResponseEntity(CustomErrorCode.INVALID_INPUT, fieldErrors);
    }

    // 기타 Exception
    @ExceptionHandler(Exception.class)
    public ResponseEntity<BaseResponse<Void>> handleException(Exception e, HttpServletRequest request) {
        log.error("*** Common Exception - url: {} ({}), errorMessage: {}",
                request.getRequestURL(), request.getMethod(), e.getMessage());

        return buildResponseEntity(CustomErrorCode.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<BaseResponse<Void>> buildResponseEntity(CustomErrorCode customErrorCode) {
        return ResponseEntity
                .status(customErrorCode.getHttpStatus())
                .body(BaseResponse.fail(customErrorCode));
    }

    private ResponseEntity<BaseResponse<Void>> buildResponseEntity(CustomErrorCode customErrorCode, String message) {
        return ResponseEntity
                .status(customErrorCode.getHttpStatus())
                .body(BaseResponse.fail(customErrorCode.getCode(), message));
    }

    private <T> ResponseEntity<BaseResponse<T>> buildResponseEntity(CustomErrorCode customErrorCode, T data) {
        return ResponseEntity
                .status(customErrorCode.getHttpStatus())
                .body(BaseResponse.fail(customErrorCode, data));
    }
}
