package com.moive.MoiveBE.domain.recommendation.service;

import com.moive.MoiveBE.domain.recommendation.dto.GoogleRouteMatrixResponse;
import com.moive.MoiveBE.domain.recommendation.dto.ParticipantRecommendationCondition;
import com.moive.MoiveBE.domain.recommendation.dto.PlaceCandidate;
import com.moive.MoiveBE.domain.recommendation.dto.PlaceMatchResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class PlaceMatchService {

    private final RouteMatrixService routeMatrixService;

    public PlaceMatchResult calculateMatchCount(
            PlaceCandidate candidate,
            int destinationIndex,
            List<ParticipantRecommendationCondition> participants,
            List<GoogleRouteMatrixResponse> responses
    ) {
        int matchCount = 0;

        for (ParticipantRecommendationCondition participant : participants) {

            boolean preferenceMatched =
                    participant.preferenceTypes()
                            .contains(candidate.preferenceType());

            if (!preferenceMatched) {
                continue;
            }

            GoogleRouteMatrixResponse route = responses.stream()
                    .filter(response ->
                            response.originIndex() == participant.originIndex()
                                    && response.destinationIndex() == destinationIndex)
                    .findFirst()
                    .orElseThrow(() ->
                            new IllegalStateException("이동시간 결과가 존재하지 않습니다."));

            if (!routeMatrixService.hasRoute(route)) {
                continue;
            }

            double travelSeconds =
                    routeMatrixService.parseDurationSeconds(route.duration());

            boolean timeMatched =
                    participant.maxTravelMinutes() == null
                            || travelSeconds <= participant.maxTravelMinutes() * 60.0;

            if (timeMatched) {
                matchCount++;
            }
        }

        return new PlaceMatchResult(
                destinationIndex,
                matchCount
        );
    }

    public List<PlaceMatchResult> calculateMatchCounts(
            List<PlaceCandidate> candidates,
            List<ParticipantRecommendationCondition> participants,
            List<GoogleRouteMatrixResponse> responses
    ) {
        return IntStream.range(0, candidates.size())
                .filter(destinationIndex ->
                        routeMatrixService.isCandidateReachable(
                                responses,
                                destinationIndex,
                                participants.size()
                        )
                )
                .mapToObj(destinationIndex ->
                        calculateMatchCount(
                                candidates.get(destinationIndex),
                                destinationIndex,
                                participants,
                                responses
                        )
                )
                .toList();
    }
}