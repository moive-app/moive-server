package com.moive.MoiveBE.domain.meeting.service;

import com.moive.MoiveBE.domain.meeting.dto.HomeResponse;
import com.moive.MoiveBE.domain.meeting.dto.MeetingListResponse;
import com.moive.MoiveBE.domain.meeting.entity.*;
import com.moive.MoiveBE.domain.meeting.repository.MeetingPurposeRepository;
import com.moive.MoiveBE.domain.meeting.repository.MeetingRepository;
import com.moive.MoiveBE.domain.meeting.repository.ParticipantRepository;
import com.moive.MoiveBE.domain.recommendation.client.GooglePlacesClient;
import com.moive.MoiveBE.domain.recommendation.repository.RecommendedPlaceRepository;
import com.moive.MoiveBE.domain.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HomeServiceTest {

    @Mock private MeetingRepository meetingRepository;
    @Mock private MeetingPurposeRepository meetingPurposeRepository;
    @Mock private ParticipantRepository participantRepository;
    @Mock private UserRepository userRepository;
    @Mock private RecommendedPlaceRepository recommendedPlaceRepository;
    @Mock private GooglePlacesClient googlePlacesClient;

    private HomeService homeService;
    private static final Long CURRENT_USER_ID = 1L;

    @BeforeEach
    void setUp() {
        homeService = new HomeService(
                meetingRepository, meetingPurposeRepository, participantRepository,
                userRepository, recommendedPlaceRepository, googlePlacesClient
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        CURRENT_USER_ID, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))
                )
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 참여_모임이_없으면_빈_응답을_반환한다() {
        when(participantRepository.findAllByUserIdAndLeftAtIsNullOrderByIdAsc(CURRENT_USER_ID))
                .thenReturn(List.of());

        HomeResponse response = homeService.getHome("ALL");

        assertThat(response.confirmedMeetings()).isEmpty();
        assertThat(response.myMeetings()).isEmpty();
        verifyNoInteractions(meetingRepository);
    }

    @Test
    void filter_ALL이면_모든_상태의_모임을_반환한다() {
        Participant p1 = mockParticipant(1L, 10L);
        Participant p2 = mockParticipant(2L, 20L);
        Meeting m1 = mockMeeting(10L, MeetingStatus.CONDITION_INPUT);
        Meeting m2 = mockMeeting(20L, MeetingStatus.COMPLETED);

        when(participantRepository.findAllByUserIdAndLeftAtIsNullOrderByIdAsc(CURRENT_USER_ID))
                .thenReturn(List.of(p1, p2));
        when(meetingRepository.findAllById(any())).thenReturn(List.of(m1, m2));
        when(meetingPurposeRepository.findAllByMeetingIdIn(any())).thenReturn(List.of());

        HomeResponse response = homeService.getHome("ALL");

        assertThat(response.myMeetings()).hasSize(2);
    }

    @Test
    void filter_UPCOMING이면_COMPLETED_모임은_제외된다() {
        Participant p1 = mockParticipant(1L, 10L);
        Participant p2 = mockParticipant(2L, 20L);
        Meeting m1 = mockMeeting(10L, MeetingStatus.VOTING);
        Meeting m2 = mockMeeting(20L, MeetingStatus.COMPLETED);

        when(participantRepository.findAllByUserIdAndLeftAtIsNullOrderByIdAsc(CURRENT_USER_ID))
                .thenReturn(List.of(p1, p2));
        when(meetingRepository.findAllById(any())).thenReturn(List.of(m1, m2));
        when(meetingPurposeRepository.findAllByMeetingIdIn(any())).thenReturn(List.of());

        HomeResponse response = homeService.getHome("UPCOMING");

        assertThat(response.myMeetings()).hasSize(1);
        assertThat(response.myMeetings().get(0).status()).isEqualTo("VOTING");
    }

    @Test
    void filter_PAST이면_COMPLETED_모임만_반환한다() {
        Participant p1 = mockParticipant(1L, 10L);
        Participant p2 = mockParticipant(2L, 20L);
        Meeting m1 = mockMeeting(10L, MeetingStatus.CONDITION_INPUT);
        Meeting m2 = mockMeeting(20L, MeetingStatus.COMPLETED);

        when(participantRepository.findAllByUserIdAndLeftAtIsNullOrderByIdAsc(CURRENT_USER_ID))
                .thenReturn(List.of(p1, p2));
        when(meetingRepository.findAllById(any())).thenReturn(List.of(m1, m2));
        when(meetingPurposeRepository.findAllByMeetingIdIn(any())).thenReturn(List.of());

        HomeResponse response = homeService.getHome("PAST");

        assertThat(response.myMeetings()).hasSize(1);
        assertThat(response.myMeetings().get(0).status()).isEqualTo("COMPLETED");
    }

    @Test
    void CONFIRMED_모임은_confirmedMeetings에_포함되고_dDay_오름차순으로_정렬된다() {
        Participant p1 = mockParticipant(1L, 10L);
        Participant p2 = mockParticipant(2L, 20L);
        Meeting m1 = mockConfirmedMeeting(10L, LocalDate.now().plusDays(5));
        Meeting m2 = mockConfirmedMeeting(20L, LocalDate.now().plusDays(2));

        when(participantRepository.findAllByUserIdAndLeftAtIsNullOrderByIdAsc(CURRENT_USER_ID))
                .thenReturn(List.of(p1, p2));
        when(meetingRepository.findAllById(any())).thenReturn(List.of(m1, m2));
        when(meetingPurposeRepository.findAllByMeetingIdIn(any())).thenReturn(List.of());
        when(participantRepository.findAllByMeetingIdInAndLeftAtIsNullOrderByJoinedAtAsc(any()))
                .thenReturn(List.of());
        when(userRepository.findAllById(any())).thenReturn(List.of());

        HomeResponse response = homeService.getHome("ALL");

        assertThat(response.confirmedMeetings()).hasSize(2);
        assertThat(response.confirmedMeetings().get(0).dDay()).isLessThan(
                response.confirmedMeetings().get(1).dDay()
        );
    }

    // --- getMeetings 테스트 ---

    @Test
    void getMeetings_cursor가_없으면_첫_페이지를_반환한다() {
        Participant p1 = mockParticipant(1L, 10L);
        Participant p2 = mockParticipant(2L, 20L);
        Meeting m1 = mockMeeting(10L, MeetingStatus.CONDITION_INPUT);
        Meeting m2 = mockMeeting(20L, MeetingStatus.VOTING);

        when(participantRepository.findAllByUserIdAndLeftAtIsNullOrderByIdAsc(CURRENT_USER_ID))
                .thenReturn(List.of(p1, p2));
        when(meetingRepository.findAllById(any())).thenReturn(List.of(m1, m2));
        when(meetingPurposeRepository.findAllByMeetingIdIn(any())).thenReturn(List.of());

        MeetingListResponse response = homeService.getMeetings("ALL", null, 20);

        assertThat(response.meetings()).hasSize(2);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    void getMeetings_size보다_많으면_hasNext가_true이고_nextCursor가_반환된다() {
        Participant p1 = mockParticipant(1L, 10L);
        Participant p2 = mockParticipant(2L, 20L);
        Participant p3 = mockParticipant(3L, 30L);
        Meeting m1 = mockMeeting(10L, MeetingStatus.CONDITION_INPUT);
        Meeting m2 = mockMeeting(20L, MeetingStatus.VOTING);
        Meeting m3 = mockMeeting(30L, MeetingStatus.CONFIRMED);

        when(participantRepository.findAllByUserIdAndLeftAtIsNullOrderByIdAsc(CURRENT_USER_ID))
                .thenReturn(List.of(p1, p2, p3));
        when(meetingRepository.findAllById(any())).thenReturn(List.of(m1, m2, m3));
        when(meetingPurposeRepository.findAllByMeetingIdIn(any())).thenReturn(List.of());

        MeetingListResponse response = homeService.getMeetings("ALL", null, 2);

        assertThat(response.meetings()).hasSize(2);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.nextCursor()).isEqualTo(2L);
    }

    @Test
    void getMeetings_cursor가_있으면_해당_cursor_이후_항목만_반환한다() {
        Participant p1 = mockParticipant(1L, 10L);
        Participant p2 = mockParticipant(2L, 20L);
        Participant p3 = mockParticipant(3L, 30L);
        Meeting m2 = mockMeeting(20L, MeetingStatus.VOTING);
        Meeting m3 = mockMeeting(30L, MeetingStatus.CONFIRMED);

        when(participantRepository.findAllByUserIdAndLeftAtIsNullOrderByIdAsc(CURRENT_USER_ID))
                .thenReturn(List.of(p1, p2, p3));
        when(meetingRepository.findAllById(any())).thenReturn(List.of(m2, m3));
        when(meetingPurposeRepository.findAllByMeetingIdIn(any())).thenReturn(List.of());

        // cursor=1이면 id > 1 인 p2, p3만 조회
        MeetingListResponse response = homeService.getMeetings("ALL", 1L, 20);

        assertThat(response.meetings()).hasSize(2);
        assertThat(response.hasNext()).isFalse();
    }

    private Participant mockParticipant(Long id, Long meetingId) {
        Participant p = mock(Participant.class);
        lenient().when(p.getId()).thenReturn(id);
        lenient().when(p.getMeetingId()).thenReturn(meetingId);
        return p;
    }

    private Meeting mockMeeting(Long id, MeetingStatus status) {
        Meeting meeting = mock(Meeting.class);
        lenient().when(meeting.getId()).thenReturn(id);
        lenient().when(meeting.getName()).thenReturn("모임" + id);
        lenient().when(meeting.getStatus()).thenReturn(status);
        lenient().when(meeting.getScheduledDate()).thenReturn(null);
        lenient().when(meeting.getScheduledTime()).thenReturn(null);
        lenient().when(meeting.getParticipantCnt()).thenReturn(2);
        lenient().when(meeting.getSubmittedCnt()).thenReturn(0);
        lenient().when(meeting.getConfirmedPlaceId()).thenReturn(null);
        return meeting;
    }

    private Meeting mockConfirmedMeeting(Long id, LocalDate scheduledDate) {
        Meeting meeting = mockMeeting(id, MeetingStatus.CONFIRMED);
        lenient().when(meeting.getScheduledDate()).thenReturn(scheduledDate);
        lenient().when(meeting.getScheduledTime()).thenReturn(null);
        return meeting;
    }
}
