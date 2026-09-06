package com.moive.MoiveBE.domain.recommendation.service;

import com.moive.MoiveBE.domain.recommendation.dto.GoogleRouteMatrixResponse;
import com.moive.MoiveBE.domain.recommendation.dto.PlaceCandidate;
import com.moive.MoiveBE.domain.recommendation.dto.RouteTravelTime;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.moive.MoiveBE.domain.recommendation.dto.RouteTravelTime;

import static org.assertj.core.api.Assertions.tuple;

class RouteMatrixServiceTest {

    private final RouteMatrixService routeMatrixService =
            new RouteMatrixService();

    @Test
    void duration을_초로_변환한다() {
        double seconds =
                routeMatrixService.parseDurationSeconds("2481s");

        assertThat(seconds).isEqualTo(2481.0);
    }

    @Test
    void 소수_duration도_초로_변환한다() {
        double seconds =
                routeMatrixService.parseDurationSeconds("2481.5s");

        assertThat(seconds).isEqualTo(2481.5);
    }

    @Test
    void 경로가_존재하면_true를_반환한다() {
        GoogleRouteMatrixResponse response =
                new GoogleRouteMatrixResponse(
                        0,
                        0,
                        new GoogleRouteMatrixResponse.Status(null, null),
                        "ROUTE_EXISTS",
                        "2481s"
                );

        assertThat(routeMatrixService.hasRoute(response)).isTrue();
    }

    @Test
    void 경로가_없으면_false를_반환한다() {
        GoogleRouteMatrixResponse response =
                new GoogleRouteMatrixResponse(
                        0,
                        0,
                        new GoogleRouteMatrixResponse.Status(null, null),
                        "ROUTE_NOT_FOUND",
                        null
                );

        assertThat(routeMatrixService.hasRoute(response)).isFalse();
    }

    @Test
    void 요소_처리_오류가_있으면_예외가_발생한다() {
        GoogleRouteMatrixResponse response =
                new GoogleRouteMatrixResponse(
                        0,
                        0,
                        new GoogleRouteMatrixResponse.Status(
                                13,
                                "Internal error"
                        ),
                        null,
                        null
                );

        assertThatThrownBy(() -> routeMatrixService.hasRoute(response))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 여러_경로_응답을_이동시간으로_변환한다() {
        List<GoogleRouteMatrixResponse> responses = List.of(
                new GoogleRouteMatrixResponse(
                        1,
                        0,
                        new GoogleRouteMatrixResponse.Status(null, null),
                        "ROUTE_EXISTS",
                        "1200s"
                ),
                new GoogleRouteMatrixResponse(
                        0,
                        0,
                        new GoogleRouteMatrixResponse.Status(null, null),
                        "ROUTE_EXISTS",
                        "900s"
                )
        );

        List<RouteTravelTime> result =
                routeMatrixService.extractTravelTimes(responses);

        assertThat(result).hasSize(2);

        assertThat(result)
                .extracting(
                        RouteTravelTime::originIndex,
                        RouteTravelTime::destinationIndex,
                        RouteTravelTime::durationSeconds
                )
                .containsExactly(
                        tuple(1, 0, 1200.0),
                        tuple(0, 0, 900.0)
                );
    }

    @Test
    void 모든_참가자의_경로가_존재하면_후보를_유지한다() {
        List<GoogleRouteMatrixResponse> responses = List.of(
                new GoogleRouteMatrixResponse(
                        0, 0,
                        new GoogleRouteMatrixResponse.Status(null, null),
                        "ROUTE_EXISTS",
                        "1000s"
                ),
                new GoogleRouteMatrixResponse(
                        1, 0,
                        new GoogleRouteMatrixResponse.Status(null, null),
                        "ROUTE_EXISTS",
                        "1200s"
                ),
                new GoogleRouteMatrixResponse(
                        2, 0,
                        new GoogleRouteMatrixResponse.Status(null, null),
                        "ROUTE_EXISTS",
                        "1400s"
                )
        );

        boolean result =
                routeMatrixService.isCandidateReachable(
                        responses,
                        0,
                        3
                );

        assertThat(result).isTrue();
    }

    @Test
    void 한_참가자라도_경로가_없으면_후보를_제외한다() {
        List<GoogleRouteMatrixResponse> responses = List.of(
                new GoogleRouteMatrixResponse(
                        0, 0,
                        new GoogleRouteMatrixResponse.Status(null, null),
                        "ROUTE_EXISTS",
                        "1000s"
                ),
                new GoogleRouteMatrixResponse(
                        1, 0,
                        new GoogleRouteMatrixResponse.Status(null, null),
                        "ROUTE_NOT_FOUND",
                        null
                ),
                new GoogleRouteMatrixResponse(
                        2, 0,
                        new GoogleRouteMatrixResponse.Status(null, null),
                        "ROUTE_EXISTS",
                        "1400s"
                )
        );

        boolean result =
                routeMatrixService.isCandidateReachable(
                        responses,
                        0,
                        3
                );

        assertThat(result).isFalse();
    }

    @Test
    void 경로가_없는_장소_후보를_제외한다() {
        List<PlaceCandidate> candidates = List.of(
                new PlaceCandidate("A", 37.1, 127.1, 0),
                new PlaceCandidate("B", 37.2, 127.2, 1),
                new PlaceCandidate("C", 37.3, 127.3, 2)
        );

        List<GoogleRouteMatrixResponse> responses = List.of(
                route(0, 0, "ROUTE_EXISTS", "1000s"),
                route(1, 0, "ROUTE_EXISTS", "1100s"),

                route(0, 1, "ROUTE_EXISTS", "1200s"),
                route(1, 1, "ROUTE_NOT_FOUND", null),

                route(0, 2, "ROUTE_EXISTS", "1300s"),
                route(1, 2, "ROUTE_EXISTS", "1400s")
        );

        List<PlaceCandidate> result =
                routeMatrixService.filterReachableCandidates(
                        candidates,
                        responses,
                        2
                );

        assertThat(result)
                .extracting(PlaceCandidate::googlePlaceId)
                .containsExactly("A", "C");
    }
    private GoogleRouteMatrixResponse route(
            int originIndex,
            int destinationIndex,
            String condition,
            String duration
    ) {
        return new GoogleRouteMatrixResponse(
                originIndex,
                destinationIndex,
                new GoogleRouteMatrixResponse.Status(null, null),
                condition,
                duration
        );
    }
}