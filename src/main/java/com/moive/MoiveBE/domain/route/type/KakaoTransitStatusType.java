package com.moive.MoiveBE.domain.route.type;

/**
 * 카카오맵 대중교통 경로 조회 API
 * - Response의 Status
 */
public enum KakaoTransitStatusType {
    OK,
    STARTNODES_NULL,
    ENDNODES_NULL,
    EQUAL_POINTS,
    INVALID_REQUEST,
    NO_RESULTS
}
