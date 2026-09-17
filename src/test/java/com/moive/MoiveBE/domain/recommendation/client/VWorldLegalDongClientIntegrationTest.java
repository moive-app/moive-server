package com.moive.MoiveBE.domain.recommendation.client;

import com.moive.MoiveBE.domain.recommendation.dto.AreaCenter;
import com.moive.MoiveBE.domain.recommendation.service.LegalDongBboxService;
import com.moive.MoiveBE.domain.recommendation.service.LegalDongCandidateService;
import com.moive.MoiveBE.domain.recommendation.service.VWorldLegalDongParser;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled("실제 VWorld API 호출 테스트")
@SpringBootTest
@ActiveProfiles("local")
class VWorldLegalDongClientIntegrationTest {

    @Autowired
    private VWorldLegalDongClient vWorldLegalDongClient;

    @Autowired
    private VWorldLegalDongParser parser;

    @Autowired
    private LegalDongBboxService legalDongBboxService;

    @Autowired
    private LegalDongCandidateService legalDongCandidateService;

    @Test
    void 법정동을_조회한다() {

        AreaCenter center =
                new AreaCenter(
                        37.1871,
                        127.0434
                );

        String bbox =
                legalDongBboxService.create(center);

        String response =
                vWorldLegalDongClient.getLegalDongs(bbox);

        var legalDongs =
                parser.parse(response);

        assertThat(legalDongs).isNotEmpty();
    }

    @Test
    void 중심점에서_가까운_법정동_10개를_선정한다() {

        AreaCenter center =
                new AreaCenter(
                        37.1871,
                        127.0434
                );

        var candidates =
                legalDongCandidateService.generate(center);

        assertThat(candidates).hasSize(10);
    }
}