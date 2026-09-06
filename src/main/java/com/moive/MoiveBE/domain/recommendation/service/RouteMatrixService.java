package com.moive.MoiveBE.domain.recommendation.service;

import com.moive.MoiveBE.domain.recommendation.dto.GoogleRouteMatrixResponse;
import com.moive.MoiveBE.domain.recommendation.dto.PlaceCandidate;
import com.moive.MoiveBE.domain.recommendation.dto.PlaceRouteResult;
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
        List<GoogleRouteMatrixResponse> candidateResponses =
                responses.stream()
                        .filter(response ->
                                response.destinationIndex() == destinationIndex)
                        .toList();

        if (candidateResponses.size() != originCount) {
            throw new IllegalStateException(
                    "Google Routes API 응답 요소가 누락되었습니다."
            );
        }

        long distinctOriginCount = candidateResponses.stream()
                .map(GoogleRouteMatrixResponse::originIndex)
                .distinct()
                .count();

        if (distinctOriginCount != originCount) {
            throw new IllegalStateException(
                    "Google Routes API 응답 originIndex가 올바르지 않습니다."
            );
        }

        return candidateResponses.stream()
                .allMatch(this::hasRoute);
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

    public PlaceRouteResult calculateRouteResult(
            List<GoogleRouteMatrixResponse> responses,
            int destinationIndex,
            int originCount
    ) {
        List<Double> travelTimes = responses.stream()
                .filter(response ->
                        response.destinationIndex() == destinationIndex)
                .filter(this::hasRoute)
                .map(response ->
                        parseDurationSeconds(response.duration()))
                .toList();

        if (travelTimes.size() != originCount) {
            throw new IllegalArgumentException(
                    "모든 참가자의 이동시간이 존재하지 않습니다."
            );
        }

        double average = travelTimes.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElseThrow();

        double max = travelTimes.stream()
                .mapToDouble(Double::doubleValue)
                .max()
                .orElseThrow();

        return new PlaceRouteResult(
                destinationIndex,
                average,
                max
        );
    }

    public List<PlaceRouteResult> calculateRouteResults(
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
                .mapToObj(destinationIndex ->
                        calculateRouteResult(
                                responses,
                                destinationIndex,
                                originCount
                        )
                )
                .toList();
    }
}