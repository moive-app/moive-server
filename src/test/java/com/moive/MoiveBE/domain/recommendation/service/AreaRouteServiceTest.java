package com.moive.MoiveBE.domain.recommendation.service;

import com.moive.MoiveBE.domain.meeting.entity.ParticipantPreference;
import com.moive.MoiveBE.domain.recommendation.client.GoogleRoutesClient;
import com.moive.MoiveBE.domain.recommendation.dto.AreaCandidate;
import com.moive.MoiveBE.domain.recommendation.dto.GoogleRouteMatrixRequest;
import com.moive.MoiveBE.domain.recommendation.dto.GoogleRouteMatrixResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AreaRouteServiceTest {

    @Test
    void 참가자_출발지와_추천_지역으로_이동시간을_조회한다() {

        GoogleRoutesClient googleRoutesClient =
                mock(GoogleRoutesClient.class);

        AreaRouteService areaRouteService =
                new AreaRouteService(
                        googleRoutesClient
                );

        List<ParticipantPreference> preferences =
                List.of(
                        ParticipantPreference.create(
                                1L,
                                "강남역",
                                BigDecimal.valueOf(37.4979),
                                BigDecimal.valueOf(127.0276),
                                60
                        ),
                        ParticipantPreference.create(
                                2L,
                                "잠실역",
                                BigDecimal.valueOf(37.5133),
                                BigDecimal.valueOf(127.1001),
                                60
                        )
                );

        List<AreaCandidate> candidates =
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
                        )
                );

        List<GoogleRouteMatrixResponse> mockResponse =
                List.of();

        when(googleRoutesClient.computeRouteMatrix(any()))
                .thenReturn(mockResponse);

        List<GoogleRouteMatrixResponse> result =
                areaRouteService.calculate(
                        preferences,
                        candidates
                );

        ArgumentCaptor<GoogleRouteMatrixRequest> captor =
                ArgumentCaptor.forClass(
                        GoogleRouteMatrixRequest.class
                );

        verify(googleRoutesClient)
                .computeRouteMatrix(
                        captor.capture()
                );

        GoogleRouteMatrixRequest request =
                captor.getValue();

        assertThat(request.origins())
                .hasSize(2);

        assertThat(request.destinations())
                .hasSize(2);

        assertThat(request.travelMode())
                .isEqualTo("TRANSIT");

        assertThat(result)
                .isSameAs(mockResponse);
    }
}