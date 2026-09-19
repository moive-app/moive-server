package com.moive.MoiveBE.domain.recommendation.service;

import com.moive.MoiveBE.domain.recommendation.dto.VWorldLegalDong;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
public class VWorldLegalDongParser {

    public List<VWorldLegalDong> parse(String xml) {

        try {
            DocumentBuilderFactory factory =
                    DocumentBuilderFactory.newInstance();

            factory.setNamespaceAware(true);

            var builder = factory.newDocumentBuilder();

            var document = builder.parse(
                    new ByteArrayInputStream(
                            xml.getBytes(StandardCharsets.UTF_8)
                    )
            );

            var dongNodes =
                    document.getElementsByTagNameNS(
                            "https://www.vworld.kr",
                            "dt_d001_emd"
                    );

            List<VWorldLegalDong> result = new ArrayList<>();

            for (int i = 0; i < dongNodes.getLength(); i++) {

                Element dongElement =
                        (Element) dongNodes.item(i);

                String code =
                        getText(dongElement, "ld_emd_code");

                String name =
                        getText(dongElement, "ld_emd_code_nm");

                String signguCode =
                        getText(dongElement, "src_signgu_code");

                Coordinate center =
                        calculateCenter(dongElement);

                result.add(
                        new VWorldLegalDong(
                                code,
                                name,
                                signguCode,
                                center.latitude(),
                                center.longitude()
                        )
                );
            }

            return result;

        } catch (Exception e) {
            throw new IllegalStateException(
                    "VWorld 법정동 XML 파싱에 실패했습니다.",
                    e
            );
        }
    }

    private Coordinate calculateCenter(Element dongElement) {

        var coordinateNodes =
                dongElement.getElementsByTagNameNS(
                        "http://www.opengis.net/gml",
                        "coordinates"
                );

        if (coordinateNodes.getLength() == 0) {
            throw new IllegalStateException(
                    "법정동 geometry 좌표가 없습니다."
            );
        }

        String coordinatesText =
                coordinateNodes.item(0)
                        .getTextContent()
                        .trim();

        String[] coordinates =
                coordinatesText.split("\\s+");

        double minLongitude = Double.MAX_VALUE;
        double maxLongitude = -Double.MAX_VALUE;
        double minLatitude = Double.MAX_VALUE;
        double maxLatitude = -Double.MAX_VALUE;

        for (String coordinate : coordinates) {

            String[] values =
                    coordinate.split(",");

            if (values.length < 2) {
                continue;
            }

            double longitude =
                    Double.parseDouble(values[0]);

            double latitude =
                    Double.parseDouble(values[1]);

            minLongitude =
                    Math.min(minLongitude, longitude);

            maxLongitude =
                    Math.max(maxLongitude, longitude);

            minLatitude =
                    Math.min(minLatitude, latitude);

            maxLatitude =
                    Math.max(maxLatitude, latitude);
        }

        double centerLongitude =
                (minLongitude + maxLongitude) / 2.0;

        double centerLatitude =
                (minLatitude + maxLatitude) / 2.0;

        return new Coordinate(
                centerLatitude,
                centerLongitude
        );
    }

    private String getText(
            Element element,
            String tagName
    ) {

        var nodes =
                element.getElementsByTagNameNS(
                        "https://www.vworld.kr",
                        tagName
                );

        if (nodes.getLength() == 0) {
            return null;
        }

        return nodes.item(0)
                .getTextContent()
                .trim();
    }

    private record Coordinate(
            double latitude,
            double longitude
    ) {
    }
}