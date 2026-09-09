package com.moive.MoiveBE.domain.route.service;

import com.moive.MoiveBE.domain.route.client.KakaoTransitClientImpl;
import com.moive.MoiveBE.domain.route.dto.KakaoTransitRouteResponse;
import com.moive.MoiveBE.domain.route.dto.Location;
import com.moive.MoiveBE.domain.route.dto.RouteDetail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled("실제 카카오맵 대중교통 경로 API 연동 확인용 테스트")
class RouteDetailServiceTest {

    private RouteDetailService routeDetailService;
    private KakaoTransitRouteResponse.Route bestRoute;

    private Location userLocation;
    private Location placeLocation;

    @BeforeEach
    void setUpAll() {
        RestClient restClient = RestClient.create();
        KakaoTransitClientImpl kakaoTransitClientImpl = new KakaoTransitClientImpl(restClient, new ObjectMapper());

        String apiKey = System.getenv("KAKAO_TRANSIT_API_KEY");
        ReflectionTestUtils.setField(kakaoTransitClientImpl, "apiKey", apiKey);

        this.routeDetailService = new RouteDetailService(kakaoTransitClientImpl);

        // given
        userLocation = new Location(37.5788132079661, 126.901364655063);
        placeLocation = new Location(37.5510324090502, 126.91228338125131);

        KakaoTransitRouteResponse kakaoResponse = kakaoTransitClientImpl.getTransitRoute(userLocation, placeLocation);
        assertThat(kakaoResponse.routes()).isNotEmpty();
        this.bestRoute = kakaoResponse.routes().get(0);
    }

    @Test
    void 전체_경로_좌표_한번에_단순화_성공() {
        // given: 전체 원본 좌표 개수 (출발지/도착지 포함)
        int rawPointCount = 2 + bestRoute.steps().stream()
                .mapToInt(step -> step.path().points().length)
                .sum();

        // when
        List<Location> pathPoints = routeDetailService.buildSimplifiedPathPoints(
                bestRoute, userLocation, placeLocation
        );

        // then
        assertThat(pathPoints).isNotEmpty();
        assertThat(pathPoints.size())
                .as("Douglas-Peucker 단순화 후 좌표 개수는 원본 이하여야 함")
                .isLessThanOrEqualTo(rawPointCount);

        // 출발지/도착지 좌표는 단순화 후에도 양 끝에 보존되어야 함
        assertThat(pathPoints.get(0)).isEqualTo(userLocation);
        assertThat(pathPoints.get(pathPoints.size() - 1)).isEqualTo(placeLocation);
    }

    @Test
    void 최종_응답_결과_확인() {
        // when
        RouteDetail response = routeDetailService.getMyRouteDetail(
                userLocation, placeLocation, "출발지", "도착지"
        );

        // then
        assertThat(response).isNotNull();
        assertThat(response.totalTime()).isGreaterThan(0);
        assertThat(response.pathPoints()).isNotEmpty();
        assertThat(response.pathPoints().get(0)).isEqualTo(userLocation);
        assertThat(response.pathPoints().get(response.pathPoints().size() - 1)).isEqualTo(placeLocation);

        int sumOfTransitTimes = response.walkTime() + response.busTime() + response.subwayTime();
        int totalTime = response.totalTime();
        assertThat(sumOfTransitTimes)
                .as("이동 수단별 소요 시간(분)의 합산은 총 소요 시간보다 적거나 일치해야함")
                .isLessThanOrEqualTo(totalTime);

        // (출력) 응답 전체 JSON으로 출력
        ObjectMapper objectMapper = new ObjectMapper();
        String responseJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(response);
        System.out.println("===== 추천 장소 상세 조회 (이동 경로) API 응답 =====");
        System.out.println(responseJson);

        // (출력) 이동 시간 정보
        System.out.println("\n===== 최종 이동 시간 응답 결과 (분 단위) =====");
        System.out.println("- 총 소요시간: " + response.totalTime() + "분");
        System.out.println("- 도보: " + response.walkTime() + "분");
        System.out.println("- 버스: " + response.busTime() + "분");
        System.out.println("- 지하철: " + response.subwayTime() + "분");
        System.out.println("- 요금: " + response.fare() + "원");
        System.out.println("- Landing URL: " + response.landingUrl());

        // (출력) 단순화된 전체 경로 좌표
        int rawPointCount = 2 + bestRoute.steps().stream()
                .mapToInt(step -> step.path().points().length)
                .sum();
        System.out.printf("%n===== 경로 좌표: 원본 %d개 -> 단순화 후 %d개 =====%n",
                rawPointCount, response.pathPoints().size());
        for (Location point : response.pathPoints()) {
            System.out.println(" (" + point.latitude() + ", " + point.longitude() + ")");
        }
    }

}
