package com.moive.MoiveBE.domain.meeting.controller;

import com.moive.MoiveBE.domain.meeting.dto.*;
import com.moive.MoiveBE.domain.meeting.service.HomeService;
import com.moive.MoiveBE.domain.meeting.service.MeetingDetailService;
import com.moive.MoiveBE.domain.meeting.service.MeetingLeaveService;
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
    private final MeetingDetailService meetingDetailService;
    private final HomeService homeService;
    private final MeetingLeaveService meetingLeaveService;

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

    @Operation(
            summary = "모임 전체보기",
            description = "내 모임 목록을 cursor 기반 무한스크롤로 반환합니다."
    )
    @GetMapping
    public BaseResponse<MeetingListResponse> getMeetings(
            @RequestParam(defaultValue = "ALL") String filter,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size
    ) {
        return BaseResponse.success("모임 목록 조회에 성공했습니다.", homeService.getMeetings(filter, cursor, size));
    }

    @Operation(
            summary = "모임 상세 조회",
            description = "모임 상세 정보를 조회합니다. 모든 phase(조건입력중/투표진행중/확정/완료)를 하나의 응답으로 반환합니다."
    )
    @GetMapping("/{meetingId}")
    public BaseResponse<MeetingDetailResponse> getMeetingDetail(
            @PathVariable Long meetingId
    ) {
        return BaseResponse.success("모임 상세 조회에 성공했습니다.", meetingDetailService.getMeetingDetail(meetingId));
    }

    @Operation(
            summary = "모임 나가기",
            description = "모임에서 나갑니다. 모임장 나가기 시 자동 승계, 마지막 참여자 나가기 시 모임 종료 처리됩니다."
    )
    @DeleteMapping("/{meetingId}/leave")
    public BaseResponse<Void> leaveMeeting(
            @PathVariable Long meetingId
    ) {
        meetingLeaveService.leaveMeeting(meetingId);
        return BaseResponse.success("모임에서 나갔습니다.", null);
    }

    @Operation(
            summary = "모임 홈 화면 조회",
            description = "모임 홈 화면 데이터를 조회합니다. 배너 문구와 CTA 버튼 문구를 포함합니다."
    )
    @GetMapping("/{meetingId}/home")
    public BaseResponse<MeetingHomeResponse> getMeetingHome(
            @PathVariable Long meetingId
    ) {
        return BaseResponse.success("모임 홈 조회에 성공했습니다.", meetingDetailService.getMeetingHome(meetingId));
    }
}
