package com.moive.MoiveBE.domain.recommendation.service;

import com.moive.MoiveBE.domain.recommendation.dto.GooglePlaceSearchResponse;
import com.moive.MoiveBE.domain.recommendation.dto.PlaceCandidate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@Service
public class PlaceCandidateService {

    public List<PlaceCandidate> createCandidates(
            List<GooglePlaceSearchResponse.Place> places,
            String preferenceType
    ) {
        List<PlaceCandidate> candidates = new ArrayList<>();
        HashSet<String> seenPlaceIds = new HashSet<>();

        for (GooglePlaceSearchResponse.Place place : places) {

            if (!seenPlaceIds.add(place.id())) {
                continue;
            }

            int candidateOrder = candidates.size();

            candidates.add(new PlaceCandidate(
                    place.id(),
                    place.location().latitude(),
                    place.location().longitude(),
                    candidateOrder,
                    preferenceType
            ));
        }

        return candidates;
    }
}