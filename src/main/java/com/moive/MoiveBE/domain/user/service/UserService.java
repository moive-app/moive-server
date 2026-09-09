package com.moive.MoiveBE.domain.user.service;

import com.moive.MoiveBE.domain.user.dto.MyInfoResponse;
import com.moive.MoiveBE.domain.user.entity.User;
import com.moive.MoiveBE.domain.user.repository.UserAgreementRepository;
import com.moive.MoiveBE.domain.user.repository.UserRepository;
import com.moive.MoiveBE.global.exception.CustomErrorCode;
import com.moive.MoiveBE.global.exception.CustomException;
import com.moive.MoiveBE.global.s3.S3ImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final S3ImageService s3ImageService;
    private final UserAgreementRepository userAgreementRepository;

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

        userAgreementRepository.deleteAllByUser(user);

        userRepository.delete(user);
    }
}