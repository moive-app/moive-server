package com.moive.MoiveBE.domain.route.client;

import com.moive.MoiveBE.domain.route.dto.KakaoTransitRouteResponse;
import com.moive.MoiveBE.domain.route.dto.Location;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled("실제 카카오맵 대중교통 경로 API 연동 확인용 테스트")
class KakaoTransitClientTest {

    private KakaoTransitClient kakaoTransitClient;

    @BeforeEach
    void setUp() {
        RestClient restClient = RestClient.create();
        KakaoTransitClientImpl clientImpl = new KakaoTransitClientImpl(restClient);

        String apiKey = System.getenv("KAKAO_TRANSIT_API_KEY");
        ReflectionTestUtils.setField(clientImpl, "apiKey", apiKey);

        this.kakaoTransitClient = clientImpl;
    }

    @Test
    void 대중교통_경로_조회_성공() {
        // given
        Location start = new Location(37.5788132079661, 126.901364655063);
        Location end = new Location(37.5510324090502, 126.91228338125131);

        // when
        KakaoTransitRouteResponse response = kakaoTransitClient.getTransitRoute(start, end);

        // then
        // - 정상 응답 확인
        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("OK");
        assertThat(response.routes())
                .as("status가 OK인 경우 routes 항목이 존재함")
                .isNotNull();

        // - 최적 경로로 사용할 routes의 첫 번째 항목 검증
        KakaoTransitRouteResponse.Route bestRoute = response.routes().get(0);
        assertThat(bestRoute.properties())
                .as("첫번째 경로의 properties가 존재해야 함")
                .isNotNull();
        assertThat(bestRoute.steps())
                .as("첫번째 경로의 steps가 비어있지 않아야 함")
                .isNotEmpty();
    }

    @Test
    void 대중교통_경로_없음(){
        // given (도보로만 이동 가능한 경우)
        Location start = new Location(37.5788132079661, 126.90127778415635);
        Location end = new Location(37.57601374718818, 126.90127778415635);

        // when
        KakaoTransitRouteResponse response = kakaoTransitClient.getTransitRoute(start, end);

        // then
        assertThat(response).isNotNull();
        assertThat(response.status())
                .as("대중교통 경로가 없는 경우 status는 NO_RESULTS여야 함")
                .isEqualTo("NO_RESULTS");
    }

}
