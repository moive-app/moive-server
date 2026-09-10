package com.moive.MoiveBE.domain.recommendation.client;

import com.moive.MoiveBE.domain.recommendation.dto.GooglePlaceLocationResponse;
import com.moive.MoiveBE.domain.recommendation.dto.GooglePlaceDetailsResponse;
import com.moive.MoiveBE.domain.recommendation.dto.GooglePlacePhotoResponse;
import com.moive.MoiveBE.domain.recommendation.dto.GooglePlaceSearchResponse;
import com.moive.MoiveBE.global.exception.CustomErrorCode;
import com.moive.MoiveBE.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

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
        try {
            return restClient.get()
                    .uri(PLACE_DETAILS_URL, googlePlaceId)
                    .header("X-Goog-Api-Key", apiKey)
                    .header(
                            "X-Goog-FieldMask",
                            "displayName,primaryTypeDisplayName"
                    )
                    .retrieve()
                    .body(GooglePlaceDetailsResponse.class);

        } catch (RestClientResponseException e) {
            throw new CustomException(
                    CustomErrorCode.PLACE_INFO_LOOKUP_FAILED
            );
        } catch (Exception e) {
            throw new CustomException(
                    CustomErrorCode.PLACE_INFO_LOOKUP_FAILED
            );
        }
    }

    public GooglePlaceDetailsResponse getPlaceDetails(
            String googlePlaceId
    ) {
        try {
            return restClient.get()
                    .uri(PLACE_DETAILS_URL, googlePlaceId)
                    .header("X-Goog-Api-Key", apiKey)
                    .header(
                            "X-Goog-FieldMask",
                            "displayName,primaryTypeDisplayName,formattedAddress,photos,location"
                    )
                    .retrieve()
                    .body(GooglePlaceDetailsResponse.class);

        } catch (RestClientResponseException e) {
            throw new CustomException(
                    CustomErrorCode.PLACE_INFO_LOOKUP_FAILED
            );
        } catch (Exception e) {
            throw new CustomException(
                    CustomErrorCode.PLACE_INFO_LOOKUP_FAILED
            );
        }
    }

    public String getPlacePhotoUrl(String photoName) {
        try {
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

        } catch (RestClientResponseException e) {
            throw new CustomException(
                    CustomErrorCode.PLACE_INFO_LOOKUP_FAILED
            );
        } catch (Exception e) {
            throw new CustomException(
                    CustomErrorCode.PLACE_INFO_LOOKUP_FAILED
            );
        }
    }

    public GooglePlaceLocationResponse getPlaceLocation (
            String googlePlaceId
    ) {
        try {
            return restClient.get()
                    .uri(PLACE_DETAILS_URL, googlePlaceId)
                    .header("X-Goog-Api-Key", apiKey)
                    .header(
                            "X-Goog-FieldMask",
                            "displayName,primaryTypeDisplayName,formattedAddress,location"
                    )
                    .retrieve()
                    .body(GooglePlaceLocationResponse.class);

        } catch (RestClientResponseException e) {
            throw new CustomException(
                    CustomErrorCode.PLACE_INFO_LOOKUP_FAILED
            );
        } catch (Exception e) {
            throw new CustomException(
                    CustomErrorCode.PLACE_INFO_LOOKUP_FAILED
            );
        }
    }

    public GooglePlaceSearchResponse searchAreaPlace(
            String textQuery,
            double latitude,
            double longitude
    ) {

        GoogleAreaSearchRequest request =
                new GoogleAreaSearchRequest(
                        textQuery,
                        1,
                        "ko",
                        new LocationBias(
                                new Circle(
                                        new Center(latitude, longitude),
                                        5000.0
                                )
                        )
                );

        try {
            return restClient.post()
                    .uri(TEXT_SEARCH_URL)
                    .header("X-Goog-Api-Key", apiKey)
                    .header(
                            "X-Goog-FieldMask",
                            "places.id,places.location"
                    )
                    .body(request)
                    .retrieve()
                    .body(GooglePlaceSearchResponse.class);

        } catch (RestClientResponseException e) {
            throw new CustomException(
                    CustomErrorCode.AREA_INFO_LOOKUP_FAILED
            );
        } catch (Exception e) {
            throw new CustomException(
                    CustomErrorCode.AREA_INFO_LOOKUP_FAILED
            );
        }
    }

    private record GoogleAreaSearchRequest(
            String textQuery,
            int pageSize,
            String languageCode,
            LocationBias locationBias
    ) {
    }

    private record LocationBias(
            Circle circle
    ) {
    }

    private record Circle(
            Center center,
            double radius
    ) {
    }

    private record Center(
            double latitude,
            double longitude
    ) {
    }

}