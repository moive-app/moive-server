package com.moive.MoiveBE.domain.recommendation.service;

import com.moive.MoiveBE.domain.recommendation.dto.AreaCandidate;
import com.moive.MoiveBE.domain.recommendation.dto.AreaCandidateResponse;
import com.moive.MoiveBE.domain.recommendation.dto.AreaCenter;
import com.moive.MoiveBE.domain.recommendation.dto.GooglePlaceSearchResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AreaCandidateServiceTest {

    @Test
    void 유효한_추천_지역_후보만_반환한다() {

        AreaCandidateResolver resolver =
                mock(AreaCandidateResolver.class);

        AreaCandidateService service =
                new AreaCandidateService(resolver);

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

        GooglePlaceSearchResponse.Place yeoksam =
                new GooglePlaceSearchResponse.Place(
                        "yeoksam-place-id",
                        new GooglePlaceSearchResponse.Location(
                                37.500643,
                                127.036377
                        )
                );

        GooglePlaceSearchResponse.Place nonhyeon =
                new GooglePlaceSearchResponse.Place(
                        "nonhyeon-place-id",
                        new GooglePlaceSearchResponse.Location(
                                37.5112,
                                127.0285
                        )
                );

        when(resolver.resolve(
                "역삼동",
                center.latitude(),
                center.longitude()
        )).thenReturn(yeoksam);

        when(resolver.resolve(
                "논현동",
                center.latitude(),
                center.longitude()
        )).thenReturn(nonhyeon);

        // 신사동은 Google 검증 실패했다고 가정
        when(resolver.resolve(
                "신사동",
                center.latitude(),
                center.longitude()
        )).thenReturn(null);

        List<AreaCandidate> result =
                service.resolveCandidates(response, center);

        assertThat(result).hasSize(2);

        assertThat(result)
                .extracting(AreaCandidate::name)
                .containsExactly(
                        "역삼동",
                        "논현동"
                );
    }
}