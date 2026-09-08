package com.moive.MoiveBE.domain.recommendation.client;

import com.moive.MoiveBE.domain.recommendation.dto.GooglePlaceDetailsResponse;
import com.moive.MoiveBE.domain.recommendation.dto.GooglePlacePhotoResponse;
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
    private static final String PLACE_DETAILS_URL =
            "https://places.googleapis.com/v1/places/{placeId}?languageCode=ko";

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

    public GooglePlaceDetailsResponse getPlaceSummaryDetails(
            String googlePlaceId
    ) {
        return restClient.get()
                .uri(PLACE_DETAILS_URL, googlePlaceId)
                .header("X-Goog-Api-Key", apiKey)
                .header(
                        "X-Goog-FieldMask",
                        "displayName,primaryTypeDisplayName"
                )
                .retrieve()
                .body(GooglePlaceDetailsResponse.class);
    }

    public GooglePlaceDetailsResponse getPlaceDetails(
            String googlePlaceId
    ) {
        return restClient.get()
                .uri(PLACE_DETAILS_URL, googlePlaceId)
                .header("X-Goog-Api-Key", apiKey)
                .header(
                        "X-Goog-FieldMask",
                        "displayName,primaryTypeDisplayName,formattedAddress,photos"
                )
                .retrieve()
                .body(GooglePlaceDetailsResponse.class);
    }

    public String getPlacePhotoUrl(String photoName) {

        String photoUrl =
                "https://places.googleapis.com/v1/"
                        + photoName
                        + "/media?maxWidthPx=800&skipHttpRedirect=true";

        GooglePlacePhotoResponse response =
                restClient.get()
                        .uri(photoUrl)
                        .header("X-Goog-Api-Key", apiKey)
                        .retrieve()
                        .body(GooglePlacePhotoResponse.class);

        if (response == null) {
            return null;
        }

        return response.photoUri();
    }
}