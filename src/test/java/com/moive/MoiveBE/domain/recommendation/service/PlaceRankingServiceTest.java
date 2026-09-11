package com.moive.MoiveBE.domain.recommendation.service;

import com.moive.MoiveBE.domain.recommendation.dto.PlaceCandidate;
import com.moive.MoiveBE.domain.recommendation.dto.PlaceMatchResult;
import com.moive.MoiveBE.domain.recommendation.dto.PlaceRankingResult;
import com.moive.MoiveBE.domain.recommendation.dto.PlaceRouteResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlaceRankingServiceTest {

    private final PlaceRankingService placeRankingService =
            new PlaceRankingService();

    @Test
    void 매칭인원_평균이동시간_최대이동시간_후보순서_기준으로_TOP3를_선정한다() {

        List<PlaceCandidate> candidates = List.of(
                candidate("A", 0),
                candidate("B", 1),
                candidate("C", 2),
                candidate("D", 3),
                candidate("E", 4)
        );

        List<PlaceMatchResult> matchResults = List.of(
                new PlaceMatchResult(0, 2), // A
                new PlaceMatchResult(1, 3), // B
                new PlaceMatchResult(2, 3), // C
                new PlaceMatchResult(3, 3), // D
                new PlaceMatchResult(4, 3)  // E
        );

        List<PlaceRouteResult> routeResults = List.of(
                new PlaceRouteResult(0, 500, 700), // A: matchCnt에서 밀림
                new PlaceRouteResult(1, 600, 800), // B
                new PlaceRouteResult(2, 500, 900), // C
                new PlaceRouteResult(3, 500, 700), // D
                new PlaceRouteResult(4, 500, 700)  // E
        );

        List<PlaceRankingResult> result =
                placeRankingService.selectTopPlaces(
                        candidates,
                        routeResults,
                        matchResults
                );

        assertThat(result).hasSize(3);

        assertThat(result)
                .extracting(PlaceRankingResult::googlePlaceId)
                .containsExactly(
                        "D",
                        "E",
                        "C"
                );
    }

    private PlaceCandidate candidate(
            String googlePlaceId,
            int candidateOrder
    ) {
        return new PlaceCandidate(
                googlePlaceId,
                37.0,
                127.0,
                candidateOrder,
                "한식"
        );
    }

    @Test
    void 유효한_장소가_3개보다_적으면_존재하는_장소만_반환한다() {

        List<PlaceCandidate> candidates = List.of(
                candidate("A", 0),
                candidate("B", 1)
        );

        List<PlaceMatchResult> matchResults = List.of(
                new PlaceMatchResult(0, 2),
                new PlaceMatchResult(1, 1)
        );

        List<PlaceRouteResult> routeResults = List.of(
                new PlaceRouteResult(0, 500, 700),
                new PlaceRouteResult(1, 600, 800)
        );

        List<PlaceRankingResult> result =
                placeRankingService.selectTopPlaces(
                        candidates,
                        routeResults,
                        matchResults
                );

        assertThat(result)
                .extracting(PlaceRankingResult::googlePlaceId)
                .containsExactly("A", "B");
    }

    @Test
    void 이동시간이나_매칭결과가_없는_장소는_추천대상에서_제외한다() {

        List<PlaceCandidate> candidates = List.of(
                candidate("A", 0),
                candidate("B", 1),
                candidate("C", 2)
        );

        List<PlaceMatchResult> matchResults = List.of(
                new PlaceMatchResult(0, 2),
                new PlaceMatchResult(2, 3)
        );

        List<PlaceRouteResult> routeResults = List.of(
                new PlaceRouteResult(0, 500, 700),
                new PlaceRouteResult(1, 400, 600)
        );

        List<PlaceRankingResult> result =
                placeRankingService.selectTopPlaces(
                        candidates,
                        routeResults,
                        matchResults
                );

        assertThat(result)
                .extracting(PlaceRankingResult::googlePlaceId)
                .containsExactly("A");
    }
}