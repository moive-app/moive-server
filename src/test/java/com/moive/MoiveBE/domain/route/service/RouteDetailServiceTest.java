package com.moive.MoiveBE.domain.route.service;

import com.moive.MoiveBE.domain.route.client.KakaoTransitClientImpl;
import com.moive.MoiveBE.domain.route.dto.KakaoTransitRouteResponse;
import com.moive.MoiveBE.domain.route.dto.Location;
import com.moive.MoiveBE.domain.route.dto.RouteDetailResponse;
import com.moive.MoiveBE.domain.route.type.TransitType;
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
    void 경로_단계별_이동수단과_좌표_목록_구성_성공() {
        // when
        List<RouteDetailResponse.RouteStep> routeSteps = routeDetailService.getRouteSteps(bestRoute);

        // then
        assertThat(routeSteps).hasSameSizeAs(bestRoute.steps());

        for (int i = 0; i < routeSteps.size(); i++) {
            RouteDetailResponse.RouteStep routeStep = routeSteps.get(i);
            KakaoTransitRouteResponse.Step kakaoStep = bestRoute.steps().get(i);

            assertThat(routeStep.type())
                    .isEqualTo(TransitType.fromKakaoType(kakaoStep.properties().type()));

            int originalSize = kakaoStep.path().points().length;
            assertThat(routeStep.path().size())
                    .as("Douglas-Peucker 단순화 후 좌표 개수는 원본 이하여야 함")
                    .isLessThanOrEqualTo(originalSize);

            // 시작/끝 좌표는 단순화 후에도 보존되어야 함 (구간 접점이 끊기지 않아야 함)
            Double[] firstRawPoint = kakaoStep.path().points()[0];
            Double[] lastRawPoint = kakaoStep.path().points()[originalSize - 1];
            Location firstConvertedPoint = routeStep.path().get(0);
            Location lastConvertedPoint = routeStep.path().get(routeStep.path().size() - 1);

            assertThat(firstConvertedPoint.latitude()).isEqualTo(firstRawPoint[1]);
            assertThat(firstConvertedPoint.longitude()).isEqualTo(firstRawPoint[0]);
            assertThat(lastConvertedPoint.latitude()).isEqualTo(lastRawPoint[1]);
            assertThat(lastConvertedPoint.longitude()).isEqualTo(lastRawPoint[0]);
        }

        //System.out.println("- routeSteps.size()=" + routeSteps.size());
        //routeSteps.forEach(step ->
        //        System.out.println("- type=" + step.type() + ", pathSize=" + step.path().size()));
    }

    @Test
    void 최종_응답_결과_확인() {
        // when
        RouteDetailResponse response = routeDetailService.getMyRouteDetail(
                userLocation, placeLocation, "출발지", "도착지"
        );

        // then
        assertThat(response).isNotNull();
        assertThat(response.totalTime()).isGreaterThan(0);
        assertThat(response.routeSteps()).isNotEmpty();

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

        // (출력) 이동 단계별 정보
        // userLocation->첫 탑승, 마지막 하차->placeLocation 구간(맨 앞/뒤)은 카카오 응답에 없는
        // 합성(WALKING, 직선) 구간이라 원본이 없다. 그 사이 구간만 bestRoute.steps()와 대응된다.
        System.out.println("\n===== RouteStep 목록 상세 (총 " + response.routeSteps().size() + "단계) =====");
        List<RouteDetailResponse.RouteStep> allSteps = response.routeSteps();
        List<KakaoTransitRouteResponse.Step> kakaoSteps = bestRoute.steps();

        for (int i = 0; i < allSteps.size(); i++) {
            RouteDetailResponse.RouteStep step = allSteps.get(i);
            boolean isBoundaryWalk = (i == 0 || i == allSteps.size() - 1);

            if (isBoundaryWalk) {
                System.out.printf("[%d단계] 이동수단: %s | 합성 도보 구간(원본 없음) | 좌표 %d개%n",
                        i + 1, step.type(), step.path().size());
            } else {
                KakaoTransitRouteResponse.Step kakaoStep = kakaoSteps.get(i - 1);
                int originalSize = kakaoStep.path().points().length;
                System.out.printf("[%d단계] 이동수단: %s | 원본 %d개 -> 단순화 후 %d개%n",
                        i + 1, step.type(), originalSize, step.path().size());
            }

            // response DTO에 있는 값 그대로(반올림 없이) 출력
            for (Location point : step.path()) {
                System.out.println(" (" + point.latitude() + ", " + point.longitude() + ")");
            }
        }
    }

}
