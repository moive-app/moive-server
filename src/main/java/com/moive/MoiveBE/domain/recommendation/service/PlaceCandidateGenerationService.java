package com.moive.MoiveBE.domain.recommendation.service;

import com.moive.MoiveBE.domain.recommendation.client.GooglePlacesClient;
import com.moive.MoiveBE.domain.recommendation.dto.GooglePlaceSearchResponse;
import com.moive.MoiveBE.domain.recommendation.dto.PlaceCandidate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PlaceCandidateGenerationService {

    private final GooglePlacesClient googlePlacesClient;
    private final PlaceCandidateService placeCandidateService;

    public List<PlaceCandidate> generateCandidates(
            String regionName,
            Map<String, Integer> allocations
    ) {
        List<GooglePlaceSearchResponse.Place> allPlaces = new ArrayList<>();

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

            allPlaces.addAll(response.places());
        }

        return placeCandidateService.createCandidates(allPlaces);
    }
}