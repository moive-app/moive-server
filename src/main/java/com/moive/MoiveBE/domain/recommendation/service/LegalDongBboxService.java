package com.moive.MoiveBE.domain.recommendation.service;

import com.moive.MoiveBE.domain.recommendation.dto.AreaCenter;
import org.springframework.stereotype.Service;

@Service
public class LegalDongBboxService {

    // 우선 반경 3km로 테스트
    private static final double RADIUS_KM = 3.0;

    public String create(AreaCenter center) {

        double latitude = center.latitude();
        double longitude = center.longitude();

        // 위도 1도 ≈ 111km
        double latitudeDelta = RADIUS_KM / 111.0;

        // 경도 1도는 위도에 따라 달라짐
        double longitudeDelta =
                RADIUS_KM /
                        (111.0 * Math.cos(Math.toRadians(latitude)));

        double minLatitude = latitude - latitudeDelta;
        double maxLatitude = latitude + latitudeDelta;

        double minLongitude = longitude - longitudeDelta;
        double maxLongitude = longitude + longitudeDelta;

        // VWorld WFS 1.1.0 + EPSG:4326에서 확인한 순서
        // minLat,minLng,maxLat,maxLng
        return String.format(
                java.util.Locale.US,
                "%.6f,%.6f,%.6f,%.6f",
                minLatitude,
                minLongitude,
                maxLatitude,
                maxLongitude
        );
    }
}