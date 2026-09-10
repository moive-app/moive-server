package com.moive.MoiveBE.domain.route.service;

import com.moive.MoiveBE.domain.route.client.KakaoTransitClient;
import com.moive.MoiveBE.domain.route.dto.KakaoTransitRouteResponse;
import com.moive.MoiveBE.domain.route.dto.Location;
import com.moive.MoiveBE.domain.route.dto.RouteDetail;
import com.moive.MoiveBE.domain.route.type.TransitType;
import com.moive.MoiveBE.domain.route.type.KakaoTransitStatusType;
import com.moive.MoiveBE.domain.route.util.PathPointsSimplifier;
import com.moive.MoiveBE.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.moive.MoiveBE.global.exception.CustomErrorCode.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class RouteDetailService {

    private final int FIRST_ROUTE_IDX = 0;

    private final KakaoTransitClient kakaoTransitClient;

    // 이동 경로 관련 정보(경로 좌표, 이동 시간, 요금 등)만 생성한다. 응답 DTO 조립은 RouteService가 담당한다.
    public RouteDetail getMyRouteDetail(
            Location userLocation, Location placeLocation,
            String userAddress, String placeName
    ) {
        // 카카오맵 대중교통 경로 조회 API 호출
        KakaoTransitRouteResponse kakaoResponse = kakaoTransitClient.getTransitRoute(
                userLocation, placeLocation,
                userAddress, placeName
        );
        if (kakaoResponse == null || kakaoResponse.status() == null) {
            log.error("[카카오맵 대중교통 경로 조회 API] Response Body가 비어있습니다.");
            throw new CustomException(KAKAO_MAP_API_SERVER_ERROR,
                    KAKAO_MAP_API_SERVER_ERROR.messageWith("응답 데이터 규격 확인 필요"));
        }

        KakaoTransitStatusType status = KakaoTransitStatusType.valueOf(kakaoResponse.status());
        return switch (status) {
            // 대중교통 경로가 존재하는 경우 => 첫번째 경로 활용
            case OK -> {
                KakaoTransitRouteResponse.Route bestRoute = kakaoResponse.routes().get(FIRST_ROUTE_IDX);
                KakaoTransitRouteResponse.RouteProperties totalSummary = bestRoute.properties();

                TransitTimeSummary timeSummary = calculateTransitTimeByType(bestRoute);
                int totalSeconds = totalSummary.totalTime();
                int busSeconds = timeSummary.busSeconds();
                int subwaySeconds = timeSummary.subwaySeconds();
                int walkSeconds = Math.max(0, totalSeconds - (busSeconds + subwaySeconds));

                List<Location> pathPoints = buildSimplifiedPathPoints(bestRoute, userLocation, placeLocation);

                Integer fare = totalSummary.fare() != null ? totalSummary.fare().value() : null;

                yield RouteDetail.builder()
                        .pathPoints(pathPoints)
                        .totalTime(toMinutes(totalSeconds))
                        .subwayTime(toMinutes(subwaySeconds))
                        .busTime(toMinutes(busSeconds))
                        .walkTime(toMinutes(walkSeconds))
                        .fare(fare)
                        .landingUrl(kakaoResponse.properties().landingURL())
                        .build();
            }

            // 대중교통 경로 조회가 불가능한 경우
            case STARTNODES_NULL, ENDNODES_NULL, INVALID_REQUEST -> {
                log.error("[카카오맵 대중교통 경로 조회 API] 요청 좌표로 경로 탐색이 불가능합니다. (status={})", status);
                throw new CustomException(KAKAO_MAP_API_SERVER_ERROR,
                        KAKAO_MAP_API_SERVER_ERROR.messageWith("요청 좌표로 경로 탐색 불가"));
            }

            // 대중교통 경로가 존재하지 않는 경우
            case EQUAL_POINTS, NO_RESULTS -> {
                // TODO: NO_RESULTS인 경우 도보 API 연동 추가 예정
                log.warn("[카카오맵 대중교통 경로 조회 API] 경로가 존재하지 않습니다. (status={})", status);
                throw new CustomException(TRANSIT_ROUTE_NOT_FOUND);
            }
        };
    }

    // 이동 수단(WALKING/BUS/SUBWAY)별 이동 시간 누적합(초 단위) 계산
    TransitTimeSummary calculateTransitTimeByType(KakaoTransitRouteResponse.Route bestRoute) {
        List<KakaoTransitRouteResponse.Step> steps = bestRoute.steps();

        int busSeconds = 0;
        int subwaySeconds = 0;

        for (KakaoTransitRouteResponse.Step step : steps) {
            KakaoTransitRouteResponse.StepProperties stepProperties = step.properties();
            TransitType type = TransitType.fromKakaoType(stepProperties.type());
            int stepSeconds = stepProperties.time() != null ? stepProperties.time() : 0;

            switch (type) {
                case BUS -> busSeconds += stepSeconds;
                case SUBWAY -> subwaySeconds += stepSeconds;
            }
        }

        return new TransitTimeSummary(subwaySeconds, busSeconds);
    }

    record TransitTimeSummary(int subwaySeconds, int busSeconds) {
    }

    // 모든 step의 원본 좌표를 순서대로 이어붙이고, 출발지/도착지를 양 끝에 붙인 뒤 전체를 한 번에 단순화
    List<Location> buildSimplifiedPathPoints(
            KakaoTransitRouteResponse.Route bestRoute,
            Location userLocation,
            Location placeLocation
    ) {
        List<Location> rawPoints = new ArrayList<>();
        addIfNotDuplicate(rawPoints, userLocation);

        for (KakaoTransitRouteResponse.Step step : bestRoute.steps()) {
            // step 경계에서 앞 step의 마지막 좌표와 겹치는 중복 좌표는 제거
            getPathPoints(step.path()).forEach(point -> addIfNotDuplicate(rawPoints, point));
        }

        addIfNotDuplicate(rawPoints, placeLocation);

        return PathPointsSimplifier.simplify(rawPoints);
    }

    // 직전 좌표와 동일하지 않은 경우에만 추가 (연속 중복 좌표 제거)
    private void addIfNotDuplicate(List<Location> points, Location point) {
        if (points.isEmpty() || !points.get(points.size() - 1).equals(point)) {
            points.add(point);
        }
    }

    // 카카오 원본 좌표 전부 반환 (카카오 규격: [경도, 위도] 순서)
    private List<Location> getPathPoints(KakaoTransitRouteResponse.Path path) {
        return Arrays.stream(path.points())
                .map(point -> new Location(point[1], point[0]))
                .toList();
    }

    // 초 -> 분 변환 (반올림)
    private int toMinutes(int seconds) {
        return Math.round(seconds / 60.0f);
    }

}
