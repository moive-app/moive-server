package com.moive.MoiveBE.global.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Getter
@RequiredArgsConstructor
public enum CustomErrorCode {

    // Common (1xxx)
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, 1001, "서버 내부에 오류가 있습니다."),
    INVALID_INPUT(BAD_REQUEST, 1002, "잘못된 입력값입니다."),

    // TODO Custom ErrorCode 를 추가해 주세요
    // Auth (2xxx)
    INVALID_KAKAO_TOKEN(HttpStatus.UNAUTHORIZED, 2001, "유효하지 않은 카카오 액세스 토큰입니다."),
    KAKAO_API_ERROR(HttpStatus.BAD_GATEWAY, 2002, "카카오 서버와의 통신에 실패했습니다."),
    KAKAO_REQUIRED_INFO_MISSING(HttpStatus.BAD_REQUEST, 2003, "카카오 필수 사용자 정보가 누락되었습니다."),
    ALREADY_REGISTERED_USER(HttpStatus.CONFLICT, 2004, "이미 가입된 회원입니다."),
    REQUIRED_AGREEMENT_NOT_ACCEPTED(HttpStatus.BAD_REQUEST, 2005, "필수 약관에 동의해야 합니다.");

    private final HttpStatus httpStatus;
    private final int code;
    private final String message;
}