package com.moive.MoiveBE.domain.recommendation.service;

import com.moive.MoiveBE.domain.recommendation.dto.AreaCandidate;
import com.moive.MoiveBE.domain.recommendation.dto.AreaCandidateResponse;
import com.moive.MoiveBE.domain.recommendation.dto.AreaCenter;
import com.moive.MoiveBE.domain.recommendation.dto.GooglePlaceSearchResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AreaCandidateService {

    private final AreaCandidateResolver areaCandidateResolver;

    public List<AreaCandidate> resolveCandidates(
            AreaCandidateResponse response,
            AreaCenter center
    ) {

        List<AreaCandidate> candidates = new ArrayList<>();

        for (String areaName : response.areas()) {

            GooglePlaceSearchResponse.Place place =
                    areaCandidateResolver.resolve(
                            areaName,
                            center.latitude(),
                            center.longitude()
                    );

            if (place == null) {
                continue;
            }

            candidates.add(
                    new AreaCandidate(
                            areaName,
                            place.id(),
                            place.location().latitude(),
                            place.location().longitude()
                    )
            );
        }

        return candidates;
    }
}