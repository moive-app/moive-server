package com.moive.MoiveBE.domain.recommendation.service;

import com.moive.MoiveBE.domain.meeting.entity.ParticipantPreference;
import com.moive.MoiveBE.domain.recommendation.client.GoogleRoutesClient;
import com.moive.MoiveBE.domain.recommendation.dto.GoogleRouteMatrixRequest;
import com.moive.MoiveBE.domain.recommendation.dto.GoogleRouteMatrixResponse;
import com.moive.MoiveBE.domain.recommendation.dto.PlaceCandidate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlaceRouteServiceTest {

    @Mock
    private GoogleRoutesClient googleRoutesClient;

    @InjectMocks
    private PlaceRouteService placeRouteService;

    @Test
    void 참가자_출발지와_장소후보로_이동시간을_조회한다() {

        // given
        ParticipantPreference preference1 =
                mock(ParticipantPreference.class);
        ParticipantPreference preference2 =
                mock(ParticipantPreference.class);

        when(preference1.getDepartureLatitude())
                .thenReturn(new BigDecimal("37.5000000"));
        when(preference1.getDepartureLongitude())
                .thenReturn(new BigDecimal("127.0000000"));

        when(preference2.getDepartureLatitude())
                .thenReturn(new BigDecimal("37.5100000"));
        when(preference2.getDepartureLongitude())
                .thenReturn(new BigDecimal("127.0100000"));

        PlaceCandidate candidate1 = new PlaceCandidate(
                "place-1",
                37.5200000,
                127.0200000,
                0,
                "한식"
        );

        PlaceCandidate candidate2 = new PlaceCandidate(
                "place-2",
                37.5300000,
                127.0300000,
                1,
                "볼링"
        );

        List<GoogleRouteMatrixResponse> expectedResponse = List.of();

        when(googleRoutesClient.computeRouteMatrix(any()))
                .thenReturn(expectedResponse);

        // when
        List<GoogleRouteMatrixResponse> result =
                placeRouteService.calculate(
                        List.of(preference1, preference2),
                        List.of(candidate1, candidate2)
                );

        // then
        ArgumentCaptor<GoogleRouteMatrixRequest> captor =
                ArgumentCaptor.forClass(GoogleRouteMatrixRequest.class);

        verify(googleRoutesClient)
                .computeRouteMatrix(captor.capture());

        GoogleRouteMatrixRequest request = captor.getValue();

        assertThat(request.origins()).hasSize(2);
        assertThat(request.destinations()).hasSize(2);
        assertThat(request.travelMode()).isEqualTo("TRANSIT");

        assertThat(result).isSameAs(expectedResponse);
    }
}