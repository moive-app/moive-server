package com.moive.MoiveBE.domain.vote.controller;

import com.moive.MoiveBE.domain.vote.dto.DateVoteResultResponse;
import com.moive.MoiveBE.domain.vote.dto.PlaceVoteRequest;
import com.moive.MoiveBE.domain.vote.dto.PlaceVoteResultResponse;
import com.moive.MoiveBE.domain.vote.service.VoteService;
import com.moive.MoiveBE.global.common.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Vote", description = "투표 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/meetings/{meetingId}")
public class VoteController {

    private final VoteService voteService;

    @Operation(
            summary = "일정 투표 현황 조회",
            description = "일정 투표 현황을 조회합니다."
    )
    @GetMapping("/date-votes/result")
    public BaseResponse<DateVoteResultResponse> getMeetingScheduleVoteResult (
            @AuthenticationPrincipal Long userId,
            @PathVariable Long meetingId
    ) {
        return BaseResponse.success(
                voteService.getMeetingScheduleVoteResult(
                        userId,
                        meetingId
                )
        );
    }

    @Operation(
            summary = "장소 투표",
            description = "추천 장소들에 대한 투표를 진행합니다."
    )
    @PostMapping("/place-votes")
    public BaseResponse<Void> createMeetingPlaceVote (
            @AuthenticationPrincipal Long userId,
            @PathVariable Long meetingId,
            @Valid @RequestBody PlaceVoteRequest request
    ) {
        voteService.createPlaceVote(userId, meetingId, request);
        return BaseResponse.success(null);
    }

    @Operation(
            summary = "장소 투표 현황 조회",
            description = "장소 투표 현황을 조회합니다."
    )
    @GetMapping("/place-votes/result")
    public BaseResponse<PlaceVoteResultResponse> getMeetingPlaceVoteResult (
            @AuthenticationPrincipal Long userId,
            @PathVariable Long meetingId
    ) {
        return BaseResponse.success(
                voteService.getMeetingPlaceVoteResult(userId, meetingId)
        );
    }

}
