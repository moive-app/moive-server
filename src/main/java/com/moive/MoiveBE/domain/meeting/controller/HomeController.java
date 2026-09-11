package com.moive.MoiveBE.domain.meeting.controller;

import com.moive.MoiveBE.domain.meeting.dto.HomeResponse;
import com.moive.MoiveBE.domain.meeting.service.HomeService;
import com.moive.MoiveBE.global.common.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Home", description = "홈 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class HomeController {

    private final HomeService homeService;

    @Operation(
            summary = "홈 메인화면 조회",
            description = "확정된 모임 캐러셀과 내 모임 목록을 반환합니다."
    )
    @GetMapping("/home")
    public BaseResponse<HomeResponse> getHome(
            @RequestParam(defaultValue = "ALL") String filter
    ) {
        return BaseResponse.success("홈 데이터 조회에 성공했습니다.", homeService.getHome(filter));
    }
}
