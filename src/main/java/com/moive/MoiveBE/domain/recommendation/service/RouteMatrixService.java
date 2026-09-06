package com.moive.MoiveBE.domain.recommendation.service;

import com.moive.MoiveBE.domain.recommendation.dto.GoogleRouteMatrixResponse;
import com.moive.MoiveBE.domain.recommendation.dto.PlaceCandidate;
import com.moive.MoiveBE.domain.recommendation.dto.RouteTravelTime;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.IntStream;

@Service
public class RouteMatrixService {

    public double parseDurationSeconds(String duration) {
        if (duration == null || !duration.endsWith("s")) {
            throw new IllegalArgumentException("잘못된 duration 형식입니다.");
        }

        String seconds = duration.substring(0, duration.length() - 1);

        return Double.parseDouble(seconds);
    }

    public boolean hasRoute(GoogleRouteMatrixResponse response) {

        if (response.status() != null
                && response.status().code() != null
                && response.status().code() != 0) {
            throw new IllegalStateException("Google Routes API 요소 처리 중 오류가 발생했습니다.");
        }

        return "ROUTE_EXISTS".equals(response.condition());
    }

    public List<RouteTravelTime> extractTravelTimes(
            List<GoogleRouteMatrixResponse> responses
    ) {
        return responses.stream()
                .filter(this::hasRoute)
                .map(response ->
                        new RouteTravelTime(
                                response.originIndex(),
                                response.destinationIndex(),
                                parseDurationSeconds(response.duration())
                        )
                )
                .toList();
    }

    public boolean isCandidateReachable(
            List<GoogleRouteMatrixResponse> responses,
            int destinationIndex,
            int originCount
    ) {
        long reachableCount = responses.stream()
                .filter(response -> response.destinationIndex() == destinationIndex)
                .filter(this::hasRoute)
                .count();

        return reachableCount == originCount;
    }

    public List<PlaceCandidate> filterReachableCandidates(
            List<PlaceCandidate> candidates,
            List<GoogleRouteMatrixResponse> responses,
            int originCount
    ) {
        return IntStream.range(0, candidates.size())
                .filter(destinationIndex ->
                        isCandidateReachable(
                                responses,
                                destinationIndex,
                                originCount
                        )
                )
                .mapToObj(candidates::get)
                .toList();
    }
}