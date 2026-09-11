package com.moive.MoiveBE.domain.recommendation.client;

import com.moive.MoiveBE.domain.recommendation.dto.AreaCandidateResponse;
import com.moive.MoiveBE.domain.recommendation.dto.AreaCenter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAIClientTest {

    private OpenAIClient openAIClient;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {

        RestClient.Builder builder = RestClient.builder();

        mockServer = MockRestServiceServer
                .bindTo(builder)
                .build();

        RestClient restClient = builder.build();

        ObjectMapper objectMapper =
                JsonMapper.builder().build();

        openAIClient = new OpenAIClient(
                restClient,
                objectMapper
        );

        ReflectionTestUtils.setField(
                openAIClient,
                "apiKey",
                "test-api-key"
        );

        ReflectionTestUtils.setField(
                openAIClient,
                "model",
                "gpt-5-mini"
        );
    }

    @Test
    void 추천_지역_후보를_생성한다() {

        String responseBody = """
                {
                  "output": [
                    {
                      "content": [
                        {
                          "type": "output_text",
                          "text": "{\\"areas\\":[\\"역삼동\\",\\"논현동\\",\\"신사동\\",\\"서초동\\",\\"삼성동\\",\\"대치동\\",\\"청담동\\",\\"압구정동\\",\\"잠원동\\",\\"반포동\\"]}"
                        }
                      ]
                    }
                  ]
                }
                """;

        mockServer.expect(
                        requestTo("https://api.openai.com/v1/responses")
                )
                .andExpect(method(POST))
                .andRespond(
                        withSuccess(
                                responseBody,
                                MediaType.APPLICATION_JSON
                        )
                );

        AreaCenter center = new AreaCenter(
                37.4979,
                127.0276
        );

        AreaCandidateResponse response =
                openAIClient.generateAreaCandidates(center);

        assertThat(response).isNotNull();
        assertThat(response.areas()).hasSize(10);
        assertThat(response.areas()).contains("역삼동", "논현동", "신사동");

        mockServer.verify();
    }
}