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

    // Auth (2xxx)
    INVALID_KAKAO_TOKEN(HttpStatus.UNAUTHORIZED, 2001, "유효하지 않은 카카오 액세스 토큰입니다."),
    KAKAO_API_ERROR(HttpStatus.BAD_GATEWAY, 2002, "카카오 서버와의 통신에 실패했습니다."),
    KAKAO_REQUIRED_INFO_MISSING(HttpStatus.BAD_REQUEST, 2003, "카카오 필수 사용자 정보가 누락되었습니다."),
    ALREADY_REGISTERED_USER(HttpStatus.CONFLICT, 2004, "이미 가입된 회원입니다."),
    REQUIRED_AGREEMENT_NOT_ACCEPTED(HttpStatus.BAD_REQUEST, 2005, "필수 약관에 동의해야 합니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, 2006, "유효하지 않은 Refresh Token입니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, 2007, "해당하는 유저를 찾을 수 없습니다."),
    DUPLICATE_AGREEMENT_TYPE(HttpStatus.BAD_REQUEST, 2008, "동일한 약관 유형이 중복되었습니다."),
    INVALID_AGREEMENT_VERSION(HttpStatus.BAD_REQUEST, 2009, "유효하지 않은 약관 버전입니다."),
    TOKEN_MISSING(HttpStatus.UNAUTHORIZED, 2010, "인증 토큰이 누락되었습니다."),
    INVALID_ACCESS_TOKEN(HttpStatus.UNAUTHORIZED, 2011, "유효하지 않은 Access Token입니다."),
    ACCESS_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, 2012, "Access Token이 만료되었습니다."),
    REFRESH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, 2013, "Refresh Token이 만료되었습니다. 다시 로그인해주세요."),

    // Meeting - 생성 (POST /api/meetings)
    MEETING_NAME_EMPTY(HttpStatus.BAD_REQUEST, 4001, "모임 이름을 입력해주세요."),
    MEETING_NAME_TOO_LONG(HttpStatus.BAD_REQUEST, 4002, "모임 이름은 최대 20자까지 입력할 수 있습니다."),
    MEETING_SCHEDULE_INCOMPLETE(HttpStatus.BAD_REQUEST, 4003, "일정을 확정한 경우 날짜와 시간을 모두 입력해주세요."),
    MEETING_PURPOSE_EMPTY(HttpStatus.BAD_REQUEST, 4004, "모임 목적을 선택해주세요."),
    MEETING_CREATE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, 4005, "모임을 만들지 못했어요. 잠시 후 다시 시도해주세요."),

    // Meeting - 참여 (POST /api/meetings/invite/{inviteCode}/join)
    INVALID_INVITE_CODE(HttpStatus.NOT_FOUND, 4041, "유효하지 않은 초대 링크예요."),
    MEETING_NOT_FOUND(HttpStatus.NOT_FOUND, 4042, "존재하지 않는 모임이에요."),
    MEETING_FULL(HttpStatus.BAD_REQUEST, 4043, "모임 참여 인원이 가득 찼어요."),
    MEETING_COMPLETED(HttpStatus.BAD_REQUEST, 4044, "종료된 모임이에요. 참여할 수 없어요."),
    NOT_A_PARTICIPANT(HttpStatus.FORBIDDEN, 4045, "해당 모임의 참여자가 아닙니다."),
    CANNOT_LEAVE_COMPLETED_MEETING(HttpStatus.BAD_REQUEST, 4046, "종료된 모임은 나갈 수 없습니다."),

    // Meeting - 조건입력 (POST /api/meetings/{meetingId}/preferences)
    MAX_TRAVEL_MINUTES_INVALID(HttpStatus.BAD_REQUEST, 4047, "이동 가능 시간을 선택해주세요."),
    ACTIVITY_TYPES_EMPTY(HttpStatus.BAD_REQUEST, 4048, "취향을 최소 1개 선택해주세요."),
    AVAILABLE_SCHEDULES_EMPTY(HttpStatus.BAD_REQUEST, 4049, "만날 수 있는 일정을 최소 1개 입력해주세요."),
    DEPARTURE_MISSING(HttpStatus.BAD_REQUEST, 4050, "출발 위치를 입력해주세요."),
    MEETING_STATUS_INVALID_FOR_CONDITION(HttpStatus.BAD_REQUEST, 4051, "조건 입력이 불가능한 모임 상태입니다."),

    // Meeting - Participant, ParticipantPreference
    PARTICIPANT_NOT_FOUND(HttpStatus.NOT_FOUND, 4091, "존재하지 않는 참가자입니다."),
    PARTICIPANT_PREFERENCE_NOT_FOUND(HttpStatus.NOT_FOUND, 4092, "존재하지 않는 참가자 선호 조건입니다."),

    // Recommendation(6xxx)
    RECOMMENDED_PLACE_NOT_FOUND(HttpStatus.NOT_FOUND,6001,"추천 장소를 찾을 수 없습니다."),
    RECOMMENDED_AREA_NOT_FOUND(HttpStatus.NOT_FOUND, 6002, "추천 지역을 찾을 수 없습니다."),
    RECOMMENDATION_RESULT_NOT_FOUND(HttpStatus.NOT_FOUND, 6003, "추천 결과를 찾을 수 없습니다."),
    PLACE_INFO_LOOKUP_FAILED(HttpStatus.BAD_GATEWAY, 6004, "장소 정보 조회에 실패했습니다."),
    RECOMMENDATION_SOURCE_LOCATION_NOT_FOUND(HttpStatus.BAD_REQUEST, 6005, "추천 지역 계산에 필요한 참가자 출발 위치를 찾을 수 없습니다."),
    AREA_CANDIDATE_GENERATION_FAILED(HttpStatus.BAD_GATEWAY, 6006, "추천 지역 후보 생성에 실패했습니다."),
    AREA_INFO_LOOKUP_FAILED(HttpStatus.BAD_GATEWAY, 6007, "추천 지역 정보 조회에 실패했습니다."),
    INSUFFICIENT_AREA_CANDIDATES(HttpStatus.INTERNAL_SERVER_ERROR, 6008, "추천 가능한 지역 후보가 부족합니다."),

    // Route (Kakao MAP API 연동) (3xxx)
    // - 카카오맵 연동 오류
    KAKAO_MAP_API_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, 3003, "카카오맵 API 연동 오류가 발생했습니다."),
    KAKAO_MAP_API_CONNECTION_ERROR(HttpStatus.BAD_GATEWAY, 3004, "카카오맵 API 통신 오류가 발생했습니다."),
    // - 카카오맵 대중교통 경로 조회 API 에러코드
    TRANSIT_ROUTE_NOT_FOUND(HttpStatus.NOT_FOUND, 3007, "이용 가능한 대중교통 경로가 없습니다."),

    ;
    private final HttpStatus httpStatus;
    private final int code;
    private final String message;

    // 기본 메시지 뒤에 세부 원인을 괄호로 덧붙인 메시지 생성 (ex. "... 오류가 발생했습니다. (호출 한도 초과)")
    public String messageWith(String detail) {
        return message + " (" + detail + ")";
    }
}