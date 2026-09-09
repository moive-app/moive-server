package com.moive.MoiveBE.domain.recommendation.service;

import com.moive.MoiveBE.domain.recommendation.client.GooglePlacesClient;
import com.moive.MoiveBE.domain.recommendation.dto.GooglePlaceSearchResponse;
import com.moive.MoiveBE.domain.recommendation.dto.PlaceCandidate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class PlaceCandidateGenerationService {

    private final GooglePlacesClient googlePlacesClient;
    private final PlaceCandidateService placeCandidateService;

    public List<PlaceCandidate> generateCandidates(
            String regionName,
            Map<String, Integer> allocations
    ) {
        List<PlaceCandidate> candidates = new ArrayList<>();

        for (Map.Entry<String, Integer> entry : allocations.entrySet()) {

            String preferenceKeyword = entry.getKey();
            int candidateCount = entry.getValue();

            if (candidateCount <= 0) {
                continue;
            }

            String textQuery = regionName + " " + preferenceKeyword;

            GooglePlaceSearchResponse response =
                    googlePlacesClient.searchPlaces(
                            textQuery,
                            candidateCount
                    );

            if (response == null || response.places() == null) {
                continue;
            }

            List<PlaceCandidate> searchedCandidates =
                    placeCandidateService.createCandidates(
                            response.places(),
                            preferenceKeyword
                    );
            mergeCandidates(candidates, searchedCandidates);

        }

        return candidates;
    }

    private void mergeCandidates(
            List<PlaceCandidate> candidates,
            List<PlaceCandidate> searchedCandidates
    ) {
        for (PlaceCandidate searchedCandidate : searchedCandidates) {

            int existingIndex =
                    findCandidateIndex(
                            candidates,
                            searchedCandidate.googlePlaceId()
                    );

            if (existingIndex >= 0) {
                PlaceCandidate existing =
                        candidates.get(existingIndex);

                Set<String> mergedPreferenceTypes =
                        new HashSet<>(existing.preferenceTypes());

                mergedPreferenceTypes.addAll(
                        searchedCandidate.preferenceTypes()
                );

                candidates.set(
                        existingIndex,
                        new PlaceCandidate(
                                existing.googlePlaceId(),
                                existing.latitude(),
                                existing.longitude(),
                                existing.candidateOrder(),
                                mergedPreferenceTypes
                        )
                );

                continue;
            }

            candidates.add(
                    new PlaceCandidate(
                            searchedCandidate.googlePlaceId(),
                            searchedCandidate.latitude(),
                            searchedCandidate.longitude(),
                            candidates.size(),
                            searchedCandidate.preferenceTypes()
                    )
            );
        }
    }

    private int findCandidateIndex(
            List<PlaceCandidate> candidates,
            String googlePlaceId
    ) {
        for (int i = 0; i < candidates.size(); i++) {
            if (candidates.get(i)
                    .googlePlaceId()
                    .equals(googlePlaceId)) {
                return i;
            }
        }

        return -1;
    }
}