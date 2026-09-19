package com.moive.MoiveBE.domain.recommendation.service;

import com.moive.MoiveBE.domain.recommendation.dto.AreaCandidate;
import com.moive.MoiveBE.domain.recommendation.dto.AreaCenter;
import com.moive.MoiveBE.domain.recommendation.dto.GooglePlaceSearchResponse;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled("실제 VWorld / Google Places API 호출 테스트")
@SpringBootTest
class AreaCandidateResolverIntegrationTest {

    @Autowired
    private LegalDongCandidateService legalDongCandidateService;

    @Autowired
    private AreaCandidateResolver areaCandidateResolver;

    @Test
    void meeting71_남면_실제조회_테스트() {

        // meeting 71 참가자 출발지 기준 중심 좌표
        AreaCenter center =
                new AreaCenter(
                        37.5599985,
                        127.6927097
                );

        // 1. 실제 VWorld에서 중심점 주변 법정동 조회
        List<AreaCandidate> candidates =
                legalDongCandidateService.generate(center);

        System.out.println("===== VWorld 후보 =====");

        candidates.forEach(candidate ->
                System.out.println(
                        "name=" + candidate.name()
                                + ", searchName=" + candidate.searchName()
                )
        );

        // 2. 기존 문제 지역인 '남면' 찾기
        AreaCandidate namMyeon =
                candidates.stream()
                        .filter(candidate ->
                                candidate.name().equals("남면")
                        )
                        .findFirst()
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "VWorld 후보에서 남면을 찾지 못했습니다."
                                )
                        );

        // 3. VWorld에서 같은 이름의 다른 남면이 아니라
        //    홍천군 남면으로 식별됐는지 확인
        assertThat(namMyeon.searchName())
                .isEqualTo("강원특별자치도 홍천군 남면");

        System.out.println(
                "[VWorld 결과] 남면 searchName = "
                        + namMyeon.searchName()
        );

        // 4. 전체 행정구역명으로 실제 Google Places 조회
        GooglePlaceSearchResponse.Place place =
                areaCandidateResolver.resolve(
                        namMyeon.searchName(),
                        center.latitude(),
                        center.longitude()
                );

        System.out.println(
                "[Google 결과] "
                        + namMyeon.searchName()
                        + " = "
                        + place
        );

        // 5. Google에서도 실제 좌표를 얻었는지 확인
        assertThat(place).isNotNull();
        assertThat(place.location()).isNotNull();
    }
}