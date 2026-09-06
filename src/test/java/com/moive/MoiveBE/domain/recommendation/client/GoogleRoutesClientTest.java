package com.moive.MoiveBE.domain.recommendation.client;

import com.moive.MoiveBE.domain.recommendation.dto.GoogleRouteMatrixRequest;
import com.moive.MoiveBE.domain.recommendation.dto.GoogleRouteMatrixResponse;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled("실제 Google Routes APi 연동 확인용 테스트")
@SpringBootTest
class GoogleRoutesClientTest {

    @Autowired
    private GoogleRoutesClient googleRoutesClient;

    @Test
    void 대중교통_이동시간을_조회한다() {

        GoogleRouteMatrixRequest request = new GoogleRouteMatrixRequest(
                List.of(
                        new GoogleRouteMatrixRequest.RouteMatrixOrigin(
                                new GoogleRouteMatrixRequest.Waypoint(
                                        new GoogleRouteMatrixRequest.Location(
                                                new GoogleRouteMatrixRequest.LatLng(
                                                        37.4979,
                                                        127.0276
                                                )
                                        )
                                )
                        )
                ),
                List.of(
                        new GoogleRouteMatrixRequest.RouteMatrixDestination(
                                new GoogleRouteMatrixRequest.Waypoint(
                                        new GoogleRouteMatrixRequest.Location(
                                                new GoogleRouteMatrixRequest.LatLng(
                                                        37.5563,
                                                        126.9236
                                                )
                                        )
                                )
                        )
                ),
                "TRANSIT"
        );

        List<GoogleRouteMatrixResponse> response =
                googleRoutesClient.computeRouteMatrix(request);

        assertThat(response).isNotNull();
        assertThat(response).isNotEmpty();

        response.forEach(System.out::println);
    }
}