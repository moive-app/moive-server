package com.moive.MoiveBE.domain.recommendation.service;

import com.moive.MoiveBE.domain.meeting.entity.ParticipantPreference;
import com.moive.MoiveBE.domain.recommendation.client.GoogleRoutesClient;
import com.moive.MoiveBE.domain.recommendation.dto.GoogleRouteMatrixRequest;
import com.moive.MoiveBE.domain.recommendation.dto.GoogleRouteMatrixResponse;
import com.moive.MoiveBE.domain.recommendation.dto.PlaceCandidate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PlaceRouteService {

    private final GoogleRoutesClient googleRoutesClient;

    public List<GoogleRouteMatrixResponse> calculate(
            List<ParticipantPreference> preferences,
            List<PlaceCandidate> candidates
    ) {

        List<GoogleRouteMatrixRequest.RouteMatrixOrigin> origins =
                preferences.stream()
                        .map(preference ->
                                new GoogleRouteMatrixRequest.RouteMatrixOrigin(
                                        new GoogleRouteMatrixRequest.Waypoint(
                                                new GoogleRouteMatrixRequest.Location(
                                                        new GoogleRouteMatrixRequest.LatLng(
                                                                preference.getDepartureLatitude().doubleValue(),
                                                                preference.getDepartureLongitude().doubleValue()
                                                        )
                                                )
                                        )
                                )
                        )
                        .toList();

        List<GoogleRouteMatrixRequest.RouteMatrixDestination> destinations =
                candidates.stream()
                        .map(candidate ->
                                new GoogleRouteMatrixRequest.RouteMatrixDestination(
                                        new GoogleRouteMatrixRequest.Waypoint(
                                                new GoogleRouteMatrixRequest.Location(
                                                        new GoogleRouteMatrixRequest.LatLng(
                                                                candidate.latitude(),
                                                                candidate.longitude()
                                                        )
                                                )
                                        )
                                )
                        )
                        .toList();

        GoogleRouteMatrixRequest request =
                new GoogleRouteMatrixRequest(
                        origins,
                        destinations,
                        "TRANSIT"
                );

        return googleRoutesClient.computeRouteMatrix(request);
    }
}