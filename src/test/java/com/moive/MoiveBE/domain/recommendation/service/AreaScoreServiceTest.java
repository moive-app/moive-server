package com.moive.MoiveBE.domain.recommendation.service;

import com.moive.MoiveBE.domain.recommendation.dto.AreaRouteResult;
import com.moive.MoiveBE.domain.recommendation.dto.AreaScoreResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AreaScoreServiceTest {

    private final AreaScoreService areaScoreService =
            new AreaScoreService();

    @Test
    void Min_Max_정규화를_계산한다() {

        double result =
                areaScoreService.normalize(
                        1500.0,
                        1000.0,
                        2000.0
                );

        assertThat(result)
                .isEqualTo(0.5);
    }

    @Test
    void 최소값과_최대값이_같으면_0을_반환한다() {

        double result =
                areaScoreService.normalize(
                        1500.0,
                        1500.0,
                        1500.0
                );

        assertThat(result)
                .isEqualTo(0.0);
    }

    @Test
    void 추천_지역_점수를_계산한다() {

        AreaRouteResult routeResult =
                new AreaRouteResult(
                        0,
                        1500.0,
                        1800.0,
                        300.0
                );

        AreaScoreResult result =
                areaScoreService.calculateScore(
                        routeResult,
                        1000.0,
                        2000.0,
                        1200.0,
                        2000.0,
                        100.0,
                        500.0
                );

        assertThat(result.destinationIndex())
                .isEqualTo(0);

        assertThat(result.normalizedAverage())
                .isEqualTo(0.5);

        assertThat(result.normalizedMax())
                .isEqualTo(0.75);

        assertThat(result.normalizedStandardDeviation())
                .isEqualTo(0.5);

        assertThat(result.score())
                .isEqualTo(0.6);
    }

    @Test
    void 여러_추천_지역의_점수를_계산한다() {

        List<AreaRouteResult> routeResults = List.of(
                new AreaRouteResult(
                        0,
                        1000.0,
                        1500.0,
                        100.0
                ),
                new AreaRouteResult(
                        1,
                        1500.0,
                        1750.0,
                        300.0
                ),
                new AreaRouteResult(
                        2,
                        2000.0,
                        2000.0,
                        500.0
                )
        );

        List<AreaScoreResult> results =
                areaScoreService.calculateScores(routeResults);

        assertThat(results).hasSize(3);

        assertThat(results.get(0).score())
                .isEqualTo(0.0);

        assertThat(results.get(1).score())
                .isEqualTo(0.5);

        assertThat(results.get(2).score())
                .isEqualTo(1.0);
    }

    @Test
    void 점수가_낮은_추천_지역_TOP3를_선정한다() {

        List<AreaScoreResult> scoreResults = List.of(
                new AreaScoreResult(0, 0.5, 0.5, 0.5, 0.50),
                new AreaScoreResult(1, 0.2, 0.2, 0.2, 0.20),
                new AreaScoreResult(2, 0.8, 0.8, 0.8, 0.80),
                new AreaScoreResult(3, 0.1, 0.1, 0.1, 0.10),
                new AreaScoreResult(4, 0.3, 0.3, 0.3, 0.30)
        );

        List<AreaScoreResult> results =
                areaScoreService.selectTop3(scoreResults);

        assertThat(results)
                .hasSize(3);

        assertThat(results)
                .extracting(AreaScoreResult::destinationIndex)
                .containsExactly(
                        3,
                        1,
                        4
                );
    }
}