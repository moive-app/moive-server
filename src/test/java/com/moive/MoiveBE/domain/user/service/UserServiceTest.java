package com.moive.MoiveBE.domain.user.service;

import com.moive.MoiveBE.domain.user.dto.MyInfoResponse;
import com.moive.MoiveBE.domain.user.entity.User;
import com.moive.MoiveBE.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

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
}