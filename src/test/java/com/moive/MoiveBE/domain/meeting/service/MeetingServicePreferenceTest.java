package com.moive.MoiveBE.domain.meeting.service;

import com.moive.MoiveBE.domain.meeting.dto.SubmitPreferenceRequest;
import com.moive.MoiveBE.domain.meeting.dto.SubmitPreferenceResponse;
import com.moive.MoiveBE.domain.meeting.entity.*;
import com.moive.MoiveBE.domain.meeting.repository.*;
import com.moive.MoiveBE.domain.notification.service.NotificationService;
import com.moive.MoiveBE.domain.recommendation.service.AreaRecommendationService;
import com.moive.MoiveBE.global.exception.CustomErrorCode;
import com.moive.MoiveBE.global.exception.CustomException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MeetingServicePreferenceTest {

    @Mock private MeetingRepository meetingRepository;
    @Mock private MeetingPurposeRepository meetingPurposeRepository;
    @Mock private ParticipantRepository participantRepository;
    @Mock private ParticipantPreferenceRepository preferenceRepository;
    @Mock private PreferenceActivityRepository preferenceActivityRepository;
    @Mock private ActivityRepository activityRepository;
    @Mock private DateVoteRepository dateVoteRepository;
    @Mock private NotificationService notificationService;
    @Mock private AreaRecommendationService areaRecommendationService;

    private MeetingService meetingService;

    @BeforeEach
    void setUp() {
        meetingService = new MeetingService(
                meetingRepository,
                meetingPurposeRepository,
                participantRepository,
                preferenceRepository,
                preferenceActivityRepository,
                activityRepository,
                dateVoteRepository,
                notificationService,
                areaRecommendationService,
                "https://moiveserver.store/invite"
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        1L, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))
                )
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ─────────────────── 헬퍼 ───────────────────

    private Meeting mockMeeting(MeetingStatus status, boolean hasSchedule, int participantCnt, int submittedCnt) {
        Meeting meeting = mock(Meeting.class);
        lenient().when(meeting.getId()).thenReturn(10L);
        lenient().when(meeting.getStatus()).thenReturn(status);
        lenient().when(meeting.hasSchedule()).thenReturn(hasSchedule);
        lenient().when(meeting.getParticipantCnt()).thenReturn(participantCnt);
        lenient().when(meeting.getSubmittedCnt()).thenReturn(submittedCnt);
        return meeting;
    }

    private Participant mockParticipant(boolean conditionCompleted) {
        Participant participant = mock(Participant.class);
        lenient().when(participant.getId()).thenReturn(3L);
        lenient().when(participant.isConditionCompleted()).thenReturn(conditionCompleted);
        lenient().when(participant.getState()).thenReturn(ParticipantState.COND_DONE);
        return participant;
    }

    private SubmitPreferenceRequest validRequest(boolean includeSchedules) {
        List<SubmitPreferenceRequest.AvailableSchedule> schedules = includeSchedules
                ? List.of(new SubmitPreferenceRequest.AvailableSchedule("2026-12-12", "18:00"))
                : null;
        return new SubmitPreferenceRequest(
                schedules,
                "정자천로32번길 27",
                new BigDecimal("37.1234567"),
                new BigDecimal("127.1234567"),
                30,
                List.of(ActivityType.BOARD_GAME, ActivityType.KOREAN_FOOD)
        );
    }

    // ─────────────────── 정상 케이스 ───────────────────

    @Test
    void 조건입력_성공_hasSchedule_false_첫_제출_CONDITION_INPUT_유지() {
        // given
        Meeting meeting = mockMeeting(MeetingStatus.CONDITION_INPUT, false, 3, 1);
        Participant participant = mockParticipant(false);
        ParticipantPreference savedPref = mock(ParticipantPreference.class);
        Activity activity = mock(Activity.class);
        when(savedPref.getId()).thenReturn(100L);
        when(activity.getId()).thenReturn(1L);

        when(meetingRepository.findById(10L)).thenReturn(Optional.of(meeting));
        when(participantRepository.findByMeetingIdAndUserIdAndLeftAtIsNull(10L, 1L))
                .thenReturn(Optional.of(participant));
        when(preferenceRepository.findByParticipantId(3L)).thenReturn(Optional.empty());
        when(preferenceRepository.save(any())).thenReturn(savedPref);
        when(activityRepository.findByName(any())).thenReturn(Optional.of(activity));
        // 첫 제출 후 submittedCnt=2, participantCnt=3 → 아직 미완료
        when(meeting.getSubmittedCnt()).thenReturn(2);

        // when
        SubmitPreferenceResponse response = meetingService.submitPreference(10L, validRequest(true));

        // then
        assertThat(response.meetingStatus()).isEqualTo("CONDITION_INPUT");
        assertThat(response.recommendationTriggered()).isFalse();
        verify(participant).completeCondition();
        verify(meeting).incrementSubmittedCnt();
        verify(meeting, never()).transitionToVoting();
    }

    @Test
    void 조건입력_성공_마지막_참여자_제출시_VOTING_전환_및_추천_트리거() {
        // given
        Meeting meeting = mockMeeting(MeetingStatus.CONDITION_INPUT, false, 2, 1);
        Participant participant = mockParticipant(false);
        ParticipantPreference savedPref = mock(ParticipantPreference.class);
        Activity activity = mock(Activity.class);
        when(savedPref.getId()).thenReturn(100L);
        when(activity.getId()).thenReturn(1L);

        when(meetingRepository.findById(10L)).thenReturn(Optional.of(meeting));
        when(participantRepository.findByMeetingIdAndUserIdAndLeftAtIsNull(10L, 1L))
                .thenReturn(Optional.of(participant));
        when(preferenceRepository.findByParticipantId(3L)).thenReturn(Optional.empty());
        when(preferenceRepository.save(any())).thenReturn(savedPref);
        when(activityRepository.findByName(any())).thenReturn(Optional.of(activity));
        when(meeting.getSubmittedCnt()).thenReturn(2);
        // 첫 호출(상태 체크)=CONDITION_INPUT, 이후 호출(응답 생성)=VOTING
        when(meeting.getStatus()).thenReturn(MeetingStatus.CONDITION_INPUT, MeetingStatus.VOTING);

        // when
        SubmitPreferenceResponse response = meetingService.submitPreference(10L, validRequest(true));

        // then
        assertThat(response.recommendationTriggered()).isTrue();
        assertThat(response.meetingStatus()).isEqualTo("VOTING");
        verify(meeting).transitionToVoting();
    }

    @Test
    void 조건입력_성공_hasSchedule_true이면_일정없이도_정상_제출() {
        // given
        Meeting meeting = mockMeeting(MeetingStatus.CONDITION_INPUT, true, 2, 0);
        Participant participant = mockParticipant(false);
        ParticipantPreference savedPref = mock(ParticipantPreference.class);
        Activity activity = mock(Activity.class);
        when(savedPref.getId()).thenReturn(100L);
        when(activity.getId()).thenReturn(1L);

        when(meetingRepository.findById(10L)).thenReturn(Optional.of(meeting));
        when(participantRepository.findByMeetingIdAndUserIdAndLeftAtIsNull(10L, 1L))
                .thenReturn(Optional.of(participant));
        when(preferenceRepository.findByParticipantId(3L)).thenReturn(Optional.empty());
        when(preferenceRepository.save(any())).thenReturn(savedPref);
        when(activityRepository.findByName(any())).thenReturn(Optional.of(activity));
        when(meeting.getSubmittedCnt()).thenReturn(1);

        // when: schedules=null 로 요청
        SubmitPreferenceResponse response = meetingService.submitPreference(10L, validRequest(false));

        // then: 예외 없이 성공
        assertThat(response.participantState()).isEqualTo("COND_DONE");
        verify(dateVoteRepository, never()).save(any());
    }

    @Test
    void 재제출시_기존_선호조건을_덮어쓴다() {
        // given
        Meeting meeting = mockMeeting(MeetingStatus.CONDITION_INPUT, false, 2, 1);
        Participant participant = mockParticipant(true); // 이미 제출 완료
        ParticipantPreference existingPref = mock(ParticipantPreference.class);
        Activity activity = mock(Activity.class);
        when(existingPref.getId()).thenReturn(100L);
        when(activity.getId()).thenReturn(1L);

        when(meetingRepository.findById(10L)).thenReturn(Optional.of(meeting));
        when(participantRepository.findByMeetingIdAndUserIdAndLeftAtIsNull(10L, 1L))
                .thenReturn(Optional.of(participant));
        when(preferenceRepository.findByParticipantId(3L)).thenReturn(Optional.of(existingPref));
        when(activityRepository.findByName(any())).thenReturn(Optional.of(activity));

        // when
        meetingService.submitPreference(10L, validRequest(true));

        // then: update() 호출, save() 미호출, completeCondition() 미호출
        verify(existingPref).update(any(), any(), any(), any());
        verify(preferenceRepository, never()).save(any());
        verify(participant, never()).completeCondition();
        verify(meeting, never()).incrementSubmittedCnt();
    }

    // ─────────────────── 에러 케이스 ───────────────────

    @Test
    void 모임이_없으면_MEETING_NOT_FOUND() {
        when(meetingRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> meetingService.submitPreference(10L, validRequest(true)))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getCustomErrorCode())
                        .isEqualTo(CustomErrorCode.MEETING_NOT_FOUND));
    }

    @Test
    void 모임_상태가_CONDITION_INPUT이_아니면_MEETING_STATUS_INVALID() {
        Meeting meeting = mockMeeting(MeetingStatus.VOTING, false, 2, 1);
        when(meetingRepository.findById(10L)).thenReturn(Optional.of(meeting));

        assertThatThrownBy(() -> meetingService.submitPreference(10L, validRequest(true)))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getCustomErrorCode())
                        .isEqualTo(CustomErrorCode.MEETING_STATUS_INVALID_FOR_CONDITION));
    }

    @Test
    void 모임_참여자가_아니면_NOT_A_PARTICIPANT() {
        Meeting meeting = mockMeeting(MeetingStatus.CONDITION_INPUT, false, 2, 0);
        when(meetingRepository.findById(10L)).thenReturn(Optional.of(meeting));
        when(participantRepository.findByMeetingIdAndUserIdAndLeftAtIsNull(10L, 1L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> meetingService.submitPreference(10L, validRequest(true)))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getCustomErrorCode())
                        .isEqualTo(CustomErrorCode.NOT_A_PARTICIPANT));
    }

    @Test
    void 출발지가_없으면_DEPARTURE_MISSING() {
        Meeting meeting = mockMeeting(MeetingStatus.CONDITION_INPUT, false, 2, 0);
        Participant participant = mockParticipant(false);
        when(meetingRepository.findById(10L)).thenReturn(Optional.of(meeting));
        when(participantRepository.findByMeetingIdAndUserIdAndLeftAtIsNull(10L, 1L))
                .thenReturn(Optional.of(participant));

        SubmitPreferenceRequest request = new SubmitPreferenceRequest(
                List.of(new SubmitPreferenceRequest.AvailableSchedule("2026-12-12", "18:00")),
                null, null, null, 30,
                List.of(ActivityType.BOARD_GAME)
        );

        assertThatThrownBy(() -> meetingService.submitPreference(10L, request))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getCustomErrorCode())
                        .isEqualTo(CustomErrorCode.DEPARTURE_MISSING));
    }

    @Test
    void 유효하지_않은_이동시간이면_MAX_TRAVEL_MINUTES_INVALID() {
        Meeting meeting = mockMeeting(MeetingStatus.CONDITION_INPUT, false, 2, 0);
        Participant participant = mockParticipant(false);
        when(meetingRepository.findById(10L)).thenReturn(Optional.of(meeting));
        when(participantRepository.findByMeetingIdAndUserIdAndLeftAtIsNull(10L, 1L))
                .thenReturn(Optional.of(participant));

        SubmitPreferenceRequest request = new SubmitPreferenceRequest(
                List.of(new SubmitPreferenceRequest.AvailableSchedule("2026-12-12", "18:00")),
                "정자천로32번길 27",
                new BigDecimal("37.1234567"),
                new BigDecimal("127.1234567"),
                45, // 유효하지 않은 값
                List.of(ActivityType.BOARD_GAME)
        );

        assertThatThrownBy(() -> meetingService.submitPreference(10L, request))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getCustomErrorCode())
                        .isEqualTo(CustomErrorCode.MAX_TRAVEL_MINUTES_INVALID));
    }

    @Test
    void 취향이_없으면_ACTIVITY_TYPES_EMPTY() {
        Meeting meeting = mockMeeting(MeetingStatus.CONDITION_INPUT, false, 2, 0);
        Participant participant = mockParticipant(false);
        when(meetingRepository.findById(10L)).thenReturn(Optional.of(meeting));
        when(participantRepository.findByMeetingIdAndUserIdAndLeftAtIsNull(10L, 1L))
                .thenReturn(Optional.of(participant));

        SubmitPreferenceRequest request = new SubmitPreferenceRequest(
                List.of(new SubmitPreferenceRequest.AvailableSchedule("2026-12-12", "18:00")),
                "정자천로32번길 27",
                new BigDecimal("37.1234567"),
                new BigDecimal("127.1234567"),
                30,
                List.of() // 빈 리스트
        );

        assertThatThrownBy(() -> meetingService.submitPreference(10L, request))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getCustomErrorCode())
                        .isEqualTo(CustomErrorCode.ACTIVITY_TYPES_EMPTY));
    }

    @Test
    void hasSchedule_false인데_일정이_없으면_AVAILABLE_SCHEDULES_EMPTY() {
        Meeting meeting = mockMeeting(MeetingStatus.CONDITION_INPUT, false, 2, 0);
        Participant participant = mockParticipant(false);
        when(meetingRepository.findById(10L)).thenReturn(Optional.of(meeting));
        when(participantRepository.findByMeetingIdAndUserIdAndLeftAtIsNull(10L, 1L))
                .thenReturn(Optional.of(participant));

        SubmitPreferenceRequest request = new SubmitPreferenceRequest(
                List.of(), // 빈 일정
                "정자천로32번길 27",
                new BigDecimal("37.1234567"),
                new BigDecimal("127.1234567"),
                30,
                List.of(ActivityType.BOARD_GAME)
        );

        assertThatThrownBy(() -> meetingService.submitPreference(10L, request))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getCustomErrorCode())
                        .isEqualTo(CustomErrorCode.AVAILABLE_SCHEDULES_EMPTY));
    }
}
