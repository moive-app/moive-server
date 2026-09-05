package com.moive.MoiveBE.domain.recommendation.service;

import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CandidateAllocationService {

    private static final int MAX_CANDIDATE_COUNT = 10;

    public <T> Map<T, Integer> allocate(Map<T, Integer> selectionCounts) {

        int totalSelectionCount = selectionCounts.values().stream()
                .mapToInt(Integer::intValue)
                .sum();

        if (totalSelectionCount == 0) {
            return Map.of();
        }

        Map<T, Integer> allocations = new LinkedHashMap<>();

        List<AllocationInfo<T>> allocationInfos = selectionCounts.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .map(entry -> {
                    double exactAllocation =
                            (double) entry.getValue()
                                    / totalSelectionCount
                                    * MAX_CANDIDATE_COUNT;

                    int baseAllocation = (int) Math.floor(exactAllocation);
                    double remainder = exactAllocation - baseAllocation;

                    return new AllocationInfo<>(
                            entry.getKey(),
                            baseAllocation,
                            remainder
                    );
                })
                .toList();

        int allocatedCount = 0;

        for (AllocationInfo<T> info : allocationInfos) {
            allocations.put(info.type(), info.baseAllocation());
            allocatedCount += info.baseAllocation();
        }

        int remainingCount = MAX_CANDIDATE_COUNT - allocatedCount;

        List<AllocationInfo<T>> sortedByRemainder = allocationInfos.stream()
                .sorted(Comparator.comparingDouble(
                        (AllocationInfo<T> info) -> info.remainder()
                ).reversed())
                .toList();

        for (int i = 0; i < remainingCount; i++) {
            T type = sortedByRemainder.get(i).type();
            allocations.put(type, allocations.get(type) + 1);
        }

        return allocations;
    }

    private record AllocationInfo<T>(
            T type,
            int baseAllocation,
            double remainder
    ) {
    }
}