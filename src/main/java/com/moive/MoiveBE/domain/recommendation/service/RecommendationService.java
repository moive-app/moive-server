package com.moive.MoiveBE.domain.recommendation.service;

import com.moive.MoiveBE.domain.recommendation.client.GooglePlacesClient;
import com.moive.MoiveBE.domain.recommendation.dto.GooglePlaceDetailsResponse;
import com.moive.MoiveBE.domain.recommendation.dto.RecommendedPlaceListResponse;
import com.moive.MoiveBE.domain.recommendation.entity.RecommendedPlace;
import com.moive.MoiveBE.domain.recommendation.repository.RecommendedPlaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final RecommendedPlaceRepository recommendedPlaceRepository;
    private final GooglePlacesClient googlePlacesClient;

    public RecommendedPlaceListResponse getRecommendedPlaces(
            Long recommendedAreaId
    ) {
        List<RecommendedPlace> recommendedPlaces =
                recommendedPlaceRepository.findAllByRecommendedAreaId(recommendedAreaId);

        List<RecommendedPlaceListResponse.Place> places =
                recommendedPlaces.stream()
                        .map(this::toPlaceResponse)
                        .toList();

        return new RecommendedPlaceListResponse(
                recommendedAreaId,
                places
        );
    }

    private RecommendedPlaceListResponse.Place toPlaceResponse(
            RecommendedPlace recommendedPlace
    ) {
        GooglePlaceDetailsResponse details =
                googlePlacesClient.getPlaceDetails(
                        recommendedPlace.getGooglePlaceId()
                );

        return new RecommendedPlaceListResponse.Place(
                recommendedPlace.getId(),
                extractKoreanPlaceName(details.displayName().text()),
                details.primaryTypeDisplayName().text(),
                null,
                null,
                null,
                recommendedPlace.getPreferenceMatchCnt()
        );
    }

    private String extractKoreanPlaceName(String displayName) {
        if (displayName == null) {
            return null;
        }

        return displayName.split("\\|")[0].trim();
    }
}