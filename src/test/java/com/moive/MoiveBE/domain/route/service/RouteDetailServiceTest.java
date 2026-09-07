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
            assertThat(routeStep.path())
                    .as("카카오 원본 좌표 개수를 간소화 없이 그대로 유지해야 함")
                    .hasSize(kakaoStep.path().points().length);

            // 좌표 검증: 카카오 원본 [경도, 위도] -> Location(latitude, longitude)
            Double[] firstRawPoint = kakaoStep.path().points()[0];
            Location firstConvertedPoint = routeStep.path().get(0);
            assertThat(firstConvertedPoint.latitude()).isEqualTo(firstRawPoint[1]);
            assertThat(firstConvertedPoint.longitude()).isEqualTo(firstRawPoint[0]);
        }

        System.out.println("routeSteps.size()=" + routeSteps.size());
        routeSteps.forEach(step ->
                System.out.println("type=" + step.type() + ", pathSize=" + step.path().size()));
    }

    @Test
    void 최종_응답_조회_시_초단위가_분단위로_정상_환산된다() {
        // when
        RouteDetailResponse response = routeDetailService.getMyRouteDetail(userLocation, placeLocation);

        // then
        assertThat(response).isNotNull();
        assertThat(response.totalTime()).isGreaterThan(0);
        assertThat(response.routeSteps()).isNotEmpty();

        int sumOfTransitTimes = response.walkTime() + response.busTime() + response.subwayTime();
        int totalTime = response.totalTime();
        assertThat(sumOfTransitTimes)
                .as("이동 수단별 소요 시간(분)의 합산은 총 소요 시간보다 적거나 일치해야함")
                .isLessThanOrEqualTo(totalTime);

        // (출력) 이동 시간 정보
        System.out.println("===== 최종 이동 시간 응답 결과 (분 단위) =====");
        System.out.println("- 총 소요시간: " + response.totalTime() + "분");
        System.out.println("- 도보: " + response.walkTime() + "분");
        System.out.println("- 버스: " + response.busTime() + "분");
        System.out.println("- 지하철: " + response.subwayTime() + "분");
        System.out.println("- 요금: " + response.fare() + "원");
        System.out.println("- Landing URL: " + response.landingUrl());

        // (출력) 이동 단계별 정보
        System.out.println("\n===== RouteStep 목록 상세 (총 " + response.routeSteps().size() + "단계) =====");
        for (int i = 0; i < response.routeSteps().size(); i++) {
            RouteDetailResponse.RouteStep step = response.routeSteps().get(i);
            Location startPoint = step.path().get(0);
            Location endPoint = step.path().get(step.path().size() - 1);

            System.out.printf("[%d단계] 이동수단: %-7s | 좌표 개수: %3d개 | 시작: (%.5f, %.5f) -> 종료: (%.5f, %.5f)%n",
                    i + 1,
                    step.type(),
                    step.path().size(),
                    startPoint.latitude(), startPoint.longitude(),
                    endPoint.latitude(), endPoint.longitude()
            );
        }
    }

}
