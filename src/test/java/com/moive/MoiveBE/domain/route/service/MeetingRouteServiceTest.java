package com.moive.MoiveBE.domain.route.service;

import com.moive.MoiveBE.domain.meeting.entity.Meeting;
import com.moive.MoiveBE.domain.meeting.entity.MeetingStatus;
import com.moive.MoiveBE.domain.meeting.entity.Participant;
import com.moive.MoiveBE.domain.meeting.entity.ParticipantPreference;
import com.moive.MoiveBE.domain.meeting.repository.MeetingRepository;
import com.moive.MoiveBE.domain.meeting.repository.ParticipantPreferenceRepository;
import com.moive.MoiveBE.domain.meeting.repository.ParticipantRepository;
import com.moive.MoiveBE.domain.recommendation.client.GooglePlacesClient;
import com.moive.MoiveBE.domain.recommendation.dto.GooglePlaceLocationResponse;
import com.moive.MoiveBE.domain.recommendation.entity.RecommendedPlace;
import com.moive.MoiveBE.domain.recommendation.repository.RecommendedPlaceRepository;
import com.moive.MoiveBE.domain.route.dto.MeetingDetailResponse;
import com.moive.MoiveBE.domain.route.dto.RouteDetail;
import com.moive.MoiveBE.domain.user.entity.User;
import com.moive.MoiveBE.domain.user.repository.UserRepository;
import com.moive.MoiveBE.global.exception.CustomErrorCode;
import com.moive.MoiveBE.global.exception.CustomException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static com.moive.MoiveBE.global.exception.CustomErrorCode.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MeetingRouteServiceTest {

    private static final Long MEETING_ID = 1L;
    private static final Long USER_ID = 10L;
    private static final Long CONFIRMED_PLACE_ID = 100L;
    private static final String GOOGLE_PLACE_ID = "google-place-id";

    private static final LocalDate PAST_DATE = LocalDate.of(2025, 1, 1);    // 모임 일시 경과 => COMPLETED
    private static final LocalDate FUTURE_DATE = LocalDate.of(2027, 1, 1);  // 모임 일시 이전 => CONFIRMED
    private static final LocalTime SCHEDULED_TIME = LocalTime.of(18, 0);

    @Mock private MeetingRepository meetingRepository;
    @Mock private ParticipantRepository participantRepository;
    @Mock private ParticipantPreferenceRepository participantPreferenceRepository;
    @Mock private UserRepository userRepository;
    @Mock private RecommendedPlaceRepository recommendedPlaceRepository;
    @Mock private GooglePlacesClient googlePlacesClient;
    @Mock private RouteDetailService routeDetailService;

    @InjectMocks private MeetingRouteService meetingRouteService;

    /**
     * 모임 상세 조회 가능 조건 검증
     */

    @Test
    void 존재하지_않는_모임이면_MEETING_NOT_FOUND() {
        // given
        when(meetingRepository.findById(MEETING_ID)).thenReturn(Optional.empty());

        // when & then
        assertErrorCode(() -> meetingRouteService.getMeetingDetail(MEETING_ID, USER_ID), MEETING_NOT_FOUND);
        verifyNoInteractions(participantRepository, recommendedPlaceRepository, googlePlacesClient, routeDetailService);
    }

    @Test
    void 확정_전_모임이면_MEETING_NOT_CONFIRMED() {
        // given
        Meeting meeting = meeting(MeetingStatus.VOTING, CONFIRMED_PLACE_ID, false);
        when(meetingRepository.findById(MEETING_ID)).thenReturn(Optional.of(meeting));

        // when & then
        assertErrorCode(() -> meetingRouteService.getMeetingDetail(MEETING_ID, USER_ID), MEETING_NOT_CONFIRMED);
        // 참여자 검증까지 가지 않음
        verifyNoInteractions(participantRepository, recommendedPlaceRepository, googlePlacesClient, routeDetailService);
    }

    @Test
    void 유저가_모임_참여자가_아니면_MEETING_ACCESS_DENIED() {
        // given
        Meeting meeting = meeting(MeetingStatus.CONFIRMED, CONFIRMED_PLACE_ID, false);
        when(meetingRepository.findById(MEETING_ID)).thenReturn(Optional.of(meeting));
        when(participantRepository.findByMeetingIdAndUserIdAndLeftAtIsNull(MEETING_ID, USER_ID))
                .thenReturn(Optional.empty());

        // when & then
        assertErrorCode(() -> meetingRouteService.getMeetingDetail(MEETING_ID, USER_ID), MEETING_ACCESS_DENIED);
        verifyNoInteractions(recommendedPlaceRepository, googlePlacesClient, routeDetailService);
    }

    /**
     * status(응답) = 모임 종료 여부(모임 일시 vs 현재 시각) x 최종 장소 확정 여부 조합
     *
     * 1. CONFIRMED (모임 일시 이전)
     *   - 장소 확정 (confirmedPlaceId != null) : 장소 정보 + 참여자 이동 정보 반환
     *   - 장소 미확정 (confirmedPlaceId == null) : place null, participants 빈 배열([])
     *
     * 2. COMPLETED (모임 일시 경과)
     *   - 장소 확정 (confirmedPlaceId != null) : 장소 정보 반환, 이동 정보는 null
     *   - 장소 미확정 (confirmedPlaceId == null) : place null, 이동 정보는 null
     */

    @Test
    void 모임_일시가_지나지_않았으면_status는_CONFIRMED이다() {
        // given: 모임 일시가 미래 (전원 미투표 케이스 가정)
        stubMeetingAndAccess(meeting(MeetingStatus.CONFIRMED, null, false));

        // when
        MeetingDetailResponse response = meetingRouteService.getMeetingDetail(MEETING_ID, USER_ID);

        // then
        assertThat(response.status()).isEqualTo("CONFIRMED");
        assertThat(response.place()).isNull();
        assertThat(response.participants()).isEmpty();
        assertThat(response.meetingDate()).isEqualTo(FUTURE_DATE.toString());
        assertThat(response.meetingTime()).isEqualTo("18:00");

        // 장소/참여자/이동 조회 진행 x
        verifyNoInteractions(recommendedPlaceRepository, googlePlacesClient, routeDetailService);
        verify(participantRepository, never()).findAllByMeetingIdAndLeftAtIsNullOrderByJoinedAtAsc(any());
    }

    @Test
    void 모임_일시가_지났으면_엔티티_status가_COMPLETED가_아니어도_응답_status는_COMPLETED이다() {
        // given: CONFIRMED + 모임 진행 후 (엔티티 status는 아직 CONFIRMED)
        stubMeetingAndAccess(meeting(MeetingStatus.CONFIRMED, null, true));
        stubParticipants(
                List.of(participant(1L, 11L)),
                List.of(user(11L, "lee", "img-url-1")),
                List.of(preference(1L, "경기도 고양시"))
        );

        // when
        MeetingDetailResponse response = meetingRouteService.getMeetingDetail(MEETING_ID, USER_ID);

        // then
        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.participants()).hasSize(1);
        assertThat(response.participants().get(0).transferCnt()).isNull(); // 종료 => 이동 정보 없음

        verifyNoInteractions(routeDetailService);
    }

    @Test
    void CONFIRMED_전원미투표면_place는_null이고_participants는_빈_배열이다() {
        // given: 모임 진행 전 + confirmedPlaceId 없음(전원 미투표)
        stubMeetingAndAccess(meeting(MeetingStatus.CONFIRMED, null, false));

        // when
        MeetingDetailResponse response = meetingRouteService.getMeetingDetail(MEETING_ID, USER_ID);

        // then
        assertThat(response.status()).isEqualTo("CONFIRMED");
        assertThat(response.place()).isNull();
        assertThat(response.participants()).isEmpty();

        // 장소/참여자/이동 조회 진행 x
        verifyNoInteractions(recommendedPlaceRepository, googlePlacesClient, routeDetailService);
        verify(participantRepository, never()).findAllByMeetingIdAndLeftAtIsNullOrderByJoinedAtAsc(any());
    }

    @Test
    void CONFIRMED_장소확정_구글맵조회가_성공이면_place와_참여자별_이동정보가_모두_채워진다() {
        // given: 모임 진행 전 + 장소 확정 + 구글 조회 성공 + 참여자 2명
        stubMeetingAndAccess(meeting(MeetingStatus.CONFIRMED, CONFIRMED_PLACE_ID, false));
        stubRecommendedPlace();
        when(googlePlacesClient.getPlaceLocation(GOOGLE_PLACE_ID)).thenReturn(googlePlace());

        stubParticipants(
                List.of(participant(1L, 11L), participant(2L, 12L)),
                List.of(user(11L, "lee", "img-url-1"), user(12L, "kim", "img-url-2")),
                List.of(preference(1L, "경기도 고양시"), preference(2L, "경기도 수원시"))
        );
        when(routeDetailService.getMyRouteDetail(any(), any(), any(), any()))
                .thenReturn(routeDetail(50, 0))
                .thenReturn(routeDetail(70, 1));

        // when
        MeetingDetailResponse response = meetingRouteService.getMeetingDetail(MEETING_ID, USER_ID);

        // then
        assertThat(response.status()).isEqualTo("CONFIRMED");
        assertThat(response.place()).isNotNull();
        assertThat(response.place().id()).isEqualTo(CONFIRMED_PLACE_ID);
        assertThat(response.place().isFetchFailed()).isFalse();
        assertThat(response.place().name()).isEqualTo("XXX 맛집");
        assertThat(response.place().category()).isEqualTo("일식");
        assertThat(response.place().location().latitude()).isEqualTo(37.5510324090502);

        assertThat(response.participants()).hasSize(2);
        // 응답 순서는 참여자 조회 순서(joinedAt) 유지
        assertThat(response.participants().get(0).nickname()).isEqualTo("lee");
        assertThat(response.participants().get(0).address()).isEqualTo("경기도 고양시");
        assertThat(response.participants().get(0).transferCnt()).isEqualTo(0);
        assertThat(response.participants().get(0).totalTime()).isEqualTo(50);
        assertThat(response.participants().get(1).nickname()).isEqualTo("kim");
        assertThat(response.participants().get(1).transferCnt()).isEqualTo(1);
        assertThat(response.participants().get(1).totalTime()).isEqualTo(70);

        // 참여자가 2명이어도 유저/선호 조건 조회는 IN 배치로 1번씩, 카카오맵 API 호출은 참여자 수만큼
        verify(userRepository, times(1)).findAllById(any());
        verify(participantPreferenceRepository, times(1)).findAllByParticipantIdIn(anyList());
        verify(routeDetailService, times(2)).getMyRouteDetail(any(), any(), any(), any());
    }

    @Test
    void CONFIRMED_장소확정_구글맵조회가_예외로_실패하면_place는_isFetchFailed이고_이동정보는_null이다() {
        // given: 모임 진행 전 + 장소 확정 + 구글 조회 실패
        stubMeetingAndAccess(meeting(MeetingStatus.CONFIRMED, CONFIRMED_PLACE_ID, false));
        stubRecommendedPlace();
        when(googlePlacesClient.getPlaceLocation(GOOGLE_PLACE_ID))
                .thenThrow(new CustomException(PLACE_INFO_LOOKUP_FAILED));

        stubParticipants(
                List.of(participant(1L, 11L)),
                List.of(user(11L, "lee", "img-url-1")),
                List.of(preference(1L, "경기도 고양시"))
        );

        // when
        MeetingDetailResponse response = meetingRouteService.getMeetingDetail(MEETING_ID, USER_ID);

        // then
        assertThat(response.place()).isNotNull();
        assertThat(response.place().id()).isEqualTo(CONFIRMED_PLACE_ID);
        assertThat(response.place().isFetchFailed()).isTrue();
        assertThat(response.place().name()).isNull();
        assertThat(response.place().location()).isNull();

        assertThat(response.participants()).hasSize(1);
        assertThat(response.participants().get(0).transferCnt()).isNull();
        assertThat(response.participants().get(0).totalTime()).isNull();

        // 장소 조회에 실패했으므로 이동 정보 조회를 위한 카카오맵 API 호출 x
        verifyNoInteractions(routeDetailService);
    }

    @Test
    void CONFIRMED_장소확정_구글맵조회_응답이_비어있으면_place는_isFetchFailed이다() {
        // given: 모임 진행 전 + 장소 확정, 구글 응답 null(빈 응답)
        stubMeetingAndAccess(meeting(MeetingStatus.CONFIRMED, CONFIRMED_PLACE_ID, false));
        stubRecommendedPlace();
        when(googlePlacesClient.getPlaceLocation(GOOGLE_PLACE_ID)).thenReturn(null);

        stubParticipants(
                List.of(participant(1L, 11L)),
                List.of(user(11L, "lee", "img-url-1")),
                List.of(preference(1L, "경기도 고양시"))
        );

        // when
        MeetingDetailResponse response = meetingRouteService.getMeetingDetail(MEETING_ID, USER_ID);

        // then
        assertThat(response.place().isFetchFailed()).isTrue();
        assertThat(response.participants().get(0).totalTime()).isNull();
        verifyNoInteractions(routeDetailService);
    }

    @Test
    void COMPLETED_장소확정이면_status는_COMPLETED이고_이동정보는_null이며_카카오맵을_호출하지_않는다() {
        // given: 모임 진행 후 + 장소 확정 + 구글 조회 성공
        stubMeetingAndAccess(meeting(MeetingStatus.CONFIRMED, CONFIRMED_PLACE_ID, true));
        stubRecommendedPlace();
        when(googlePlacesClient.getPlaceLocation(GOOGLE_PLACE_ID)).thenReturn(googlePlace());

        stubParticipants(
                List.of(participant(1L, 11L)),
                List.of(user(11L, "lee", "img-url-1")),
                List.of(preference(1L, "경기도 고양시"))
        );

        // when
        MeetingDetailResponse response = meetingRouteService.getMeetingDetail(MEETING_ID, USER_ID);

        // then
        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.place()).isNotNull();
        assertThat(response.place().location()).isNotNull(); // 종료돼도 장소 정보 자체는 제공
        assertThat(response.participants()).hasSize(1);
        assertThat(response.participants().get(0).transferCnt()).isNull();
        assertThat(response.participants().get(0).totalTime()).isNull();

        verifyNoInteractions(routeDetailService);
    }

    @Test
    void COMPLETED_전원미투표면_place는_null이지만_participants는_목록을_반환한다() {
        // given: 모임 진행 후 + confirmedPlaceId 없음(전원 미투표)
        stubMeetingAndAccess(meeting(MeetingStatus.CONFIRMED, null, true));
        stubParticipants(
                List.of(participant(1L, 11L), participant(2L, 12L)),
                List.of(user(11L, "lee", "img-url-1"), user(12L, "kim", "img-url-2")),
                List.of(preference(1L, "경기도 고양시"), preference(2L, "경기도 수원시"))
        );

        // when
        MeetingDetailResponse response = meetingRouteService.getMeetingDetail(MEETING_ID, USER_ID);

        // then
        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.place()).isNull();
        assertThat(response.participants()).hasSize(2); // 빈 배열 아님
        assertThat(response.participants().get(0).transferCnt()).isNull();
        assertThat(response.participants().get(0).totalTime()).isNull();

        verifyNoInteractions(recommendedPlaceRepository, googlePlacesClient, routeDetailService);
    }

    /**
     * 이동 정보 조회에 실패하는 경우
     */

    @Test
    void 일부_참여자만_카카오맵_조회에_실패하면_그_참여자만_이동정보가_null이고_나머지는_유지된다() {
        // given: 모임 진행 전 + 장소 확정, 2번 참여자의 카카오맵 API 조회만 예외 발생
        stubMeetingAndAccess(meeting(MeetingStatus.CONFIRMED, CONFIRMED_PLACE_ID, false));
        stubRecommendedPlace();
        when(googlePlacesClient.getPlaceLocation(GOOGLE_PLACE_ID)).thenReturn(googlePlace());

        stubParticipants(
                List.of(participant(1L, 11L), participant(2L, 12L)),
                List.of(user(11L, "lee", "img-1"), user(12L, "kim", "img-2")),
                List.of(preference(1L, "경기도 고양시"), preference(2L, "대중교통 사각지대"))
        );
        when(routeDetailService.getMyRouteDetail(any(), any(), any(), any()))
                .thenReturn(routeDetail(50, 0))
                .thenThrow(new CustomException(TRANSIT_ROUTE_NOT_FOUND));

        // when
        MeetingDetailResponse response = meetingRouteService.getMeetingDetail(MEETING_ID, USER_ID);

        // then
        assertThat(response.participants()).hasSize(2);
        // 1번 참여자: 정상 계산
        assertThat(response.participants().get(0).nickname()).isEqualTo("lee");
        assertThat(response.participants().get(0).transferCnt()).isEqualTo(0);
        assertThat(response.participants().get(0).totalTime()).isEqualTo(50);
        // 2번 참여자: 경로 조회 실패 -> 이동 정보만 null, 참여자 자체는 응답에 포함
        assertThat(response.participants().get(1).nickname()).isEqualTo("kim");
        assertThat(response.participants().get(1).address()).isEqualTo("대중교통 사각지대");
        assertThat(response.participants().get(1).transferCnt()).isNull();
        assertThat(response.participants().get(1).totalTime()).isNull();
    }

    /**
     * Fixtures
     */

    private void assertErrorCode(ThrowingCallable callable, CustomErrorCode expected) {
        assertThatThrownBy(callable)
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getCustomErrorCode())
                .isEqualTo(expected);
    }

    private void stubMeetingAndAccess(Meeting meeting) {
        when(meetingRepository.findById(MEETING_ID)).thenReturn(Optional.of(meeting));
        when(participantRepository.findByMeetingIdAndUserIdAndLeftAtIsNull(MEETING_ID, USER_ID))
                .thenReturn(Optional.of(mock(Participant.class)));
    }

    private void stubRecommendedPlace() {
        when(recommendedPlaceRepository.findById(CONFIRMED_PLACE_ID))
                .thenReturn(Optional.of(RecommendedPlace.create(1L, GOOGLE_PLACE_ID, "일식", 2)));
    }

    private void stubParticipants(
            List<Participant> participants, List<User> users, List<ParticipantPreference> preferences
    ) {
        when(participantRepository.findAllByMeetingIdAndLeftAtIsNullOrderByJoinedAtAsc(MEETING_ID))
                .thenReturn(participants);
        when(userRepository.findAllById(any())).thenReturn(users);
        when(participantPreferenceRepository.findAllByParticipantIdIn(anyList())).thenReturn(preferences);
    }

    // ended=true면 모임 일시를 과거로(=> COMPLETED), false면 미래로(=> CONFIRMED) 설정
    private Meeting meeting(MeetingStatus status, Long confirmedPlaceId, boolean ended) {
        Meeting meeting = mock(Meeting.class);
        lenient().when(meeting.getStatus()).thenReturn(status);
        lenient().when(meeting.getConfirmedPlaceId()).thenReturn(confirmedPlaceId);
        lenient().when(meeting.getScheduledDate()).thenReturn(ended ? PAST_DATE : FUTURE_DATE);
        lenient().when(meeting.getScheduledTime()).thenReturn(SCHEDULED_TIME);
        return meeting;
    }

    private Participant participant(Long id, Long userId) {
        Participant participant = mock(Participant.class);
        lenient().when(participant.getId()).thenReturn(id);
        lenient().when(participant.getUserId()).thenReturn(userId);
        return participant;
    }

    private User user(Long id, String nickname, String profileImageUrl) {
        User user = mock(User.class);
        lenient().when(user.getId()).thenReturn(id);
        lenient().when(user.getNickname()).thenReturn(nickname);
        lenient().when(user.getProfileImageUrl()).thenReturn(profileImageUrl);
        return user;
    }

    private ParticipantPreference preference(Long participantId, String departureName) {
        ParticipantPreference preference = mock(ParticipantPreference.class);
        lenient().when(preference.getParticipantId()).thenReturn(participantId);
        lenient().when(preference.getDepartureName()).thenReturn(departureName);
        lenient().when(preference.getDepartureLatitude()).thenReturn(new BigDecimal("37.5788132079661"));
        lenient().when(preference.getDepartureLongitude()).thenReturn(new BigDecimal("126.901364655063"));
        return preference;
    }

    private GooglePlaceLocationResponse googlePlace() {
        return new GooglePlaceLocationResponse(
                new GooglePlaceLocationResponse.LocalizedText("XXX 맛집", "ko"),
                new GooglePlaceLocationResponse.LocalizedText("레스토랑", "ko"),
                "서울 마포구 포은로2나길 44",
                new GooglePlaceLocationResponse.Location(37.5510324090502, 126.91228338125131)
        );
    }

    private RouteDetail routeDetail(int totalTime, int transferCnt) {
        return RouteDetail.builder()
                .pathPoints(List.of())
                .totalTime(totalTime)
                .transferCnt(transferCnt)
                .build();
    }
}
