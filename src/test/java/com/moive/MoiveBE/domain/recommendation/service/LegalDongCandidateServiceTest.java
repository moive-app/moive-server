package com.moive.MoiveBE.domain.recommendation.service;

import com.moive.MoiveBE.domain.recommendation.client.VWorldLegalDongClient;
import com.moive.MoiveBE.domain.recommendation.dto.AreaCandidate;
import com.moive.MoiveBE.domain.recommendation.dto.AreaCenter;
import com.moive.MoiveBE.domain.recommendation.dto.VWorldLegalDong;
import com.moive.MoiveBE.domain.recommendation.dto.VWorldSigunguResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LegalDongCandidateServiceTest {

    @Mock
    private LegalDongBboxService legalDongBboxService;

    @Mock
    private VWorldLegalDongClient vWorldLegalDongClient;

    @Mock
    private VWorldLegalDongParser vWorldLegalDongParser;

    private LegalDongCandidateService legalDongCandidateService;

    @BeforeEach
    void setUp() {
        legalDongCandidateService =
                new LegalDongCandidateService(
                        legalDongBboxService,
                        vWorldLegalDongClient,
                        vWorldLegalDongParser
                );
    }

    @Test
    void 법정동_후보에_전체_행정구역명을_searchName으로_생성한다() {

        // given
        AreaCenter center =
                new AreaCenter(
                        37.5599985,
                        127.6927097
                );

        when(legalDongBboxService.create(center))
                .thenReturn("test-bbox");

        when(vWorldLegalDongClient.getLegalDongs("test-bbox"))
                .thenReturn("<test></test>");

        VWorldLegalDong namMyeon =
                new VWorldLegalDong(
                        "51720330",
                        "남면",
                        "51720",
                        37.5385231,
                        127.6729582
                );

        when(vWorldLegalDongParser.parse(anyString()))
                .thenReturn(List.of(namMyeon));

        VWorldSigunguResponse.Properties properties =
                new VWorldSigunguResponse.Properties(
                        "51720",
                        "홍천군",
                        "강원특별자치도 홍천군"
                );

        VWorldSigunguResponse.Feature feature =
                new VWorldSigunguResponse.Feature(
                        properties
                );

        VWorldSigunguResponse.FeatureCollection featureCollection =
                new VWorldSigunguResponse.FeatureCollection(
                        List.of(feature)
                );

        VWorldSigunguResponse.Result result =
                new VWorldSigunguResponse.Result(
                        featureCollection
                );

        VWorldSigunguResponse.Response response =
                new VWorldSigunguResponse.Response(
                        "OK",
                        result
                );

        when(vWorldLegalDongClient.getSigungu("51720"))
                .thenReturn(
                        new VWorldSigunguResponse(response)
                );

        // when
        List<AreaCandidate> candidates =
                legalDongCandidateService.generate(center);

        // then
        assertThat(candidates).hasSize(1);

        AreaCandidate candidate = candidates.get(0);

        assertThat(candidate.name())
                .isEqualTo("남면");

        assertThat(candidate.searchName())
                .isEqualTo("강원특별자치도 홍천군 남면");

        assertThat(candidate.signguCode())
                .isEqualTo("51720");
    }
}