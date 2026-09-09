package com.moive.MoiveBE.domain.meeting.service;

import com.moive.MoiveBE.domain.meeting.dto.MeetingDetailResponse;
import com.moive.MoiveBE.domain.meeting.dto.MeetingHomeResponse;
import com.moive.MoiveBE.domain.meeting.entity.*;
import com.moive.MoiveBE.domain.meeting.repository.MeetingPurposeRepository;
import com.moive.MoiveBE.domain.meeting.repository.MeetingRepository;
import com.moive.MoiveBE.domain.meeting.repository.ParticipantRepository;
import com.moive.MoiveBE.domain.recommendation.client.GooglePlacesClient;
import com.moive.MoiveBE.domain.recommendation.repository.RecommendedPlaceRepository;
import com.moive.MoiveBE.domain.user.entity.User;
import com.moive.MoiveBE.domain.user.repository.UserRepository;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MeetingDetailServiceTest {

    @Mock private MeetingRepository meetingRepository;
    @Mock private MeetingPurposeRepository meetingPurposeRepository;
    @Mock private ParticipantRepository participantRepository;
    @Mock private UserRepository userRepository;
    @Mock private RecommendedPlaceRepository recommendedPlaceRepository;
    @Mock private GooglePlacesClient googlePlacesClient;

    private MeetingDetailService meetingDetailService;
    private static final Long CURRENT_USER_ID = 1L;
    private static final Long MEETING_ID = 10L;

    @BeforeEach
    void setUp() {
        meetingDetailService = new MeetingDetailService(
                meetingRepository, meetingPurposeRepository, participantRepository,
                userRepository, recommendedPlaceRepository, googlePlacesClient,
                "https://moive.app/invite"
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
    void CONDITION_INPUT_모임_상세를_정상_조회한다() {
        Meeting meeting = mockMeeting(MEETING_ID, MeetingStatus.CONDITION_INPUT, null);
        Participant me = mockParticipant(1L, CURRENT_USER_ID, ParticipantState.COND_PENDING);
        Participant other = mockParticipant(2L, 2L, ParticipantState.COND_PENDING);
        MeetingPurpose purpose = mockPurpose(PurposeType.NETWORKING);
        User user1 = mockUser(CURRENT_USER_ID, "나");
        User user2 = mockUser(2L, "상대");

        when(meetingRepository.findById(MEETING_ID)).thenReturn(Optional.of(meeting));
        when(participantRepository.findByMeetingIdAndUserIdAndLeftAtIsNull(MEETING_ID, CURRENT_USER_ID))
                .thenReturn(Optional.of(me));
        when(meetingPurposeRepository.findByMeetingId(MEETING_ID)).thenReturn(Optional.of(purpose));
        when(participantRepository.findAllByMeetingIdAndLeftAtIsNullOrderByJoinedAtAsc(MEETING_ID))
                .thenReturn(List.of(me, other));
        when(userRepository.findAllById(any())).thenReturn(List.of(user1, user2));

        MeetingDetailResponse response = meetingDetailService.getMeetingDetail(MEETING_ID);

        assertThat(response.meetingId()).isEqualTo(MEETING_ID);
        assertThat(response.status()).isEqualTo("CONDITION_INPUT");
        assertThat(response.statusLabel()).isEqualTo("조건 입력중");
        assertThat(response.purposeType()).isEqualTo("NETWORKING");
        assertThat(response.canShare()).isTrue();
        assertThat(response.participants()).hasSize(2);
        assertThat(response.confirmedPlace()).isNull();
        assertThat(response.travelSummary()).isNull();
    }

    @Test
    void COMPLETED_모임이면_inviteCode가_null이고_canShare가_false이다() {
        Meeting meeting = mockMeeting(MEETING_ID, MeetingStatus.COMPLETED, null);
        Participant me = mockParticipant(1L, CURRENT_USER_ID, ParticipantState.CONFIRMED);
        MeetingPurpose purpose = mockPurpose(PurposeType.FRIENDLY);
        User user = mockUser(CURRENT_USER_ID, "나");

        when(meetingRepository.findById(MEETING_ID)).thenReturn(Optional.of(meeting));
        when(participantRepository.findByMeetingIdAndUserIdAndLeftAtIsNull(MEETING_ID, CURRENT_USER_ID))
                .thenReturn(Optional.of(me));
        when(meetingPurposeRepository.findByMeetingId(MEETING_ID)).thenReturn(Optional.of(purpose));
        when(participantRepository.findAllByMeetingIdAndLeftAtIsNullOrderByJoinedAtAsc(MEETING_ID))
                .thenReturn(List.of(me));
        when(userRepository.findAllById(any())).thenReturn(List.of(user));

        MeetingDetailResponse response = meetingDetailService.getMeetingDetail(MEETING_ID);

        assertThat(response.inviteCode()).isNull();
        assertThat(response.inviteUrl()).isNull();
        assertThat(response.canShare()).isFalse();
    }

    @Test
    void recommendationReady는_전원_COND_DONE일_때만_true이다() {
        Meeting meeting = mockMeeting(MEETING_ID, MeetingStatus.CONDITION_INPUT, null);
        Participant p1 = mockParticipant(1L, CURRENT_USER_ID, ParticipantState.COND_DONE);
        Participant p2 = mockParticipant(2L, 2L, ParticipantState.COND_DONE);
        MeetingPurpose purpose = mockPurpose(PurposeType.NETWORKING);
        User user1 = mockUser(CURRENT_USER_ID, "나");
        User user2 = mockUser(2L, "상대");

        when(meetingRepository.findById(MEETING_ID)).thenReturn(Optional.of(meeting));
        when(participantRepository.findByMeetingIdAndUserIdAndLeftAtIsNull(MEETING_ID, CURRENT_USER_ID))
                .thenReturn(Optional.of(p1));
        when(meetingPurposeRepository.findByMeetingId(MEETING_ID)).thenReturn(Optional.of(purpose));
        when(participantRepository.findAllByMeetingIdAndLeftAtIsNullOrderByJoinedAtAsc(MEETING_ID))
                .thenReturn(List.of(p1, p2));
        when(userRepository.findAllById(any())).thenReturn(List.of(user1, user2));

        MeetingDetailResponse response = meetingDetailService.getMeetingDetail(MEETING_ID);

        assertThat(response.recommendationReady()).isTrue();
    }

    @Test
    void recommendationReady는_일부만_COND_DONE이면_false이다() {
        Meeting meeting = mockMeeting(MEETING_ID, MeetingStatus.CONDITION_INPUT, null);
        Participant p1 = mockParticipant(1L, CURRENT_USER_ID, ParticipantState.COND_DONE);
        Participant p2 = mockParticipant(2L, 2L, ParticipantState.COND_PENDING);
        MeetingPurpose purpose = mockPurpose(PurposeType.NETWORKING);
        User user1 = mockUser(CURRENT_USER_ID, "나");
        User user2 = mockUser(2L, "상대");

        when(meetingRepository.findById(MEETING_ID)).thenReturn(Optional.of(meeting));
        when(participantRepository.findByMeetingIdAndUserIdAndLeftAtIsNull(MEETING_ID, CURRENT_USER_ID))
                .thenReturn(Optional.of(p1));
        when(meetingPurposeRepository.findByMeetingId(MEETING_ID)).thenReturn(Optional.of(purpose));
        when(participantRepository.findAllByMeetingIdAndLeftAtIsNullOrderByJoinedAtAsc(MEETING_ID))
                .thenReturn(List.of(p1, p2));
        when(userRepository.findAllById(any())).thenReturn(List.of(user1, user2));

        MeetingDetailResponse response = meetingDetailService.getMeetingDetail(MEETING_ID);

        assertThat(response.recommendationReady()).isFalse();
    }

    @Test
    void 존재하지_않는_모임이면_MEETING_NOT_FOUND_예외가_발생한다() {
        when(meetingRepository.findById(MEETING_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> meetingDetailService.getMeetingDetail(MEETING_ID))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getCustomErrorCode())
                        .isEqualTo(CustomErrorCode.MEETING_NOT_FOUND));
    }

    @Test
    void 모임_참여자가_아니면_NOT_A_PARTICIPANT_예외가_발생한다() {
        Meeting meeting = mockMeeting(MEETING_ID, MeetingStatus.CONDITION_INPUT, null);
        when(meetingRepository.findById(MEETING_ID)).thenReturn(Optional.of(meeting));
        when(participantRepository.findByMeetingIdAndUserIdAndLeftAtIsNull(MEETING_ID, CURRENT_USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> meetingDetailService.getMeetingDetail(MEETING_ID))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getCustomErrorCode())
                        .isEqualTo(CustomErrorCode.NOT_A_PARTICIPANT));
    }

    // --- getMeetingHome 테스트 ---

    @Test
    void CONDITION_INPUT이면_배너_문구와_CTA가_올바르게_조립된다() {
        MeetingHomeResponse response = setupAndCallGetMeetingHome(MeetingStatus.CONDITION_INPUT);

        assertThat(response.homeMessage()).isEqualTo("아직 조건 입력 중이에요!");
        assertThat(response.primaryActionLabel()).isEqualTo("추천 장소 확인");
        assertThat(response.primaryActionEnabled()).isFalse();
    }

    @Test
    void VOTING이면_배너_문구와_CTA가_올바르게_조립된다() {
        MeetingHomeResponse response = setupAndCallGetMeetingHome(MeetingStatus.VOTING);

        assertThat(response.homeMessage()).isEqualTo("이미 조건 입력이 완료된 모임이에요!");
        assertThat(response.primaryActionLabel()).isEqualTo("추천 장소 확인 및 투표");
        assertThat(response.primaryActionEnabled()).isTrue();
    }

    @Test
    void CONFIRMED이면_배너_문구와_CTA가_올바르게_조립된다() {
        MeetingHomeResponse response = setupAndCallGetMeetingHome(MeetingStatus.CONFIRMED);

        assertThat(response.homeMessage()).isEqualTo("모임이 확정됐어요, 모임 정보를 확인해보세요!");
        assertThat(response.primaryActionLabel()).isEqualTo("확정된 모임 보러 가기");
        assertThat(response.primaryActionEnabled()).isTrue();
    }

    @Test
    void getMeetingHome에서_참여자가_아니면_NOT_A_PARTICIPANT_예외가_발생한다() {
        Meeting meeting = mockMeeting(MEETING_ID, MeetingStatus.CONDITION_INPUT, null);
        when(meetingRepository.findById(MEETING_ID)).thenReturn(Optional.of(meeting));
        when(participantRepository.findByMeetingIdAndUserIdAndLeftAtIsNull(MEETING_ID, CURRENT_USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> meetingDetailService.getMeetingHome(MEETING_ID))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getCustomErrorCode())
                        .isEqualTo(CustomErrorCode.NOT_A_PARTICIPANT));
    }

    private MeetingHomeResponse setupAndCallGetMeetingHome(MeetingStatus status) {
        Meeting meeting = mockMeeting(MEETING_ID, status, null);
        Participant me = mockParticipant(1L, CURRENT_USER_ID, ParticipantState.COND_PENDING);
        MeetingPurpose purpose = mockPurpose(PurposeType.NETWORKING);
        User user = mockUser(CURRENT_USER_ID, "나");

        when(meetingRepository.findById(MEETING_ID)).thenReturn(Optional.of(meeting));
        when(participantRepository.findByMeetingIdAndUserIdAndLeftAtIsNull(MEETING_ID, CURRENT_USER_ID))
                .thenReturn(Optional.of(me));
        when(meetingPurposeRepository.findByMeetingId(MEETING_ID)).thenReturn(Optional.of(purpose));
        when(participantRepository.findAllByMeetingIdAndLeftAtIsNullOrderByJoinedAtAsc(MEETING_ID))
                .thenReturn(List.of(me));
        when(userRepository.findAllById(any())).thenReturn(List.of(user));

        return meetingDetailService.getMeetingHome(MEETING_ID);
    }

    private Meeting mockMeeting(Long id, MeetingStatus status, Long confirmedPlaceId) {
        Meeting meeting = mock(Meeting.class);
        lenient().when(meeting.getId()).thenReturn(id);
        lenient().when(meeting.getName()).thenReturn("테스트 모임");
        lenient().when(meeting.getStatus()).thenReturn(status);
        lenient().when(meeting.getConfirmedPlaceId()).thenReturn(confirmedPlaceId);
        lenient().when(meeting.getInviteCode()).thenReturn("ABC123XY");
        lenient().when(meeting.getScheduledDate()).thenReturn(null);
        lenient().when(meeting.getScheduledTime()).thenReturn(null);
        return meeting;
    }

    private Participant mockParticipant(Long participantId, Long userId, ParticipantState state) {
        Participant p = mock(Participant.class);
        lenient().when(p.getId()).thenReturn(participantId);
        lenient().when(p.getUserId()).thenReturn(userId);
        lenient().when(p.getState()).thenReturn(state);
        return p;
    }

    private MeetingPurpose mockPurpose(PurposeType purposeType) {
        MeetingPurpose purpose = mock(MeetingPurpose.class);
        lenient().when(purpose.getPurposeType()).thenReturn(purposeType);
        return purpose;
    }

    private User mockUser(Long userId, String nickname) {
        User user = mock(User.class);
        lenient().when(user.getId()).thenReturn(userId);
        lenient().when(user.getNickname()).thenReturn(nickname);
        lenient().when(user.getProfileImageUrl()).thenReturn(null);
        return user;
    }
}
