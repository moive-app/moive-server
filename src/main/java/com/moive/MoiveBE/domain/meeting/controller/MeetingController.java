package com.moive.MoiveBE.domain.meeting.controller;

import com.moive.MoiveBE.domain.meeting.dto.CreateMeetingRequest;
import com.moive.MoiveBE.domain.meeting.dto.CreateMeetingResponse;
import com.moive.MoiveBE.domain.meeting.dto.JoinMeetingResponse;
import com.moive.MoiveBE.domain.meeting.service.MeetingService;
import com.moive.MoiveBE.global.common.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Meeting", description = "모임 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/meetings")
public class MeetingController {

    private final MeetingService meetingService;

    @Operation(
            summary = "모임 생성",
            description = "모임을 생성합니다. 생성 즉시 모임장이 참여자로 등록되며 초대 링크가 발급됩니다."
    )
    @PostMapping
    public BaseResponse<CreateMeetingResponse> createMeeting(
            @RequestBody CreateMeetingRequest request
    ) {
        return BaseResponse.success("모임이 생성되었습니다.", meetingService.createMeeting(request));
    }

    @Operation(
            summary = "초대 링크 참여",
            description = "초대 코드를 통해 모임에 참여합니다. 이미 참여 중이면 기존 상태를 그대로 반환합니다."
    )
    @PostMapping("/invite/{inviteCode}/join")
    public BaseResponse<JoinMeetingResponse> joinMeeting(
            @PathVariable String inviteCode
    ) {
        return BaseResponse.success("모임에 참여했습니다.", meetingService.joinMeeting(inviteCode));
    }
}
