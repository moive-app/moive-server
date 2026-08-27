package com.moive.MoiveBE.domain.auth.service;

import com.moive.MoiveBE.domain.auth.dto.KakaoUserInfoResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import com.moive.MoiveBE.global.exception.CustomErrorCode;
import com.moive.MoiveBE.global.exception.CustomException;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class KakaoAuthServiceTest {

    private KakaoAuthService kakaoAuthService;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();

        mockServer = MockRestServiceServer
                .bindTo(builder)
                .build();

        RestClient restClient = builder.build();

        kakaoAuthService = new KakaoAuthService(restClient);
    }

    @Test
    void 카카오_사용자_정보_조회에_성공한다() {
        // given
        String accessToken = "test-access-token";

        String responseBody = """
                {
                  "id": 12345,
                  "properties": {
                    "nickname": "테스트유저",
                    "profile_image": "https://example.com/profile.jpg"
                  }
                }
                """;

        mockServer.expect(
                        requestTo("https://kapi.kakao.com/v2/user/me")
                )
                .andExpect(
                        header(
                                "Authorization",
                                "Bearer " + accessToken
                        )
                )
                .andRespond(
                        withSuccess(
                                responseBody,
                                MediaType.APPLICATION_JSON
                        )
                );

        // when
        KakaoUserInfoResponse result =
                kakaoAuthService.getUserInfo(accessToken);

        // then
        assertThat(result.id()).isEqualTo(12345L);
        assertThat(result.properties().nickname())
                .isEqualTo("테스트유저");
        assertThat(result.properties().profileImage())
                .isEqualTo("https://example.com/profile.jpg");

        mockServer.verify();
    }

    @Test
    void 잘못된_카카오_토큰이면_예외가_발생한다() {
        // given
        String accessToken = "invalid-token";

        mockServer.expect(
                        requestTo("https://kapi.kakao.com/v2/user/me")
                )
                .andExpect(
                        header(
                                "Authorization",
                                "Bearer " + accessToken
                        )
                )
                .andRespond(
                        withStatus(HttpStatus.UNAUTHORIZED)
                );

        // when & then
        assertThatThrownBy(() ->
                kakaoAuthService.getUserInfo(accessToken)
        )
                .isInstanceOf(CustomException.class)
                .satisfies(exception -> {
                    CustomException customException =
                            (CustomException) exception;

                    assertThat(customException.getCustomErrorCode())
                            .isEqualTo(CustomErrorCode.INVALID_KAKAO_TOKEN);
                });

        mockServer.verify();
    }
}