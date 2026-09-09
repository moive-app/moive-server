package com.moive.MoiveBE.domain.recommendation.service;

import com.moive.MoiveBE.domain.recommendation.client.GooglePlacesClient;
import com.moive.MoiveBE.domain.recommendation.dto.GooglePlaceSearchResponse;
import com.moive.MoiveBE.domain.recommendation.dto.RecommendedAreaListResponse;
import com.moive.MoiveBE.domain.recommendation.entity.RecommendationRun;
import com.moive.MoiveBE.domain.recommendation.entity.RecommendationStatus;
import com.moive.MoiveBE.domain.recommendation.entity.RecommendedArea;
import com.moive.MoiveBE.domain.recommendation.repository.RecommendationRunRepository;
import com.moive.MoiveBE.domain.recommendation.repository.RecommendedAreaRepository;
import com.moive.MoiveBE.global.exception.CustomErrorCode;
import com.moive.MoiveBE.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RecommendedAreaQueryService {

    private final RecommendationRunRepository recommendationRunRepository;
    private final RecommendedAreaRepository recommendedAreaRepository;
    private final GooglePlacesClient googlePlacesClient;

    public RecommendedAreaListResponse getRecommendedAreas(
            Long meetingId
    ) {

        RecommendationRun run =
                recommendationRunRepository
                        .findTopByMeetingIdAndStatusOrderByCreatedAtDesc(
                                meetingId,
                                RecommendationStatus.COMPLETED
                        )
                        .orElseThrow(() ->
                                new CustomException(
                                        CustomErrorCode.RECOMMENDATION_RESULT_NOT_FOUND
                                )
                        );

        List<RecommendedArea> recommendedAreas =
                recommendedAreaRepository
                        .findAllByRecommendationRunId(
                                run.getId()
                        );

        List<RecommendedAreaListResponse.Area> areas =
                recommendedAreas.stream()
                        .map(this::toResponse)
                        .toList();

        return new RecommendedAreaListResponse(areas);
    }

    private RecommendedAreaListResponse.Area toResponse(
            RecommendedArea recommendedArea
    ) {

        GooglePlaceSearchResponse response =
                googlePlacesClient.searchAreaByName(
                        recommendedArea.getAreaName()
                );

        if (response == null
                || response.places() == null
                || response.places().isEmpty()) {
            throw new CustomException(
                    CustomErrorCode.AREA_INFO_LOOKUP_FAILED
            );
        }

        GooglePlaceSearchResponse.Place place =
                response.places().get(0);

        if (place.location() == null) {
            throw new CustomException(
                    CustomErrorCode.AREA_INFO_LOOKUP_FAILED
            );
        }

        return new RecommendedAreaListResponse.Area(
                recommendedArea.getId(),
                recommendedArea.getAreaName(),
                place.location().latitude(),
                place.location().longitude()
        );
    }
}