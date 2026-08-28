package com.moive.MoiveBE.domain.auth.service;

import com.moive.MoiveBE.domain.auth.dto.KakaoUserInfoResponse;
import com.moive.MoiveBE.domain.auth.dto.SignupRequest;
import com.moive.MoiveBE.domain.auth.dto.TokenResponse;
import com.moive.MoiveBE.domain.user.entity.AgreementType;
import com.moive.MoiveBE.domain.user.entity.User;
import com.moive.MoiveBE.domain.user.entity.UserAgreement;
import com.moive.MoiveBE.domain.user.entity.UserStatus;
import com.moive.MoiveBE.domain.user.repository.UserAgreementRepository;
import com.moive.MoiveBE.domain.user.repository.UserRepository;
import com.moive.MoiveBE.global.exception.CustomErrorCode;
import com.moive.MoiveBE.global.exception.CustomException;
import com.moive.MoiveBE.global.jwt.JwtTokenProvider;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceSignupTest {

    @Mock
    private KakaoAuthService kakaoAuthService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserAgreementRepository userAgreementRepository;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                kakaoAuthService,
                userRepository,
                userAgreementRepository,
                jwtTokenProvider
        );
    }

    @Test
    void 회원가입에_성공하면_회원과_약관동의를_저장하고_JWT를_발급한다() {
        // given
        String accessToken = "test-access-token";
        KakaoUserInfoResponse kakaoUser = mockKakaoUser();

        when(kakaoAuthService.getUserInfo(accessToken))
                .thenReturn(kakaoUser);

        when(userRepository.findByKakaoMemberIdAndStatus(
                12345L,
                UserStatus.ACTIVE
        )).thenReturn(Optional.empty());

        SignupRequest request = mockSignupRequest(
                accessToken,
                true,
                true,
                false
        );

        /*
         * Repository를 Mock으로 사용하기 때문에 실제 JPA처럼
         * 저장 후 ID가 자동 생성되지 않는다.
         *
         * 따라서 save()가 호출되면 테스트용 User를 반환하도록 설정한다.
         */
        User savedUser = mock(User.class);

        when(savedUser.getId())
                .thenReturn(1L);

        when(userRepository.saveAndFlush(any(User.class)))
                .thenReturn(savedUser);

        when(jwtTokenProvider.createAccessToken(1L))
                .thenReturn("access-token");

        when(jwtTokenProvider.createRefreshToken(1L))
                .thenReturn("refresh-token");

        // when
        TokenResponse result =
                authService.signup(request);

        // then
        ArgumentCaptor<User> userCaptor =
                ArgumentCaptor.forClass(User.class);

        verify(userRepository)
                .saveAndFlush(userCaptor.capture());

        User createdUser = userCaptor.getValue();

        assertThat(createdUser.getKakaoMemberId())
                .isEqualTo(12345L);

        assertThat(createdUser.getEmail())
                .isEqualTo("test@kakao.com");

        assertThat(createdUser.getNickname())
                .isEqualTo("테스트유저");

        assertThat(createdUser.getStatus())
                .isEqualTo(UserStatus.ACTIVE);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UserAgreement>> agreementCaptor =
                ArgumentCaptor.forClass(List.class);

        verify(userAgreementRepository)
                .saveAll(agreementCaptor.capture());

        List<UserAgreement> agreements =
                agreementCaptor.getValue();

        assertThat(agreements).hasSize(3);

        assertThat(result.accessToken())
                .isEqualTo("access-token");

        assertThat(result.refreshToken())
                .isEqualTo("refresh-token");

        verify(jwtTokenProvider)
                .createAccessToken(1L);

        verify(jwtTokenProvider)
                .createRefreshToken(1L);

        String hashedRefreshToken =
                hashRefreshToken("refresh-token");

        verify(savedUser)
                .updateRefreshToken(
                        eq(hashedRefreshToken),
                        any()
                );
    }

    @Test
    void 이미_가입된_회원이면_회원가입_예외가_발생한다() {
        // given
        String accessToken = "test-access-token";
        KakaoUserInfoResponse kakaoUser = mockKakaoUser();

        when(kakaoAuthService.getUserInfo(accessToken))
                .thenReturn(kakaoUser);

        when(userRepository.findByKakaoMemberIdAndStatus(
                12345L,
                UserStatus.ACTIVE
        )).thenReturn(Optional.of(mock(User.class)));

        SignupRequest request = mockSignupRequest(
                accessToken,
                true,
                true,
                false
        );

        // when & then
        assertThatThrownBy(() ->
                authService.signup(request)
        )
                .isInstanceOf(CustomException.class)
                .satisfies(exception -> {
                    CustomException customException =
                            (CustomException) exception;

                    assertThat(customException.getCustomErrorCode())
                            .isEqualTo(
                                    CustomErrorCode.ALREADY_REGISTERED_USER
                            );
                });

        verify(userRepository, never())
                .saveAndFlush(any(User.class));

        verifyNoInteractions(userAgreementRepository);
        verifyNoInteractions(jwtTokenProvider);
    }

    @Test
    void 필수_약관에_동의하지_않으면_회원가입_예외가_발생한다() {
        // given
        String accessToken = "test-access-token";
        KakaoUserInfoResponse kakaoUser = mockKakaoUser();

        when(kakaoAuthService.getUserInfo(accessToken))
                .thenReturn(kakaoUser);

        when(userRepository.findByKakaoMemberIdAndStatus(
                12345L,
                UserStatus.ACTIVE
        )).thenReturn(Optional.empty());

        SignupRequest request = mockSignupRequest(
                accessToken,
                true,
                false,
                false
        );

        // when & then
        assertThatThrownBy(() ->
                authService.signup(request)
        )
                .isInstanceOf(CustomException.class)
                .satisfies(exception -> {
                    CustomException customException =
                            (CustomException) exception;

                    assertThat(customException.getCustomErrorCode())
                            .isEqualTo(
                                    CustomErrorCode.REQUIRED_AGREEMENT_NOT_ACCEPTED
                            );
                });

        verify(userRepository, never())
                .saveAndFlush(any(User.class));

        verifyNoInteractions(userAgreementRepository);
        verifyNoInteractions(jwtTokenProvider);
    }

    @Test
    void 동일한_약관_유형이_중복되면_회원가입_예외가_발생한다() {
        // given
        String accessToken = "test-access-token";
        KakaoUserInfoResponse kakaoUser = mockKakaoUser();

        when(kakaoAuthService.getUserInfo(accessToken))
                .thenReturn(kakaoUser);

        when(userRepository.findByKakaoMemberIdAndStatus(
                12345L,
                UserStatus.ACTIVE
        )).thenReturn(Optional.empty());

        SignupRequest request = new SignupRequest(
                accessToken,
                List.of(
                        new SignupRequest.AgreementRequest(
                                AgreementType.SERVICE,
                                "1.0",
                                true
                        ),
                        new SignupRequest.AgreementRequest(
                                AgreementType.SERVICE,
                                "1.0",
                                false
                        ),
                        new SignupRequest.AgreementRequest(
                                AgreementType.PRIVACY,
                                "1.0",
                                true
                        )
                )
        );

        // when & then
        assertThatThrownBy(() ->
                authService.signup(request)
        )
                .isInstanceOf(CustomException.class)
                .satisfies(exception -> {
                    CustomException customException =
                            (CustomException) exception;

                    assertThat(customException.getCustomErrorCode())
                            .isEqualTo(
                                    CustomErrorCode.DUPLICATE_AGREEMENT_TYPE
                            );
                });

        verify(userRepository, never())
                .saveAndFlush(any(User.class));

        verifyNoInteractions(userAgreementRepository);
        verifyNoInteractions(jwtTokenProvider);
    }
    @Test
    void 약관_버전이_유효하지_않으면_회원가입_예외가_발생한다() {
        // given
        String accessToken = "test-access-token";
        KakaoUserInfoResponse kakaoUser = mockKakaoUser();

        when(kakaoAuthService.getUserInfo(accessToken))
                .thenReturn(kakaoUser);

        when(userRepository.findByKakaoMemberIdAndStatus(
                12345L,
                UserStatus.ACTIVE
        )).thenReturn(Optional.empty());

        SignupRequest request = new SignupRequest(
                accessToken,
                List.of(
                        new SignupRequest.AgreementRequest(
                                AgreementType.SERVICE,
                                "2.0",
                                true
                        ),
                        new SignupRequest.AgreementRequest(
                                AgreementType.PRIVACY,
                                "1.0",
                                true
                        ),
                        new SignupRequest.AgreementRequest(
                                AgreementType.MARKETING,
                                "1.0",
                                false
                        )
                )
        );

        // when & then
        assertThatThrownBy(() ->
                authService.signup(request)
        )
                .isInstanceOf(CustomException.class)
                .satisfies(exception -> {
                    CustomException customException =
                            (CustomException) exception;

                    assertThat(customException.getCustomErrorCode())
                            .isEqualTo(
                                    CustomErrorCode.INVALID_AGREEMENT_VERSION
                            );
                });

        verify(userRepository, never())
                .saveAndFlush(any(User.class));

        verifyNoInteractions(userAgreementRepository);
        verifyNoInteractions(jwtTokenProvider);
    }
    @Test
    void 동시_회원가입으로_카카오_ID_중복이_발생하면_이미_가입된_회원_예외가_발생한다() {
        // given
        String accessToken = "test-access-token";
        KakaoUserInfoResponse kakaoUser = mockKakaoUser();

        when(kakaoAuthService.getUserInfo(accessToken))
                .thenReturn(kakaoUser);

        // 사전 조회 시점에는 아직 가입된 회원이 없는 상황
        when(userRepository.findByKakaoMemberIdAndStatus(
                12345L,
                UserStatus.ACTIVE
        )).thenReturn(Optional.empty());

        SignupRequest request = mockSignupRequest(
                accessToken,
                true,
                true,
                false
        );

        // 실제 INSERT 시점에 다른 요청이 먼저 가입을 완료해 UNIQUE 충돌 발생
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "Duplicate kakaoMemberId"
                ));

        // when & then
        assertThatThrownBy(() ->
                authService.signup(request)
        )
                .isInstanceOf(CustomException.class)
                .satisfies(exception -> {
                    CustomException customException =
                            (CustomException) exception;

                    assertThat(customException.getCustomErrorCode())
                            .isEqualTo(
                                    CustomErrorCode.ALREADY_REGISTERED_USER
                            );
                });

        verify(userRepository)
                .saveAndFlush(any(User.class));

        verifyNoInteractions(userAgreementRepository);
        verifyNoInteractions(jwtTokenProvider);
    }

    private KakaoUserInfoResponse mockKakaoUser() {

        KakaoUserInfoResponse kakaoUser =
                mock(KakaoUserInfoResponse.class);

        KakaoUserInfoResponse.Properties properties =
                mock(KakaoUserInfoResponse.Properties.class);

        KakaoUserInfoResponse.KakaoAccount kakaoAccount =
                mock(KakaoUserInfoResponse.KakaoAccount.class);

        when(kakaoUser.id())
                .thenReturn(12345L);

        when(kakaoUser.properties())
                .thenReturn(properties);

        when(properties.nickname())
                .thenReturn("테스트유저");

        when(properties.profileImage())
                .thenReturn("https://example.com/profile.jpg");

        when(kakaoUser.kakaoAccount())
                .thenReturn(kakaoAccount);

        when(kakaoAccount.email())
                .thenReturn("test@kakao.com");

        return kakaoUser;
    }

    private SignupRequest mockSignupRequest(
            String accessToken,
            boolean serviceAgreed,
            boolean privacyAgreed,
            boolean marketingAgreed
    ) {

        SignupRequest request =
                mock(SignupRequest.class);

        SignupRequest.AgreementRequest service =
                mock(SignupRequest.AgreementRequest.class);

        SignupRequest.AgreementRequest privacy =
                mock(SignupRequest.AgreementRequest.class);

        SignupRequest.AgreementRequest marketing =
                mock(SignupRequest.AgreementRequest.class);

        lenient().when(request.accessToken())
                .thenReturn(accessToken);

        lenient().when(service.type())
                .thenReturn(AgreementType.SERVICE);

        lenient().when(service.version())
                .thenReturn("1.0");

        lenient().when(service.agreed())
                .thenReturn(serviceAgreed);

        lenient().when(privacy.type())
                .thenReturn(AgreementType.PRIVACY);

        lenient().when(privacy.version())
                .thenReturn("1.0");

        lenient().when(privacy.agreed())
                .thenReturn(privacyAgreed);

        lenient().when(marketing.type())
                .thenReturn(AgreementType.MARKETING);

        lenient().when(marketing.version())
                .thenReturn("1.0");

        lenient().when(marketing.agreed())
                .thenReturn(marketingAgreed);

        lenient().when(request.agreements())
                .thenReturn(
                        List.of(
                                service,
                                privacy,
                                marketing
                        )
                );

        return request;
    }

    private String hashRefreshToken(String refreshToken) {
        try {
            MessageDigest messageDigest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hashedBytes = messageDigest.digest(
                    refreshToken.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of()
                    .formatHex(hashedBytes);

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}