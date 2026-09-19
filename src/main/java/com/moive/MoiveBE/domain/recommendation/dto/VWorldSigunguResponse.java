package com.moive.MoiveBE.domain.recommendation.dto;

import java.util.List;

public record VWorldSigunguResponse(
        Response response
) {

    public record Response(
            String status,
            Result result
    ) {}

    public record Result(
            FeatureCollection featureCollection
    ) {}

    public record FeatureCollection(
            List<Feature> features
    ) {}

    public record Feature(
            Properties properties
    ) {}

    public record Properties(
            String sig_cd,
            String sig_kor_nm,
            String full_nm
    ) {}
}