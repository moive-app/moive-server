package com.moive.MoiveBE.domain.recommendation.service;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CandidateAllocationServiceTest {

    private final CandidateAllocationService candidateAllocationService =
            new CandidateAllocationService();

    @Test
    void 취향_선택_비율에_따라_후보_개수를_배분한다() {

        Map<String, Integer> selectionCounts = new LinkedHashMap<>();
        selectionCounts.put("한식", 5);
        selectionCounts.put("카페", 4);
        selectionCounts.put("보드게임", 3);
        selectionCounts.put("영화관", 2);

        Map<String, Integer> result =
                candidateAllocationService.allocate(selectionCounts);

        assertThat(result)
                .containsEntry("한식", 4)
                .containsEntry("카페", 3)
                .containsEntry("보드게임", 2)
                .containsEntry("영화관", 1);

        assertThat(result.values().stream()
                .mapToInt(Integer::intValue)
                .sum())
                .isEqualTo(10);
    }

    @Test
    void 선택된_취향이_없으면_빈_결과를_반환한다() {

        Map<String, Integer> selectionCounts = Map.of();

        Map<String, Integer> result =
                candidateAllocationService.allocate(selectionCounts);

        assertThat(result).isEmpty();
    }

    @Test
    void 하나의_취향만_선택되면_후보_10개를_모두_배분한다() {

        Map<String, Integer> selectionCounts = Map.of(
                "한식", 5
        );

        Map<String, Integer> result =
                candidateAllocationService.allocate(selectionCounts);

        assertThat(result)
                .containsEntry("한식", 10);
    }

    @Test
    void 선택_횟수가_0인_취향은_배분에서_제외한다() {

        Map<String, Integer> selectionCounts = new LinkedHashMap<>();
        selectionCounts.put("한식", 5);
        selectionCounts.put("카페", 0);

        Map<String, Integer> result =
                candidateAllocationService.allocate(selectionCounts);

        assertThat(result)
                .containsEntry("한식", 10)
                .doesNotContainKey("카페");
    }
}