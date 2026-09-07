package com.moive.MoiveBE.domain.recommendation.controller;

import com.moive.MoiveBE.domain.recommendation.dto.RecommendedPlaceListResponse;
import com.moive.MoiveBE.domain.recommendation.service.RecommendationService;
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
}