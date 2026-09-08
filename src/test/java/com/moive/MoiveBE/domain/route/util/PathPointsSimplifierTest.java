package com.moive.MoiveBE.domain.route.util;

import com.moive.MoiveBE.domain.route.dto.Location;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PathPointsSimplifierTest {

    @Test
    void 좌표가_3개_미만이면_단순화하지_않고_그대로_반환한다() {
        // given
        List<Location> onePoint = List.of(new Location(0, 0));
        List<Location> twoPoints = List.of(new Location(0, 0), new Location(1, 1));

        // when & then
        assertThat(PathPointsSimplifier.simplify(onePoint)).isEqualTo(onePoint);
        assertThat(PathPointsSimplifier.simplify(twoPoints)).isEqualTo(twoPoints);
    }

    @Test
    void 일직선상의_중간_좌표는_모두_제거되고_시작점과_끝점만_남는다() {
        // given (위도 0 -> 4로 일직선 이동, 중간에 촘촘한 점들)
        List<Location> straightLine = List.of(
                new Location(0, 0),
                new Location(1, 0),
                new Location(2, 0),
                new Location(3, 0),
                new Location(4, 0)
        );

        // when
        List<Location> simplified = PathPointsSimplifier.simplify(straightLine, 0.001);

        // then
        assertThat(simplified).containsExactly(
                new Location(0, 0),
                new Location(4, 0)
        );
    }

    @Test
    void 꺾이는_지점은_유지되고_직선구간의_중간점은_제거된다() {
        // given (0,0) -> (0,2)로 수직 이동 후 (0,2) -> (2,2)로 수평 이동하는 ㄴ자 경로
        List<Location> lShapedPath = List.of(
                new Location(0, 0),
                new Location(1, 0),
                new Location(2, 0), // 꺾이는 지점
                new Location(2, 1),
                new Location(2, 2)
        );

        // when
        List<Location> simplified = PathPointsSimplifier.simplify(lShapedPath, 0.001);

        // then (시작점, 꺾이는 지점, 끝점만 남아야 함)
        assertThat(simplified).containsExactly(
                new Location(0, 0),
                new Location(2, 0),
                new Location(2, 2)
        );
    }

    @Test
    void 시작점과_끝점은_tolerance와_무관하게_항상_보존된다() {
        // given
        List<Location> path = List.of(
                new Location(37.5788132079661, 126.901364655063),
                new Location(37.577, 126.902),
                new Location(37.576, 126.9025),
                new Location(37.5510324090502, 126.91228338125131)
        );
        Location expectedFirst = path.get(0);
        Location expectedLast = path.get(path.size() - 1);

        // when
        List<Location> simplifiedWithSmallTolerance = PathPointsSimplifier.simplify(path, 0.00001);
        List<Location> simplifiedWithLargeTolerance = PathPointsSimplifier.simplify(path, 1.0);

        // then
        assertThat(simplifiedWithSmallTolerance.get(0)).isEqualTo(expectedFirst);
        assertThat(simplifiedWithSmallTolerance.get(simplifiedWithSmallTolerance.size() - 1)).isEqualTo(expectedLast);

        assertThat(simplifiedWithLargeTolerance.get(0)).isEqualTo(expectedFirst);
        assertThat(simplifiedWithLargeTolerance.get(simplifiedWithLargeTolerance.size() - 1)).isEqualTo(expectedLast);
    }

    @Test
    void tolerance가_클수록_남는_좌표_개수가_같거나_더_적다() {
        // given (약간씩 어긋난 지그재그 경로)
        List<Location> zigzagPath = List.of(
                new Location(0, 0),
                new Location(1, 0.1),
                new Location(2, -0.1),
                new Location(3, 0.15),
                new Location(4, -0.05),
                new Location(5, 0)
        );

        // when
        List<Location> simplifiedWithSmallTolerance = PathPointsSimplifier.simplify(zigzagPath, 0.05);
        List<Location> simplifiedWithLargeTolerance = PathPointsSimplifier.simplify(zigzagPath, 0.5);

        // then
        assertThat(simplifiedWithLargeTolerance.size())
                .as("tolerance가 클수록 더 많이 단순화되어 좌표 개수가 같거나 적어야 함")
                .isLessThanOrEqualTo(simplifiedWithSmallTolerance.size());
    }

}
