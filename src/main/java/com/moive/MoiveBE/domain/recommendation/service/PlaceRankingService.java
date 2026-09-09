package com.moive.MoiveBE.domain.recommendation.service;

import com.moive.MoiveBE.domain.recommendation.dto.PlaceCandidate;
import com.moive.MoiveBE.domain.recommendation.dto.PlaceMatchResult;
import com.moive.MoiveBE.domain.recommendation.dto.PlaceRankingResult;
import com.moive.MoiveBE.domain.recommendation.dto.PlaceRouteResult;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PlaceRankingService {

    private static final int TOP_PLACE_COUNT = 3;

    public List<PlaceRankingResult> selectTopPlaces(
            List<PlaceCandidate> candidates,
            List<PlaceRouteResult> routeResults,
            List<PlaceMatchResult> matchResults
    ) {
        Map<Integer, PlaceRouteResult> routeResultMap =
                routeResults.stream()
                        .collect(Collectors.toMap(
                                PlaceRouteResult::destinationIndex,
                                Function.identity()
                        ));

        Map<Integer, PlaceMatchResult> matchResultMap =
                matchResults.stream()
                        .collect(Collectors.toMap(
                                PlaceMatchResult::destinationIndex,
                                Function.identity()
                        ));

        return candidates.stream()
                .filter(candidate ->
                        routeResultMap.containsKey(candidate.candidateOrder())
                                && matchResultMap.containsKey(candidate.candidateOrder())
                )
                .map(candidate -> {
                    int destinationIndex = candidate.candidateOrder();

                    PlaceRouteResult routeResult =
                            routeResultMap.get(destinationIndex);

                    PlaceMatchResult matchResult =
                            matchResultMap.get(destinationIndex);

                    return new PlaceRankingResult(
                            candidate.googlePlaceId(),
                            destinationIndex,
                            candidate.candidateOrder(),
                            matchResult.preferenceMatchCnt(),
                            routeResult.averageTravelSeconds(),
                            routeResult.maxTravelSeconds()
                    );
                })
                .sorted(
                        Comparator
                                .comparingInt(PlaceRankingResult::preferenceMatchCnt)
                                .reversed()
                                .thenComparingDouble(
                                        PlaceRankingResult::averageTravelSeconds
                                )
                                .thenComparingDouble(
                                        PlaceRankingResult::maxTravelSeconds
                                )
                                .thenComparingInt(
                                        PlaceRankingResult::candidateOrder
                                )
                )
                .limit(TOP_PLACE_COUNT)
                .toList();
    }
}