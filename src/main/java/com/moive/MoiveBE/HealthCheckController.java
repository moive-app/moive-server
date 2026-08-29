package com.moive.MoiveBE;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.moive.MoiveBE.global.common.BaseResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;


@Tag(name = "테스트용")
@RestController
public class HealthCheckController {

    @Operation(summary = "hello 테스트")
    @SecurityRequirements
    @GetMapping("/hello")
    public ResponseEntity<BaseResponse<String>> hello() {
        return ResponseEntity.ok(BaseResponse.success("hello success!"));
    }
}
