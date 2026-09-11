package com.moive.MoiveBE.domain.recommendation.service;

import com.moive.MoiveBE.domain.recommendation.client.OpenAIClient;
import com.moive.MoiveBE.domain.recommendation.dto.AreaCandidate;
import com.moive.MoiveBE.domain.recommendation.dto.AreaCandidateResponse;
import com.moive.MoiveBE.domain.recommendation.dto.AreaCenter;
import com.moive.MoiveBE.global.exception.CustomException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AreaCandidateGenerationServiceTest {

    @Test
    void 첫번째_후보가_3개_이상이면_재요청하지_않는다() {

        OpenAIClient openAIClient =
                mock(OpenAIClient.class);

        AreaCandidateService areaCandidateService =
                mock(AreaCandidateService.class);

        AreaCandidateGenerationService service =
                new AreaCandidateGenerationService(
                        openAIClient,
                        areaCandidateService
                );

        AreaCenter center =
                new AreaCenter(37.4979, 127.0276);

        AreaCandidateResponse response =
                new AreaCandidateResponse(
                        List.of(
                                "역삼동",
                                "논현동",
                                "신사동"
                        )
                );

        List<AreaCandidate> validCandidates =
                List.of(
                        new AreaCandidate(
                                "역삼동",
                                "place-1",
                                37.500643,
                                127.036377
                        ),
                        new AreaCandidate(
                                "논현동",
                                "place-2",
                                37.5112,
                                127.0285
                        ),
                        new AreaCandidate(
                                "신사동",
                                "place-3",
                                37.516,
                                127.020
                        )
                );

        when(openAIClient.generateAreaCandidates(center))
                .thenReturn(response);

        when(areaCandidateService.resolveCandidates(
                response,
                center
        )).thenReturn(validCandidates);

        List<AreaCandidate> result =
                service.generate(center);

        assertThat(result).hasSize(3);
    }

    @Test
    void 유효한_후보가_3개_미만이면_한번_재요청한다() {

        OpenAIClient openAIClient =
                mock(OpenAIClient.class);

        AreaCandidateService areaCandidateService =
                mock(AreaCandidateService.class);

        AreaCandidateGenerationService service =
                new AreaCandidateGenerationService(
                        openAIClient,
                        areaCandidateService
                );

        AreaCenter center =
                new AreaCenter(37.4979, 127.0276);

        AreaCandidateResponse firstResponse =
                new AreaCandidateResponse(
                        List.of(
                                "역삼동",
                                "논현동",
                                "신사동"
                        )
                );

        AreaCandidateResponse retryResponse =
                new AreaCandidateResponse(
                        List.of(
                                "서초동",
                                "삼성동",
                                "대치동"
                        )
                );

        List<AreaCandidate> firstCandidates =
                List.of(
                        new AreaCandidate(
                                "역삼동",
                                "place-1",
                                37.500643,
                                127.036377
                        )
                );

        List<AreaCandidate> retryCandidates =
                List.of(
                        new AreaCandidate(
                                "서초동",
                                "place-2",
                                37.4901,
                                127.0086
                        ),
                        new AreaCandidate(
                                "삼성동",
                                "place-3",
                                37.5145,
                                127.0560
                        )
                );

        when(openAIClient.generateAreaCandidates(center))
                .thenReturn(firstResponse);

        when(areaCandidateService.resolveCandidates(
                firstResponse,
                center
        )).thenReturn(firstCandidates);

        when(openAIClient.generateAreaCandidates(
                center,
                firstResponse.areas()
        )).thenReturn(retryResponse);

        when(areaCandidateService.resolveCandidates(
                retryResponse,
                center
        )).thenReturn(retryCandidates);

        List<AreaCandidate> result =
                service.generate(center);

        assertThat(result).hasSize(3);
    }

    @Test
    void 재요청_후에도_후보가_3개_미만이면_예외가_발생한다() {

        OpenAIClient openAIClient =
                mock(OpenAIClient.class);

        AreaCandidateService areaCandidateService =
                mock(AreaCandidateService.class);

        AreaCandidateGenerationService service =
                new AreaCandidateGenerationService(
                        openAIClient,
                        areaCandidateService
                );

        AreaCenter center =
                new AreaCenter(37.4979, 127.0276);

        AreaCandidateResponse firstResponse =
                new AreaCandidateResponse(
                        List.of(
                                "역삼동",
                                "논현동",
                                "신사동"
                        )
                );

        AreaCandidateResponse retryResponse =
                new AreaCandidateResponse(
                        List.of(
                                "서초동",
                                "삼성동",
                                "대치동"
                        )
                );

        List<AreaCandidate> firstCandidates =
                List.of(
                        new AreaCandidate(
                                "역삼동",
                                "place-1",
                                37.500643,
                                127.036377
                        )
                );

        List<AreaCandidate> retryCandidates =
                List.of(
                        new AreaCandidate(
                                "서초동",
                                "place-2",
                                37.4901,
                                127.0086
                        )
                );

        when(openAIClient.generateAreaCandidates(center))
                .thenReturn(firstResponse);

        when(areaCandidateService.resolveCandidates(
                firstResponse,
                center
        )).thenReturn(firstCandidates);

        when(openAIClient.generateAreaCandidates(
                center,
                firstResponse.areas()
        )).thenReturn(retryResponse);

        when(areaCandidateService.resolveCandidates(
                retryResponse,
                center
        )).thenReturn(retryCandidates);

        assertThatThrownBy(
                () -> service.generate(center)
        )
                .isInstanceOf(CustomException.class);
    }
}