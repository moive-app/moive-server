package com.moive.MoiveBE.domain.recommendation.service;

import com.moive.MoiveBE.domain.recommendation.dto.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.tuple;

class PlaceMatchServiceTest {

    private final RouteMatrixService routeMatrixService =
            new RouteMatrixService();

    private final PlaceMatchService placeMatchService =
            new PlaceMatchService(routeMatrixService);

    @Test
    void 취향과_최대이동시간을_모두_만족한_참가자_수를_계산한다() {

        PlaceCandidate candidate =
                new PlaceCandidate(
                        "A",
                        37.1,
                        127.1,
                        0,
                        "한식"
                );

        List<ParticipantRecommendationCondition> participants = List.of(
                new ParticipantRecommendationCondition(
                        0, Set.of("한식"), 30
                ),
                new ParticipantRecommendationCondition(
                        1, Set.of("한식"), 30
                ),
                new ParticipantRecommendationCondition(
                        2, Set.of("보드게임"), 60
                )
        );

        List<GoogleRouteMatrixResponse> responses = List.of(
                route(0, 0, "1200s"), // 한식 + 20분 → 매칭
                route(1, 0, "2400s"), // 한식 + 40분 → 시간 초과
                route(2, 0, "1200s")  // 취향 불일치
        );

        PlaceMatchResult result =
                placeMatchService.calculateMatchCount(
                        candidate,
                        0,
                        participants,
                        responses
                );

        assertThat(result.preferenceMatchCnt()).isEqualTo(1);
    }

    @Test
    void 최대이동시간_제한이_없으면_취향만_일치해도_매칭된다() {

        PlaceCandidate candidate =
                new PlaceCandidate(
                        "A",
                        37.1,
                        127.1,
                        0,
                        "한식"
                );

        List<ParticipantRecommendationCondition> participants = List.of(
                new ParticipantRecommendationCondition(
                        0,
                        Set.of("한식"),
                        null
                )
        );

        List<GoogleRouteMatrixResponse> responses = List.of(
                route(0, 0, "7200s") // 2시간이어도 제한 없음
        );

        PlaceMatchResult result =
                placeMatchService.calculateMatchCount(
                        candidate,
                        0,
                        participants,
                        responses
                );

        assertThat(result.preferenceMatchCnt()).isEqualTo(1);
    }

    @Test
    void 여러_장소_후보의_매칭_인원을_계산한다() {

        List<PlaceCandidate> candidates = List.of(
                new PlaceCandidate(
                        "A", 37.1, 127.1, 0,
                        "한식"
                ),
                new PlaceCandidate(
                        "B", 37.2, 127.2, 1,
                        "카페"
                )
        );

        List<ParticipantRecommendationCondition> participants = List.of(
                new ParticipantRecommendationCondition(
                        0, Set.of("한식", "카페"), 30
                ),
                new ParticipantRecommendationCondition(
                        1, Set.of("한식"), 30
                )
        );

        List<GoogleRouteMatrixResponse> responses = List.of(
                route(0, 0, "1200s"),
                route(1, 0, "1500s"),

                route(0, 1, "1200s"),
                route(1, 1, "1200s")
        );

        List<PlaceMatchResult> result =
                placeMatchService.calculateMatchCounts(
                        candidates,
                        participants,
                        responses
                );

        assertThat(result).hasSize(2);

        assertThat(result)
                .extracting(
                        PlaceMatchResult::destinationIndex,
                        PlaceMatchResult::preferenceMatchCnt
                )
                .containsExactly(
                        tuple(0, 2),
                        tuple(1, 1)
                );
    }

    private GoogleRouteMatrixResponse route(
            int originIndex,
            int destinationIndex,
            String duration
    ) {
        return new GoogleRouteMatrixResponse(
                originIndex,
                destinationIndex,
                new GoogleRouteMatrixResponse.Status(null, null),
                "ROUTE_EXISTS",
                duration
        );
    }
}