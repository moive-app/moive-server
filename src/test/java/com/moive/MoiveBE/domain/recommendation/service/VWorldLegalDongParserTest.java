package com.moive.MoiveBE.domain.recommendation.service;

import com.moive.MoiveBE.domain.recommendation.dto.VWorldLegalDong;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VWorldLegalDongParserTest {

    private final VWorldLegalDongParser parser =
            new VWorldLegalDongParser();

    @Test
    void 법정동_코드의_앞_5자리로_시군구_코드를_생성한다() {

        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <wfs:FeatureCollection
                    xmlns:wfs="http://www.opengis.net/wfs"
                    xmlns:gml="http://www.opengis.net/gml"
                    xmlns:sop="https://www.vworld.kr">

                    <gml:featureMember>
                        <sop:dt_d001_emd>
                            <sop:ld_emd_code>41135111</sop:ld_emd_code>
                            <sop:ld_emd_code_nm>금곡동</sop:ld_emd_code_nm>
                            <sop:src_signgu_code>41130</sop:src_signgu_code>

                            <sop:ag_geom>
                                <gml:MultiPolygon>
                                    <gml:polygonMember>
                                        <gml:Polygon>
                                            <gml:outerBoundaryIs>
                                                <gml:LinearRing>
                                                    <gml:coordinates>
                                                        127.077,37.346 127.117,37.346
                                                        127.117,37.368 127.077,37.368
                                                        127.077,37.346
                                                    </gml:coordinates>
                                                </gml:LinearRing>
                                            </gml:outerBoundaryIs>
                                        </gml:Polygon>
                                    </gml:polygonMember>
                                </gml:MultiPolygon>
                            </sop:ag_geom>
                        </sop:dt_d001_emd>
                    </gml:featureMember>

                </wfs:FeatureCollection>
                """;

        List<VWorldLegalDong> result =
                parser.parse(xml);

        assertThat(result).hasSize(1);

        VWorldLegalDong dong = result.get(0);

        assertThat(dong.code()).isEqualTo("41135111");
        assertThat(dong.name()).isEqualTo("금곡동");

        assertThat(dong.signguCode()).isEqualTo("41135");
    }
}