package com.moive.MoiveBE.domain.route.util;

import com.moive.MoiveBE.domain.route.dto.Location;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.simplify.DouglasPeuckerSimplifier;

import java.util.Arrays;
import java.util.List;

// Douglas-Peucker 알고리즘으로 이동 경로 좌표 개수를 줄인다.
// - 목적: 지도에 표시할 때 꺾이는 지점만 남기고 촘촘한 중간 좌표들은 제거
public class PathPointsSimplifier {

    public static final double DEFAULT_TOLERANCE = 0.0001;

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

    public static List<Location> simplify(List<Location> path) {
        return simplify(path, DEFAULT_TOLERANCE);
    }

    public static List<Location> simplify(List<Location> path, double tolerance) {
        // 좌표가 2개 이하면 단순화 불필요
        if (path.size() < 3) {
            return path;
        }

        Coordinate[] coordinates = path.stream()
                .map(location -> new Coordinate(location.longitude(), location.latitude()))
                .toArray(Coordinate[]::new);

        LineString lineString = GEOMETRY_FACTORY.createLineString(coordinates);
        Geometry simplified = DouglasPeuckerSimplifier.simplify(lineString, tolerance);

        return Arrays.stream(simplified.getCoordinates())
                .map(coordinate -> new Location(coordinate.y, coordinate.x))
                .toList();
    }
}
