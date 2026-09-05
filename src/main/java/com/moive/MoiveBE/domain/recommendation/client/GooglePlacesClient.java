package com.moive.MoiveBE.domain.recommendation.client;

import com.moive.MoiveBE.domain.recommendation.dto.GooglePlaceSearchResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class GooglePlacesClient {

    private static final String TEXT_SEARCH_URL =
            "https://places.googleapis.com/v1/places:searchText";

    private final RestClient restClient;

    @Value("${google.maps.api-key}")
    private String apiKey;

    public GooglePlaceSearchResponse searchPlaces(String textQuery, int pageSize) {

        GooglePlaceSearchRequest request =
                new GooglePlaceSearchRequest(textQuery, pageSize, "ko");

        return restClient.post()
                .uri(TEXT_SEARCH_URL)
                .header("X-Goog-Api-Key", apiKey)
                .header("X-Goog-FieldMask", "places.id,places.location")
                .body(request)
                .retrieve()
                .body(GooglePlaceSearchResponse.class);
    }

    private record GooglePlaceSearchRequest(
            String textQuery,
            int pageSize,
            String languageCode
    ) {
    }
}