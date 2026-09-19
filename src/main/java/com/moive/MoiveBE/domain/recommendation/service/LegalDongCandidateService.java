package com.moive.MoiveBE.domain.recommendation.service;

import com.moive.MoiveBE.domain.recommendation.client.VWorldLegalDongClient;
import com.moive.MoiveBE.domain.recommendation.dto.AreaCandidate;
import com.moive.MoiveBE.domain.recommendation.dto.AreaCenter;
import com.moive.MoiveBE.domain.recommendation.dto.VWorldLegalDong;
import com.moive.MoiveBE.domain.recommendation.dto.VWorldSigunguResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LegalDongCandidateService {

    private static final int MAX_CANDIDATES = 10;
    private static final double EARTH_RADIUS_KM = 6371.0;

    private final LegalDongBboxService legalDongBboxService;
    private final VWorldLegalDongClient vWorldLegalDongClient;
    private final VWorldLegalDongParser vWorldLegalDongParser;

    public List<AreaCandidate> generate(AreaCenter center) {

        String bbox =
                legalDongBboxService.create(center);

        String xml =
                vWorldLegalDongClient.getLegalDongs(bbox);

        List<VWorldLegalDong> legalDongs =
                vWorldLegalDongParser.parse(xml);

        List<VWorldLegalDong> selectedDongs =
                legalDongs.stream()
                        .collect(Collectors.toMap(
                                dong -> dong.signguCode() + ":" + dong.name(),
                                dong -> dong,
                                (existing, replacement) -> existing
                        ))
                        .values()
                        .stream()
                        .sorted(Comparator.comparingDouble(
                                dong -> calculateDistance(
                                        center.latitude(),
                                        center.longitude(),
                                        dong.latitude(),
                                        dong.longitude()
                                )
                        ))
                        .limit(MAX_CANDIDATES)
                        .toList();

        Map<String, String> sigunguNameCache = new HashMap<>();

        return selectedDongs.stream()
                .map(dong -> {

                    String fullSigunguName =
                            sigunguNameCache.computeIfAbsent(
                                    dong.signguCode(),
                                    this::getFullSigunguName
                            );

                    String searchName =
                            fullSigunguName + " " + dong.name();

                    return new AreaCandidate(
                            dong.name(),
                            searchName,
                            dong.signguCode(),
                            dong.latitude(),
                            dong.longitude()
                    );
                })
                .toList();
    }

    private String getFullSigunguName(String signguCode) {

        VWorldSigunguResponse response =
                vWorldLegalDongClient.getSigungu(signguCode);

        return response.response()
                .result()
                .featureCollection()
                .features()
                .get(0)
                .properties()
                .full_nm();
    }

    private double calculateDistance(
            double lat1,
            double lng1,
            double lat2,
            double lng2
    ) {

        double latDistance =
                Math.toRadians(lat2 - lat1);

        double lngDistance =
                Math.toRadians(lng2 - lng1);

        double a =
                Math.sin(latDistance / 2)
                        * Math.sin(latDistance / 2)
                        + Math.cos(Math.toRadians(lat1))
                        * Math.cos(Math.toRadians(lat2))
                        * Math.sin(lngDistance / 2)
                        * Math.sin(lngDistance / 2);

        double c =
                2 * Math.atan2(
                        Math.sqrt(a),
                        Math.sqrt(1 - a)
                );

        return EARTH_RADIUS_KM * c;
    }
}