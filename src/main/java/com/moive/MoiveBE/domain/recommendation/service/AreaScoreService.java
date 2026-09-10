package com.moive.MoiveBE.domain.recommendation.service;

import com.moive.MoiveBE.domain.recommendation.dto.AreaRouteResult;
import com.moive.MoiveBE.domain.recommendation.dto.AreaScoreResult;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AreaScoreService {

    public double normalize(
            double value,
            double min,
            double max
    ) {

        if (max == min) {
            return 0.0;
        }

        return (value - min) / (max - min);
    }

    public AreaScoreResult calculateScore(
            AreaRouteResult result,
            double minAverage,
            double maxAverage,
            double minMax,
            double maxMax,
            double minStandardDeviation,
            double maxStandardDeviation
    ) {

        double normalizedAverage =
                normalize(
                        result.averageTravelSeconds(),
                        minAverage,
                        maxAverage
                );

        double normalizedMax =
                normalize(
                        result.maxTravelSeconds(),
                        minMax,
                        maxMax
                );

        double normalizedStandardDeviation =
                normalize(
                        result.standardDeviationSeconds(),
                        minStandardDeviation,
                        maxStandardDeviation
                );

        double score =
                normalizedAverage * 0.4
                        + normalizedMax * 0.4
                        + normalizedStandardDeviation * 0.2;

        return new AreaScoreResult(
                result.destinationIndex(),
                normalizedAverage,
                normalizedMax,
                normalizedStandardDeviation,
                score
        );
    }

    public List<AreaScoreResult> calculateScores(
            List<AreaRouteResult> routeResults
    ) {

        double minAverage = routeResults.stream()
                .mapToDouble(AreaRouteResult::averageTravelSeconds)
                .min()
                .orElseThrow();

        double maxAverage = routeResults.stream()
                .mapToDouble(AreaRouteResult::averageTravelSeconds)
                .max()
                .orElseThrow();

        double minMax = routeResults.stream()
                .mapToDouble(AreaRouteResult::maxTravelSeconds)
                .min()
                .orElseThrow();

        double maxMax = routeResults.stream()
                .mapToDouble(AreaRouteResult::maxTravelSeconds)
                .max()
                .orElseThrow();

        double minStandardDeviation = routeResults.stream()
                .mapToDouble(AreaRouteResult::standardDeviationSeconds)
                .min()
                .orElseThrow();

        double maxStandardDeviation = routeResults.stream()
                .mapToDouble(AreaRouteResult::standardDeviationSeconds)
                .max()
                .orElseThrow();

        return routeResults.stream()
                .map(result ->
                        calculateScore(
                                result,
                                minAverage,
                                maxAverage,
                                minMax,
                                maxMax,
                                minStandardDeviation,
                                maxStandardDeviation
                        )
                )
                .toList();
    }

    public List<AreaScoreResult> selectTop3(
            List<AreaScoreResult> scoreResults
    ) {
        return scoreResults.stream()
                .sorted((a, b) ->
                        Double.compare(
                                a.score(),
                                b.score()
                        )
                )
                .limit(3)
                .toList();
    }
}