package com.moive.MoiveBE.domain.recommendation.client;

import com.moive.MoiveBE.domain.recommendation.dto.AreaCandidateResponse;
import com.moive.MoiveBE.domain.recommendation.dto.AreaCenter;
import com.moive.MoiveBE.domain.recommendation.dto.OpenAIResponse;
import com.moive.MoiveBE.global.exception.CustomErrorCode;
import com.moive.MoiveBE.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OpenAIClient {

    private static final String OPENAI_RESPONSES_URL =
            "https://api.openai.com/v1/responses";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Value("${openai.api-key}")
    private String apiKey;

    @Value("${openai.model}")
    private String model;

    public AreaCandidateResponse generateAreaCandidates(
            AreaCenter center
    ) {
        return generateAreaCandidates(
                center,
                List.of()
        );
    }

    public AreaCandidateResponse generateAreaCandidates(
            AreaCenter center,
            List<String> excludedAreas
    ) {

        String prompt =
                buildPrompt(
                        center,
                        excludedAreas
                );

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "input", prompt,
                "text", Map.of(
                        "format", Map.of(
                                "type", "json_schema",
                                "name", "area_candidates",
                                "strict", true,
                                "schema", Map.of(
                                        "type", "object",
                                        "properties", Map.of(
                                                "areas", Map.of(
                                                        "type", "array",
                                                        "items", Map.of(
                                                                "type", "string"
                                                        ),
                                                        "minItems", 10,
                                                        "maxItems", 10
                                                )
                                        ),
                                        "required", List.of("areas"),
                                        "additionalProperties", false
                                )
                        )
                )
        );

        try {
            OpenAIResponse response =
                    restClient.post()
                            .uri(OPENAI_RESPONSES_URL)
                            .header(
                                    "Authorization",
                                    "Bearer " + apiKey
                            )
                            .body(requestBody)
                            .retrieve()
                            .body(OpenAIResponse.class);

            if (response == null
                    || response.output() == null
                    || response.output().isEmpty()
                    || response.output().get(0).content() == null
                    || response.output().get(0).content().isEmpty()) {

                throw new CustomException(
                        CustomErrorCode.AREA_CANDIDATE_GENERATION_FAILED
                );
            }

            String json =
                    response.output()
                            .get(0)
                            .content()
                            .get(0)
                            .text();

            return objectMapper.readValue(
                    json,
                    AreaCandidateResponse.class
            );

        } catch (RestClientResponseException e) {
            throw new CustomException(
                    CustomErrorCode.AREA_CANDIDATE_GENERATION_FAILED
            );
        } catch (Exception e) {
            throw new CustomException(
                    CustomErrorCode.AREA_CANDIDATE_GENERATION_FAILED
            );
        }
    }

    private String buildPrompt(
            AreaCenter center,
            List<String> excludedAreas
    ) {

        String excludedText =
                excludedAreas.isEmpty()
                        ? "없음"
                        : String.join(
                        ", ",
                        excludedAreas
                );

        return """
                다음 중심 좌표를 기준으로 반경 5km 이내에서
                사람들이 약속이나 모임 장소로 자주 선택하는 지역 10개를 추천해줘.

                중심 위도: %f
                중심 경도: %f

                제외할 지역:
                %s

                조건:
                - 음식점, 카페, 상권이 발달한 지역을 우선 고려한다.
                - 대중교통 접근성이 좋은 지역을 우선 고려한다.
                - 제외할 지역에 포함된 지역은 반환하지 않는다.
                - 서로 다른 지역 10개를 반환한다.
                - '역삼1동', '역삼2동'처럼 세분화하지 말고 '역삼동'처럼 반환한다.
                - 지역명만 반환한다.
                """
                .formatted(
                        center.latitude(),
                        center.longitude(),
                        excludedText
                );
    }
}