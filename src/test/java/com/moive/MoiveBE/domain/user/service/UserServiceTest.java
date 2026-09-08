package com.moive.MoiveBE.domain.user.service;

import com.moive.MoiveBE.domain.user.dto.MyInfoResponse;
import com.moive.MoiveBE.domain.user.entity.User;
import com.moive.MoiveBE.domain.user.repository.UserRepository;
import com.moive.MoiveBE.global.exception.CustomException;
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

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private S3ImageService s3ImageService;

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
}