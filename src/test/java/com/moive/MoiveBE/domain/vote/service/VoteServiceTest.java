package com.moive.MoiveBE.domain.vote.service;

import com.moive.MoiveBE.domain.meeting.entity.Meeting;
import com.moive.MoiveBE.domain.meeting.entity.MeetingStatus;
import com.moive.MoiveBE.domain.meeting.entity.Participant;
import com.moive.MoiveBE.domain.meeting.entity.ParticipantPreference;
import com.moive.MoiveBE.domain.meeting.repository.DateVoteRepository;
import com.moive.MoiveBE.domain.meeting.repository.MeetingRepository;
import com.moive.MoiveBE.domain.meeting.repository.ParticipantPreferenceRepository;
import com.moive.MoiveBE.domain.meeting.repository.ParticipantRepository;
import com.moive.MoiveBE.domain.recommendation.client.GooglePlacesClient;
import com.moive.MoiveBE.domain.recommendation.dto.GooglePlaceLocationResponse;
import com.moive.MoiveBE.domain.recommendation.entity.RecommendationRun;
import com.moive.MoiveBE.domain.recommendation.entity.RecommendationStatus;
import com.moive.MoiveBE.domain.recommendation.entity.RecommendedPlace;
import com.moive.MoiveBE.domain.recommendation.repository.RecommendationRunRepository;
import com.moive.MoiveBE.domain.recommendation.repository.RecommendedPlaceRepository;
import com.moive.MoiveBE.domain.recommendation.service.AreaDistanceService;
import com.moive.MoiveBE.domain.vote.dto.DateVoteSummary;
import com.moive.MoiveBE.domain.vote.dto.DateVoteResultResponse;
import com.moive.MoiveBE.domain.vote.dto.PlaceVoteRequest;
import com.moive.MoiveBE.domain.vote.dto.PlaceVoteResultResponse;
import com.moive.MoiveBE.domain.vote.dto.PlaceVoteSummary;
import com.moive.MoiveBE.domain.vote.entity.PlaceVote;
import com.moive.MoiveBE.domain.vote.repository.PlaceVoteRepository;
import com.moive.MoiveBE.global.exception.CustomErrorCode;
import com.moive.MoiveBE.global.exception.CustomException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static com.moive.MoiveBE.global.exception.CustomErrorCode.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VoteServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long MEETING_ID = 10L;
    private static final Long MY_PARTICIPANT_ID = 100L;

    private static final Long RECOMMENDATION_RUN_ID = 1000L;

    @Mock private MeetingRepository meetingRepository;
    @Mock private ParticipantRepository participantRepository;
    @Mock private ParticipantPreferenceRepository participantPreferenceRepository;
    @Mock private DateVoteRepository dateVoteRepository;
    @Mock private PlaceVoteRepository placeVoteRepository;
    @Mock private RecommendationRunRepository recommendationRunRepository;
    @Mock private RecommendedPlaceRepository recommendedPlaceRepository;
    @Mock private GooglePlacesClient googlePlacesClient;
    @Mock private AreaDistanceService areaDistanceService;

    @InjectMocks private VoteService voteService;

    /**
     * [일정 투표 현황 조회] 테스트
     */

    @Test
    void 일정_투표_현황_조회시_존재하지_않는_모임이면_MEETING_NOT_FOUND() {
        // given
        when(meetingRepository.findById(MEETING_ID)).thenReturn(Optional.empty());

        // when & then
        assertErrorCode(() -> voteService.getMeetingScheduleVoteResult(USER_ID, MEETING_ID), MEETING_NOT_FOUND);
        verifyNoInteractions(participantRepository, dateVoteRepository);
    }

    @Test
    void 유저가_모임_참여자가_아니면_VOTE_ACCESS_DENIED() {
        // given
        when(meetingRepository.findById(MEETING_ID)).thenReturn(Optional.of(mock(Meeting.class)));
        when(participantRepository.findByMeetingIdAndUserIdAndLeftAtIsNull(MEETING_ID, USER_ID))
                .thenReturn(Optional.empty());

        // when & then
        assertErrorCode(() -> voteService.getMeetingScheduleVoteResult(USER_ID, MEETING_ID), VOTE_ACCESS_DENIED);
        verifyNoInteractions(dateVoteRepository);
    }

    @Test
    void 모임_생성_시_일정이_확정된_경우_투표가_생략되고_확정_일정_1건만_반환한다() {
        // given
        LocalDate scheduledDate = LocalDate.of(2026, 10, 10);
        LocalTime scheduledTime = LocalTime.of(18, 0);
        Meeting meeting = mock(Meeting.class);
        when(meeting.getScheduledDate()).thenReturn(scheduledDate);
        when(meeting.getScheduledTime()).thenReturn(scheduledTime);
        stubMeetingAndParticipant(meeting);

        // when
        DateVoteResultResponse response = voteService.getMeetingScheduleVoteResult(USER_ID, MEETING_ID);

        // then
        assertThat(response.isVoteSkipped()).isTrue();
        assertThat(response.totalVoterCnt()).isNull();
        assertThat(response.candidates()).hasSize(1);

        DateVoteResultResponse.Candidate candidate = response.candidates().get(0);
        assertThat(candidate.meetingDate()).isEqualTo(scheduledDate);
        assertThat(candidate.meetingTime()).isEqualTo(scheduledTime);
        assertThat(candidate.voterCnt()).isNull();
        assertThat(candidate.isVotedByMe()).isFalse();

        // 투표 집계 진행 x
        verifyNoInteractions(dateVoteRepository);
    }

    @Test
    void 일정_미확정이면_집계_결과를_순서_그대로_candidate로_매핑하고_상위_3건을_요청한다() {
        // given
        Meeting meeting = mock(Meeting.class);
        stubMeetingAndParticipant(meeting, MY_PARTICIPANT_ID);

        when(dateVoteRepository.countDistinctVoters(MEETING_ID)).thenReturn(4L);
        when(dateVoteRepository.aggregateTopDates(eq(MEETING_ID), eq(MY_PARTICIPANT_ID), any()))
                .thenReturn(List.of(
                        new DateVoteSummary(LocalDate.of(2026, 9, 15), LocalTime.of(18, 0), 4L, 1L),
                        new DateVoteSummary(LocalDate.of(2026, 9, 12), LocalTime.of(15, 0), 2L, 0L),
                        new DateVoteSummary(LocalDate.of(2026, 9, 13), LocalTime.of(14, 0), 2L, null)
                ));

        // when
        DateVoteResultResponse response = voteService.getMeetingScheduleVoteResult(USER_ID, MEETING_ID);

        // then
        assertThat(response.isVoteSkipped()).isFalse();
        assertThat(response.totalVoterCnt()).isEqualTo(4);
        assertThat(response.candidates()).extracting(
                DateVoteResultResponse.Candidate::meetingDate,
                DateVoteResultResponse.Candidate::meetingTime,
                DateVoteResultResponse.Candidate::voterCnt,
                DateVoteResultResponse.Candidate::isVotedByMe
        ).containsExactly(
                tuple(LocalDate.of(2026, 9, 15), LocalTime.of(18, 0), 4, true),
                tuple(LocalDate.of(2026, 9, 12), LocalTime.of(15, 0), 2, false),
                tuple(LocalDate.of(2026, 9, 13), LocalTime.of(14, 0), 2, false)
        );

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(dateVoteRepository).aggregateTopDates(eq(MEETING_ID), eq(MY_PARTICIPANT_ID), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(3);
    }

    @Test
    void 일정_미확정_모임에_투표가_없으면_totalVoterCnt는_0이고_candidates는_빈_리스트다() {
        // given
        Meeting meeting = mock(Meeting.class);
        stubMeetingAndParticipant(meeting);
        when(dateVoteRepository.countDistinctVoters(MEETING_ID)).thenReturn(0L);
        when(dateVoteRepository.aggregateTopDates(any(), any(), any())).thenReturn(List.of());

        // when
        DateVoteResultResponse response = voteService.getMeetingScheduleVoteResult(USER_ID, MEETING_ID);

        // then
        assertThat(response.isVoteSkipped()).isFalse();
        assertThat(response.totalVoterCnt()).isEqualTo(0);
        assertThat(response.candidates()).isEmpty();
    }

    /**
     * [장소 투표] 테스트
     */

    @Test
    void 이미_장소가_확정된_모임이면_PLACE_VOTE_CLOSED() {
        // given
        Meeting meeting = meetingWithStatus(MeetingStatus.CONFIRMED);
        when(meetingRepository.findById(MEETING_ID)).thenReturn(Optional.of(meeting));

        // when & then
        assertErrorCode(
                () -> voteService.createPlaceVote(USER_ID, MEETING_ID, placeVoteRequest(101L)),
                PLACE_VOTE_CLOSED
        );
        verifyNoInteractions(participantRepository, placeVoteRepository,
                recommendationRunRepository, recommendedPlaceRepository);
    }

    @Test
    void 조건_입력_중인_모임이면_PLACE_VOTE_NOT_STARTED() {
        // given
        Meeting meeting = meetingWithStatus(MeetingStatus.CONDITION_INPUT);
        when(meetingRepository.findById(MEETING_ID)).thenReturn(Optional.of(meeting));

        // when & then
        assertErrorCode(
                () -> voteService.createPlaceVote(USER_ID, MEETING_ID, placeVoteRequest(101L)),
                PLACE_VOTE_NOT_STARTED
        );
        verifyNoInteractions(participantRepository, placeVoteRepository,
                recommendationRunRepository, recommendedPlaceRepository);
    }

    @Test
    void 장소_추천이_실행되지_않았다면_PLACE_VOTE_NOT_STARTED() {
        // given
        Meeting meeting = meetingWithStatus(MeetingStatus.VOTING);
        when(meetingRepository.findById(MEETING_ID)).thenReturn(Optional.of(meeting));
        when(recommendationRunRepository.findTopByMeetingIdAndStatusOrderByCreatedAtDesc(MEETING_ID, RecommendationStatus.COMPLETED))
                .thenReturn(Optional.empty());

        // when & then
        assertErrorCode(
                () -> voteService.createPlaceVote(USER_ID, MEETING_ID, placeVoteRequest(101L)),
                PLACE_VOTE_NOT_STARTED
        );
        verifyNoInteractions(participantRepository, placeVoteRepository);
    }

    @Test
    void 장소_추천은_실행되었지만_추천_장소가_없으면_PLACE_VOTE_NOT_STARTED() {
        // given
        Meeting meeting = meetingWithStatus(MeetingStatus.VOTING);
        stubMeetingAndRecommendation(meeting);
        stubParticipant();
        when(placeVoteRepository.existsByMeetingIdAndParticipantId(MEETING_ID, MY_PARTICIPANT_ID)).thenReturn(false);

        // when & then
        assertErrorCode(
                () -> voteService.createPlaceVote(USER_ID, MEETING_ID, placeVoteRequest(101L)),
                PLACE_VOTE_NOT_STARTED
        );
        verify(placeVoteRepository, never()).saveAll(anyList());
    }

    @Test
    void 유저가_모임_참여자가_아니면_PLACE_VOTE_ACCESS_DENIED() {
        // given
        Meeting meeting = meetingWithStatus(MeetingStatus.VOTING);
        stubMeetingAndRecommendation(meeting, 101L, 102L);
        when(participantRepository.findByMeetingIdAndUserIdAndLeftAtIsNull(MEETING_ID, USER_ID))
                .thenReturn(Optional.empty());

        // when & then
        assertErrorCode(
                () -> voteService.createPlaceVote(USER_ID, MEETING_ID, placeVoteRequest(101L)),
                PLACE_VOTE_ACCESS_DENIED
        );
        verifyNoInteractions(placeVoteRepository);
    }

    @Test
    void 이미_투표한_참여자면_PLACE_VOTE_ALREADY_DONE() {
        // given
        Meeting meeting = meetingWithStatus(MeetingStatus.VOTING);
        stubMeetingAndRecommendation(meeting, 101L, 102L);
        stubParticipant();
        when(placeVoteRepository.existsByMeetingIdAndParticipantId(MEETING_ID, MY_PARTICIPANT_ID)).thenReturn(true);

        // when & then
        assertErrorCode(
                () -> voteService.createPlaceVote(USER_ID, MEETING_ID, placeVoteRequest(101L)),
                PLACE_VOTE_ALREADY_DONE
        );
        verify(placeVoteRepository, never()).saveAll(anyList());
    }

    @Test
    void 이_모임의_추천_장소가_아닌_id가_포함되면_PLACE_VOTE_INVALID_PLACE() {
        // given
        Meeting meeting = meetingWithStatus(MeetingStatus.VOTING);
        stubMeetingAndRecommendation(meeting, 101L, 102L);
        stubParticipant();
        when(placeVoteRepository.existsByMeetingIdAndParticipantId(MEETING_ID, MY_PARTICIPANT_ID)).thenReturn(false);

        // when & then: 999L은 이 모임의 추천 장소 목록(101L, 102L)에 없음
        assertErrorCode(
                () -> voteService.createPlaceVote(USER_ID, MEETING_ID, placeVoteRequest(101L, 999L)),
                PLACE_VOTE_INVALID_PLACE
        );
        verify(placeVoteRepository, never()).saveAll(anyList());
    }

    @Test
    void 장소_투표_요청에_null이_섞여있어도_NPE없이_PLACE_VOTE_INVALID_PLACE로_처리된다() {
        // given
        Meeting meeting = meetingWithStatus(MeetingStatus.VOTING);
        stubMeetingAndRecommendation(meeting, 101L, 102L);
        stubParticipant();
        when(placeVoteRepository.existsByMeetingIdAndParticipantId(MEETING_ID, MY_PARTICIPANT_ID)).thenReturn(false);

        PlaceVoteRequest request = new PlaceVoteRequest(Arrays.asList(101L, null));

        // when & then
        assertErrorCode(
                () -> voteService.createPlaceVote(USER_ID, MEETING_ID, request),
                PLACE_VOTE_INVALID_PLACE
        );
        verify(placeVoteRepository, never()).saveAll(anyList());
    }

    @Test
    void 정상_투표시_선택한_장소는_전부_저장되고_중복되는_id가_있으면_한번만_저장된다() {
        // given
        Meeting meeting = meetingWithStatus(MeetingStatus.VOTING);
        lenient().when(meeting.getParticipantCnt()).thenReturn(5);
        stubMeetingAndRecommendation(meeting, 101L, 102L, 103L);
        stubParticipant();
        when(placeVoteRepository.existsByMeetingIdAndParticipantId(MEETING_ID, MY_PARTICIPANT_ID)).thenReturn(false);
        when(placeVoteRepository.countDistinctVoters(MEETING_ID)).thenReturn(2L);

        // when: 101L 중복 선택
        voteService.createPlaceVote(USER_ID, MEETING_ID, placeVoteRequest(101L, 102L, 101L));

        // then
        ArgumentCaptor<List<PlaceVote>> captor = ArgumentCaptor.forClass(List.class);
        verify(placeVoteRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
        assertThat(captor.getValue()).extracting(PlaceVote::getRecommendedPlaceId)
                .containsExactlyInAnyOrder(101L, 102L);
        assertThat(captor.getValue()).allSatisfy(vote -> {
            assertThat(vote.getMeetingId()).isEqualTo(MEETING_ID);
            assertThat(vote.getParticipantId()).isEqualTo(MY_PARTICIPANT_ID);
        });
    }

    @Test
    void 마지막_투표자가_투표하면_득표_1위_장소로_모임이_확정된다() {
        // given: 참여자 2명 모임, 마지막 투표 상황 가정
        Meeting meeting = meetingWithStatus(MeetingStatus.VOTING);
        lenient().when(meeting.getParticipantCnt()).thenReturn(2);
        stubMeetingAndRecommendation(meeting, 101L, 102L);
        stubParticipant();
        when(placeVoteRepository.existsByMeetingIdAndParticipantId(MEETING_ID, MY_PARTICIPANT_ID)).thenReturn(false);
        when(placeVoteRepository.countDistinctVoters(MEETING_ID)).thenReturn(2L);

        // 투표 집계: 101L=2표 (1위), 102L=1표
        when(placeVoteRepository.aggregateByPlace(MEETING_ID, MY_PARTICIPANT_ID)).thenReturn(List.of(
                new PlaceVoteSummary(101L, 2L, 1L),
                new PlaceVoteSummary(102L, 1L, 0L)
        ));
        RecommendedPlace place101 = recommendedPlaceWithId(101L, "gp-101", 1);
        RecommendedPlace place102 = recommendedPlaceWithId(102L, "gp-102", 1);
        when(recommendedPlaceRepository.findAllById(anyList())).thenReturn(List.of(place101, place102));
        stubSingleActiveParticipantLocation(37.0, 127.0);
        lenient().when(googlePlacesClient.getPlaceLocation(any())).thenThrow(new CustomException(PLACE_INFO_LOOKUP_FAILED));

        // when
        voteService.createPlaceVote(USER_ID, MEETING_ID, placeVoteRequest(101L));

        // then: 1위(101L)로 모임이 확정됨
        verify(meeting).confirmPlace(101L);
    }

    /**
     * [장소 투표 현황 조회] 테스트
     */

    @Test
    void 장소_투표_결과_조회하는_유저가_해당_모임_참여자가_아니면_VOTE_ACCESS_DENIED() {
        // given
        Meeting meeting = meetingWithStatus(MeetingStatus.VOTING);
        when(meetingRepository.findById(MEETING_ID)).thenReturn(Optional.of(meeting));
        when(participantRepository.findByMeetingIdAndUserIdAndLeftAtIsNull(MEETING_ID, USER_ID))
                .thenReturn(Optional.empty());

        // when & then
        assertErrorCode(() -> voteService.getMeetingPlaceVoteResult(USER_ID, MEETING_ID), VOTE_ACCESS_DENIED);
        verifyNoInteractions(placeVoteRepository, recommendedPlaceRepository, googlePlacesClient);
    }

    @Test
    void 투표가_하나도_없으면_totalVoterCnt는_0이고_candidates는_빈_리스트다() {
        // given
        stubMeetingAndAccess(meetingWithStatus(MeetingStatus.VOTING));
        when(placeVoteRepository.countDistinctVoters(MEETING_ID)).thenReturn(0L);
        when(placeVoteRepository.aggregateByPlace(MEETING_ID, MY_PARTICIPANT_ID)).thenReturn(List.of());

        // when
        PlaceVoteResultResponse response = voteService.getMeetingPlaceVoteResult(USER_ID, MEETING_ID);

        // then
        assertThat(response.isFinished()).isFalse();
        assertThat(response.totalVoterCnt()).isZero();
        assertThat(response.candidates()).isEmpty();

        // 투표 내역이 없을 경우 장소 상세 조회(구글맵 호출/추천 장소/참여자 위치) 진행 x
        verifyNoInteractions(recommendedPlaceRepository, googlePlacesClient, participantPreferenceRepository);
    }

    @Test
    void 모임_상태가_CONFIRMED면_isFinished는_true다() {
        // given
        stubMeetingAndAccess(meetingWithStatus(MeetingStatus.CONFIRMED));
        when(placeVoteRepository.countDistinctVoters(MEETING_ID)).thenReturn(0L);
        when(placeVoteRepository.aggregateByPlace(MEETING_ID, MY_PARTICIPANT_ID)).thenReturn(List.of());

        // when
        PlaceVoteResultResponse response = voteService.getMeetingPlaceVoteResult(USER_ID, MEETING_ID);

        // then
        assertThat(response.isFinished()).isTrue();
    }

    @Test
    void 득표수가_많은_장소가_상위에_온다() {
        // given
        stubMeetingAndAccess(meetingWithStatus(MeetingStatus.VOTING));
        when(placeVoteRepository.countDistinctVoters(MEETING_ID)).thenReturn(3L);
        when(placeVoteRepository.aggregateByPlace(MEETING_ID, MY_PARTICIPANT_ID)).thenReturn(List.of(
                new PlaceVoteSummary(101L, 1L, 0L),
                new PlaceVoteSummary(102L, 3L, 1L)
        ));
        stubRecommendedPlaces(101L, "gp-101", 1, 102L, "gp-102", 1);
        stubSingleActiveParticipantLocation(37.0, 127.0);
        when(googlePlacesClient.getPlaceLocation("gp-101")).thenReturn(googlePlaceAt("A", 37.1, 127.1));
        when(googlePlacesClient.getPlaceLocation("gp-102")).thenReturn(googlePlaceAt("B", 37.2, 127.2));

        // when
        PlaceVoteResultResponse response = voteService.getMeetingPlaceVoteResult(USER_ID, MEETING_ID);

        // then: 득표수 3인 102L이 1위
        assertThat(response.candidates()).extracting(PlaceVoteResultResponse.Candidate::placeId)
                .containsExactly(102L, 101L);
        assertThat(response.candidates().get(0).voterCnt()).isEqualTo(3);
        assertThat(response.candidates().get(0).isVotedByMe()).isTrue();
        assertThat(response.candidates().get(1).isVotedByMe()).isFalse();
    }

    @Test
    void 득표수가_동률이면_참여자들의_출발지와_추천장소간_직선거리_평균값이_짧은_장소가_우선시된다() {
        // given: 101L, 102L 둘 다 득표 2표로 동률
        stubMeetingAndAccess(meetingWithStatus(MeetingStatus.VOTING));
        when(placeVoteRepository.countDistinctVoters(MEETING_ID)).thenReturn(2L);
        when(placeVoteRepository.aggregateByPlace(MEETING_ID, MY_PARTICIPANT_ID)).thenReturn(List.of(
                new PlaceVoteSummary(101L, 2L, 0L),
                new PlaceVoteSummary(102L, 2L, 0L)
        ));
        stubRecommendedPlaces(101L, "gp-101", 1, 102L, "gp-102", 1);
        stubSingleActiveParticipantLocation(37.0, 127.0);
        when(googlePlacesClient.getPlaceLocation("gp-101")).thenReturn(googlePlaceAt("먼_장소", 38.0, 128.0));
        when(googlePlacesClient.getPlaceLocation("gp-102")).thenReturn(googlePlaceAt("가까운_장소", 37.01, 127.01));
        when(areaDistanceService.calculateDistanceKm(37.0, 127.0, 38.0, 128.0)).thenReturn(130.0);
        when(areaDistanceService.calculateDistanceKm(37.0, 127.0, 37.01, 127.01)).thenReturn(1.3);

        // when
        PlaceVoteResultResponse response = voteService.getMeetingPlaceVoteResult(USER_ID, MEETING_ID);

        // then: 평균 거리가 더 짧은 102L이 우선
        assertThat(response.candidates()).extracting(PlaceVoteResultResponse.Candidate::placeId)
                .containsExactly(102L, 101L);
    }

    @Test
    void 구글_장소_조회에_실패하면_평균거리_대신_취향_일치수가_많은_장소가_우선한다() {
        // given: 101L, 102L 득표 동률(2표), 101L은 구글 조회 실패
        stubMeetingAndAccess(meetingWithStatus(MeetingStatus.VOTING));
        when(placeVoteRepository.countDistinctVoters(MEETING_ID)).thenReturn(2L);
        when(placeVoteRepository.aggregateByPlace(MEETING_ID, MY_PARTICIPANT_ID)).thenReturn(List.of(
                new PlaceVoteSummary(101L, 2L, 0L),
                new PlaceVoteSummary(102L, 2L, 0L)
        ));
        stubRecommendedPlaces(101L, "gp-101", 5, 102L, "gp-102", 2);
        stubSingleActiveParticipantLocation(37.0, 127.0);
        when(googlePlacesClient.getPlaceLocation("gp-101")).thenThrow(new CustomException(PLACE_INFO_LOOKUP_FAILED));
        when(googlePlacesClient.getPlaceLocation("gp-102")).thenReturn(googlePlaceAt("B", 37.01, 127.01));

        // when
        PlaceVoteResultResponse response = voteService.getMeetingPlaceVoteResult(USER_ID, MEETING_ID);

        // then: 취향 일치수가 더 높은 101L(5)이 102L(2)보다 순위가 높음
        assertThat(response.candidates()).extracting(PlaceVoteResultResponse.Candidate::placeId)
                .containsExactly(101L, 102L);
        // 구글 조회 실패한 장소 이름 => null 처리
        assertThat(response.candidates().get(0).placeName()).isNull();
    }

    @Test
    void 평균거리도_취향_일치수도_동률이면_recommendedPlaceId_오름차순으로_정렬한다() {
        // given: 둘 다 구글 조회 실패 + 취향 일치수도 동일 -> 최종적으로 id 오름차순
        stubMeetingAndAccess(meetingWithStatus(MeetingStatus.VOTING));
        when(placeVoteRepository.countDistinctVoters(MEETING_ID)).thenReturn(2L);
        when(placeVoteRepository.aggregateByPlace(MEETING_ID, MY_PARTICIPANT_ID)).thenReturn(List.of(
                new PlaceVoteSummary(205L, 2L, 0L),
                new PlaceVoteSummary(101L, 2L, 0L)
        ));
        stubRecommendedPlaces(205L, "gp-205", 3, 101L, "gp-101", 3);
        stubSingleActiveParticipantLocation(37.0, 127.0);
        when(googlePlacesClient.getPlaceLocation("gp-205")).thenThrow(new CustomException(PLACE_INFO_LOOKUP_FAILED));
        when(googlePlacesClient.getPlaceLocation("gp-101")).thenThrow(new CustomException(PLACE_INFO_LOOKUP_FAILED));

        // when
        PlaceVoteResultResponse response = voteService.getMeetingPlaceVoteResult(USER_ID, MEETING_ID);

        // then
        assertThat(response.candidates()).extracting(PlaceVoteResultResponse.Candidate::placeId)
                .containsExactly(101L, 205L);
    }

    @Test
    void 후보가_3개를_초과하면_상위_3개만_반환한다() {
        // given: 득표수가 서로 달라 순위가 명확한 후보 4개
        stubMeetingAndAccess(meetingWithStatus(MeetingStatus.VOTING));
        when(placeVoteRepository.countDistinctVoters(MEETING_ID)).thenReturn(4L);
        when(placeVoteRepository.aggregateByPlace(MEETING_ID, MY_PARTICIPANT_ID)).thenReturn(List.of(
                new PlaceVoteSummary(101L, 4L, 0L),
                new PlaceVoteSummary(102L, 3L, 0L),
                new PlaceVoteSummary(103L, 2L, 0L),
                new PlaceVoteSummary(104L, 1L, 0L)
        ));

        RecommendedPlace place101 = recommendedPlaceWithId(101L, "gp-101", 1);
        RecommendedPlace place102 = recommendedPlaceWithId(102L, "gp-102", 1);
        RecommendedPlace place103 = recommendedPlaceWithId(103L, "gp-103", 1);
        RecommendedPlace place104 = recommendedPlaceWithId(104L, "gp-104", 1);

        when(recommendedPlaceRepository.findAllById(anyList()))
                .thenReturn(List.of(place101, place102, place103, place104));
        stubSingleActiveParticipantLocation(37.0, 127.0);
        lenient().when(googlePlacesClient.getPlaceLocation(any())).thenThrow(new CustomException(PLACE_INFO_LOOKUP_FAILED));

        // when
        PlaceVoteResultResponse response = voteService.getMeetingPlaceVoteResult(USER_ID, MEETING_ID);

        // then
        assertThat(response.candidates()).hasSize(3);
        assertThat(response.candidates()).extracting(PlaceVoteResultResponse.Candidate::placeId)
                .containsExactly(101L, 102L, 103L);
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

    private void stubMeetingAndParticipant(Meeting meeting) {
        stubMeetingAndParticipant(meeting, MY_PARTICIPANT_ID);
    }

    private void stubMeetingAndParticipant(Meeting meeting, Long participantId) {
        Participant participant = mock(Participant.class);
        lenient().when(participant.getId()).thenReturn(participantId);
        when(meetingRepository.findById(MEETING_ID)).thenReturn(Optional.of(meeting));
        when(participantRepository.findByMeetingIdAndUserIdAndLeftAtIsNull(MEETING_ID, USER_ID))
                .thenReturn(Optional.of(participant));
    }

    private PlaceVoteRequest placeVoteRequest(Long... recommendedPlaceIds) {
        return new PlaceVoteRequest(List.of(recommendedPlaceIds));
    }

    private Meeting meetingWithStatus(MeetingStatus status) {
        Meeting meeting = mock(Meeting.class);
        lenient().when(meeting.getStatus()).thenReturn(status);
        return meeting;
    }

    private void stubMeetingAndRecommendation(Meeting meeting, Long... validRecommendedPlaceIds) {
        when(meetingRepository.findById(MEETING_ID)).thenReturn(Optional.of(meeting));

        RecommendationRun run = mock(RecommendationRun.class);
        lenient().when(run.getId()).thenReturn(RECOMMENDATION_RUN_ID);
        when(recommendationRunRepository.findTopByMeetingIdAndStatusOrderByCreatedAtDesc(MEETING_ID, RecommendationStatus.COMPLETED))
                .thenReturn(Optional.of(run));
        lenient().when(recommendedPlaceRepository.findIdsByRecommendationRunId(RECOMMENDATION_RUN_ID))
                .thenReturn(List.of(validRecommendedPlaceIds));
    }

    private void stubParticipant() {
        Participant participant = mock(Participant.class);
        lenient().when(participant.getId()).thenReturn(MY_PARTICIPANT_ID);
        when(participantRepository.findByMeetingIdAndUserIdAndLeftAtIsNull(MEETING_ID, USER_ID))
                .thenReturn(Optional.of(participant));
    }

    private void stubMeetingAndAccess(Meeting meeting) {
        when(meetingRepository.findById(MEETING_ID)).thenReturn(Optional.of(meeting));
        stubParticipant();
    }

    private void stubRecommendedPlaces(
            Long placeId1, String googlePlaceId1, int preferenceMatchCnt1,
            Long placeId2, String googlePlaceId2, int preferenceMatchCnt2
    ) {
        RecommendedPlace place1 = recommendedPlaceWithId(placeId1, googlePlaceId1, preferenceMatchCnt1);
        RecommendedPlace place2 = recommendedPlaceWithId(placeId2, googlePlaceId2, preferenceMatchCnt2);
        when(recommendedPlaceRepository.findAllById(anyList())).thenReturn(List.of(place1, place2));
    }

    private RecommendedPlace recommendedPlaceWithId(Long id, String googlePlaceId, int preferenceMatchCnt) {
        RecommendedPlace place = mock(RecommendedPlace.class);
        lenient().when(place.getId()).thenReturn(id);
        lenient().when(place.getGooglePlaceId()).thenReturn(googlePlaceId);
        lenient().when(place.getPreferenceMatchCnt()).thenReturn(preferenceMatchCnt);
        return place;
    }

    // 참여자 1명의 출발지 좌표를 고정해 평균 거리 계산 결과가 그 1명의 거리값과 동일하도록 단순화
    private void stubSingleActiveParticipantLocation(double latitude, double longitude) {
        Long participantId = 500L;
        Participant participant = mock(Participant.class);
        lenient().when(participant.getId()).thenReturn(participantId);
        when(participantRepository.findAllByMeetingIdAndLeftAtIsNull(MEETING_ID)).thenReturn(List.of(participant));

        ParticipantPreference preference = mock(ParticipantPreference.class);
        lenient().when(preference.getDepartureLatitude()).thenReturn(BigDecimal.valueOf(latitude));
        lenient().when(preference.getDepartureLongitude()).thenReturn(BigDecimal.valueOf(longitude));
        when(participantPreferenceRepository.findAllByParticipantIdIn(List.of(participantId)))
                .thenReturn(List.of(preference));
    }

    private GooglePlaceLocationResponse googlePlaceAt(String name, double latitude, double longitude) {
        return new GooglePlaceLocationResponse(
                new GooglePlaceLocationResponse.LocalizedText(name, "ko"),
                null,
                "테스트 주소",
                new GooglePlaceLocationResponse.Location(latitude, longitude)
        );
    }
}
