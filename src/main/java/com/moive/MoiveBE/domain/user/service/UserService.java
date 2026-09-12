package com.moive.MoiveBE.domain.user.service;

import com.moive.MoiveBE.domain.meeting.entity.Participant;
import com.moive.MoiveBE.domain.meeting.repository.ParticipantPreferenceRepository;
import com.moive.MoiveBE.domain.meeting.repository.ParticipantRepository;
import com.moive.MoiveBE.domain.meeting.repository.PreferenceActivityRepository;
import com.moive.MoiveBE.domain.meeting.service.MeetingLeaveService;
import com.moive.MoiveBE.domain.notification.repository.DeviceTokenRepository;
import com.moive.MoiveBE.domain.notification.repository.NotificationRepository;
import com.moive.MoiveBE.domain.user.dto.MyInfoResponse;
import com.moive.MoiveBE.domain.user.entity.User;
import com.moive.MoiveBE.domain.user.repository.UserAgreementRepository;
import com.moive.MoiveBE.domain.user.repository.UserRepository;
import com.moive.MoiveBE.domain.vote.repository.PlaceVoteRepository;
import com.moive.MoiveBE.global.exception.CustomErrorCode;
import com.moive.MoiveBE.global.exception.CustomException;
import com.moive.MoiveBE.global.s3.S3ImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final S3ImageService s3ImageService;
    private final UserAgreementRepository userAgreementRepository;
    private final ParticipantRepository participantRepository;
    private final MeetingLeaveService meetingLeaveService;
    private final ParticipantPreferenceRepository participantPreferenceRepository;
    private final PreferenceActivityRepository preferenceActivityRepository;
    private final PlaceVoteRepository placeVoteRepository;
    private final NotificationRepository notificationRepository;
    private final DeviceTokenRepository deviceTokenRepository;

    public MyInfoResponse getMyInfo(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new CustomException(CustomErrorCode.USER_NOT_FOUND)
                );

        return new MyInfoResponse(
                user.getNickname(),
                user.getProfileImageUrl(),
                user.getEmail()
        );
    }

    @Transactional
    public MyInfoResponse updateMyProfile(
            Long userId,
            String nickname,
            MultipartFile profileImage
    ) {

        if (nickname == null || !nickname.matches("^[가-힣a-zA-Z]{1,10}$")) {
            throw new CustomException(CustomErrorCode.INVALID_INPUT);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new CustomException(CustomErrorCode.USER_NOT_FOUND)
                );

        String profileImageUrl = user.getProfileImageUrl();

        if (profileImage != null && !profileImage.isEmpty()) {
            String contentType = profileImage.getContentType();

            if (contentType == null || !contentType.startsWith("image/")) {
                throw new CustomException(CustomErrorCode.INVALID_INPUT);
            }

            profileImageUrl = s3ImageService.uploadProfileImage(profileImage);
        }

        user.updateProfile(
                nickname,
                profileImageUrl
        );

        return new MyInfoResponse(
                user.getNickname(),
                user.getProfileImageUrl(),
                user.getEmail()
        );
    }

    @Transactional
    public void withdraw(Long userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new CustomException(CustomErrorCode.USER_NOT_FOUND)
                );

        List<Participant> participants =
                participantRepository
                        .findAllByUserIdAndLeftAtIsNullOrderByIdAsc(userId);

        for (Participant participant : participants) {

            placeVoteRepository.deleteAllByParticipantId(
                    participant.getId()
            );

            participantPreferenceRepository
                    .findByParticipantId(participant.getId())
                            .ifPresent(preference -> {

                                preferenceActivityRepository
                                        .deleteByPreferenceId(
                                                preference.getId()
                                        );

                                participantPreferenceRepository
                                        .delete(preference);
                            });

            meetingLeaveService.leaveMeeting(
                    participant.getMeetingId()
            );
        }

        notificationRepository.deleteAllByUserId(userId);
        deviceTokenRepository.deleteAllByUserId(userId);

        userAgreementRepository.deleteAllByUser(user);

        userRepository.delete(user);
    }
}