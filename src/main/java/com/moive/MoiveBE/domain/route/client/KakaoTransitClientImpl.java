package com.moive.MoiveBE.domain.route.client;

import com.moive.MoiveBE.domain.route.dto.KakaoTransitRouteResponse;
import com.moive.MoiveBE.domain.route.dto.Location;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@Component
@RequiredArgsConstructor
public class KakaoTransitClientImpl implements KakaoTransitClient {

    private static final String TRANSIT_ROUTE_URL =
            "https://dapi.kakao.com/v2/routing/publictraffic";

    private final RestClient restClient;

    @Value("${kakao.transit.api-key}")
    private String apiKey;

    @Override
    public KakaoTransitRouteResponse getTransitRoute(Location start, Location end) {
        return restClient.get()
                .uri(buildTransitRouteUri(start, end))
                .header("Authorization", "KakaoAK " + apiKey)
                .retrieve()
                .body(KakaoTransitRouteResponse.class);
    }

    // 카카오맵 대중교통 경로 조회 URI 및 파라미터 생성
    private URI buildTransitRouteUri(Location start, Location end) {
        // 카카오 좌표계 기준: WGS84 (기본값), x=경도(longitude), y=위도(latitude)
        return UriComponentsBuilder.fromUriString(TRANSIT_ROUTE_URL)
                .queryParam("start_x", start.longitude())
                .queryParam("start_y", start.latitude())
                .queryParam("end_x", end.longitude())
                .queryParam("end_y", end.latitude())
                // .queryParam("s_name", "출발지")
                // .queryParam("e_name", "도착지")
                .build()
                .toUri();
    }
}
