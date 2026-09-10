package com.moive.MoiveBE.domain.recommendation.service;

import com.moive.MoiveBE.domain.recommendation.client.GooglePlacesClient;
import com.moive.MoiveBE.domain.recommendation.dto.GooglePlaceDetailsResponse;
import com.moive.MoiveBE.domain.recommendation.dto.RecommendedPlaceDetailResponse;
import com.moive.MoiveBE.domain.recommendation.dto.RecommendedPlaceListResponse;
import com.moive.MoiveBE.domain.recommendation.entity.RecommendationRun;
import com.moive.MoiveBE.domain.recommendation.entity.RecommendedArea;
import com.moive.MoiveBE.domain.recommendation.entity.RecommendedPlace;
import com.moive.MoiveBE.domain.recommendation.repository.RecommendationRunRepository;
import com.moive.MoiveBE.domain.recommendation.repository.RecommendedAreaRepository;
import com.moive.MoiveBE.domain.recommendation.repository.RecommendedPlaceRepository;
import com.moive.MoiveBE.global.exception.CustomErrorCode;
import com.moive.MoiveBE.global.exception.CustomException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final RecommendedPlaceRepository recommendedPlaceRepository;
    private final GooglePlacesClient googlePlacesClient;
    private final RecommendedAreaRepository recommendedAreaRepository;
    private final RecommendationRunRepository recommendationRunRepository;
    private final PlaceRecommendationGenerationService placeRecommendationGenerationService;

    @Transactional
    public RecommendedPlaceListResponse getRecommendedPlaces(
            Long meetingId,
            Long recommendedAreaId
    ) {
        validateRecommendedArea(meetingId, recommendedAreaId);

        if (!recommendedPlaceRepository.existsByRecommendedAreaId(recommendedAreaId)) {
            placeRecommendationGenerationService.recommend(recommendedAreaId);
        }

        List<RecommendedPlace> recommendedPlaces =
                recommendedPlaceRepository.findAllByRecommendedAreaId(
                        recommendedAreaId
                );

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
                googlePlacesClient.getPlaceSummaryDetails(
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

    public RecommendedPlaceDetailResponse getRecommendedPlaceDetail(
            Long recommendedAreaId,
            Long recommendedPlaceId
    ) {
        RecommendedPlace recommendedPlace =
                recommendedPlaceRepository.findById(recommendedPlaceId)
                        .orElseThrow(() ->
                                new CustomException(
                                        CustomErrorCode.RECOMMENDED_PLACE_NOT_FOUND
                                )
                        );

        if (!recommendedPlace.getRecommendedAreaId().equals(recommendedAreaId)) {
            throw new CustomException(
                    CustomErrorCode.RECOMMENDED_PLACE_NOT_FOUND
            );
        }

        GooglePlaceDetailsResponse details =
                googlePlacesClient.getPlaceDetails(
                        recommendedPlace.getGooglePlaceId()
                );

        List<String> imageUrls =
                details.photos() == null
                        ? List.of()
                        : details.photos().stream()
                        .limit(3)
                        .map(GooglePlaceDetailsResponse.Photo::name)
                        .map(googlePlacesClient::getPlacePhotoUrl)
                        .filter(url -> url != null && !url.isBlank())
                        .toList();

        return new RecommendedPlaceDetailResponse(
                recommendedPlace.getId(),
                extractKoreanPlaceName(details.displayName().text()),
                details.primaryTypeDisplayName().text(),
                details.formattedAddress(),
                recommendedPlace.getPreferenceMatchCnt(),
                null,
                imageUrls
        );
    }

    private void validateRecommendedArea(
            Long meetingId,
            Long recommendedAreaId
    ) {
        RecommendedArea recommendedArea =
                recommendedAreaRepository.findById(recommendedAreaId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "추천 지역이 존재하지 않습니다."
                                )
                        );

        RecommendationRun recommendationRun =
                recommendationRunRepository.findById(
                                recommendedArea.getRecommendationRunId()
                        )
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "추천 실행 정보가 존재하지 않습니다."
                                )
                        );

        if (!recommendationRun.getMeetingId().equals(meetingId)) {
            throw new IllegalArgumentException(
                    "해당 모임의 추천 지역이 아닙니다."
            );
        }
    }
}