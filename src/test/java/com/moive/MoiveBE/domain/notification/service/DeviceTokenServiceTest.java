package com.moive.MoiveBE.domain.notification.service;

import com.moive.MoiveBE.domain.notification.dto.RegisterDeviceTokenRequest;
import com.moive.MoiveBE.domain.notification.entity.DeviceToken;
import com.moive.MoiveBE.domain.notification.repository.DeviceTokenRepository;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeviceTokenServiceTest {

    @Mock private DeviceTokenRepository deviceTokenRepository;

    private DeviceTokenService deviceTokenService;

    private static final Long USER_ID = 1L;
    private static final String DEVICE_ID = "device-uuid-1234";
    private static final String FCM_TOKEN = "fcm-token-abc";

    @BeforeEach
    void setUp() {
        deviceTokenService = new DeviceTokenService(deviceTokenRepository);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(USER_ID, null, List.of(new SimpleGrantedAuthority("ROLE_USER")))
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 신규_기기_토큰을_정상_등록한다() {
        when(deviceTokenRepository.findByDeviceId(DEVICE_ID)).thenReturn(Optional.empty());

        deviceTokenService.registerToken(new RegisterDeviceTokenRequest(FCM_TOKEN, DEVICE_ID));

        ArgumentCaptor<DeviceToken> captor = ArgumentCaptor.forClass(DeviceToken.class);
        verify(deviceTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getFcmToken()).isEqualTo(FCM_TOKEN);
        assertThat(captor.getValue().getDeviceId()).isEqualTo(DEVICE_ID);
    }

    @Test
    void 동일_기기_토큰이_있으면_갱신한다() {
        DeviceToken existing = mock(DeviceToken.class);
        when(deviceTokenRepository.findByDeviceId(DEVICE_ID)).thenReturn(Optional.of(existing));

        deviceTokenService.registerToken(new RegisterDeviceTokenRequest("new-token", DEVICE_ID));

        verify(existing).updateFcmToken("new-token");
        verify(deviceTokenRepository, never()).save(any());
    }

    @Test
    void fcmToken이_null이면_DEVICE_TOKEN_MISSING_예외가_발생한다() {
        assertThatThrownBy(() -> deviceTokenService.registerToken(new RegisterDeviceTokenRequest(null, DEVICE_ID)))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getCustomErrorCode())
                        .isEqualTo(CustomErrorCode.DEVICE_TOKEN_MISSING));
    }

    @Test
    void deviceId가_빈값이면_DEVICE_TOKEN_MISSING_예외가_발생한다() {
        assertThatThrownBy(() -> deviceTokenService.registerToken(new RegisterDeviceTokenRequest(FCM_TOKEN, "")))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getCustomErrorCode())
                        .isEqualTo(CustomErrorCode.DEVICE_TOKEN_MISSING));
    }

    @Test
    void 기기_토큰을_정상_삭제한다() {
        DeviceToken token = mock(DeviceToken.class);
        when(deviceTokenRepository.findByDeviceIdAndUserId(DEVICE_ID, USER_ID)).thenReturn(Optional.of(token));

        deviceTokenService.deleteToken(DEVICE_ID);

        verify(deviceTokenRepository).delete(token);
    }

    @Test
    void 등록되지_않은_기기이면_DEVICE_TOKEN_NOT_FOUND_예외가_발생한다() {
        when(deviceTokenRepository.findByDeviceIdAndUserId(DEVICE_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deviceTokenService.deleteToken(DEVICE_ID))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getCustomErrorCode())
                        .isEqualTo(CustomErrorCode.DEVICE_TOKEN_NOT_FOUND));
    }
}
