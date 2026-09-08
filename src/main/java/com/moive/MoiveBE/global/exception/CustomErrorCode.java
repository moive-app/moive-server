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
    REQUIRED_AGREEMENT_NOT_ACCEPTED(HttpStatus.BAD_REQUEST, 2005, "필수 약관에 동의해야 합니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, 2006, "유효하지 않은 Refresh Token입니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, 2007, "회원을 찾을 수 없습니다."),
    DUPLICATE_AGREEMENT_TYPE(HttpStatus.BAD_REQUEST, 2008, "동일한 약관 유형이 중복되었습니다."),
    INVALID_AGREEMENT_VERSION(HttpStatus.BAD_REQUEST, 2009, "유효하지 않은 약관 버전입니다."),


    // Recommendation(6xxx)
    RECOMMENDED_PLACE_NOT_FOUND(HttpStatus.NOT_FOUND,6001,"추천 장소를 찾을 수 없습니다."),
    RECOMMENDED_AREA_NOT_FOUND(HttpStatus.NOT_FOUND, 6002, "추천 지역을 찾을 수 없습니다."),
    RECOMMENDATION_RESULT_NOT_FOUND(HttpStatus.NOT_FOUND, 6003, "추천 결과를 찾을 수 없습니다."),
    PLACE_INFO_LOOKUP_FAILED(HttpStatus.BAD_GATEWAY, 6004, "장소 정보 조회에 실패했습니다."),

    // Route (Kakao MAP API 연동) (3xxx)
    // - 카카오맵 공통 에러코드
    KAKAO_MAP_API_CONFIG_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, 3001, "카카오맵 API 연동 오류가 발생했습니다. (서버 내부 설정 확인 필요)"),
    KAKAO_MAP_API_QUOTA_EXCEEDED(HttpStatus.INTERNAL_SERVER_ERROR, 3002, "카카오맵 API 연동 오류가 발생했습니다. (호출 한도 초과)"),
    KAKAO_MAP_API_SERVER_ERROR(HttpStatus.SERVICE_UNAVAILABLE, 3003, "카카오맵 API 연동 오류가 발생했습니다. (카카오 서버 장애 및 점검)"),
    KAKAO_MAP_API_CONNECTION_ERROR(HttpStatus.BAD_GATEWAY, 3004, "카카오맵 API 연동 오류가 발생했습니다. (외부 통신 및 네트워크 오류)"),
    // - 카카오맵 대중교통 경로 조회 API 에러코드
    KAKAO_MAP_API_INVALID_RESPONSE(HttpStatus.INTERNAL_SERVER_ERROR, 3005, "카카오맵 API 연동 오류가 발생했습니다. (응답 데이터 규격 확인 필요)"),
    KAKAO_MAP_API_INVALID_REQUEST(HttpStatus.INTERNAL_SERVER_ERROR, 3006, "카카오맵 API 연동 오류가 발생했습니다. (요청 좌표로 경로 탐색 불가)"),
    TRANSIT_ROUTE_NOT_FOUND(HttpStatus.NOT_FOUND, 3007, "이용 가능한 대중교통 경로가 없습니다."),

    ;
    private final HttpStatus httpStatus;
    private final int code;
    private final String message;
}