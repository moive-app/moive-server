package com.moive.MoiveBE.domain.meeting.service;

import com.moive.MoiveBE.domain.meeting.dto.CreateMeetingRequest;
import com.moive.MoiveBE.domain.meeting.dto.CreateMeetingResponse;
import com.moive.MoiveBE.domain.meeting.entity.*;
import com.moive.MoiveBE.domain.meeting.repository.*;
import com.moive.MoiveBE.domain.notification.service.NotificationService;

import com.moive.MoiveBE.global.exception.CustomErrorCode;
import com.moive.MoiveBE.global.exception.CustomException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MeetingServiceCreateTest {

    @Mock private MeetingRepository meetingRepository;
    @Mock private MeetingPurposeRepository meetingPurposeRepository;
    @Mock private ParticipantRepository participantRepository;
    @Mock private ParticipantPreferenceRepository preferenceRepository;
    @Mock private PreferenceActivityRepository preferenceActivityRepository;
    @Mock private ActivityRepository activityRepository;
    @Mock private DateVoteRepository dateVoteRepository;
    @Mock private NotificationService notificationService;

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
                "https://moive.app/invite"
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

    @Test
    void 모임_생성에_성공하면_모임과_목적과_참여자를_저장하고_응답을_반환한다() {
        // given
        CreateMeetingRequest request = new CreateMeetingRequest(
                "강남에서 만나자", true, "2026-09-02", "19:00:00", "NETWORKING"
        );

        Meeting savedMeeting = mock(Meeting.class);
        when(savedMeeting.getId()).thenReturn(1L);
        when(savedMeeting.getName()).thenReturn("강남에서 만나자");
        when(savedMeeting.getScheduledDate()).thenReturn(LocalDate.of(2026, 9, 2));
        when(savedMeeting.getScheduledTime()).thenReturn(LocalTime.of(19, 0));
        when(savedMeeting.getStatus()).thenReturn(MeetingStatus.CONDITION_INPUT);
        when(savedMeeting.getInviteCode()).thenReturn("ABC123XY");
        when(savedMeeting.getCreatedAt()).thenReturn(java.time.LocalDateTime.of(2026, 9, 2, 14, 30));

        when(meetingRepository.existsByInviteCode(any())).thenReturn(false);
        when(meetingRepository.save(any(Meeting.class))).thenReturn(savedMeeting);

        // when
        CreateMeetingResponse response = meetingService.createMeeting(request);

        // then
        assertThat(response.meetingId()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("강남에서 만나자");
        assertThat(response.hasSchedule()).isTrue();
        assertThat(response.purposeType()).isEqualTo("NETWORKING");
        assertThat(response.status()).isEqualTo("CONDITION_INPUT");
        assertThat(response.inviteUrl()).startsWith("https://moive.app/invite/");

        verify(meetingPurposeRepository).save(any(MeetingPurpose.class));

        ArgumentCaptor<Participant> participantCaptor = ArgumentCaptor.forClass(Participant.class);
        verify(participantRepository).save(participantCaptor.capture());
        assertThat(participantCaptor.getValue().getState()).isEqualTo(ParticipantState.COND_PENDING);
    }

    @Test
    void 이름이_null이면_MEETING_NAME_EMPTY_예외가_발생한다() {
        CreateMeetingRequest request = new CreateMeetingRequest(
                null, false, null, null, "FRIENDLY"
        );

        assertThatThrownBy(() -> meetingService.createMeeting(request))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getCustomErrorCode())
                        .isEqualTo(CustomErrorCode.MEETING_NAME_EMPTY));

        verifyNoInteractions(meetingRepository, meetingPurposeRepository, participantRepository);
    }

    @Test
    void 이름이_공백만이면_MEETING_NAME_EMPTY_예외가_발생한다() {
        CreateMeetingRequest request = new CreateMeetingRequest(
                "   ", false, null, null, "FRIENDLY"
        );

        assertThatThrownBy(() -> meetingService.createMeeting(request))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getCustomErrorCode())
                        .isEqualTo(CustomErrorCode.MEETING_NAME_EMPTY));
    }

    @Test
    void 이름이_21자_이상이면_MEETING_NAME_TOO_LONG_예외가_발생한다() {
        CreateMeetingRequest request = new CreateMeetingRequest(
                "가".repeat(21), false, null, null, "FRIENDLY"
        );

        assertThatThrownBy(() -> meetingService.createMeeting(request))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getCustomErrorCode())
                        .isEqualTo(CustomErrorCode.MEETING_NAME_TOO_LONG));
    }

    @Test
    void 일정확정인데_날짜가_없으면_MEETING_SCHEDULE_INCOMPLETE_예외가_발생한다() {
        CreateMeetingRequest request = new CreateMeetingRequest(
                "모임", true, null, "19:00:00", "FRIENDLY"
        );

        assertThatThrownBy(() -> meetingService.createMeeting(request))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getCustomErrorCode())
                        .isEqualTo(CustomErrorCode.MEETING_SCHEDULE_INCOMPLETE));
    }

    @Test
    void 일정확정인데_시간이_없으면_MEETING_SCHEDULE_INCOMPLETE_예외가_발생한다() {
        CreateMeetingRequest request = new CreateMeetingRequest(
                "모임", true, "2026-09-02", null, "FRIENDLY"
        );

        assertThatThrownBy(() -> meetingService.createMeeting(request))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getCustomErrorCode())
                        .isEqualTo(CustomErrorCode.MEETING_SCHEDULE_INCOMPLETE));
    }

    @Test
    void 목적이_null이면_MEETING_PURPOSE_EMPTY_예외가_발생한다() {
        CreateMeetingRequest request = new CreateMeetingRequest(
                "모임", false, null, null, null
        );

        assertThatThrownBy(() -> meetingService.createMeeting(request))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getCustomErrorCode())
                        .isEqualTo(CustomErrorCode.MEETING_PURPOSE_EMPTY));
    }

    @Test
    void 유효하지_않은_목적값이면_MEETING_PURPOSE_EMPTY_예외가_발생한다() {
        CreateMeetingRequest request = new CreateMeetingRequest(
                "모임", false, null, null, "INVALID_TYPE"
        );

        assertThatThrownBy(() -> meetingService.createMeeting(request))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getCustomErrorCode())
                        .isEqualTo(CustomErrorCode.MEETING_PURPOSE_EMPTY));
    }

    @Test
    void hasSchedule가_false이면_날짜와_시간_없이_성공한다() {
        CreateMeetingRequest request = new CreateMeetingRequest(
                "모임", false, null, null, "ETC"
        );

        Meeting savedMeeting = mock(Meeting.class);
        when(savedMeeting.getId()).thenReturn(1L);
        when(savedMeeting.getName()).thenReturn("모임");
        when(savedMeeting.getScheduledDate()).thenReturn(null);
        when(savedMeeting.getStatus()).thenReturn(MeetingStatus.CONDITION_INPUT);
        when(savedMeeting.getInviteCode()).thenReturn("XY123ABC");
        when(savedMeeting.getCreatedAt()).thenReturn(java.time.LocalDateTime.now());

        when(meetingRepository.existsByInviteCode(any())).thenReturn(false);
        when(meetingRepository.save(any(Meeting.class))).thenReturn(savedMeeting);

        CreateMeetingResponse response = meetingService.createMeeting(request);

        assertThat(response.hasSchedule()).isFalse();
        assertThat(response.scheduledDate()).isNull();
        assertThat(response.scheduledTime()).isNull();
    }

}
