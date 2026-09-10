package com.moive.MoiveBE.domain.recommendation.service;

import com.moive.MoiveBE.domain.recommendation.dto.AreaCenter;
import com.moive.MoiveBE.domain.recommendation.dto.GooglePlaceSearchResponse;
import com.moive.MoiveBE.domain.recommendation.dto.RecommendedAreaListResponse;
import com.moive.MoiveBE.domain.recommendation.entity.RecommendationRun;
import com.moive.MoiveBE.domain.recommendation.entity.RecommendationStatus;
import com.moive.MoiveBE.domain.recommendation.entity.RecommendedArea;
import com.moive.MoiveBE.domain.recommendation.repository.RecommendationRunRepository;
import com.moive.MoiveBE.domain.recommendation.repository.RecommendedAreaRepository;
import com.moive.MoiveBE.global.exception.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecommendedAreaQueryServiceTest {

    @Mock
    private RecommendationRunRepository recommendationRunRepository;

    @Mock
    private RecommendedAreaRepository recommendedAreaRepository;

    @Mock
    private AreaCenterService areaCenterService;

    @Mock
    private AreaCandidateResolver areaCandidateResolver;

    @InjectMocks
    private RecommendedAreaQueryService recommendedAreaQueryService;

    private RecommendationRun run;
    private RecommendedArea area1;
    private RecommendedArea area2;
    private RecommendedArea area3;

    private AreaCenter center;

    @BeforeEach
    void setUp() throws Exception {

        run = RecommendationRun.create(1L);
        setId(run, 10L);
        run.complete();

        area1 = RecommendedArea.create(10L, "역삼동");
        area2 = RecommendedArea.create(10L, "강남동");
        area3 = RecommendedArea.create(10L, "서초동");

        setId(area1, 101L);
        setId(area2, 102L);
        setId(area3, 103L);

        center = new AreaCenter(
                37.4979,
                127.0276
        );
    }

    @Test
    void 추천_지역_TOP3를_조회한다() {

        when(
                recommendationRunRepository
                        .findTopByMeetingIdAndStatusOrderByCreatedAtDesc(
                                1L,
                                RecommendationStatus.COMPLETED
                        )
        ).thenReturn(Optional.of(run));

        when(
                recommendedAreaRepository
                        .findAllByRecommendationRunId(10L)
        ).thenReturn(
                List.of(area1, area2, area3)
        );

        when(
                areaCenterService.calculate(1L)
        ).thenReturn(center);

        when(
                areaCandidateResolver.resolve(
                        "역삼동",
                        center.latitude(),
                        center.longitude()
                )
        ).thenReturn(
                place(
                        37.5006,
                        127.0364
                )
        );

        when(
                areaCandidateResolver.resolve(
                        "강남동",
                        center.latitude(),
                        center.longitude()
                )
        ).thenReturn(
                place(
                        37.4979,
                        127.0276
                )
        );

        when(
                areaCandidateResolver.resolve(
                        "서초동",
                        center.latitude(),
                        center.longitude()
                )
        ).thenReturn(
                place(
                        37.4837,
                        127.0324
                )
        );

        RecommendedAreaListResponse response =
                recommendedAreaQueryService.getRecommendedAreas(1L);

        assertThat(response.areas()).hasSize(3);

        assertThat(response.areas().get(0).recommendedAreaId())
                .isEqualTo(101L);
        assertThat(response.areas().get(0).name())
                .isEqualTo("역삼동");
        assertThat(response.areas().get(0).latitude())
                .isEqualTo(37.5006);
        assertThat(response.areas().get(0).longitude())
                .isEqualTo(127.0364);

        assertThat(response.areas().get(1).recommendedAreaId())
                .isEqualTo(102L);
        assertThat(response.areas().get(1).name())
                .isEqualTo("강남동");

        assertThat(response.areas().get(2).recommendedAreaId())
                .isEqualTo(103L);
        assertThat(response.areas().get(2).name())
                .isEqualTo("서초동");

        verify(recommendationRunRepository)
                .findTopByMeetingIdAndStatusOrderByCreatedAtDesc(
                        1L,
                        RecommendationStatus.COMPLETED
                );

        verify(recommendedAreaRepository)
                .findAllByRecommendationRunId(10L);

        verify(areaCenterService)
                .calculate(1L);

        verify(areaCandidateResolver)
                .resolve(
                        "역삼동",
                        center.latitude(),
                        center.longitude()
                );

        verify(areaCandidateResolver)
                .resolve(
                        "강남동",
                        center.latitude(),
                        center.longitude()
                );

        verify(areaCandidateResolver)
                .resolve(
                        "서초동",
                        center.latitude(),
                        center.longitude()
                );
    }

    @Test
    void 완료된_추천_결과가_없으면_예외가_발생한다() {

        when(
                recommendationRunRepository
                        .findTopByMeetingIdAndStatusOrderByCreatedAtDesc(
                                1L,
                                RecommendationStatus.COMPLETED
                        )
        ).thenReturn(Optional.empty());

        assertThatThrownBy(
                () -> recommendedAreaQueryService.getRecommendedAreas(1L)
        )
                .isInstanceOf(CustomException.class);

        verify(recommendationRunRepository)
                .findTopByMeetingIdAndStatusOrderByCreatedAtDesc(
                        1L,
                        RecommendationStatus.COMPLETED
                );

        verifyNoInteractions(
                recommendedAreaRepository,
                areaCenterService,
                areaCandidateResolver
        );
    }

    private GooglePlaceSearchResponse.Place place(
            double latitude,
            double longitude
    ) {

        return new GooglePlaceSearchResponse.Place(
                "test-place-id",
                new GooglePlaceSearchResponse.Location(
                        latitude,
                        longitude
                )
        );
    }

    private void setId(
            Object target,
            Long id
    ) throws Exception {

        Field field =
                target.getClass().getDeclaredField("id");

        field.setAccessible(true);
        field.set(target, id);
    }
}