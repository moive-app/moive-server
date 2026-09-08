package com.moive.MoiveBE.domain.route.type;

/**
 * 카카오맵 대중교통 경로 조회 API
 * - 이동 수단
 */
public enum TransitType {
    SUBWAY, BUS, WALKING;

    // StepProperties.type("BUS"|"SUBWAY"|"WALKING") 매핑
    public static TransitType fromKakaoType(String kakaoType) {
        return switch (kakaoType) {
            case "WALKING" -> WALKING;
            case "BUS" -> BUS;
            case "SUBWAY" -> SUBWAY;
            default -> throw new IllegalStateException("알 수 없는 대중교통 타입: " + kakaoType);
        };
    }
}
