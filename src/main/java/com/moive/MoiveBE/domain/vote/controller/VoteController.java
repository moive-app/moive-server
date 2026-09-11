package com.moive.MoiveBE.domain.vote.controller;

import com.moive.MoiveBE.domain.vote.dto.DateVoteResultResponse;
import com.moive.MoiveBE.domain.vote.service.VoteService;
import com.moive.MoiveBE.global.common.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Vote", description = "투표 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/meetings/{meetingId}")
public class VoteController {

    private final VoteService voteService;

    @Operation(
            summary = "일정 투표 현황 조회",
            description = "일정 투표 결과를 조회합니다."
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

}
