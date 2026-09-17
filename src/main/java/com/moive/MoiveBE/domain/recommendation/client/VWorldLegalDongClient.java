package com.moive.MoiveBE.domain.recommendation.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

@Component
public class VWorldLegalDongClient {

    private final RestClient restClient;

    @Value("${vworld.api-key}")
    private String apiKey;

    public VWorldLegalDongClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public String getLegalDongs(String bbox) {

        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host("api.vworld.kr")
                        .path("/req/wfs")
                        .queryParam("service", "WFS")
                        .queryParam("request", "GetFeature")
                        .queryParam("version", "1.1.0")
                        .queryParam("typename", "dt_d001_emd")
                        .queryParam("bbox", bbox)
                        .queryParam("srsName", "EPSG:4326")
                        .queryParam("maxFeatures", 30)
                        .queryParam("key", apiKey)
                        .build())
                .exchange((request, response) -> {
                    try {
                        return new String(
                                response.getBody().readAllBytes(),
                                StandardCharsets.UTF_8
                        );
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
    }
}