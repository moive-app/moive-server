package com.moive.MoiveBE.domain.route.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoTransitErrorResponse(
        String errorType,
        String message,
        List<Detail> details
) {
    public record Detail(
            String field,
            String error
    ) {
    }
}
