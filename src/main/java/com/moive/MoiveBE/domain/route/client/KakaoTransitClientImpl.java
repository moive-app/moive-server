package com.moive.MoiveBE.domain.route.client;

import com.moive.MoiveBE.domain.route.dto.KakaoTransitErrorResponse;
import com.moive.MoiveBE.domain.route.dto.KakaoTransitRouteResponse;
import com.moive.MoiveBE.domain.route.dto.Location;
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
import java.nio.charset.StandardCharsets;

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
            KakaoApiErrorCase cause = classify(e.getStatusCode().value(), errorBody);

            log.error("[카카오맵 대중교통 경로 조회 API 연동 오류] - 분류={}, 원인={}, httpStatus={}, errorType={}, message={}",
                    KAKAO_MAP_API_SERVER_ERROR.name(),
                    cause.getDetail(),
                    e.getStatusCode().value(),
                    errorBody != null ? errorBody.errorType() : null,
                    errorBody != null ? errorBody.message() : e.getMessage());

            throw new CustomException(KAKAO_MAP_API_SERVER_ERROR, KAKAO_MAP_API_SERVER_ERROR.messageWith(cause.getDetail()));

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
                .encode(StandardCharsets.UTF_8)
                .build()
                .toUri();
    }

    // 카카오맵 API 오류 원인 분류
    private KakaoApiErrorCase classify(int httpStatus, KakaoTransitErrorResponse errorBody) {
        if(httpStatus == 401) {
            return KakaoApiErrorCase.CONFIG;
        }
        if(httpStatus == 503) {
            return KakaoApiErrorCase.SERVER;
        }
        if(httpStatus == 400) {
            String errorType = errorBody != null ? errorBody.errorType() : null;
            String message = errorBody != null ? errorBody.message() : null;

            if("ValidationError".equalsIgnoreCase(errorType)) {
                return KakaoApiErrorCase.CONFIG;
            }
            if(looksLikeQuotaExceeded(message)) {
                return KakaoApiErrorCase.QUOTA_EXCEEDED;
            }
            return KakaoApiErrorCase.SERVER;
        }
        return KakaoApiErrorCase.SERVER;
    }

    // 카카오맵 연동 오류의 세부 원인
    private enum KakaoApiErrorCase {
        CONFIG("서버 내부 설정 확인 필요"),
        QUOTA_EXCEEDED("호출 한도 초과"),
        SERVER("카카오 서버 장애 및 점검");

        private final String detail;

        KakaoApiErrorCase(String detail) {
            this.detail = detail;
        }

        private String getDetail() {
            return detail;
        }
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
