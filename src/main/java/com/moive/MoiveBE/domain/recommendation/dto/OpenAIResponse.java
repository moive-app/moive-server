package com.moive.MoiveBE.domain.recommendation.dto;

import java.util.List;

public record OpenAIResponse(
        List<Output> output
) {

    public record Output(
            List<Content> content
    ) {
    }

    public record Content(
            String type,
            String text
    ) {
    }
}