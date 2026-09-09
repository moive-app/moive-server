package com.moive.MoiveBE.domain.route.client;

import com.moive.MoiveBE.domain.route.dto.KakaoTransitErrorResponse;
import com.moive.MoiveBE.domain.route.dto.KakaoTransitRouteResponse;
import com.moive.MoiveBE.domain.route.dto.Location;
import com.moive.MoiveBE.global.exception.CustomErrorCode;
import com.moive.MoiveBE.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;

import static com.moive.MoiveBE.global.exception.CustomErrorCode.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoTransitClientImpl implements KakaoTransitClient {

    private static final String TRANSIT_ROUTE_URL =
            "https://dapi.kakao.com/v2/routing/publictraffic";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Value("${kakao.transit.api-key}")
    private String apiKey;

    @Override
    public KakaoTransitRouteResponse getTransitRoute(
            Location start, Location end
    ) {
        return getTransitRoute(start, end, "출발지", "도착지");
    }

    @Override
    public KakaoTransitRouteResponse getTransitRoute(
            Location start, Location end,
            String userAddress, String placeName
    ) {
        try {
            return restClient.get()
                    .uri(buildTransitRouteUri(start, end, userAddress, placeName))
                    .header("Authorization", "KakaoAK " + apiKey)
                    .retrieve()
                    .body(KakaoTransitRouteResponse.class);
        } catch (RestClientResponseException e) {
            KakaoTransitErrorResponse errorBody = parseErrorBody(e.getResponseBodyAsString());
            CustomErrorCode errorCode = classify(e.getStatusCode().value(), errorBody);

            log.error("[카카오맵 대중교통 경로 조회 API 연동 오류] - 분류={}, httpStatus={}, errorType={}, message={}",
                    errorCode.name(),
                    e.getStatusCode().value(),
                    errorBody != null ? errorBody.errorType() : null,
                    errorBody != null ? errorBody.message() : e.getMessage());

            throw new CustomException(errorCode);

        } catch (Exception e) {
            log.error("[카카오맵 대중교통 경로 조회 API 통신 오류] - 분류={}, exceptionType={}, message={}",
                    KAKAO_MAP_API_CONNECTION_ERROR.name(),
                    e.getClass().getSimpleName(),
                    e.getMessage(), e);

            throw new CustomException(KAKAO_MAP_API_CONNECTION_ERROR);
        }
    }

    // 카카오맵 대중교통 경로 조회 URI 및 파라미터 생성
    private URI buildTransitRouteUri(
            Location start, Location end,
            String userAddress, String placeAddress
    ) {
        // 카카오 좌표계 기준: WGS84 (기본값), x=경도(longitude), y=위도(latitude)
        return UriComponentsBuilder.fromUriString(TRANSIT_ROUTE_URL)
                .queryParam("start_x", start.longitude())
                .queryParam("start_y", start.latitude())
                .queryParam("end_x", end.longitude())
                .queryParam("end_y", end.latitude())
                .queryParam("s_name", userAddress)
                .queryParam("e_name", placeAddress)
                .build()
                .toUri();
    }

    // 카카오맵 API 공통 예외 처리
    private CustomErrorCode classify(int httpStatus, KakaoTransitErrorResponse errorBody) {
        if(httpStatus == 401) {
            return KAKAO_MAP_API_CONFIG_ERROR;
        }
        if(httpStatus == 503) {
            return KAKAO_MAP_API_SERVER_ERROR;
        }
        if(httpStatus == 400) {
            String errorType = errorBody != null ? errorBody.errorType() : null;
            String message = errorBody != null ? errorBody.message() : null;

            if("ValidationError".equalsIgnoreCase(errorType)) {
                return KAKAO_MAP_API_CONFIG_ERROR;
            }
            if(looksLikeQuotaExceeded(message)) {
                return KAKAO_MAP_API_QUOTA_EXCEEDED;
            }
            return KAKAO_MAP_API_SERVER_ERROR;
        }
        return CustomErrorCode.KAKAO_MAP_API_SERVER_ERROR;
    }

    private boolean looksLikeQuotaExceeded(String message) {
        if(message == null) {
            return false;
        }
        String lower = message.toLowerCase();
        return lower.contains("limit") || lower.contains("exceed");
    }

    private KakaoTransitErrorResponse parseErrorBody(String body) {
        if(body == null || body.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(body, KakaoTransitErrorResponse.class);
        } catch (Exception e) {
            return null;
        }
    }

}
