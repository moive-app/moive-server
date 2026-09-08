package com.moive.MoiveBE.domain.recommendation.client;

import com.moive.MoiveBE.domain.recommendation.dto.GoogleRouteMatrixRequest;
import com.moive.MoiveBE.domain.recommendation.dto.GoogleRouteMatrixResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
@RequiredArgsConstructor
public class GoogleRoutesClient {

    private static final String COMPUTE_ROUTE_MATRIX_URL =
            "https://routes.googleapis.com/distanceMatrix/v2:computeRouteMatrix";

    private final RestClient restClient;

    @Value("${google.maps.api-key}")
    private String apiKey;

    public List<GoogleRouteMatrixResponse> computeRouteMatrix(
            GoogleRouteMatrixRequest request
    ) {
        return restClient.post()
                .uri(COMPUTE_ROUTE_MATRIX_URL)
                .header("X-Goog-Api-Key", apiKey)
                .header(
                        "X-Goog-FieldMask",
                        "originIndex,destinationIndex,status,condition,duration"
                )
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }
}