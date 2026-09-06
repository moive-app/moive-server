package com.moive.MoiveBE.domain.route.client;

import com.moive.MoiveBE.domain.route.dto.KakaoTransitErrorResponse;
import com.moive.MoiveBE.domain.route.dto.KakaoTransitRouteResponse;
import com.moive.MoiveBE.domain.route.dto.Location;
import com.moive.MoiveBE.global.exception.CustomErrorCode;
import com.moive.MoiveBE.global.exception.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Disabled("실제 카카오맵 대중교통 경로 API 연동 확인용 테스트")
class KakaoTransitClientTest {

    private static final String TRANSIT_ROUTE_URL =
            "https://dapi.kakao.com/v2/routing/publictraffic";

    private KakaoTransitClient kakaoTransitClient;

    @BeforeEach
    void setUp() {
        RestClient restClient = RestClient.create();
        KakaoTransitClientImpl clientImpl = new KakaoTransitClientImpl(restClient, new ObjectMapper());

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

    @Test
    void 유효하지_않은_API_키인_경우_커스텀_오류가_발생한다() {
        // given (잘못된 앱 키 사용)
        ReflectionTestUtils.setField(kakaoTransitClient, "apiKey", "invalid-api-key");

        Location start = new Location(37.5788132079661, 126.901364655063);
        Location end = new Location(37.5510324090502, 126.91228338125131);

        // 원본 응답 구조 + DTO 매핑 결과 확인
        printRawErrorResponse(TRANSIT_ROUTE_URL
                + "?start_x=" + start.longitude() + "&start_y=" + start.latitude()
                + "&end_x=" + end.longitude() + "&end_y=" + end.latitude(),
                "invalid-api-key");

        // when & then
        assertThatThrownBy(() -> kakaoTransitClient.getTransitRoute(start, end))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getCustomErrorCode())
                .isEqualTo(CustomErrorCode.KAKAO_MAP_API_CONFIG_ERROR);
    }

    // 카카오 에러 응답 포맷이 KakaoTransitErrorResponse로 매핑이 잘 되는지 확인하기 위한 출력
    private void printRawErrorResponse(String uri, String apiKey) {
        ObjectMapper objectMapper = new ObjectMapper();

        try {
            RestClient.create().get()
                    .uri(uri)
                    .header("Authorization", "KakaoAK " + apiKey)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            String rawBody = e.getResponseBodyAsString();
            System.out.println("- 원본 에러 응답: httpStatus=" + e.getStatusCode() + ", body=" + rawBody);

            try {
                KakaoTransitErrorResponse errorResponse =
                        objectMapper.readValue(rawBody, KakaoTransitErrorResponse.class);
                System.out.println("- DTO 매핑 성공: errorType=" + errorResponse.errorType()
                        + ", message=" + errorResponse.message()
                        + ", details=" + errorResponse.details());
            } catch (Exception parseException) {
                System.out.println("- DTO 매핑 실패: " + parseException.getMessage());
            }
        }
    }

}
