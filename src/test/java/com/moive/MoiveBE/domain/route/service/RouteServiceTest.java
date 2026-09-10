package com.moive.MoiveBE.domain.route.service;

import com.moive.MoiveBE.domain.meeting.entity.Meeting;
import com.moive.MoiveBE.domain.meeting.entity.Participant;
import com.moive.MoiveBE.domain.meeting.entity.ParticipantPreference;
import com.moive.MoiveBE.domain.meeting.repository.MeetingRepository;
import com.moive.MoiveBE.domain.meeting.repository.ParticipantPreferenceRepository;
import com.moive.MoiveBE.domain.meeting.repository.ParticipantRepository;
import com.moive.MoiveBE.domain.recommendation.client.GooglePlacesClient;
import com.moive.MoiveBE.domain.recommendation.dto.GooglePlaceLocationResponse;
import com.moive.MoiveBE.domain.recommendation.entity.RecommendedPlace;
import com.moive.MoiveBE.domain.recommendation.repository.RecommendedPlaceRepository;
import com.moive.MoiveBE.domain.route.client.KakaoTransitClient;
import com.moive.MoiveBE.domain.route.dto.KakaoTransitRouteResponse;
import com.moive.MoiveBE.domain.route.dto.Location;
import com.moive.MoiveBE.domain.route.dto.RouteDetailResponse;
import com.moive.MoiveBE.domain.user.entity.User;
import com.moive.MoiveBE.domain.user.repository.UserRepository;
import com.moive.MoiveBE.global.exception.CustomErrorCode;
import com.moive.MoiveBE.global.exception.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RouteServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long MEETING_ID = 2L;
    private static final Long RECOMMENDED_PLACE_ID = 3L;
    private static final Long PARTICIPANT_ID = 100L;
    private static final String GOOGLE_PLACE_ID = "test-google-place-id";
    private static final String DEPARTURE_NAME = "출발지";
    private static final String PLACE_NAME = "도착지";

    // 출발지(참여자 선호 조건에 저장된 위치) 좌표 예시
    private static final BigDecimal DEPARTURE_LATITUDE = new BigDecimal("37.5788132079661");
    private static final BigDecimal DEPARTURE_LONGITUDE = new BigDecimal("126.901364655063");

    // 도착지(추천 장소) 좌표 예시
    private static final double PLACE_LATITUDE = 37.5510324090502;
    private static final double PLACE_LONGITUDE = 126.91228338125131;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MeetingRepository meetingRepository;

    @Mock
    private ParticipantRepository participantRepository;

    @Mock
    private ParticipantPreferenceRepository participantPreferenceRepository;

    @Mock
    private RecommendedPlaceRepository recommendedPlaceRepository;

    @Mock
    private GooglePlacesClient googlePlacesClient;

    @Mock
    private KakaoTransitClient kakaoTransitClient;

    private RouteService routeService;

    @BeforeEach
    void setUp() {
        RouteDetailService routeDetailService = new RouteDetailService(kakaoTransitClient);
        routeService = new RouteService(
                userRepository,
                meetingRepository,
                participantRepository,
                participantPreferenceRepository,
                recommendedPlaceRepository,
                googlePlacesClient,
                routeDetailService
        );
    }

    @Test
    void 출발지_추천장소_좌표로_대중교통_경로를_조회하고_이동시간을_계산해_반환한다() {
        // given
        stubUserAndMeetingExist();
        stubParticipant(DEPARTURE_LATITUDE, DEPARTURE_LONGITUDE);
        stubRecommendedPlace();
        stubGooglePlaceLocation(PLACE_LATITUDE, PLACE_LONGITUDE);
        // 도보 300초 + 지하철 600초 + 버스 480초, 총 1800초, 요금 1500원
        when(kakaoTransitClient.getTransitRoute(
                any(Location.class), any(Location.class), anyString(), anyString()))
                .thenReturn(okTransitRoute());

        // when
        RouteDetailResponse response = routeService.getRecommendedPlaceRoute(
                USER_ID, MEETING_ID, RECOMMENDED_PLACE_ID);

        // then: 출발지, 도착지 좌표 검증
        assertThat(response.userLocation().latitude()).isEqualTo(DEPARTURE_LATITUDE.doubleValue());
        assertThat(response.userLocation().longitude()).isEqualTo(DEPARTURE_LONGITUDE.doubleValue());
        assertThat(response.placeLocation().latitude()).isEqualTo(PLACE_LATITUDE);
        assertThat(response.placeLocation().longitude()).isEqualTo(PLACE_LONGITUDE);

        // then: 이동 시간(초 -> 분 반올림), walk = 총시간 - (버스 + 지하철)
        assertThat(response.totalTime()).isEqualTo(30);
        assertThat(response.subwayTime()).isEqualTo(10);
        assertThat(response.busTime()).isEqualTo(8);
        assertThat(response.walkTime()).isEqualTo(12);
        assertThat(response.fare()).isEqualTo(1500);
        assertThat(response.landingUrl()).isEqualTo("https://map.kakao.com/route");
    }

    @Test
    void 대중교통_경로가_없으면_TRANSIT_ROUTE_NOT_FOUND_예외가_발생한다() {
        // given
        stubUserAndMeetingExist();
        stubParticipant(DEPARTURE_LATITUDE, DEPARTURE_LONGITUDE);
        stubRecommendedPlace();
        stubGooglePlaceLocation(PLACE_LATITUDE, PLACE_LONGITUDE);
        when(kakaoTransitClient.getTransitRoute(
                any(Location.class), any(Location.class), anyString(), anyString()))
                .thenReturn(new KakaoTransitRouteResponse("NO_RESULTS", null, List.of()));

        // when & then
        assertThatThrownBy(() -> routeService.getRecommendedPlaceRoute(
                USER_ID, MEETING_ID, RECOMMENDED_PLACE_ID))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getCustomErrorCode())
                        .isEqualTo(CustomErrorCode.TRANSIT_ROUTE_NOT_FOUND));
    }

    @Test
    void 카카오_응답의_status가_경로_탐색_불가면_KAKAO_MAP_API_SERVER_ERROR_예외가_발생한다() {
        // given
        stubUserAndMeetingExist();
        stubParticipant(DEPARTURE_LATITUDE, DEPARTURE_LONGITUDE);
        stubRecommendedPlace();
        stubGooglePlaceLocation(PLACE_LATITUDE, PLACE_LONGITUDE);
        when(kakaoTransitClient.getTransitRoute(
                any(Location.class), any(Location.class), anyString(), anyString()))
                .thenReturn(new KakaoTransitRouteResponse("INVALID_REQUEST", null, List.of()));

        // when & then
        assertThatThrownBy(() -> routeService.getRecommendedPlaceRoute(
                USER_ID, MEETING_ID, RECOMMENDED_PLACE_ID))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> {
                    assertThat(((CustomException) e).getCustomErrorCode())
                            .isEqualTo(CustomErrorCode.KAKAO_MAP_API_SERVER_ERROR);
                    assertThat(e.getMessage()).contains("요청 좌표로 경로 탐색 불가");
                });
    }

    @Test
    void 카카오_응답_본문이_비어있으면_KAKAO_MAP_API_SERVER_ERROR_예외가_발생한다() {
        // given
        stubUserAndMeetingExist();
        stubParticipant(DEPARTURE_LATITUDE, DEPARTURE_LONGITUDE);
        stubRecommendedPlace();
        stubGooglePlaceLocation(PLACE_LATITUDE, PLACE_LONGITUDE);
        when(kakaoTransitClient.getTransitRoute(
                any(Location.class), any(Location.class), anyString(), anyString()))
                .thenReturn(null);

        // when & then
        assertThatThrownBy(() -> routeService.getRecommendedPlaceRoute(
                USER_ID, MEETING_ID, RECOMMENDED_PLACE_ID))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> {
                    assertThat(((CustomException) e).getCustomErrorCode())
                            .isEqualTo(CustomErrorCode.KAKAO_MAP_API_SERVER_ERROR);
                    assertThat(e.getMessage()).contains("응답 데이터 규격 확인 필요");
                });
    }

    @Test
    void 유저가_존재하지_않으면_USER_NOT_FOUND_예외가_발생하고_외부_API를_호출하지_않는다() {
        // given
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> routeService.getRecommendedPlaceRoute(
                USER_ID, MEETING_ID, RECOMMENDED_PLACE_ID))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getCustomErrorCode())
                        .isEqualTo(CustomErrorCode.USER_NOT_FOUND));

        verifyNoInteractions(googlePlacesClient, kakaoTransitClient);
    }

    private void stubUserAndMeetingExist() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(mock(User.class)));
        when(meetingRepository.findById(MEETING_ID)).thenReturn(Optional.of(mock(Meeting.class)));
    }

    private void stubParticipant(BigDecimal latitude, BigDecimal longitude) {
        Participant participant = mock(Participant.class);
        when(participant.getId()).thenReturn(PARTICIPANT_ID);
        when(participantRepository.findByMeetingIdAndUserId(MEETING_ID, USER_ID))
                .thenReturn(Optional.of(participant));

        ParticipantPreference preference = mock(ParticipantPreference.class);
        lenient().when(preference.getDepartureLatitude()).thenReturn(latitude);
        lenient().when(preference.getDepartureLongitude()).thenReturn(longitude);
        lenient().when(preference.getDepartureName()).thenReturn(DEPARTURE_NAME);
        when(participantPreferenceRepository.findByParticipantId(PARTICIPANT_ID))
                .thenReturn(Optional.of(preference));
    }

    private void stubRecommendedPlace() {
        when(recommendedPlaceRepository.findById(RECOMMENDED_PLACE_ID))
                .thenReturn(Optional.of(RecommendedPlace.create(10L, GOOGLE_PLACE_ID, 2)));
    }

    private void stubGooglePlaceLocation(double latitude, double longitude) {
        when(googlePlacesClient.getPlaceLocation(GOOGLE_PLACE_ID))
                .thenReturn(new GooglePlaceLocationResponse(
                        new GooglePlaceLocationResponse.LocalizedText(PLACE_NAME, "ko"),
                        null,
                        null,
                        new GooglePlaceLocationResponse.Location(latitude, longitude)
                ));
    }

    /**
     * status=OK, 3개 step(도보 300초 / 지하철 600초 / 버스 480초), 총 1800초, 요금 1500원 응답.
     * path 좌표는 [경도, 위도] 순서(카카오 규격)
     */
    private KakaoTransitRouteResponse okTransitRoute() {
        KakaoTransitRouteResponse.Step walk = transitStep("WALKING", 300,
                new Double[][]{{126.9014, 37.5788}, {126.9020, 37.5780}});
        KakaoTransitRouteResponse.Step subway = transitStep("SUBWAY", 600,
                new Double[][]{{126.9020, 37.5780}, {126.9100, 37.5600}});
        KakaoTransitRouteResponse.Step bus = transitStep("BUS", 480,
                new Double[][]{{126.9100, 37.5600}, {126.9123, 37.5510}});

        KakaoTransitRouteResponse.RouteProperties routeProperties =
                new KakaoTransitRouteResponse.RouteProperties(
                        "TRANSIT", 5000, 1800, 1,
                        new KakaoTransitRouteResponse.Fare(1500, 1500, 1500));

        KakaoTransitRouteResponse.Route route =
                new KakaoTransitRouteResponse.Route(routeProperties, List.of(walk, subway, bus));

        KakaoTransitRouteResponse.Properties properties =
                new KakaoTransitRouteResponse.Properties(1800, 480, 600, 1080, "https://map.kakao.com/route");

        return new KakaoTransitRouteResponse("OK", properties, List.of(route));
    }

    private KakaoTransitRouteResponse.Step transitStep(String type, int timeSeconds, Double[][] points) {
        KakaoTransitRouteResponse.StepProperties stepProperties =
                new KakaoTransitRouteResponse.StepProperties(
                        "guidance", type, 1000, timeSeconds, List.of(), List.of());
        return new KakaoTransitRouteResponse.Step(stepProperties, new KakaoTransitRouteResponse.Path(points));
    }
}
