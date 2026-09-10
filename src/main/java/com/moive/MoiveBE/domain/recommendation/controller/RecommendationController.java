package com.moive.MoiveBE.domain.recommendation.controller;

import com.moive.MoiveBE.domain.recommendation.dto.RecommendedAreaListResponse;
import com.moive.MoiveBE.domain.recommendation.dto.RecommendedPlaceDetailResponse;
import com.moive.MoiveBE.domain.recommendation.dto.RecommendedPlaceListResponse;
import com.moive.MoiveBE.domain.recommendation.service.RecommendationService;
import com.moive.MoiveBE.domain.recommendation.service.RecommendedAreaQueryService;
import com.moive.MoiveBE.global.common.BaseResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

@Tag(name = "Recommendation", description = "지역/장소 추천 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/meetings/{meetingId}/recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;
    private final RecommendedAreaQueryService recommendedAreaQueryService;

    @Operation(
            summary = "추천 지역 목록 조회",
            description = "특정 모임의 참가자 출발 위치를 기반으로 선정된 추천 지역 TOP3를 조회합니다."
    )
    @GetMapping("/areas")
    public BaseResponse<RecommendedAreaListResponse> getRecommendedAreas(
            @PathVariable Long meetingId
    ) {
        return BaseResponse.success(
                recommendedAreaQueryService.getRecommendedAreas(meetingId)
        );
    }

    @Operation(
            summary = "추천 장소 목록 조회",
            description = "특정 모임의 추천 지역을 기준으로 추천 장소 목록을 조회합니다."
    )
    @GetMapping("/areas/{recommendedAreaId}/places")
    public BaseResponse<RecommendedPlaceListResponse> getRecommendedPlaces(
            @PathVariable Long meetingId,
            @PathVariable Long recommendedAreaId
    ) {
        return BaseResponse.success(
                recommendationService.getRecommendedPlaces(recommendedAreaId)
        );
    }

    @Operation(
            summary = "추천 장소 상세 조회",
            description = "특정 추천 장소의 상세 정보를 조회합니다."
    )
    @GetMapping("/areas/{recommendedAreaId}/places/{recommendedPlaceId}")
    public BaseResponse<RecommendedPlaceDetailResponse> getRecommendedPlaceDetail(
            @PathVariable Long meetingId,
            @PathVariable Long recommendedAreaId,
            @PathVariable Long recommendedPlaceId
    ) {
        return BaseResponse.success(
                recommendationService.getRecommendedPlaceDetail(
                        recommendedAreaId,
                        recommendedPlaceId
                )
        );
    }
}