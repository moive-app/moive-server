package com.moive.MoiveBE.domain.user.service;

import com.moive.MoiveBE.domain.meeting.entity.Participant;
import com.moive.MoiveBE.domain.meeting.entity.ParticipantPreference;
import com.moive.MoiveBE.domain.user.dto.MyInfoResponse;
import com.moive.MoiveBE.domain.user.entity.User;
import com.moive.MoiveBE.domain.user.repository.UserAgreementRepository;
import com.moive.MoiveBE.domain.user.repository.UserRepository;
import com.moive.MoiveBE.global.exception.CustomException;
import com.moive.MoiveBE.domain.meeting.repository.ParticipantPreferenceRepository;
import com.moive.MoiveBE.domain.meeting.repository.ParticipantRepository;
import com.moive.MoiveBE.domain.meeting.repository.PreferenceActivityRepository;
import com.moive.MoiveBE.domain.meeting.service.MeetingLeaveService;
import com.moive.MoiveBE.domain.notification.repository.DeviceTokenRepository;
import com.moive.MoiveBE.domain.notification.repository.NotificationRepository;
import com.moive.MoiveBE.domain.vote.repository.PlaceVoteRepository;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.moive.MoiveBE.global.s3.S3ImageService;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private S3ImageService s3ImageService;
    @Mock
    private UserAgreementRepository userAgreementRepository;
    @Mock
    private ParticipantRepository participantRepository;

    @Mock
    private MeetingLeaveService meetingLeaveService;

    @Mock
    private ParticipantPreferenceRepository participantPreferenceRepository;

    @Mock
    private PreferenceActivityRepository preferenceActivityRepository;

    @Mock
    private PlaceVoteRepository placeVoteRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private DeviceTokenRepository deviceTokenRepository;



    @InjectMocks
    private UserService userService;

    @Test
    void 내_정보를_조회한다() {

        Long userId = 1L;

        User user = User.createKakaoUser(
                123456789L,
                "test@kakao.com",
                "한재경",
                "https://example.com/profile.jpg"
        );

        given(userRepository.findById(userId))
                .willReturn(Optional.of(user));

        MyInfoResponse response =
                userService.getMyInfo(userId);

        assertThat(response.nickname())
                .isEqualTo("한재경");
        assertThat(response.profileImageUrl())
                .isEqualTo("https://example.com/profile.jpg");
        assertThat(response.email())
                .isEqualTo("test@kakao.com");
    }

    @Test
    void 프로필을_수정한다() {
        Long userId = 1L;

        User user = User.createKakaoUser(
                123456789L,
                "test@kakao.com",
                "기존닉네임",
                "https://example.com/old.jpg"
        );

        MockMultipartFile profileImage = new MockMultipartFile(
                "profileImage",
                "profile.jpg",
                "image/jpeg",
                "test-image".getBytes()
        );

        given(userRepository.findById(userId))
                .willReturn(Optional.of(user));

        given(s3ImageService.uploadProfileImage(profileImage))
                .willReturn("https://example.com/new.jpg");

        MyInfoResponse response = userService.updateMyProfile(
                userId,
                "새닉네임",
                profileImage
        );

        assertThat(response.nickname()).isEqualTo("새닉네임");
        assertThat(response.profileImageUrl()).isEqualTo("https://example.com/new.jpg");
        assertThat(response.email()).isEqualTo("test@kakao.com");
    }

    @Test
    void 프로필_이미지_없이_닉네임만_수정한다() {
        Long userId = 1L;

        User user = User.createKakaoUser(
                123456789L,
                "test@kakao.com",
                "기존닉네임",
                "https://example.com/old.jpg"
        );

        given(userRepository.findById(userId))
                .willReturn(Optional.of(user));

        MyInfoResponse response = userService.updateMyProfile(
                userId,
                "새닉네임",
                null
        );

        assertThat(response.nickname()).isEqualTo("새닉네임");
        assertThat(response.profileImageUrl()).isEqualTo("https://example.com/old.jpg");
        assertThat(response.email()).isEqualTo("test@kakao.com");
    }
    @Test
    void 잘못된_닉네임으로_프로필을_수정할_수_없다() {
        Long userId = 1L;

        assertThatThrownBy(() ->
                userService.updateMyProfile(
                        userId,
                        "잘못된 닉네임!",
                        null
                )
        ).isInstanceOf(CustomException.class);
    }
    @Test
    void 이미지가_아닌_파일로_프로필을_수정할_수_없다() {
        Long userId = 1L;

        User user = User.createKakaoUser(
                123456789L,
                "test@kakao.com",
                "기존닉네임",
                "https://example.com/old.jpg"
        );

        MockMultipartFile invalidFile = new MockMultipartFile(
                "profileImage",
                "test.txt",
                "text/plain",
                "test".getBytes()
        );

        given(userRepository.findById(userId))
                .willReturn(Optional.of(user));

        assertThatThrownBy(() ->
                userService.updateMyProfile(
                        userId,
                        "새닉네임",
                        invalidFile
                )
        ).isInstanceOf(CustomException.class);
    }
    @Test
    void 회원_탈퇴를_한다() {
        Long userId = 1L;

        User user = User.createKakaoUser(
                123456789L,
                "test@kakao.com",
                "한재경",
                "https://example.com/profile.jpg"
        );

        given(userRepository.findById(userId))
                .willReturn(Optional.of(user));

        given(participantRepository
                .findAllByUserIdAndLeftAtIsNullOrderByIdAsc(userId))
                .willReturn(List.of());

        userService.withdraw(userId);

        then(notificationRepository)
                .should()
                .deleteAllByUserId(userId);

        then(deviceTokenRepository)
                .should()
                .deleteAllByUserId(userId);

        then(userAgreementRepository)
                .should()
                .deleteAllByUser(user);

        then(userRepository)
                .should()
                .delete(user);
    }

    @Test
    void 존재하지_않는_회원은_탈퇴할_수_없다() {
        Long userId = 1L;

        given(userRepository.findById(userId))
                .willReturn(Optional.empty());

        assertThatThrownBy(() ->
                userService.withdraw(userId)
        ).isInstanceOf(CustomException.class);
    }

    @Test
    void 참여중인_모임이_있는_회원은_관련_데이터를_삭제하고_탈퇴한다() {
        Long userId = 1L;
        Long participantId = 10L;
        Long meetingId = 100L;

        User user = User.createKakaoUser(
                123456789L,
                "test@kakao.com",
                "한재경",
                "https://example.com/profile.jpg"
        );

        Participant participant = mock(Participant.class);

        given(participant.getId())
                .willReturn(participantId);

        given(participant.getMeetingId())
                .willReturn(meetingId);

        given(userRepository.findById(userId))
                .willReturn(Optional.of(user));

        given(participantRepository
                .findAllByUserIdAndLeftAtIsNullOrderByIdAsc(userId))
                .willReturn(List.of(participant));

        given(participantPreferenceRepository
                .findByParticipantId(participantId))
                .willReturn(Optional.empty());

        userService.withdraw(userId);

        then(placeVoteRepository)
                .should()
                .deleteAllByParticipantId(participantId);

        then(meetingLeaveService)
                .should()
                .leaveMeeting(meetingId);

        then(notificationRepository)
                .should()
                .deleteAllByUserId(userId);

        then(deviceTokenRepository)
                .should()
                .deleteAllByUserId(userId);

        then(userAgreementRepository)
                .should()
                .deleteAllByUser(user);

        then(userRepository)
                .should()
                .delete(user);
    }

    @Test
    void 회원_탈퇴시_참여자_취향과_활동_취향을_삭제한다() {
        Long userId = 1L;
        Long participantId = 10L;
        Long meetingId = 100L;
        Long preferenceId = 20L;

        User user = User.createKakaoUser(
                123456789L,
                "test@kakao.com",
                "한재경",
                "https://example.com/profile.jpg"
        );

        Participant participant = mock(Participant.class);
        ParticipantPreference preference = mock(ParticipantPreference.class);

        given(participant.getId())
                .willReturn(participantId);

        given(participant.getMeetingId())
                .willReturn(meetingId);

        given(preference.getId())
                .willReturn(preferenceId);

        given(userRepository.findById(userId))
                .willReturn(Optional.of(user));

        given(participantRepository
                .findAllByUserIdAndLeftAtIsNullOrderByIdAsc(userId))
                .willReturn(List.of(participant));

        given(participantPreferenceRepository
                .findByParticipantId(participantId))
                .willReturn(Optional.of(preference));

        userService.withdraw(userId);

        then(placeVoteRepository)
                .should()
                .deleteAllByParticipantId(participantId);

        then(preferenceActivityRepository)
                .should()
                .deleteByPreferenceId(preferenceId);

        then(participantPreferenceRepository)
                .should()
                .delete(preference);

        then(meetingLeaveService)
                .should()
                .leaveMeeting(meetingId);

        then(notificationRepository)
                .should()
                .deleteAllByUserId(userId);

        then(deviceTokenRepository)
                .should()
                .deleteAllByUserId(userId);

        then(userAgreementRepository)
                .should()
                .deleteAllByUser(user);

        then(userRepository)
                .should()
                .delete(user);
    }
}